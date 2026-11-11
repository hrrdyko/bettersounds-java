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
			if (Math.abs(v.volume - 1.0f) < 0.001f && Math.abs(v.pitchMin - 1.0f) < 0.001f
					&& Math.abs(v.pitchMax - 1.0f) < 0.001f) continue;
			try {
				String key = e.getKey();
				int c = key.indexOf(':');
				Identifier id = c >= 0
						? Identifier.fromNamespaceAndPath(key.substring(0, c), key.substring(c + 1))
						: Identifier.fromNamespaceAndPath("minecraft", key);
				m.put(id, new float[] {v.volume, v.pitchMin, v.pitchMax});
			} catch (Exception ex) {
				SoundConfig.LOGGER.warn("Kunci suara tidak valid: {}", e.getKey());
			}
		}
		map = m;
	}

	// Override sementara: pratinjau di layar pengaturan memakai nilai slider yang belum disimpan.
	private static volatile boolean ovOn = false;
	private static volatile Identifier ovId = null;
	private static volatile float ovVol = 1.0F, ovPitch = 1.0F;

	public static void setOverride(Identifier id, float vol, float pitch) {
		ovId = id;
		ovVol = vol;
		ovPitch = pitch;
		ovOn = true;
	}

	public static void clearOverride() {
		ovOn = false;
		ovId = null;
	}

	public static float volume(Identifier id) {
		if (ovOn && id != null && id.equals(ovId)) return ovVol;
		float[] v = map.get(id);
		return v == null ? 1.0F : v[0];
	}

	public static float pitchMin(Identifier id) {
		if (ovOn && id != null && id.equals(ovId)) return ovPitch;
		float[] v = map.get(id);
		return v == null ? 1.0F : v[1];
	}

	public static float pitchMax(Identifier id) {
		if (ovOn && id != null && id.equals(ovId)) return ovPitch;
		float[] v = map.get(id);
		return v == null ? 1.0F : v[2];
	}
}
