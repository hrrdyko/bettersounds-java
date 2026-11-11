package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara scroll mouse saat sebuah layar terbuka. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	// tanpa argumen target: cukup CallbackInfo, supaya tidak bergantung pada tanda tangan method
	@Inject(method = "onScroll", at = @At("HEAD"), require = 0)
	private void bettersounds$scroll(CallbackInfo ci) {
		UiSounds.scroll();
	}
}
