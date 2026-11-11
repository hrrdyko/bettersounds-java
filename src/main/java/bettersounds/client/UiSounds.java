package bettersounds.client;

import bettersounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;

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

	/** Suara klik tombol bawaan Minecraft. Volumenya 0.25 seperti di game aslinya (tombol vanilla). */
	public static void click() {
		try {
			Identifier id = Identifier.fromNamespaceAndPath("minecraft", "ui.button.click");
			play(SoundEvent.createVariableRangeEvent(id), 1.0F, 0.25F);
		} catch (Throwable ignored) {
		}
	}

	private static final long[] SW = {0}, SEL = {0}, CT = {0};

	/** Suara ayunan senjata/alat saat menyerang (meleset atau mengenai). */
	public static void swing() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || !throttle(SW, 130)) return;
		play(ModSounds.get("item.swing." + ItemCategory.swing(mc.player.getMainHandItem())), 1.0F, 0.7F);
	}

	private static final long[] SC = {0}, IO = {0}, IT = {0};

	private static boolean playSelect(ItemStack stack) {
		String c = ItemCategory.of(stack);
		if (c == null) return false;
		play(ModSounds.get("item.select." + c), 1.0F, 0.3F);
		return true;
	}

	/** Suara milik item itu sendiri: untuk item blok, suara pasang blok tersebut. */
	private static boolean playOwn(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (stack.getItem() instanceof BlockItem bi) {
			SoundType st = bi.getBlock().defaultBlockState().getSoundType();
			play(st.getPlaceSound(), st.getPitch() * 0.9F, 0.45F);
			return true;
		}
		return false;
	}

	/** Suara pelan sesuai jenis item yang baru dipilih di hotbar. */
	public static void select(ItemStack stack) {
		if (!throttle(SEL, 60)) return;
		playSelect(stack);
	}

	/** Suara item yang sedang dipegang (blok: suara blok itu). */
	public static void itemOwn(ItemStack stack) {
		if (!throttle(IO, 40)) return;
		playOwn(stack);
	}

	/** Item disentuh di inventory: suara blok kalau item blok, kalau bukan suara jenis item. */
	public static void itemTouch(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !throttle(IT, 50)) return;
		if (!playOwn(stack)) playSelect(stack);
	}

	/** Klik slot di layar kontainer (inventory, peti, dst.), dipanggil sebelum klik diproses. */
	public static void slotClick(AbstractContainerMenu menu, int slotId, String input) {
		if (menu == null || input.equals("QUICK_CRAFT") || input.equals("PICKUP_ALL") || input.equals("CLONE")) return;
		ItemStack cursor = menu.getCarried();
		ItemStack slotStack = ItemStack.EMPTY;
		if (slotId >= 0 && slotId < menu.slots.size()) slotStack = menu.slots.get(slotId).getItem();
		ItemStack played = input.equals("PICKUP") && !cursor.isEmpty() ? cursor : slotStack;
		if (played.isEmpty()) played = cursor;
		itemTouch(played);
	}

	/** Suara scroll di layar mana pun (mod menu, pengaturan, creative inventory, dst.). */
	public static void scroll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui.screen() == null || !throttle(SC, 45)) return;
		play(ModSounds.get("ui.scroll"), 1.0F, 0.4F);
	}

	private static boolean inChat() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.gui.screen() instanceof ChatScreen;
	}

	public static void chatType() {
		if (inChat() && throttle(CT, 20)) play(ModSounds.get("ui.chat_type"), 1.0F, 0.4F);
	}

	public static void chatDelete() {
		if (inChat() && throttle(CT, 20)) play(ModSounds.get("ui.chat_delete"), 1.0F, 0.4F);
	}

	public static void chatSend() {
		play(ModSounds.get("ui.chat_send"), 1.0F, 0.5F);
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
