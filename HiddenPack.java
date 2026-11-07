package bettersounds.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.util.Optional;

/**
 * Pack tersembunyi: dibaca langsung dari config/bettersounds/pack dan ditambahkan ke daftar resource
 * yang dimuat game (lewat PackRepositoryMixin), sehingga tidak muncul sebagai resource pack.
 */
public final class HiddenPack {
	/** Menjadi true kalau mixin berjalan (dipakai untuk mendeteksi kegagalan injeksi). */
	public static volatile boolean seen = false;

	private HiddenPack() {}

	public static PackResources create(PackRepository repo) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || repo != mc.getResourcePackRepository()) return null;
		seen = true;
		if (!Files.isDirectory(SoundConfig.PACK_DIR.resolve("assets"))) return null;
		PackLocationInfo info = new PackLocationInfo("bettersounds_config",
				Component.literal("Better Sounds"), PackSource.BUILT_IN, Optional.empty());
		return new PathPackResources(info, SoundConfig.PACK_DIR);
	}
}
