package bettersounds.client;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Menyediakan stream audio untuk suara Library yang dirender sendiri (mp3/wav/hasil edit). */
public final class CustomAudio {
	private record Data(byte[] pcm, int rate) {}

	private static final Map<String, Data> CACHE = new ConcurrentHashMap<>();
	private static final String MARK = "sounds/bettersounds_custom/";

	private CustomAudio() {}

	public static void clear() {
		CACHE.clear();
	}

	/** Mengambil nama dasar dari id file suara, atau null kalau bukan suara Library. */
	public static String stemOf(Identifier location) {
		String s = location.toString();
		int i = s.indexOf(MARK);
		if (i < 0 || !s.endsWith(".ogg")) return null;
		return s.substring(i + MARK.length(), s.length() - 4);
	}

	public static boolean has(String stem) {
		return CACHE.containsKey(stem) || Files.exists(SoundConfig.RENDER_DIR.resolve(stem + ".wav"));
	}

	public static AudioStream open(String stem, boolean loop) {
		Data d = CACHE.computeIfAbsent(stem, k -> {
			try {
				byte[] all = Files.readAllBytes(SoundConfig.RENDER_DIR.resolve(k + ".wav"));
				ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
				int rate = b.getInt(24);
				byte[] pcm = new byte[all.length - 44];
				System.arraycopy(all, 44, pcm, 0, pcm.length);
				return new Data(pcm, rate);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
		return new PcmStream(d.pcm, d.rate, loop);
	}

	private static final class PcmStream implements AudioStream {
		private final byte[] pcm;
		private final AudioFormat format;
		private final boolean loop;
		private int pos = 0;

		PcmStream(byte[] pcm, int rate, boolean loop) {
			this.pcm = pcm;
			this.format = new AudioFormat(rate, 16, 1, true, false);
			this.loop = loop;
		}

		@Override
		public AudioFormat getFormat() {
			return format;
		}

		@Override
		public ByteBuffer read(int size) throws IOException {
			int n = Math.min(size, pcm.length - pos);
			if (n <= 0 && loop && pcm.length > 0) {
				pos = 0;
				n = Math.min(size, pcm.length);
			}
			n -= n % 2;
			if (n <= 0) return ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
			ByteBuffer b = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder());
			b.put(pcm, pos, n);
			b.flip();
			pos += n;
			return b;
		}

		@Override
		public void close() throws IOException {
		}
	}
}
