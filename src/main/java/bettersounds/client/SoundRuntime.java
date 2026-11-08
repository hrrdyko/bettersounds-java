package bettersounds.client;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Pengali volume/pitch yang dipakai mixin saat suara diputar (berlaku untuk semua event suara). */
public final class SoundRuntime {
	private static volatile Map<Identifier, float[]> map = Map.of();

	private SoundRuntime() {}

	public static void rebuild(SoundConfig cfg) {
		Map<Identifier, float[]> m = new HashMap<>();
		for (Map.Entry<String, SoundConfig.Entry> e : cfg.entries.entrySet()) {
			SoundConfig.Entry v = e.getValue();
			if (v.volume >= 0.999f && Math.abs(v.pitch - 1.0f) < 0.001f) continue;
			try {
				String key = e.getKey();
				int c = key.indexOf(':');
				Identifier id = c >= 0
						? Identifier.fromNamespaceAndPath(key.substring(0, c), key.substring(c + 1))
						: Identifier.fromNamespaceAndPath("minecraft", key);
				m.put(id, new float[] {v.volume, v.pitch});
			} catch (Exception ex) {
				SoundConfig.LOGGER.warn("Kunci suara tidak valid: {}", e.getKey());
			}
		}
		map = m;
	}

	public static float volume(Identifier id) {
		float[] v = map.get(id);
		return v == null ? 1.0F : v[0];
	}

	public static float pitch(Identifier id) {
		float[] v = map.get(id);
		return v == null ? 1.0F : v[1];
	}
}
