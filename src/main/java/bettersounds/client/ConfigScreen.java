package bettersounds.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layar pengaturan: volume, pitch, dan pilihan suara untuk setiap suara. */
public class ConfigScreen extends Screen {
	private static final String[] CATS = {"Block", "Entity", "Item", "Other"};
	private static final int GAP = 2;
	private static final int ROW_H = 22;

	private final Screen parent;
	private final SoundConfig cfg = SoundConfig.load();
	private int cat = 0;
	private int page = 0;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Better Sounds"));
		this.parent = parent;
	}

	private static int categoryOf(String key) {
		if (key.startsWith("block.")) return 0;
		if (key.startsWith("entity.")) return 1;
		if (key.startsWith("item.") || key.equals(SoundConfig.DROP_KEY)) return 2;
		return 3;
	}

	private static String labelOf(String key) {
		if (key.equals(SoundConfig.DROP_KEY)) return "Drop Item";
		int dot = key.indexOf('.');
		return dot >= 0 ? key.substring(dot + 1) : key;
	}

	private static String volText(SoundConfig.Entry e) {
		return Math.round(e.volume * 100f) + "%";
	}

	private static String pitchText(SoundConfig.Entry e) {
		return String.format(Locale.ROOT, "%.1fx", e.pitch);
	}

	private static float round1(float v) {
		return Math.round(v * 10f) / 10f;
	}

	private Button add(int x, int y, int w, String text, Button.OnPress onPress) {
		Button b = Button.builder(Component.literal(text), onPress).bounds(x, y, w, 20).build();
		this.addRenderableWidget(b);
		return b;
	}

	private Button label(int x, int y, int w, String text) {
		Button b = add(x, y, w, text, btn -> {});
		b.active = false;
		return b;
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	protected void init() {
		int rowW = Math.min(this.width - 20, 560);
		int left = (this.width - rowW) / 2;

		List<String> keys = new ArrayList<>();
		for (String k : SoundConfig.allKeys()) {
			if (categoryOf(k) == cat) keys.add(k);
		}
		int top = 58;
		int rowsPerPage = Math.max(1, (this.height - top - 56) / ROW_H);
		int pages = Math.max(1, (keys.size() + rowsPerPage - 1) / rowsPerPage);
		page = Math.max(0, Math.min(page, pages - 1));

		// header + tab kategori
		label(left, 6, rowW, "Better Sounds  -  " + CATS[cat] + "  (page " + (page + 1) + "/" + pages + ")");
		int tabW = rowW / CATS.length;
		for (int i = 0; i < CATS.length; i++) {
			final int idx = i;
			Button t = add(left + i * tabW, 30, tabW - GAP, CATS[i], b -> {
				cat = idx;
				page = 0;
				rebuild();
			});
			t.active = i != cat;
		}

		// baris suara
		List<String> choices = new ArrayList<>();
		choices.add("default");
		choices.addAll(SoundConfig.library());

		int btnW = 18;
		int valW = 40;
		int groupW = btnW + GAP + valW + GAP + btnW;
		int labelW = (int) (rowW * 0.30);
		int soundW = rowW - labelW - 2 * groupW - 3 * GAP;

		int start = page * rowsPerPage;
		int end = Math.min(keys.size(), start + rowsPerPage);
		for (int i = start; i < end; i++) {
			final String key = keys.get(i);
			final SoundConfig.Entry e = cfg.get(key);
			int y = top + (i - start) * ROW_H;
			int x = left;

			label(x, y, labelW, labelOf(key));
			x += labelW + GAP;

			// volume
			final Button volV = label(x + btnW + GAP, y, valW, volText(e));
			add(x, y, btnW, "-", b -> {
				e.volume = Math.max(0f, Math.min(1f, round1(e.volume - 0.1f)));
				volV.setMessage(Component.literal(volText(e)));
			});
			add(x + btnW + GAP + valW + GAP, y, btnW, "+", b -> {
				e.volume = Math.max(0f, Math.min(1f, round1(e.volume + 0.1f)));
				volV.setMessage(Component.literal(volText(e)));
			});
			x += groupW + GAP;

			// pitch
			final Button pitchV = label(x + btnW + GAP, y, valW, pitchText(e));
			add(x, y, btnW, "-", b -> {
				e.pitch = Math.max(0.5f, Math.min(2f, round1(e.pitch - 0.1f)));
				pitchV.setMessage(Component.literal(pitchText(e)));
			});
			add(x + btnW + GAP + valW + GAP, y, btnW, "+", b -> {
				e.pitch = Math.max(0.5f, Math.min(2f, round1(e.pitch + 0.1f)));
				pitchV.setMessage(Component.literal(pitchText(e)));
			});
			x += groupW + GAP;

			// pilihan suara (Default / file dari library)
			if (!choices.contains(e.choice)) e.choice = "default";
			add(x, y, soundW, choiceText(e.choice), b -> {
				int idx = choices.indexOf(e.choice);
				e.choice = choices.get((idx + 1) % choices.size());
				b.setMessage(Component.literal(choiceText(e.choice)));
			});
		}

		// info impor
		label(left, this.height - 50, rowW,
				"Drop .ogg files onto this window to import  |  Library: " + (choices.size() - 1) + " file(s)");

		// bar bawah
		int bw = rowW / 5 - GAP;
		int by = this.height - 26;
		Button prev = add(left, by, bw, "< Prev", b -> {
			page--;
			rebuild();
		});
		prev.active = page > 0;
		Button next = add(left + (bw + GAP), by, bw, "Next >", b -> {
			page++;
			rebuild();
		});
		next.active = page < pages - 1;
		add(left + 2 * (bw + GAP), by, bw, "Reset all", b -> {
			cfg.entries.clear();
			rebuild();
		});
		add(left + 3 * (bw + GAP), by, bw, "Apply", b -> apply());
		add(left + 4 * (bw + GAP), by, bw, "Done", b -> onClose());
	}

	private static String choiceText(String choice) {
		if ("default".equals(choice)) return "Sound: Default";
		return "Sound: " + (choice.endsWith(".ogg") ? choice.substring(0, choice.length() - 4) : choice);
	}

	private void apply() {
		try {
			cfg.save();
			PackBuilder.build(cfg);
			PackBuilder.enableAndReload();
		} catch (Exception ex) {
			SoundConfig.LOGGER.error("Gagal menerapkan pengaturan Better Sounds", ex);
		}
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		SoundConfig.importFiles(paths);
		rebuild();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
