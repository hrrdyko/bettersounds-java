package bettersounds.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Pengaturan per suara (volume, pitch, pilihan suara) + pustaka suara buatan pengguna (file dan grup). */
public final class SoundConfig {
	public static final Logger LOGGER = LoggerFactory.getLogger("bettersounds");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("bettersounds");
	public static final Path LIBRARY_DIR = DIR.resolve("library");
	private static final Path FILE = DIR.resolve("config.json");
	/** Pack internal (tidak muncul di daftar resource pack); dipakai lewat mixin. */
	public static final Path PACK_DIR = DIR.resolve("pack");
	/** Hasil render (WAV 16-bit mono) untuk suara yang bukan .ogg atau yang sudah diedit. */
	public static final Path RENDER_DIR = DIR.resolve("rendered");

	private static List<String> allKeys;
	private static Map<String, List<String>> betterDefaults;
	private static List<String> libCache;
	private static final Map<String, List<String>> MEMBER_CACHE = new HashMap<>();

	public static final class Entry {
		public float volume = 1.0f;
		/** Rentang pitch acak (min..max) tiap kali suara diputar. */
		public float pitchMin = 1.0f;
		public float pitchMax = 1.0f;
		/** Lama: satu nilai pitch. Dimigrasikan ke pitchMin/pitchMax saat config dimuat. */
		public float pitch = 1.0f;
		/** "default" = Better Sounds, "vanilla" = suara asli Minecraft, atau nama item Library. */
		public String choice = "default";

		boolean isDefault() {
			return Math.abs(volume - 1.0f) < 0.001f && Math.abs(pitchMin - 1.0f) < 0.001f
					&& Math.abs(pitchMax - 1.0f) < 0.001f && "default".equals(choice);
		}

		void normalize() {
			volume = Math.max(0f, Math.min(2.0f, volume)); // batas volume per event: 200%
			if (Math.abs(pitch - 1.0f) > 0.001f && Math.abs(pitchMin - 1.0f) < 0.001f && Math.abs(pitchMax - 1.0f) < 0.001f) {
				pitchMin = pitch;
				pitchMax = pitch;
			}
			pitch = 1.0f;
			if (pitchMin > pitchMax) {
				float t = pitchMin;
				pitchMin = pitchMax;
				pitchMax = t;
			}
		}
	}

	/** Pengaturan edit suara (potong, kecepatan, volume) untuk satu file di Library. */
	public static final class Edit {
		public double startMs = 0;
		public double endMs = 0; // 0 = sampai akhir
		public float speed = 1.0f;
		public float gain = 100f; // persen, 0 - 1000

		public boolean isDefault() {
			return startMs <= 0.5 && endMs <= 0.5 && Math.abs(speed - 1.0f) < 0.001f && Math.abs(gain - 100f) < 0.01f;
		}

		public void copyFrom(Edit o) {
			startMs = o.startMs;
			endMs = o.endMs;
			speed = o.speed;
			gain = o.gain;
		}
	}

	public Map<String, Entry> entries = new TreeMap<>();
	public Map<String, Edit> edits = new TreeMap<>();

	public Entry get(String key) {
		return entries.computeIfAbsent(key, k -> new Entry());
	}

	public Edit edit(String file) {
		return edits.computeIfAbsent(file, k -> new Edit());
	}

