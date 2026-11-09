package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara mengetik dan menghapus teks (hanya diputar saat layar chat terbuka). */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
	@Inject(method = "insertText", at = @At("RETURN"), require = 0)
	private void bettersounds$typed(String text, CallbackInfo ci) {
		UiSounds.chatType();
	}

	@Inject(method = "deleteCharsToPos", at = @At("HEAD"), require = 0)
	private void bettersounds$deleted(int pos, CallbackInfo ci) {
		if (!((EditBox) (Object) this).getValue().isEmpty()) UiSounds.chatDelete();
	}
}
