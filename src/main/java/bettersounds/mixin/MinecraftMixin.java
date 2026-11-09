package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suara ayunan saat menyerang ke udara (tidak mengenai apa pun). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V",
					shift = At.Shift.BEFORE),
			require = 0)
	private void bettersounds$airSwing(CallbackInfoReturnable<Boolean> cir) {
		UiSounds.swing();
	}
}
