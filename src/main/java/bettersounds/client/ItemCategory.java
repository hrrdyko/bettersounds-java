package bettersounds.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Mengelompokkan item (pedang, kapak, blok, armor, dst.) dari id-nya, untuk memilih suara yang cocok. */
public final class ItemCategory {
	private ItemCategory() {}

	/** Kategori item, atau null kalau tangan kosong. */
	public static String of(ItemStack s) {
		if (s == null || s.isEmpty()) return null;
		Item item = s.getItem();
		String id = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
		int c = id.indexOf(':');
		String p = c >= 0 ? id.substring(c + 1) : id;
		if (p.endsWith("_sword")) return "sword";
		if (p.endsWith("_pickaxe")) return "pickaxe";
		if (p.endsWith("_axe")) return "axe";
		if (p.endsWith("_shovel")) return "shovel";
		if (p.endsWith("_hoe")) return "hoe";
		if (p.equals("trident") || p.endsWith("_spear")) return "trident";
		if (p.equals("mace")) return "mace";
		if (p.equals("bow") || p.equals("crossbow")) return "ranged";
		if (p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots")
				|| p.equals("elytra")) return "armor";
		if (p.equals("shield")) return "shield";
		if (item instanceof BlockItem) return "block";
		return "item";
	}

	/** Kategori untuk suara ayunan: senjata/alat dikenali, tangan kosong = fist, sisanya = item. */
	public static String swing(ItemStack s) {
		String c = of(s);
		if (c == null) return "fist";
		switch (c) {
			case "sword", "axe", "pickaxe", "shovel", "hoe", "trident", "mace":
				return c;
			default:
				return "item";
		}
	}
}
