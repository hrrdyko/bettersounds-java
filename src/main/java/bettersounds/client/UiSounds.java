package bettersounds.client;

import bettersounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Pemutar suara UI tambahan (hotbar, hover, slider, buka/tutup layar, join dunia). */
public final class UiSounds {
	private static long lastHover = 0, lastSlider = 0, lastHotbar = 0;
	private static final String[] SKIP = {"Loading", "Progress", "Receiving", "Connect", "GenericMessage", "Title",
			"Disconnected", "Overlay", "Toast"};

	private UiSounds() {}

	public static void play(SoundEvent ev) {
		play(ev, 1.0F, 1.0F);
	}

	public static void play(SoundEvent ev, float pitch, float volume) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || ev == null) return;
		try {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
		} catch (Throwable t) {
			SoundConfig.LOGGER.debug("Gagal memutar suara UI", t);
		}
	}

	private static boolean throttle(long[] last, int ms) {
		long now = System.currentTimeMillis();
		if (now - last[0] < ms) return false;
		last[0] = now;
		return true;
	}

	private static final long[] H = {0}, S = {0}, K = {0};

	public static void hover() {
		if (throttle(H, 45)) play(ModSounds.HOVER);
	}

	public static void slider() {
		if (throttle(S, 55)) play(ModSounds.SLIDER_SCROLL);
	}

	public static void hotbar() {
		if (throttle(K, 25)) play(ModSounds.HOTBAR_SCROLL);
	}

	public static void worldJoin() {
		play(ModSounds.WORLD_JOIN);
	}

	/** Suara klik tombol bawaan Minecraft (dipakai tombol buatan sendiri di layar pengaturan). */
	public static void click() {
		try {
			Identifier id = Identifier.fromNamespaceAndPath("minecraft", "ui.button.click");
			play(SoundEvent.createVariableRangeEvent(id), 1.0F, 1.0F);
		} catch (Throwable ignored) {
		}
	}

	public static void screenChange(Screen old, Screen next) {
		if (old == next) return;
		if ((old == null) == (next == null)) return; // hanya buka (null -> layar) atau tutup (layar -> null)
		boolean open = old == null;
		Screen s = open ? next : old;
		String name = s.getClass().getSimpleName();
		for (String k : SKIP) if (name.contains(k)) return;

		SoundEvent ev;
		if (s instanceof ChatScreen) ev = open ? ModSounds.CHAT_OPEN : ModSounds.CHAT_CLOSE;
		else if (s instanceof AbstractContainerScreen<?>) ev = open ? ModSounds.INVENTORY_OPEN : ModSounds.INVENTORY_CLOSE;
		else ev = open ? ModSounds.GUI_OPEN : ModSounds.GUI_CLOSE;
		play(ev);
	}
}
