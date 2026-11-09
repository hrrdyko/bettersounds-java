package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suara saat menekan Enter untuk mengirim pesan chat. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), require = 0)
	private void bettersounds$send(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		int k = event.key();
		if (k == 257 || k == 335) UiSounds.chatSend(); // Enter / Enter numpad
	}
}
