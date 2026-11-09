package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara ayunan saat menyerang entitas. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"), require = 0)
	private void bettersounds$attackSwing(Player player, Entity target, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && player == mc.player) UiSounds.swing();
	}
}