	// ---------- simpan / muat ----------
	public static SoundConfig load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					SoundConfig c = GSON.fromJson(r, SoundConfig.class);
					if (c != null) {
						c.entries = c.entries == null ? new TreeMap<>() : new TreeMap<>(c.entries);
						c.edits = c.edits == null ? new TreeMap<>() : new TreeMap<>(c.edits);
						for (Entry en : c.entries.values()) en.normalize();
						return c;
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Gagal membaca config Better Sounds, memakai default", e);
		}
		return new SoundConfig();
	}

	public void save() throws IOException {
		Files.createDirectories(DIR);
		SoundConfig out = new SoundConfig();
		for (Map.Entry<String, Entry> e : entries.entrySet()) {
			if (!e.getValue().isDefault()) out.entries.put(e.getKey(), e.getValue());
		}
		for (Map.Entry<String, Edit> e : edits.entrySet()) {
			if (!e.getValue().isDefault()) out.edits.put(e.getKey(), e.getValue());
		}
		Files.writeString(FILE, GSON.toJson(out), StandardCharsets.UTF_8);
	}

	// ---------- semua event suara di game (vanilla + mod lain) ----------
	public static synchronized List<String> allKeys() {
		if (allKeys != null && !allKeys.isEmpty()) return allKeys;
		Set<String> keys = new TreeSet<>();
		try {
			for (SoundEvent se : BuiltInRegistries.SOUND_EVENT) {
				String s = se.location().toString();
				keys.add(s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s);
			}
		} catch (Exception e) {
			LOGGER.error("Gagal membaca daftar event suara", e);
		}
		allKeys = new ArrayList<>(keys);
		return allKeys;
	}

	// ---------- suara Better Sounds bawaan (event -> daftar file) ----------
	public static synchronized Map<String, List<String>> betterDefaults() {
		if (betterDefaults != null) return betterDefaults;
		Map<String, List<String>> m = new TreeMap<>();
		try {
			Path p = FabricLoader.getInstance().getModContainer("bettersounds").orElseThrow()
					.findPath("assets/bettersounds/default_events.json").orElseThrow();
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
				for (Map.Entry<String, JsonElement> e : root.entrySet()) {
					List<String> names = new ArrayList<>();
					for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) names.add(s.getAsString());
					m.put(e.getKey(), names);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Gagal membaca daftar suara Better Sounds bawaan", e);
		}
		betterDefaults = m;
		return m;
	}

	public static boolean hasBetterDefault(String key) {
		return betterDefaults().containsKey(key);
	}

	// ---------- pustaka suara pengguna: file tunggal dan grup (folder) ----------
	public static boolean isSupported(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".wav");
	}

	/** Nama dasar tanpa ekstensi; dipakai sebagai id suara di Minecraft. */
	public static String stem(String file) {
		int d = file.lastIndexOf('.');
		int s = file.lastIndexOf('/');
		return d > s + 1 ? file.substring(0, d) : file;
	}

	public static synchronized void invalidateLibrary() {
		libCache = null;
		MEMBER_CACHE.clear();
	}

	/** Item Library: file tunggal (dengan ekstensi) dan grup (nama folder). */
	public static synchronized List<String> library() {
		if (libCache != null) return libCache;
		List<String> out = new ArrayList<>();
		try {
			if (Files.isDirectory(LIBRARY_DIR)) {
				try (Stream<Path> s = Files.list(LIBRARY_DIR)) {
					s.forEach(p -> {
						String n = p.getFileName().toString();
						if (Files.isDirectory(p) || isSupported(n)) out.add(n);
					});
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Gagal membaca folder library", e);
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		libCache = out;
		return out;
	}

	public static boolean isGroup(String item) {
		return Files.isDirectory(LIBRARY_DIR.resolve(item));
	}

	/** Anggota item: untuk file tunggal berisi dirinya sendiri, untuk grup berisi semua file di dalamnya. */
	public static synchronized List<String> members(String item) {
		List<String> c = MEMBER_CACHE.get(item);
		if (c != null) return c;
		List<String> out = new ArrayList<>();
		if (isGroup(item)) {
			try (Stream<Path> s = Files.list(LIBRARY_DIR.resolve(item))) {
				s.forEach(p -> {
					String n = p.getFileName().toString();
					if (!Files.isDirectory(p) && isSupported(n)) out.add(item + "/" + n);
				});
			} catch (IOException e) {
				LOGGER.warn("Gagal membaca grup {}", item, e);
			}
			out.sort(String.CASE_INSENSITIVE_ORDER);
		} else {
			out.add(item);
		}
		MEMBER_CACHE.put(item, out);
		return out;
	}

	public static List<String> allMembers() {
		List<String> out = new ArrayList<>();
		for (String item : library()) out.addAll(members(item));
		return out;
	}

	/** Suara perlu dirender sendiri kalau bukan .ogg atau sudah diedit. */
	public boolean needsRender(String rel) {
		return !rel.toLowerCase(Locale.ROOT).endsWith(".ogg") || !edit(rel).isDefault();
	}

	public void removeFromLibrary(String item) {
		try {
			if (isGroup(item)) {
				deleteTree(LIBRARY_DIR.resolve(item));
				deleteTree(RENDER_DIR.resolve(item));
				edits.keySet().removeIf(k -> k.startsWith(item + "/"));
			} else {
				Files.deleteIfExists(LIBRARY_DIR.resolve(item));
				Files.deleteIfExists(RENDER_DIR.resolve(stem(item) + ".wav"));
				edits.remove(item);
			}
		} catch (IOException e) {
			LOGGER.warn("Gagal menghapus {}", item, e);
		}
		invalidateLibrary();
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> s = Files.walk(root)) {
			for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
		}
	}

	// ---------- ganti nama / hapus anggota ----------
	private static String cleanBase(String input) {
		String b = sanitize(input.trim()).replace('.', '_');
		return b;
	}

	/**
	 * Mengganti nama file atau grup di Library (ekstensi file tetap). Referensi di pengaturan ikut diperbarui.
	 * @return nama baru, atau null kalau gagal (nama kosong atau sudah dipakai).
	 */
	public String renameItem(String old, String input) {
		String base = cleanBase(input);
		if (base.isEmpty()) return null;
		boolean group = isGroup(old);
		String ext = group ? "" : old.substring(stem(old).length());
		String neu = base + ext;
		if (neu.equals(old)) return old;
		Set<String> taken = takenIds();
		taken.remove(group ? old : stem(old));
		if (taken.contains(base)) return null;
		try {
			Files.move(LIBRARY_DIR.resolve(old), LIBRARY_DIR.resolve(neu));
		} catch (IOException e) {
			LOGGER.warn("Gagal mengganti nama {}", old, e);
			return null;
		}
		if (group) {
			Map<String, Edit> moved = new TreeMap<>();
			for (Map.Entry<String, Edit> e : edits.entrySet()) {
				if (e.getKey().startsWith(old + "/")) moved.put(neu + e.getKey().substring(old.length()), e.getValue());
			}
			edits.keySet().removeIf(k -> k.startsWith(old + "/"));
			edits.putAll(moved);
		} else {
			Edit ed = edits.remove(old);
			if (ed != null) edits.put(neu, ed);
		}
		for (Entry en : entries.values()) if (old.equals(en.choice)) en.choice = neu;
		invalidateLibrary();
		return neu;
	}

	/** Mengganti nama satu file di dalam grup. @return path relatif baru ("grup/nama.ext"), atau null kalau gagal. */
	public String renameMember(String group, String oldRel, String input) {
		String base = cleanBase(input);
		if (base.isEmpty()) return null;
		String leaf = oldRel.substring(oldRel.lastIndexOf('/') + 1);
		String ext = leaf.substring(stem(leaf).length());
		String neuRel = group + "/" + base + ext;
		if (neuRel.equals(oldRel)) return oldRel;
		for (String m : members(group)) {
			if (!m.equals(oldRel) && stem(m).equals(group + "/" + base)) return null;
		}
		try {
			Files.move(LIBRARY_DIR.resolve(oldRel), LIBRARY_DIR.resolve(neuRel));
		} catch (IOException e) {
			LOGGER.warn("Gagal mengganti nama {}", oldRel, e);
			return null;
		}
		Edit ed = edits.remove(oldRel);
		if (ed != null) edits.put(neuRel, ed);
		invalidateLibrary();
		return neuRel;
	}

	public void removeMember(String rel) {
		try {
			Files.deleteIfExists(LIBRARY_DIR.resolve(rel));
		} catch (IOException e) {
			LOGGER.warn("Gagal menghapus {}", rel, e);
		}
		edits.remove(rel);
		invalidateLibrary();
	}

	// ---------- impor ----------
	/** Nama file harus huruf kecil [a-z0-9._-] agar valid sebagai nama suara Minecraft. */
	static String sanitize(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static Set<String> takenIds() {
		Set<String> taken = new TreeSet<>();
		for (String item : library()) taken.add(isGroup(item) ? item : stem(item));
		return taken;
	}

	/** Mengimpor file, folder (jadi grup), atau .zip (jadi grup). Mengembalikan jumlah file suara yang masuk. */
	public static int importPaths(List<Path> paths) {
		int total = 0;
		try {
			Files.createDirectories(LIBRARY_DIR);
		} catch (IOException e) {
			LOGGER.error("Tidak bisa membuat folder library", e);
			return 0;
		}
		for (Path p : paths) {
			try {
				String n = p.getFileName().toString();
				if (Files.isDirectory(p)) total += importFolder(p);
				else if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) total += importZip(p);
				else total += importFile(p) ? 1 : 0;
			} catch (Exception e) {
				LOGGER.warn("Gagal mengimpor {}", p, e);
			}
		}
		invalidateLibrary();
		return total;
	}

	private static boolean importFile(Path p) throws IOException {
		String name = sanitize(p.getFileName().toString());
		if (!isSupported(name) || !looksValid(p, name)) {
			LOGGER.warn("Dilewati (format tidak didukung): {}", p);
			return false;
		}
		String stem = stem(name);
		String ext = name.substring(stem.length());
		Set<String> taken = takenIds();
		// file dengan nama sama akan ditimpa; selain itu id harus unik
		for (String f : library()) if (f.equals(name)) taken.remove(stem);
		String cand = stem;
		int i = 2;
		while (taken.contains(cand)) cand = stem + "_" + i++;
		Files.copy(p, LIBRARY_DIR.resolve(cand + ext), StandardCopyOption.REPLACE_EXISTING);
		return true;
	}

	private static String uniqueGroupName(String base) {
		String b = sanitize(base).replace('.', '_');
		if (b.isEmpty()) b = "group";
		Set<String> taken = takenIds();
		String cand = b;
		int i = 2;
		while (taken.contains(cand)) cand = b + "_" + i++;
		return cand;
	}

	private static int importFolder(Path dir) throws IOException {
		String group = uniqueGroupName(dir.getFileName().toString());
		Path dest = LIBRARY_DIR.resolve(group);
		Files.createDirectories(dest);
		int n = 0;
		try (Stream<Path> s = Files.walk(dir, 3)) {
			for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
				String name = sanitize(f.getFileName().toString());
				if (!isSupported(name) || !looksValid(f, name)) continue;
				Files.copy(f, uniqueIn(dest, name), StandardCopyOption.REPLACE_EXISTING);
				n++;
			}
		}
		if (n == 0) deleteTree(dest);
		return n;
	}

	private static int importZip(Path zip) throws IOException {
		String base = zip.getFileName().toString();
		base = base.substring(0, base.length() - 4);
		String group = uniqueGroupName(base);
		Path dest = LIBRARY_DIR.resolve(group);
		Files.createDirectories(dest);
		int n = 0;
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry e;
			while ((e = in.getNextEntry()) != null) {
				if (e.isDirectory()) continue;
				String name = sanitize(Path.of(e.getName()).getFileName().toString());
				if (!isSupported(name)) continue;
				Path target = uniqueIn(dest, name);
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				if (!looksValid(target, name)) {
					Files.deleteIfExists(target);
					continue;
				}
				n++;
			}
		}
		if (n == 0) deleteTree(dest);
		return n;
	}

	private static Path uniqueIn(Path dir, String name) {
		String stem = stem(name);
		String ext = name.substring(stem.length());
		Path p = dir.resolve(name);
		int i = 2;
		while (Files.exists(p)) p = dir.resolve(stem + "_" + i++ + ext);
		return p;
	}

	private static boolean looksValid(Path p, String name) {
		try (InputStream in = Files.newInputStream(p)) {
			byte[] h = in.readNBytes(128);
			String s = new String(h, StandardCharsets.ISO_8859_1);
			if (name.endsWith(".ogg")) return s.startsWith("OggS") && s.contains("vorbis");
			if (name.endsWith(".wav")) return s.startsWith("RIFF");
			if (name.endsWith(".mp3")) {
				if (s.startsWith("ID3")) return true;
				return h.length > 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0;
			}
			return false;
		} catch (IOException e) {
			return false;
		}
	}
}
