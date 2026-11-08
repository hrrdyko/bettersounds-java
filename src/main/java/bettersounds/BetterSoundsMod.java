package bettersounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Better Sounds: mendaftarkan event suara tambahan dan suara saat pemain melempar (drop) item. */
public class BetterSoundsMod implements ModInitializer {
	public static final String MOD_ID = "bettersounds";

	@Override
	public void onInitialize() {
		ModSounds.registerAll();

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof ItemEntity item)) return;
			// hanya item yang baru muncul (bukan yang dimuat ulang dari chunk)
			if (item.getAge() != 0) return;
			// hanya item yang dilempar pemain (death drop, dispenser, dll. tidak punya "thrower")
			if (!(item.getOwner() instanceof Player)) return;

			float pitch = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F;
			level.playSound(null, item.getX(), item.getY(), item.getZ(),
					ModSounds.ITEM_DROP, SoundSource.PLAYERS, 0.3F, pitch);
		});
	}
}
