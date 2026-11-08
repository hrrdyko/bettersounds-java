package bettersounds.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.OggAudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Decode (ogg/mp3/wav), edit (potong, kecepatan, volume), dan render ke WAV untuk dipakai game. */
public final class AudioPipeline {
	/** Audio mono (float -1..1) beserta sample rate. */
	public record Pcm(float[] mono, int rate) {
		public double durationMs() {
			return mono.length * 1000.0 / rate;
		}
	}

	private static final Map<String, Pcm> CACHE = new HashMap<>();
	private static final Map<String, Long> CACHE_STAMP = new HashMap<>();

	private AudioPipeline() {}

	// ---------------- decode ----------------
	public static synchronized Pcm decodeCached(String file) throws Exception {
		Path p = SoundConfig.LIBRARY_DIR.resolve(file);
		long stamp = Files.getLastModifiedTime(p).toMillis();
		Pcm c = CACHE.get(file);
		if (c != null && CACHE_STAMP.getOrDefault(file, -1L) == stamp) return c;
		Pcm d = decode(p);
		CACHE.put(file, d);
		CACHE_STAMP.put(file, stamp);
		return d;
	}

	public static Pcm decode(Path p) throws Exception {
		String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		if (n.endsWith(".mp3")) return decodeMp3(p);
		if (n.endsWith(".wav")) return decodeWav(p);
		if (n.endsWith(".ogg")) return decodeOgg(p);
		throw new IOException("Format tidak didukung: " + n);
	}

	private static final class FloatBuf {
		float[] a = new float[1 << 16];
		int n = 0;

		void add(float v) {
			if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
			a[n++] = v;
		}

		float[] toArray() {
			return Arrays.copyOf(a, n);
		}
	}

