package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara saat berhasil masuk ke dunia atau server. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("TAIL"), require = 0)
	private void bettersounds$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
		UiSounds.worldJoin();
	}
}
