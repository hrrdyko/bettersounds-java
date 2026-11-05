package bettersounds.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class BetterSoundsClient implements ClientModInitializer {
	private static boolean openNext = false;

	@Override
	public void onInitializeClient() {
		// /bettersounds -> buka layar pengaturan
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("bettersounds")
						.executes(ctx -> {
							openNext = true; // dibuka di tick berikutnya, setelah layar chat tertutup
							return 1;
						})));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (openNext) {
				openNext = false;
				mc.gui.setScreen(new ConfigScreen(mc.gui.screen()));
			}
		});
	}
}
