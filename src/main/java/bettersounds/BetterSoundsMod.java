package bettersounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Better Sounds: suara saat pemain melempar (drop) item.
 * Event suara "bettersounds:item.drop" didefinisikan di assets/bettersounds/sounds.json
 * dan bisa dikonfigurasi lewat layar pengaturan (/bettersounds).
 */
public class BetterSoundsMod implements ModInitializer {
	public static final String MOD_ID = "bettersounds";
	public static SoundEvent ITEM_DROP;

	@Override
	public void onInitialize() {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "item.drop");
		ITEM_DROP = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof ItemEntity item)) return;
			// hanya item yang baru muncul (bukan yang dimuat ulang dari chunk)
			if (item.getAge() != 0) return;
			// hanya item yang dilempar pemain (death drop, dispenser, dll. tidak punya "thrower")
			if (!(item.getOwner() instanceof Player)) return;

			float pitch = (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F;
			level.playSound(null, item.getX(), item.getY(), item.getZ(),
					ITEM_DROP, SoundSource.PLAYERS, 0.3F, pitch);
		});
	}
}
