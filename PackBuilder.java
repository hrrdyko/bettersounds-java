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
		Path root = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_NAME);
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

	/** Mengaktifkan pack konfigurasi (di posisi teratas) dan me-reload resource. */
	public static void enableAndReload() {
		Minecraft mc = Minecraft.getInstance();
		PackRepository repo = mc.getResourcePackRepository();
		repo.reload();
		String id = "file/" + PACK_NAME;
		if (repo.getAvailableIds().contains(id)) {
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			selected.remove(id);
			selected.add(id);
			repo.setSelected(selected);
			mc.options.updateResourcePacks(repo);
		} else {
			SoundConfig.LOGGER.warn("Pack {} tidak terdeteksi (format pack tidak kompatibel?)", id);
		}
		mc.reloadResourcePacks();
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
