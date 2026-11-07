package bettersounds.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Membuat resource pack otomatis (resourcepacks/BetterSounds-Config) dari pengaturan,
 * lalu mengaktifkannya dan me-reload suara. Pack ini menimpa suara bawaan mod
 * hanya untuk event yang pengaturannya diubah.
 */
public final class PackBuilder {
	public static final String PACK_NAME = "BetterSounds-Config";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private PackBuilder() {}

	public static void build(SoundConfig cfg) throws IOException {
		Path root = SoundConfig.PACK_DIR;
		deleteRecursively(root);
		Path customDir = root.resolve("assets/minecraft/sounds/bettersounds_custom");
		Files.createDirectories(customDir);

		List<String> library = SoundConfig.library();
		for (String f : library) {
			Files.copy(SoundConfig.LIBRARY_DIR.resolve(f), customDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
		}
		Set<String> lib = new HashSet<>(library);

		Map<String, JsonObject> byNamespace = new TreeMap<>();
		for (Map.Entry<String, SoundConfig.Entry> en : cfg.entries.entrySet()) {
			SoundConfig.Entry e = en.getValue();
			if (e.isDefault()) continue;

			String key = en.getKey();
			String ns = "minecraft";
			String event = key;
			int colon = key.indexOf(':');
			if (colon >= 0) {
				ns = key.substring(0, colon);
				event = key.substring(colon + 1);
			}

			float volume = Math.max(0f, Math.min(1f, e.volume));
			float pitch = Math.max(0.5f, Math.min(2f, e.pitch));

			List<String> names = new ArrayList<>();
			if (!"default".equals(e.choice) && lib.contains(e.choice)) {
				names.add("bettersounds_custom/" + e.choice.substring(0, e.choice.length() - 4));
			} else {
				names.addAll(SoundConfig.defaultSounds().getOrDefault(key, List.of()));
			}
			if (names.isEmpty()) continue;

			JsonArray arr = new JsonArray();
			for (String n : names) {
				JsonObject s = new JsonObject();
				s.addProperty("name", n.contains(":") ? n : "minecraft:" + n);
				s.addProperty("volume", volume);
				s.addProperty("pitch", pitch);
				arr.add(s);
			}
			JsonObject ev = new JsonObject();
			ev.addProperty("replace", true);
			ev.add("sounds", arr);
			byNamespace.computeIfAbsent(ns, k -> new JsonObject()).add(event, ev);
		}

		// event pratinjau untuk tiap file library (dipakai tombol Play di layar pengaturan)
		for (String f : library) {
			String stem = f.substring(0, f.length() - 4);
			JsonArray one = new JsonArray();
			JsonObject so = new JsonObject();
			so.addProperty("name", "minecraft:bettersounds_custom/" + stem);
			one.add(so);
			JsonObject evt = new JsonObject();
			evt.add("sounds", one);
			byNamespace.computeIfAbsent("bettersounds", k -> new JsonObject()).add("lib." + stem, evt);
		}

		for (Map.Entry<String, JsonObject> n : byNamespace.entrySet()) {
			Path dir = root.resolve("assets").resolve(n.getKey());
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("sounds.json"), GSON.toJson(n.getValue()), StandardCharsets.UTF_8);
		}

		JsonObject pack = new JsonObject();
		pack.addProperty("description", "Better Sounds - pengaturan pengguna (dibuat otomatis, jangan diedit)");
		pack.addProperty("min_format", 84);
		pack.addProperty("max_format", 200);
		JsonObject mcmeta = new JsonObject();
		mcmeta.add("pack", pack);
		Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(mcmeta), StandardCharsets.UTF_8);
	}

	/**
	 * Me-reload suara. Pack konfigurasi dimuat langsung dari folder config lewat mixin
	 * (tidak muncul di daftar resource pack). Kalau mixin tidak aktif, dipakai cadangan
	 * berupa pack di folder resourcepacks.
	 */
	public static void enableAndReload() {
		Minecraft mc = Minecraft.getInstance();
		removeFolderPack(mc);
		HiddenPack.seen = false;
		mc.reloadResourcePacks();
		if (!HiddenPack.seen) {
			SoundConfig.LOGGER.warn("Injeksi pack tersembunyi tidak aktif, memakai pack di folder resourcepacks");
			fallbackFolderPack(mc);
		}
	}

	private static Path folderPackPath() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_NAME);
	}

	/** Membersihkan pack lama di folder resourcepacks (versi sebelumnya membuatnya di sana). */
	private static void removeFolderPack(Minecraft mc) {
		Path legacy = folderPackPath();
		if (!Files.exists(legacy)) return;
		try {
			deleteRecursively(legacy);
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			if (selected.remove("file/" + PACK_NAME)) {
				repo.setSelected(selected);
				mc.options.updateResourcePacks(repo);
			}
		} catch (IOException e) {
			SoundConfig.LOGGER.warn("Gagal membersihkan pack lama", e);
		}
	}

	private static void fallbackFolderPack(Minecraft mc) {
		try {
			Path dest = folderPackPath();
			deleteRecursively(dest);
			try (Stream<Path> s = Files.walk(SoundConfig.PACK_DIR)) {
				for (Path src : (Iterable<Path>) s::iterator) {
					Path target = dest.resolve(SoundConfig.PACK_DIR.relativize(src).toString());
					if (Files.isDirectory(src)) Files.createDirectories(target);
					else Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			String id = "file/" + PACK_NAME;
			if (repo.getAvailableIds().contains(id)) {
				List<String> selected = new ArrayList<>(repo.getSelectedIds());
				selected.remove(id);
				selected.add(id);
				repo.setSelected(selected);
				mc.options.updateResourcePacks(repo);
			}
			mc.reloadResourcePacks();
		} catch (IOException e) {
			SoundConfig.LOGGER.error("Gagal membuat pack cadangan", e);
		}
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> s = Files.walk(root)) {
			for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) {
				Files.delete(p);
			}
		}
	}
}
