package bettersounds.mixin;

import bettersounds.client.SoundRuntime;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Menerapkan pengaturan volume/pitch per suara ke semua suara yang diputar. */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
	@Inject(method = "getVolume", at = @At("RETURN"), cancellable = true, require = 0)
	private void bettersounds$volume(CallbackInfoReturnable<Float> cir) {
		float m = SoundRuntime.volume(((SoundInstance) (Object) this).getIdentifier());
		if (m != 1.0F) cir.setReturnValue(cir.getReturnValue() * m);
	}

	@Unique
	private float bettersounds$pitchMul = Float.NaN;

	@Inject(method = "getPitch", at = @At("RETURN"), cancellable = true, require = 0)
	private void bettersounds$pitch(CallbackInfoReturnable<Float> cir) {
		net.minecraft.resources.Identifier id = ((SoundInstance) (Object) this).getIdentifier();
		float lo = SoundRuntime.pitchMin(id);
		float hi = SoundRuntime.pitchMax(id);
		if (lo == 1.0F && hi == 1.0F) return;
		if (Float.isNaN(bettersounds$pitchMul)) {
			bettersounds$pitchMul = lo >= hi ? lo : lo + java.util.concurrent.ThreadLocalRandom.current().nextFloat() * (hi - lo);
		}
		cir.setReturnValue(cir.getReturnValue() * bettersounds$pitchMul);
	}
}
