package bettersounds.mixin;

import bettersounds.client.CustomAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Memutar suara Library hasil render (mp3/wav/edit) lewat stream audio sendiri. */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
	@Inject(method = "getStream", at = @At("HEAD"), cancellable = true, require = 0)
	private void bettersounds$customStream(Identifier location, boolean looping,
			CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
		String stem = CustomAudio.stemOf(location);
		if (stem == null || !CustomAudio.has(stem)) return;
		cir.setReturnValue(CompletableFuture.supplyAsync(() -> CustomAudio.open(stem, looping)));
	}
}
