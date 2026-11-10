package bettersounds.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Layar pengaturan Better Sounds: sidebar kategori, pencarian, slider volume/pitch, dropdown suara, dan Library. */
public class ConfigScreen extends Screen {
	private static final String[] PAGES = {"GUI", "Block", "Entity", "Item", "World", "Mods", "Library"};
	private static final int LIBRARY = 6;

	// palet (ARGB) - gaya piksel bevel ala Bedrock, aksen hijau
	private static final int C_OUT = 0xFF000000;
	private static final int C_BG = 0xFF2A2A2B, C_BG_HI = 0xFF4B4C4E, C_BG_LO = 0xFF161617;
	private static final int C_SIDE = 0xFF212122, C_HEAD = 0xFF323334;
	private static final int C_ROW_A = 0xFF2D2D2E, C_ROW_B = 0xFF343435, C_ROW_HI = 0xFF3F4041, C_ROW_LO = 0xFF222223;
	private static final int C_BTN = 0xFF4D4E50, C_BTN_HI = 0xFF7D7E81, C_BTN_LO = 0xFF2E2F30;
	private static final int C_BTN_H = 0xFF5F6063, C_BTN_HI_H = 0xFF909194;
	private static final int C_OFF = 0xFF353536, C_OFF_HI = 0xFF414243, C_OFF_LO = 0xFF2A2A2B;
	private static final int C_INSET = 0xFF121213, C_INSET_H = 0xFF1C1C1E, C_INSET_DK = 0xFF040404, C_INSET_LT = 0xFF3E3F41;
	private static final int G_FACE = 0xFF3C8527, G_HI = 0xFF72C24D, G_LO = 0xFF22501A, G_OUT = 0xFF0E2A0A;
	private static final int G_FACE_H = 0xFF4A9A31;
	private static final int C_TEXT = 0xFFFFFFFF, C_MUTED = 0xFFA8A8A8, C_ACCENT = 0xFF5DBB3D;
	private static final int C_HANDLE = 0xFFC8C9CB, C_HANDLE_HI = 0xFFF4F5F6, C_HANDLE_LO = 0xFF77787A;

	private static final int ROW_H = 24, LIB_ROW_H = 22, HEAD_H = 24, COL_H = 14, FOOT_H = 14;

	private static final class Hit {
		final int x1, y1, x2, y2, clipT, clipB;
		final Runnable click, reset;
		final DoubleConsumer slide;
		final boolean quiet; // true = tidak memutar suara klik (mis. tombol Play)

		Hit(int x1, int y1, int x2, int y2, int clipT, int clipB, Runnable click, DoubleConsumer slide, Runnable reset) {
			this(x1, y1, x2, y2, clipT, clipB, click, slide, reset, false);
		}

