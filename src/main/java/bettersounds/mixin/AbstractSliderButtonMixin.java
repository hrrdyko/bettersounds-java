package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara saat nilai slider berubah. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	@Unique
	private double bettersounds$last = Double.NaN;

	@Inject(method = "setValue", at = @At("HEAD"), require = 0)
	private void bettersounds$onValue(double value, CallbackInfo ci) {
		if (!Double.isNaN(bettersounds$last) && Math.abs(value - bettersounds$last) > 1.0E-4) UiSounds.slider();
		bettersounds$last = value;
	}
}
