package bettersounds.mixin;

import bettersounds.client.HiddenPack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Inject(method = "openAllSelected", at = @At("RETURN"), cancellable = true, require = 0)
	private void bettersounds$addConfigPack(CallbackInfoReturnable<List<PackResources>> cir) {
		PackResources extra = HiddenPack.create((PackRepository) (Object) this);
		if (extra == null) return;
		List<PackResources> list = new ArrayList<>(cir.getReturnValue());
		list.add(extra); // paling akhir = prioritas tertinggi
		cir.setReturnValue(list);
	}
}
