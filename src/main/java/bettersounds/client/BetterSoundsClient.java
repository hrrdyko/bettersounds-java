package bettersounds.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class BetterSoundsClient implements ClientModInitializer {
	private static boolean openNext = false;
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		SoundRuntime.rebuild(SoundConfig.load());

		// Keybind (default: K = kode GLFW 75, bisa diubah di Controls)
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bettersounds", "main"));
		openKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.bettersounds.open", InputConstants.Type.KEYBOARD, 75, category));

		// /bettersounds -> buka layar pengaturan
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bettersounds")
						.executes(ctx -> {
							openNext = true; // dibuka di tick berikutnya, setelah layar chat tertutup
							return 1;
						})));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.gui.screen() == null) openNext = true;
			}
			if (openNext) {
				openNext = false;
				mc.gui.setScreen(new ConfigScreen(mc.gui.screen()));
				releaseMouse(mc);
			}
		});
	}

	/** Memastikan kursor mouse tidak terkunci (lewat refleksi agar tidak bergantung pada nama field). */
	private static void releaseMouse(net.minecraft.client.Minecraft mc) {
		try {
			Object handler = net.minecraft.client.Minecraft.class.getField("mouseHandler").get(mc);
			handler.getClass().getMethod("releaseMouse").invoke(handler);
		} catch (Throwable t) {
			SoundConfig.LOGGER.warn("Tidak bisa melepas kursor mouse", t);
		}
	}
}
