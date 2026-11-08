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
 * Membuat pack tersembunyi (config/bettersounds/pack) berisi:
 * suara Better Sounds untuk event yang pilihannya "Default", dan suara Library untuk event yang memilihnya.
 * Event berpilihan "Vanilla" tidak ditulis sehingga memakai suara asli Minecraft.
 */
public final class PackBuilder {
	public static final String PACK_NAME = "BetterSounds-Config";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private PackBuilder() {}

	public static void build(SoundConfig cfg) throws IOException {
		build(cfg, true);
	}

	/** @param render true = render ulang suara Library (mp3/wav/hasil edit); false = pakai hasil render yang sudah ada. */
	public static void build(SoundConfig cfg, boolean render) throws IOException {
		Path root = SoundConfig.PACK_DIR;
		deleteRecursively(root);
		Path customDir = root.resolve("assets/minecraft/sounds/bettersounds_custom");
		Files.createDirectories(customDir);

		SoundConfig.invalidateLibrary();
		List<String> items = SoundConfig.library();
		if (render) AudioPipeline.renderLibrary(cfg);
		for (String rel : SoundConfig.allMembers()) {
			Path dest = customDir.resolve(SoundConfig.stem(rel) + ".ogg");
			Files.createDirectories(dest.getParent());
			if (cfg.needsRender(rel)) copyPlaceholder(dest); // isi sebenarnya disuplai lewat CustomAudio
			else Files.copy(SoundConfig.LIBRARY_DIR.resolve(rel), dest, StandardCopyOption.REPLACE_EXISTING);
		}
		Set<String> libSet = new HashSet<>(items);

		Map<String, JsonObject> byNamespace = new TreeMap<>();
		JsonObject mc = byNamespace.computeIfAbsent("minecraft", k -> new JsonObject());

		// 1) suara Better Sounds untuk event yang memakai "Default"
		for (Map.Entry<String, List<String>> d : SoundConfig.betterDefaults().entrySet()) {
			SoundConfig.Entry e = cfg.entries.get(d.getKey());
			if (e != null && !"default".equals(e.choice)) continue; // "vanilla" atau suara Library
			JsonArray arr = new JsonArray();
			for (String name : d.getValue()) {
				JsonObject s = new JsonObject();
				s.addProperty("name", "minecraft:" + name);
				arr.add(s);
			}
			JsonObject ev = new JsonObject();
			ev.addProperty("replace", true);
			ev.add("sounds", arr);
			mc.add(d.getKey(), ev);
		}

		// 2) suara Library (file tunggal atau grup yang diputar acak)
		for (Map.Entry<String, SoundConfig.Entry> en : cfg.entries.entrySet()) {
			SoundConfig.Entry e = en.getValue();
			if ("default".equals(e.choice) || "vanilla".equals(e.choice) || !libSet.contains(e.choice)) continue;

			String key = en.getKey();
			String ns = "minecraft";
			String event = key;
			int colon = key.indexOf(':');
			if (colon >= 0) {
				ns = key.substring(0, colon);
				event = key.substring(colon + 1);
			}

			JsonArray arr = new JsonArray();
			for (String rel : SoundConfig.members(e.choice)) {
				JsonObject s1 = new JsonObject();
				s1.addProperty("name", "minecraft:bettersounds_custom/" + SoundConfig.stem(rel));
				if (cfg.needsRender(rel)) s1.addProperty("stream", true);
				arr.add(s1);
			}
			if (arr.isEmpty()) continue;
			JsonObject ev = new JsonObject();
			ev.addProperty("replace", true);
			ev.add("sounds", arr);
			byNamespace.computeIfAbsent(ns, k -> new JsonObject()).add(event, ev);
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

	private static void copyPlaceholder(Path dest) throws IOException {
		Path src = FabricLoader.getInstance().getModContainer("bettersounds").orElseThrow()
				.findPath("assets/bettersounds/placeholder.ogg").orElseThrow();
		Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
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

	static void fallbackFolderPack(Minecraft mc) {
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
