package bettersounds;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/** Event suara milik mod ini (tidak ada di Minecraft vanilla). */
public final class ModSounds {
	public static SoundEvent ITEM_DROP;
	public static SoundEvent HOTBAR_SCROLL, SLIDER_SCROLL, HOVER;
	public static SoundEvent GUI_OPEN, GUI_CLOSE, INVENTORY_OPEN, INVENTORY_CLOSE, CHAT_OPEN, CHAT_CLOSE;
	public static SoundEvent WORLD_JOIN;

	/** Kategori item untuk suara ayunan (swing) dan suara saat item dipilih di hotbar. */
	public static final String[] SWING = {"sword", "axe", "pickaxe", "shovel", "hoe", "trident", "mace", "fist", "item"};
	public static final String[] SELECT = {"sword", "axe", "pickaxe", "shovel", "hoe", "trident", "mace", "ranged",
			"armor", "block", "shield", "item"};

	private static final Map<String, SoundEvent> MAP = new HashMap<>();

	private ModSounds() {}

	public static SoundEvent get(String path) {
		return MAP.get(path);
	}

	public static void registerAll() {
		ITEM_DROP = reg("item.drop");
		HOTBAR_SCROLL = reg("ui.hotbar_scroll");
		SLIDER_SCROLL = reg("ui.slider_scroll");
		HOVER = reg("ui.hover");
		GUI_OPEN = reg("ui.gui_open");
		GUI_CLOSE = reg("ui.gui_close");
		INVENTORY_OPEN = reg("ui.inventory_open");
		INVENTORY_CLOSE = reg("ui.inventory_close");
		CHAT_OPEN = reg("ui.chat_open");
		CHAT_CLOSE = reg("ui.chat_close");
		WORLD_JOIN = reg("ui.world_join");
		reg("ui.scroll");
		reg("ui.chat_type");
		reg("ui.chat_delete");
		reg("ui.chat_send");
		for (String c : SWING) reg("item.swing." + c);
		for (String c : SELECT) reg("item.select." + c);
	}

	private static SoundEvent reg(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(BetterSoundsMod.MOD_ID, path);
		SoundEvent ev = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		MAP.put(path, ev);
		return ev;
	}
}
