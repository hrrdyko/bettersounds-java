package bettersounds.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Pengaturan per suara (volume, pitch, pilihan suara) + pustaka suara buatan pengguna. */
public final class SoundConfig {
	public static final Logger LOGGER = LoggerFactory.getLogger("bettersounds");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("bettersounds");
	public static final Path LIBRARY_DIR = DIR.resolve("library");
	private static final Path FILE = DIR.resolve("config.json");
	/** Pack internal (tidak muncul di daftar resource pack); dipakai lewat mixin. */
	public static final Path PACK_DIR = DIR.resolve("pack");
	private static List<String> allKeys;

	public static final class Entry {
		public float volume = 1.0f;
		public float pitch = 1.0f;
		public String choice = "default";

		boolean isDefault() {
			return volume >= 0.999f && Math.abs(pitch - 1.0f) < 0.001f && "default".equals(choice);
		}
	}

	public Map<String, Entry> entries = new TreeMap<>();

	public Entry get(String key) {
		return entries.computeIfAbsent(key, k -> new Entry());
	}

	// ---------- simpan / muat ----------
	public static SoundConfig load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					SoundConfig c = GSON.fromJson(r, SoundConfig.class);
					if (c != null) {
						c.entries = c.entries == null ? new TreeMap<>() : new TreeMap<>(c.entries);
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

	// ---------- pustaka suara pengguna ----------
	public static List<String> library() {
		List<String> out = new ArrayList<>();
		try {
			if (Files.isDirectory(LIBRARY_DIR)) {
				try (Stream<Path> s = Files.list(LIBRARY_DIR)) {
					s.map(p -> p.getFileName().toString())
							.filter(n -> n.endsWith(".ogg"))
							.sorted()
							.forEach(out::add);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Gagal membaca folder library", e);
		}
		return out;
	}

	public static void removeFromLibrary(String file) {
		try {
			Files.deleteIfExists(LIBRARY_DIR.resolve(file));
		} catch (IOException e) {
			LOGGER.warn("Gagal menghapus {}", file, e);
		}
	}

	/** Nama file harus huruf kecil [a-z0-9._-] agar valid sebagai nama suara Minecraft. */
	static String sanitize(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	/** Menyalin file .ogg (Vorbis) ke folder library. Mengembalikan jumlah file yang berhasil diimpor. */
	public static int importFiles(List<Path> paths) {
		int ok = 0;
		try {
			Files.createDirectories(LIBRARY_DIR);
		} catch (IOException e) {
			LOGGER.error("Tidak bisa membuat folder library", e);
			return 0;
		}
		for (Path p : paths) {
			String name = sanitize(p.getFileName().toString());
			if (!name.endsWith(".ogg") || !looksLikeVorbis(p)) {
				LOGGER.warn("Dilewati (bukan .ogg Vorbis): {}", p);
				continue;
			}
			try {
				Files.copy(p, LIBRARY_DIR.resolve(name), StandardCopyOption.REPLACE_EXISTING);
				ok++;
			} catch (IOException e) {
				LOGGER.warn("Gagal menyalin {}", p, e);
			}
		}
		return ok;
	}

	private static boolean looksLikeVorbis(Path p) {
		try (InputStream in = Files.newInputStream(p)) {
			byte[] head = in.readNBytes(128);
			String s = new String(head, StandardCharsets.ISO_8859_1);
			return s.startsWith("OggS") && s.contains("vorbis");
		} catch (IOException e) {
			return false;
		}
	}
}
