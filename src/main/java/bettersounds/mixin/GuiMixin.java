package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara saat layar (inventory, chat, menu lain) dibuka atau ditutup. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), require = 0)
	private void bettersounds$onSetScreen(Screen next, CallbackInfo ci) {
		UiSounds.screenChange(((Gui) (Object) this).screen(), next);
	}
}
