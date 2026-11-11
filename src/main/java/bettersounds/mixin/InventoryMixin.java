package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara saat slot hotbar berpindah (scroll / tombol angka). */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Unique
	private int bettersounds$lastSlot = -1;

	@Inject(method = "setSelectedSlot", at = @At("TAIL"), require = 0)
	private void bettersounds$onSlot(int slot, CallbackInfo ci) {
		Inventory self = (Inventory) (Object) this;
		int now = self.getSelectedSlot();
		boolean changed = bettersounds$lastSlot != -1 && now != bettersounds$lastSlot;
		bettersounds$lastSlot = now;
		if (!changed) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.player != null && mc.player.getInventory() == self) {
			UiSounds.hotbar();
			UiSounds.select(mc.player.getMainHandItem());
			UiSounds.itemOwn(mc.player.getMainHandItem());
		}
	}
}