	private static Pcm decodeMp3(Path p) throws Exception {
		FloatBuf out = new FloatBuf();
		int rate = 44100;
		try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
			Bitstream bs = new Bitstream(in);
			Decoder dec = new Decoder();
			Header h;
			while ((h = bs.readFrame()) != null) {
				SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
				rate = dec.getOutputFrequency();
				int ch = Math.max(1, dec.getOutputChannels());
				short[] buf = sb.getBuffer();
				int len = sb.getBufferLength();
				for (int i = 0; i + ch <= len; i += ch) {
					int sum = 0;
					for (int c = 0; c < ch; c++) sum += buf[i + c];
					out.add(sum / (float) ch / 32768f);
				}
				bs.closeFrame();
			}
			bs.close();
		}
		return new Pcm(out.toArray(), rate);
	}

	private static Pcm decodeWav(Path p) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(p.toFile())) {
			AudioFormat src = in.getFormat();
			int ch = Math.max(1, src.getChannels());
			AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, ch, ch * 2,
					src.getSampleRate(), false);
			try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
				return fromPcm16(pcm.readAllBytes(), ch, Math.round(src.getSampleRate()));
			}
		}
	}

	private static Pcm decodeOgg(Path p) throws Exception {
		try (InputStream in = Files.newInputStream(p); AudioStream ogg = new OggAudioStream(in)) {
			AudioFormat f = ogg.getFormat();
			java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
			while (true) {
				ByteBuffer b = ogg.read(16384);
				if (b == null || !b.hasRemaining()) break;
				byte[] chunk = new byte[b.remaining()];
				b.get(chunk);
				bos.write(chunk);
			}
			return fromPcm16(bos.toByteArray(), Math.max(1, f.getChannels()), Math.round(f.getSampleRate()));
		}
	}

	private static Pcm fromPcm16(byte[] data, int ch, int rate) {
		int frames = data.length / (2 * ch);
		float[] mono = new float[frames];
		for (int i = 0; i < frames; i++) {
			int sum = 0;
			for (int c = 0; c < ch; c++) {
				int idx = (i * ch + c) * 2;
				sum += (short) ((data[idx + 1] << 8) | (data[idx] & 0xFF));
			}
			mono[i] = sum / (float) ch / 32768f;
		}
		return new Pcm(mono, rate);
	}

	// ---------------- edit ----------------
	/** Menerapkan potong, kecepatan (mengubah pitch seperti kaset), dan volume. Hasil: PCM 16-bit mono. */
	public static short[] render(Pcm in, SoundConfig.Edit e) {
		int total = in.mono().length;
		int s = (int) Math.max(0, Math.min(total, e.startMs / 1000.0 * in.rate()));
		int t = e.endMs <= 0.5 ? total : (int) Math.max(s, Math.min(total, e.endMs / 1000.0 * in.rate()));
		double speed = Math.max(0.25, Math.min(4.0, e.speed));
		int n = t - s;
		int outN = Math.max(0, (int) (n / speed));
		float g = Math.max(0f, Math.min(1000f, e.gain)) / 100f;
		short[] out = new short[outN];
		boolean trimmed = s > 0 || t < total;
		int fade = trimmed ? Math.min(132, outN / 2) : 0;
		for (int i = 0; i < outN; i++) {
			double pos = s + i * speed;
			int i0 = (int) pos;
			float fr = (float) (pos - i0);
			float a = in.mono()[Math.min(i0, total - 1)];
			float b = i0 + 1 < t ? in.mono()[i0 + 1] : a;
			float v = (a + (b - a) * fr) * g;
			if (fade > 0) {
				if (i < fade) v *= i / (float) fade;
				else if (i >= outN - fade) v *= (outN - 1 - i) / (float) fade;
			}
			out[i] = (short) Math.round(softClip(v) * 32767f);
		}
		return out;
	}

	private static float softClip(float v) {
		float a = Math.abs(v);
		if (a <= 0.8f) return v;
		float y = 0.8f + 0.2f * (float) Math.tanh((a - 0.8f) / 0.2f);
		return v < 0 ? -y : y;
	}

	// ---------------- render ke WAV ----------------
	public static void writeWav(Path path, short[] pcm, int rate) throws IOException {
		ByteBuffer b = ByteBuffer.allocate(44 + pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(36 + pcm.length * 2).put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate)
				.putInt(rate * 2).putShort((short) 2).putShort((short) 16);
		b.put("data".getBytes()).putInt(pcm.length * 2);
		for (short v : pcm) b.putShort(v);
		Files.createDirectories(path.getParent());
		Files.write(path, b.array());
	}

	/** Merender semua suara Library yang perlu (bukan .ogg atau sudah diedit); membersihkan sisa lama. */
	public static void renderLibrary(SoundConfig cfg) throws IOException {
		Set<String> keep = new HashSet<>();
		for (String rel : SoundConfig.allMembers()) {
			if (!cfg.needsRender(rel)) continue;
			String stem = SoundConfig.stem(rel);
			try {
				Pcm in = decodeCached(rel);
				short[] out = render(in, cfg.edit(rel));
				writeWav(SoundConfig.RENDER_DIR.resolve(stem + ".wav"), out, in.rate());
				keep.add(stem + ".wav");
			} catch (Exception ex) {
				SoundConfig.LOGGER.warn("Gagal merender {}", rel, ex);
			}
		}
		if (Files.isDirectory(SoundConfig.RENDER_DIR)) {
			try (java.util.stream.Stream<Path> s = Files.walk(SoundConfig.RENDER_DIR)) {
				for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
					String rel = SoundConfig.RENDER_DIR.relativize(p).toString().replace(java.io.File.separatorChar, '/');
					if (!keep.contains(rel)) Files.deleteIfExists(p);
				}
			}
		}
		CustomAudio.clear();
	}

	// ---------------- pratinjau (Java Sound, tanpa reload game) ----------------
	private static javax.sound.sampled.Clip current;

	public static synchronized void stopPreview() {
		if (current != null) {
			try {
				current.stop();
				current.close();
			} catch (Throwable ignored) {
			}
			current = null;
		}
	}

	public static synchronized void preview(Pcm in, SoundConfig.Edit e) {
		stopPreview();
		try {
			short[] pcm = render(in, e);
			byte[] bytes = new byte[pcm.length * 2];
			for (int i = 0; i < pcm.length; i++) {
				int v = (int) (pcm[i] * 0.7f); // pratinjau dikecilkan sedikit supaya aman di telinga
				bytes[2 * i] = (byte) v;
				bytes[2 * i + 1] = (byte) (v >> 8);
			}
			AudioFormat fmt = new AudioFormat(in.rate(), 16, 1, true, false);
			javax.sound.sampled.Clip clip = AudioSystem.getClip();
			clip.open(fmt, bytes, 0, bytes.length);
			clip.start();
			current = clip;
		} catch (Throwable t) {
			SoundConfig.LOGGER.warn("Pratinjau audio gagal", t);
		}
	}
}
