package bettersounds.mixin;

import bettersounds.client.UiSounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suara item saat slot di creative inventory diklik (layar ini punya jalur klik sendiri). */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"), require = 0)
	private void bettersounds$slot(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		ItemStack cursor = ((AbstractContainerScreen<?>) (Object) this).getMenu().getCarried();
		ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
		UiSounds.itemTouch(!cursor.isEmpty() ? cursor : stack);
	}
}
