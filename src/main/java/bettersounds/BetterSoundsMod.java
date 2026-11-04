package bettersounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Memutar suara pickup (entity.item.pickup) saat pemain melempar item.
 * Suara itu sendiri berasal dari sounds.json yang dibundel di mod ini.
 */
public class BetterSoundsMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof ItemEntity item)) return;
			// hanya item yang baru muncul (bukan yang dimuat ulang dari chunk)
			if (item.getAge() != 0) return;
			// hanya item yang dilempar pemain (death drop, dispenser, dll. tidak punya "thrower")
			if (!(item.getOwner() instanceof Player)) return;

			// volume & pitch dibuat sama dengan suara pickup vanilla
			float pitch = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F;
			level.playSound(null, item.getX(), item.getY(), item.getZ(),
					SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, pitch);
		});
	}
}