		Hit(int x1, int y1, int x2, int y2, int clipT, int clipB, Runnable click, DoubleConsumer slide, Runnable reset,
				boolean quiet) {
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.clipT = clipT; this.clipB = clipB;
			this.click = click; this.slide = slide; this.reset = reset;
			this.quiet = quiet;
		}

		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= Math.max(y1, clipT) && my < Math.min(y2, clipB);
		}
	}

	private static final class KeyInfo {
		final String key, label, lower;
		final int cat;

		KeyInfo(String key) {
			this.key = key;
			this.label = pretty(key);
			this.lower = (key + " " + label).toLowerCase(Locale.ROOT);
			this.cat = categoryOf(key);
		}
	}

	private final Screen parent;
	private final SoundConfig cfg = SoundConfig.load();
	private final List<Hit> hits = new ArrayList<>();
	private List<KeyInfo> infos;
	private List<KeyInfo> filtered = new ArrayList<>();
	private int cachePage = -1;
	private String cacheQuery = null;

	private EditBox searchBox;
	private int page = 0;
	private int scroll = 0;
	private int maxScroll = 0;
	private Hit dragging = null;
	private double lastQ = Double.NaN;
	private long appliedAt = 0;

	// dropdown
	private String popupKey = null;
	private int popupScroll = 0;
	private int popAx, popAy, popAw, popAh;
	private boolean popAnchorFound = false;

	// layout
	private int pX, pY, pW, pH, sideW, cX, cW, listTop, listBottom;
	private int curMx = -1, curMy = -1;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Better Sounds"));
		this.parent = parent;
	}

	// ---------------- util ----------------
	private static int categoryOf(String key) {
		String k = key;
		if (k.startsWith("bettersounds:")) k = k.substring("bettersounds:".length());
		else if (k.contains(":")) return 5; // mod lain
		if (k.startsWith("ui.")) return 0;
		if (k.startsWith("block.")) return 1;
		if (k.startsWith("entity.")) return 2;
		if (k.startsWith("item.")) return 3;
		return 4;
	}

	private static String pretty(String key) {
		String k = key;
		String prefix = "";
		if (k.startsWith("bettersounds:")) {
			k = k.substring("bettersounds:".length());
		} else if (k.contains(":")) {
			int c = k.indexOf(':');
			prefix = cap(k.substring(0, c)) + ": ";
			k = k.substring(c + 1);
		}
		if (k.equals("item.drop")) return "Drop Item";
		if (k.startsWith("item.swing.")) return "Swing - " + cap(k.substring("item.swing.".length()));
		if (k.startsWith("item.select.")) return "Equip - " + cap(k.substring("item.select.".length()));
		StringBuilder sb = new StringBuilder(prefix);
		boolean first = true;
		for (String p : k.split("[._]")) {
			if (p.isEmpty()) continue;
			if (!first) sb.append(' ');
			sb.append(first && p.equals("ui") ? "UI" : cap(p));
			first = false;
		}
		return sb.toString();
	}

	private static String cap(String p) {
		return p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1);
	}

	private static String stem(String file) {
		return SoundConfig.stem(file);
	}

	private static final String ALL = "*";

	private List<String> choicesFor(String key) {
		List<String> c = new ArrayList<>();
		c.add("default");
		if (key == null || ALL.equals(key) || SoundConfig.hasBetterDefault(key)) c.add("vanilla");
		c.addAll(SoundConfig.library());
		return c;
	}

	private static String choiceLabel(String choice) {
		if ("default".equals(choice)) return "Default";
		if ("vanilla".equals(choice)) return "Vanilla";
		if (SoundConfig.isGroup(choice)) return choice + " (x" + SoundConfig.members(choice).size() + ")";
		return stem(choice);
	}

	private boolean validChoice(String key, String choice) {
		if ("default".equals(choice)) return true;
		if ("vanilla".equals(choice)) return SoundConfig.hasBetterDefault(key);
		return SoundConfig.library().contains(choice);
	}

	private String fit(String s, int maxW) {
		Font f = this.minecraft.font;
		if (f.width(s) <= maxW) return s;
		String e = "...";
		while (!s.isEmpty() && f.width(s + e) > maxW) s = s.substring(0, s.length() - 1);
		return s + e;
	}

	private List<KeyInfo> visible() {
		if (infos == null) {
			infos = new ArrayList<>();
			for (String k : SoundConfig.allKeys()) infos.add(new KeyInfo(k));
		}
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		if (cachePage != page || !q.equals(cacheQuery)) {
			filtered = new ArrayList<>();
			for (KeyInfo i : infos) {
				if (i.cat != page) continue;
				if (!q.isEmpty() && !i.lower.contains(q)) continue;
				filtered.add(i);
			}
			cachePage = page;
			cacheQuery = q;
			scroll = 0;
		}
		return filtered;
	}

	private void layout() {
		pW = Math.min(this.width - 16, Math.max(400, Math.min(600, this.width * 78 / 100)));
		pH = Math.min(this.height - 16, Math.max(250, Math.min(360, this.height * 86 / 100)));
		pX = (this.width - pW) / 2;
		pY = (this.height - pH) / 2;
		sideW = Math.max(78, Math.min(96, pW / 5));
		cX = pX + sideW + 1;
		cW = pW - sideW - 1;
		listTop = pY + HEAD_H + (page == LIBRARY ? 36 : COL_H);
		listBottom = pY + pH - FOOT_H - 1;
	}

	private void playEvent(Identifier id) {
		try {
			SoundEvent ev = SoundEvent.createVariableRangeEvent(id);
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ev, 1.0F, 1.0F));
		} catch (Exception e) {
			SoundConfig.LOGGER.warn("Gagal memutar contoh suara {}", id, e);
		}
	}

	private void preview(String key) {
		int c = key.indexOf(':');
		Identifier id = c >= 0
				? Identifier.fromNamespaceAndPath(key.substring(0, c), key.substring(c + 1))
				: Identifier.fromNamespaceAndPath("minecraft", key);
		playEvent(id);
	}

	// ---------------- init ----------------
	@Override
	protected void init() {
		SoundConfig.invalidateLibrary();
		layout();
		String old = searchBox == null ? "" : searchBox.getValue();
		int btnTotal = 56 + 3 + 46 + 3 + 18 + 6;
		int sbW = Math.max(60, Math.min(140, cW - 90 - btnTotal - 8));
		searchBox = new EditBox(this.minecraft.font, cX + cW - btnTotal - sbW - 4, pY + 4, sbW, 16, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search sounds..."));
		searchBox.setMaxLength(32);
		searchBox.setValue(old);
		this.addRenderableWidget(searchBox);
	}

	// ---------------- helper gambar (bevel 1 piksel) ----------------
	private void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int face, int hi, int lo, int outline) {
		g.fill(x, y, x + w, y + h, outline);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
	}

	/** Bagian "masuk" (dropdown, track slider): gelap di atas/kiri, terang di bawah/kanan. */
	private void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int face) {
		g.fill(x, y, x + w, y + h, C_OUT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, C_INSET_DK);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, C_INSET_DK);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, C_INSET_LT);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, C_INSET_LT);
	}

	/** Garis pemisah berlekuk (gelap lalu terang). */
	private void groove(GuiGraphicsExtractor g, int x1, int x2, int y) {
		g.fill(x1, y, x2, y + 1, 0xFF0D0D0E);
		g.fill(x1, y + 1, x2, y + 2, 0xFF434445);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my,
			int clipT, int clipB, boolean enabled, Runnable click) {
		button(g, x, y, w, h, label, mx, my, clipT, clipB, enabled, false, click);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my,
			int clipT, int clipB, boolean enabled, boolean primary, Runnable click) {
		button(g, x, y, w, h, label, mx, my, clipT, clipB, enabled, primary, false, click);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my,
			int clipT, int clipB, boolean enabled, boolean primary, boolean quiet, Runnable click) {
		Font f = this.minecraft.font;
		boolean hov = enabled && mx >= x && mx < x + w && my >= Math.max(y, clipT) && my < Math.min(y + h, clipB);
		if (!enabled) bevel(g, x, y, w, h, C_OFF, C_OFF_HI, C_OFF_LO, C_OUT);
		else if (primary) bevel(g, x, y, w, h, hov ? G_FACE_H : G_FACE, G_HI, G_LO, G_OUT);
		else bevel(g, x, y, w, h, hov ? C_BTN_H : C_BTN, hov ? C_BTN_HI_H : C_BTN_HI, C_BTN_LO, C_OUT);
		g.text(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, enabled ? C_TEXT : C_MUTED);
		if (enabled && click != null) hits.add(new Hit(x, y, x + w, y + h, clipT, clipB, click, null, null, quiet));
	}

	private static final float MAX_VOL = 5.0f; // volume per event sampai 500% (suara UI vanilla diputar 25%)

	/** Slider volume: separuh kiri 0-100% (halus), separuh kanan 100-500%. */
	private static double volToT(double v) {
		return v <= 1.0 ? v / 2.0 : 0.5 + (v - 1.0) / (MAX_VOL - 1.0) / 2.0;
	}

	private static double tToVol(double t) {
		return t <= 0.5 ? t * 2.0 : 1.0 + (t - 0.5) * 2.0 * (MAX_VOL - 1.0);
	}

	private void slider(GuiGraphicsExtractor g, int x, int rowY, int w, float value, float min, float max, double step,
			String text, DoubleConsumer setter, Runnable reset) {
		slider(g, x, rowY, w, value, v -> (v - min) / (max - min), t -> min + t * (max - min), step, text, setter, reset);
	}

	private void slider(GuiGraphicsExtractor g, int x, int rowY, int w, float value,
			java.util.function.DoubleUnaryOperator toT, java.util.function.DoubleUnaryOperator fromT, double step,
			String text, DoubleConsumer setter, Runnable reset) {
		Font f = this.minecraft.font;
		g.text(f, text, x + (w - f.width(text)) / 2, rowY + 2, C_TEXT);
		int ty = rowY + 16;
		float t = (float) Math.max(0.0, Math.min(1.0, toT.applyAsDouble(value)));
		int hx = x + Math.round(t * w);

		inset(g, x - 1, ty - 2, w + 2, 6, C_INSET);
		if (hx > x) {
			g.fill(x, ty - 1, hx, ty + 2, G_FACE);
			g.fill(x, ty - 1, hx, ty, G_HI);
		}

		boolean hov = curMx >= hx - 5 && curMx < hx + 5 && curMy >= ty - 8 && curMy < ty + 9;
		boolean drag = dragging != null && dragging.y1 == ty - 6 && dragging.x1 == x - 3;
		if (drag) bevel(g, hx - 3, ty - 6, 7, 12, G_FACE, G_HI, G_LO, C_OUT);
		else bevel(g, hx - 3, ty - 6, 7, 12, hov ? 0xFFDADBDC : C_HANDLE, C_HANDLE_HI, C_HANDLE_LO, C_OUT);

		hits.add(new Hit(x - 3, ty - 6, x + w + 3, ty + 8, listTop, listBottom, null, mx -> {
			double tt = Math.max(0.0, Math.min(1.0, (mx - x) / (double) w));
			double nv = fromT.applyAsDouble(tt);
			double q = Math.round(nv / step) * step;
			if (Double.isNaN(lastQ)) lastQ = Math.round(value / step) * step;
			if (Math.abs(q - lastQ) > 1.0E-9) {
				UiSounds.slider();
				lastQ = q;
			}
			setter.accept(nv);
		}, reset));
	}

	// ---------------- gambar ----------------
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		layout();
		hits.clear();
		popAnchorFound = false;
		curMx = mouseX;
		curMy = mouseY;
		Font f = this.minecraft.font;

		// latar redup + bayangan panel
		g.fill(0, 0, this.width, this.height, 0xB0101010);
		g.fill(pX + 4, pY + 4, pX + pW + 4, pY + pH + 4, 0x40000000);
		g.fill(pX + 2, pY + 2, pX + pW + 2, pY + pH + 2, 0x50000000);
		bevel(g, pX, pY, pW, pH, C_BG, C_BG_HI, C_BG_LO, C_OUT);

		drawSidebar(g, mouseX, mouseY);

		// header
		g.fill(cX, pY + 1, cX + cW - 1, pY + HEAD_H - 1, C_HEAD);
		g.fill(cX, pY + 1, cX + cW - 1, pY + 2, 0xFF3F4041);
		groove(g, cX, cX + cW - 1, pY + HEAD_H - 2);
		g.text(f, PAGES[page], cX + 8, pY + 8, C_TEXT);
		g.fill(cX + 8, pY + 18, cX + 8 + f.width(PAGES[page]), pY + 19, C_ACCENT);
		int bx = cX + cW - 6;
		bx -= 18;
		button(g, bx, pY + 4, 18, 16, "X", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, this::onClose);
		bx -= 49;
		button(g, bx, pY + 4, 46, 16, "Apply", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, true, this::apply);
		bx -= 59;
		button(g, bx, pY + 4, 56, 16, "Reset all", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, () -> {
			cfg.entries.clear();
			popupKey = null;
		});

		if (page == LIBRARY) {
			searchBox.visible = false;
			drawLibrary(g, mouseX, mouseY);
		} else {
			searchBox.visible = true;
			searchBox.extractRenderState(g, mouseX, mouseY, partialTick);
			drawSounds(g, mouseX, mouseY);
		}

		// footer
		g.fill(cX, listBottom, cX + cW - 1, pY + pH - 1, C_HEAD);
		groove(g, cX, cX + cW - 1, listBottom);
		boolean applied = System.currentTimeMillis() - appliedAt < 2500;
		String hint;
		if (applied) hint = "Applied!";
		else if (page == LIBRARY) hint = "Click a name to play it  |  Edit renames and edits  |  Apply to use";
		else hint = "Click a sound name to play it  |  Double-click a slider to reset  |  Apply to save";
		g.text(f, fit(hint, cW - 16), cX + 8, listBottom + 4, applied ? C_ACCENT : C_MUTED);

		drawPopup(g, mouseX, mouseY);
		if (popupKey != null && !popAnchorFound) popupKey = null;
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		g.fill(pX + 1, pY + 1, pX + sideW, pY + pH - 1, C_SIDE);
		g.fill(pX + sideW, pY + 1, pX + sideW + 1, pY + pH - 1, C_OUT);
		g.fill(pX + sideW + 1, pY + 1, pX + sideW + 2, pY + pH - 1, 0xFF3A3B3C);
		g.text(f, "Better Sounds", pX + 9, pY + 8, C_TEXT);
		g.fill(pX + 9, pY + 18, pX + 9 + f.width("Better Sounds"), pY + 19, C_ACCENT);
		groove(g, pX + 1, pX + sideW, pY + 27);

		int y = pY + 36;
		for (int i = 0; i < LIBRARY; i++) {
			sideItem(g, i, y, mx, my);
			y += 19;
		}
		groove(g, pX + 1, pX + sideW, y + 2);
		sideItem(g, LIBRARY, y + 8, mx, my);
	}

	private void sideItem(GuiGraphicsExtractor g, int idx, int y, int mx, int my) {
		Font f = this.minecraft.font;
		boolean sel = page == idx;
		boolean hov = mx >= pX && mx < pX + sideW && my >= y && my < y + 18;
		if (sel) {
			bevel(g, pX + 4, y, sideW - 8, 18, G_FACE, G_HI, G_LO, G_OUT);
		} else if (hov) {
			bevel(g, pX + 4, y, sideW - 8, 18, C_BTN, C_BTN_HI, C_BTN_LO, C_OUT);
		}
		g.text(f, PAGES[idx], pX + 13, y + 5, sel || hov ? C_TEXT : C_MUTED);
		hits.add(new Hit(pX, y, pX + sideW, y + 18, Integer.MIN_VALUE, Integer.MAX_VALUE, () -> {
			page = idx;
			scroll = 0;
			popupKey = null;
		}, null, null));
	}

	private void drawSounds(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		int usable = cW - 22;
		int gap = 8;
		int nameW = usable * 34 / 100;
		int dropW = Math.max(76, usable * 22 / 100);
		int sliderW = Math.max(28, (usable - nameW - dropW - 3 * gap) / 2);
		int nameX = cX + 8;
		int volX = nameX + nameW + gap;
		int pitX = volX + sliderW + gap;
		int dropX = pitX + sliderW + gap;

		// judul kolom
		g.fill(cX, pY + HEAD_H, cX + cW - 1, pY + HEAD_H + COL_H, C_SIDE);
		g.fill(cX, pY + HEAD_H + COL_H - 1, cX + cW - 1, pY + HEAD_H + COL_H, 0xFF0D0D0E);
		g.text(f, "Sound", nameX, pY + HEAD_H + 3, C_MUTED);
		g.text(f, "Volume", volX + (sliderW - f.width("Volume")) / 2, pY + HEAD_H + 3, C_MUTED);
		g.text(f, "Pitch", pitX + (sliderW - f.width("Pitch")) / 2, pY + HEAD_H + 3, C_MUTED);
		g.text(f, "Playback", dropX, pY + HEAD_H + 3, C_MUTED);

		List<KeyInfo> keys = visible();
		int viewH = listBottom - listTop;
		int contentH = keys.size() * ROW_H;
		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		g.enableScissor(cX, listTop, cX + cW, listBottom);
		for (int i = 0; i < keys.size(); i++) {
			int y = listTop - scroll + i * ROW_H;
			if (y + ROW_H < listTop || y > listBottom) continue;
			final KeyInfo info = keys.get(i);
			final String key = info.key;
			final SoundConfig.Entry e = cfg.get(key);
			if (!validChoice(key, e.choice)) e.choice = "default";

			g.fill(cX, y, cX + cW - 1, y + ROW_H, i % 2 == 0 ? C_ROW_A : C_ROW_B);
			g.fill(cX, y, cX + cW - 1, y + 1, C_ROW_HI);
			g.fill(cX, y + ROW_H - 1, cX + cW - 1, y + ROW_H, C_ROW_LO);
			if (!e.isDefault()) {
				g.fill(cX, y + 1, cX + 3, y + ROW_H - 1, G_FACE);
				g.fill(cX, y + 1, cX + 1, y + ROW_H - 1, G_HI);
			}
			boolean nameHov = mx >= nameX && mx < nameX + nameW && my >= Math.max(y, listTop) && my < Math.min(y + ROW_H, listBottom);
			g.text(f, fit(info.label, nameW), nameX, y + 8, nameHov ? C_ACCENT : C_TEXT);
			hits.add(new Hit(nameX, y, nameX + nameW, y + ROW_H, listTop, listBottom, () -> preview(key), null, null, true));

			slider(g, volX, y, sliderW, e.volume, ConfigScreen::volToT, ConfigScreen::tToVol, 0.01,
					Math.round(e.volume * 100f) + "%",
					v -> e.volume = Math.round(Math.max(0f, Math.min(MAX_VOL, (float) v)) * 100f) / 100f,
					() -> e.volume = 1.0f);
			slider(g, pitX, y, sliderW, e.pitch, 0.5f, 2.0f, 0.05, String.format(Locale.ROOT, "%.2fx", e.pitch),
					v -> e.pitch = Math.round(Math.max(0.5f, Math.min(2f, (float) v)) * 20f) / 20f,
					() -> e.pitch = 1.0f);

			boolean open = key.equals(popupKey);
			boolean hov = mx >= dropX && mx < dropX + dropW && my >= Math.max(y + 4, listTop) && my < Math.min(y + 20, listBottom);
			inset(g, dropX, y + 4, dropW, 16, open || hov ? C_INSET_H : C_INSET);
			g.text(f, fit(choiceLabel(e.choice), dropW - 16), dropX + 4, y + 8, C_TEXT);
			g.text(f, "v", dropX + dropW - 10, y + 8, open || hov ? C_ACCENT : C_MUTED);
			hits.add(new Hit(dropX, y + 4, dropX + dropW, y + 20, listTop, listBottom, () -> {
				popupKey = key.equals(popupKey) ? null : key;
				popupScroll = 0;
			}, null, null));
			if (open) {
				popAx = dropX;
				popAy = y + 4;
				popAw = dropW;
				popAh = 16;
				popAnchorFound = true;
			}
		}
		g.disableScissor();

		if (keys.isEmpty()) g.text(f, "No sounds found.", nameX, listTop + 8, C_MUTED);
		drawScrollbar(g, contentH, viewH);
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int contentH, int viewH) {
		if (contentH <= viewH) return;
		int x = cX + cW - 8;
		inset(g, x, listTop, 6, listBottom - listTop, C_INSET);
		int thumbH = Math.max(16, viewH * viewH / contentH);
		int thumbY = listTop + (int) ((long) (viewH - thumbH - 2) * scroll / Math.max(1, maxScroll));
		bevel(g, x, thumbY + 1, 6, thumbH, G_FACE, G_HI, G_LO, G_OUT);
	}

	private void drawLibrary(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		List<String> items = SoundConfig.library();
		int infoY = pY + HEAD_H;
		g.fill(cX, infoY, cX + cW - 1, listTop, C_SIDE);
		g.fill(cX, listTop - 1, cX + cW - 1, listTop, 0xFF0D0D0E);
		g.text(f, fit("Import .ogg / .mp3 / .wav, a folder, or a .zip (a group plays random).", cW - 16 - 120), cX + 8, infoY + 7, C_TEXT);
		g.text(f, fit("config/bettersounds/library  |  " + items.size() + " item(s)", cW - 16 - 120), cX + 8, infoY + 20, C_MUTED);
		int ibx = cX + cW - 8 - 56;
		button(g, ibx, infoY + 5, 56, 16, "Folder", mx, my, Integer.MIN_VALUE, Integer.MAX_VALUE, true, () -> {
			java.nio.file.Path dir = FilePicker.pickFolder();
			if (dir != null) SoundConfig.importPaths(List.of(dir));
		});
		button(g, ibx - 4 - 56, infoY + 5, 56, 16, "Files", mx, my, Integer.MIN_VALUE, Integer.MAX_VALUE, true, true, () -> {
			List<java.nio.file.Path> picked = FilePicker.pickFiles();
			if (!picked.isEmpty()) SoundConfig.importPaths(picked);
		});

		int viewH = listBottom - listTop;
		int contentH = items.size() * LIB_ROW_H;
		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		g.enableScissor(cX, listTop, cX + cW, listBottom);
		for (int i = 0; i < items.size(); i++) {
			int y = listTop - scroll + i * LIB_ROW_H;
			if (y + LIB_ROW_H < listTop || y > listBottom) continue;
			final String item = items.get(i);
			final boolean group = SoundConfig.isGroup(item);
			g.fill(cX, y, cX + cW - 1, y + LIB_ROW_H, i % 2 == 0 ? C_ROW_A : C_ROW_B);
			g.fill(cX, y, cX + cW - 1, y + 1, C_ROW_HI);
			g.fill(cX, y + LIB_ROW_H - 1, cX + cW - 1, y + LIB_ROW_H, C_ROW_LO);
			int btnRight = cX + cW - 14;
			String label;
			if (group) label = "[Group] " + item + "  (" + SoundConfig.members(item).size() + " sounds)";
			else label = cfg.edit(item).isDefault() ? item : item + " *";
			int nameW = cW - 16 - 110;
			boolean nameHov = mx >= cX + 8 && mx < cX + 8 + nameW && my >= Math.max(y, listTop) && my < Math.min(y + LIB_ROW_H, listBottom);
			g.text(f, fit(label, nameW), cX + 8, y + 7, nameHov ? C_ACCENT : C_TEXT);
			hits.add(new Hit(cX + 8, y, cX + 8 + nameW, y + LIB_ROW_H, listTop, listBottom, () -> {
				try {
					List<String> mem = SoundConfig.members(item);
					if (mem.isEmpty()) return;
					String pick = mem.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(mem.size()));
					AudioPipeline.preview(AudioPipeline.decodeCached(pick), cfg.edit(pick));
				} catch (Exception ex) {
					SoundConfig.LOGGER.warn("Gagal memutar {}", item, ex);
				}
			}, null, null, true));
			button(g, btnRight - 52, y + 3, 52, 16, "Remove", mx, my, listTop, listBottom, true, () -> {
				cfg.removeFromLibrary(item);
				for (SoundConfig.Entry e : cfg.entries.values()) {
					if (item.equals(e.choice)) e.choice = "default";
				}
			});
			button(g, btnRight - 52 - 4 - 34, y + 3, 34, 16, "Edit", mx, my, listTop, listBottom, true,
					() -> this.minecraft.gui.setScreen(group ? new GroupEditorScreen(this, cfg, item)
							: new SoundEditorScreen(this, cfg, item)));
		}
		g.disableScissor();
		if (items.isEmpty()) g.text(f, "Library is empty. Use Files / Folder, or drop files here.", cX + 8, listTop + 8, C_MUTED);
		drawScrollbar(g, contentH, viewH);
	}

	private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
		if (popupKey == null || !popAnchorFound) return;
		Font f = this.minecraft.font;
		final boolean all = ALL.equals(popupKey);
		final SoundConfig.Entry e = all ? null : cfg.get(popupKey);
		List<String> choices = choicesFor(popupKey);

		int itemH = 14;
		int vis = Math.min(choices.size(), 8);
		popupScroll = Math.max(0, Math.min(popupScroll, choices.size() - vis));
		int w = Math.max(popAw, 110);
		int h = vis * itemH + 2;
		int x = popAx;
		if (x + w > this.width - 4) x = this.width - 4 - w;
		int y = popAy + popAh + 1;
		if (y + h > pY + pH) y = popAy - h - 1;

		g.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x66000000);
		bevel(g, x - 1, y - 1, w + 2, h + 2, C_SIDE, C_BG_HI, C_BG_LO, C_OUT);
		for (int i = 0; i < vis; i++) {
			final String choice = choices.get(popupScroll + i);
			int iy = y + 1 + i * itemH;
			boolean hov = mx >= x && mx < x + w && my >= iy && my < iy + itemH;
			boolean sel = e != null && choice.equals(e.choice);
			if (hov) bevel(g, x, iy, w, itemH, C_BTN_H, C_BTN_HI_H, C_BTN_LO, C_OUT);
			else if (sel) bevel(g, x, iy, w, itemH, G_FACE, G_HI, G_LO, G_OUT);
			g.text(f, fit(choiceLabel(choice), w - 12), x + 6, iy + 3, C_TEXT);
			hits.add(new Hit(x, iy, x + w, iy + itemH, Integer.MIN_VALUE, Integer.MAX_VALUE, () -> {
				if (all) {
					for (KeyInfo ki : filtered) {
						if ("vanilla".equals(choice) && !SoundConfig.hasBetterDefault(ki.key)) continue;
						cfg.get(ki.key).choice = choice;
					}
				} else {
					e.choice = choice;
				}
				popupKey = null;
			}, null, null));
		}
	}

	// ---------------- aksi ----------------
	private void apply() {
		try {
			cfg.save();
			SoundRuntime.rebuild(cfg);
			PackBuilder.build(cfg);
			PackBuilder.enableAndReload();
			appliedAt = System.currentTimeMillis();
		} catch (Exception ex) {
			SoundConfig.LOGGER.error("Gagal menerapkan pengaturan Better Sounds", ex);
		}
	}

	// ---------------- input ----------------
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int btn = event.button();

		boolean overSearch = searchBox != null && searchBox.visible && searchBox.isMouseOver(mx, my);
		if (overSearch) {
			popupKey = null;
			return super.mouseClicked(event, doubleClick);
		}
		if (searchBox != null) searchBox.setFocused(false);

		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (!h.contains(mx, my)) continue;
			if (h.slide != null) {
				if (doubleClick && h.reset != null) {
					h.reset.run();
				} else if (btn == 0 || btn == 1) {
					dragging = h;
					lastQ = Double.NaN;
					h.slide.accept(mx);
				}
			} else if ((btn == 0 || btn == 1) && h.click != null) {
				if (!h.quiet) UiSounds.click();
				h.click.run();
			}
			return true;
		}
		if (popupKey != null) {
			popupKey = null;
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null && dragging.slide != null) {
			dragging.slide.accept(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		lastQ = Double.NaN;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (popupKey != null) {
			popupScroll -= (int) Math.signum(sy);
			return true;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (Math.signum(sy) * 24)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (popupKey != null) {
				popupKey = null;
			} else {
				onClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		SoundConfig.importPaths(paths);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
