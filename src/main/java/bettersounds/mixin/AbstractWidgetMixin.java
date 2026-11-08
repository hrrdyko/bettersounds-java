package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara saat kursor mulai menyorot tombol/widget. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
	@Unique
	private boolean bettersounds$wasHovered = false;

	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void bettersounds$hover(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		AbstractWidget self = (AbstractWidget) (Object) this;
		boolean h = self.isHovered();
		if (h && !bettersounds$wasHovered && self.active && self.visible) UiSounds.hover();
		bettersounds$wasHovered = h;
	}
}
