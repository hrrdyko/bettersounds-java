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
	private static final String[] PAGES = {"All sounds", "Block", "Entity", "Item", "Other", "Library"};
	private static final int LIBRARY = 5;

	// warna (ARGB)
	private static final int C_BG = 0xFF202020, C_SIDE = 0xFF272727, C_HEAD = 0xFF2E2E2E;
	private static final int C_ROW_A = 0xFF2F2F2F, C_ROW_B = 0xFF363636, C_SEL = 0xFF404040;
	private static final int C_BTN = 0xFF4C4C4C, C_BTN_H = 0xFF646464, C_BTN_OFF = 0xFF353535;
	private static final int C_TEXT = 0xFFFFFFFF, C_MUTED = 0xFFA8A8A8, C_ACCENT = 0xFFE8672A;
	private static final int C_TRACK = 0xFF151515, C_LINE = 0xFF141414;

	private static final int ROW_H = 26, LIB_ROW_H = 24, HEAD_H = 28, COL_H = 16, FOOT_H = 16;

	private static final class Hit {
		final int x1, y1, x2, y2, clipT, clipB;
		final Runnable click, reset;
		final DoubleConsumer slide;

		Hit(int x1, int y1, int x2, int y2, int clipT, int clipB, Runnable click, DoubleConsumer slide, Runnable reset) {
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.clipT = clipT; this.clipB = clipB;
			this.click = click; this.slide = slide; this.reset = reset;
		}

		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= Math.max(y1, clipT) && my < Math.min(y2, clipB);
		}
	}

	private final Screen parent;
	private final SoundConfig cfg = SoundConfig.load();
	private final List<Hit> hits = new ArrayList<>();

	private EditBox searchBox;
	private int page = 0;
	private int scroll = 0;
	private int maxScroll = 0;
	private Hit dragging = null;
	private long appliedAt = 0;

	// dropdown
	private String popupKey = null;
	private int popupScroll = 0;
	private int popAx, popAy, popAw, popAh;
	private boolean popAnchorFound = false;

	// layout
	private int pX, pY, pW, pH, sideW, cX, cW, listTop, listBottom;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Better Sounds"));
		this.parent = parent;
	}

	// ---------------- util ----------------
	private static int categoryOf(String key) {
		if (key.startsWith("block.")) return 0;
		if (key.startsWith("entity.")) return 1;
		if (key.startsWith("item.") || key.equals(SoundConfig.DROP_KEY)) return 2;
		return 3;
	}

	private static String pretty(String key) {
		if (key.equals(SoundConfig.DROP_KEY)) return "Drop Item";
		StringBuilder sb = new StringBuilder();
		for (String p : key.split("[._]")) {
			if (p.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return sb.toString();
	}

	private static String stem(String file) {
		return file.endsWith(".ogg") ? file.substring(0, file.length() - 4) : file;
	}

	private String fit(String s, int maxW) {
		Font f = this.minecraft.font;
		if (f.width(s) <= maxW) return s;
		String e = "...";
		while (!s.isEmpty() && f.width(s + e) > maxW) s = s.substring(0, s.length() - 1);
		return s + e;
	}

	private List<String> visibleKeys() {
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String k : SoundConfig.allKeys()) {
			if (page >= 1 && page <= 4 && categoryOf(k) != page - 1) continue;
			if (!q.isEmpty() && !k.toLowerCase(Locale.ROOT).contains(q) && !pretty(k).toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(k);
		}
		return out;
	}

	private void layout() {
		pX = 8;
		pY = 8;
		pW = this.width - 16;
		pH = this.height - 16;
		sideW = Math.max(96, Math.min(140, pW / 5));
		cX = pX + sideW + 1;
		cW = pW - sideW - 1;
		listTop = pY + HEAD_H + (page == LIBRARY ? 40 : COL_H);
		listBottom = pY + pH - FOOT_H;
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
		layout();
		String old = searchBox == null ? "" : searchBox.getValue();
		int btnTotal = 60 + 4 + 50 + 4 + 20 + 8;
		int sbW = Math.max(60, Math.min(150, cW - 140 - btnTotal));
		searchBox = new EditBox(this.minecraft.font, cX + cW - btnTotal - sbW - 4, pY + 5, sbW, 18, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search sounds..."));
		searchBox.setMaxLength(32);
		searchBox.setValue(old);
		this.addRenderableWidget(searchBox);
	}

	// ---------------- gambar ----------------
	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my,
			int clipT, int clipB, boolean enabled, Runnable click) {
		Font f = this.minecraft.font;
		boolean hov = enabled && mx >= x && mx < x + w && my >= Math.max(y, clipT) && my < Math.min(y + h, clipB);
		g.fill(x, y, x + w, y + h, !enabled ? C_BTN_OFF : hov ? C_BTN_H : C_BTN);
		g.text(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, enabled ? C_TEXT : C_MUTED);
		if (enabled && click != null) hits.add(new Hit(x, y, x + w, y + h, clipT, clipB, click, null, null));
	}

	private void slider(GuiGraphicsExtractor g, int x, int rowY, int w, float value, float min, float max, String text,
			DoubleConsumer setter, Runnable reset) {
		Font f = this.minecraft.font;
		g.text(f, text, x + (w - f.width(text)) / 2, rowY + 3, C_TEXT);
		int ty = rowY + 17;
		float t = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
		int hx = x + Math.round(t * w);
		g.fill(x, ty, x + w, ty + 2, C_TRACK);
		g.fill(x, ty, hx, ty + 2, C_ACCENT);
		g.fill(hx - 2, ty - 4, hx + 2, ty + 6, C_TEXT);
		hits.add(new Hit(x - 3, ty - 6, x + w + 3, ty + 8, listTop, listBottom, null, mx -> {
			double tt = Math.max(0.0, Math.min(1.0, (mx - x) / (double) w));
			setter.accept(min + tt * (max - min));
		}, reset));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		layout();
		hits.clear();
		popAnchorFound = false;
		Font f = this.minecraft.font;

		g.fill(0, 0, this.width, this.height, 0xB0101010);
		g.fill(pX, pY, pX + pW, pY + pH, C_BG);

		drawSidebar(g, mouseX, mouseY);

		// header
		g.fill(cX, pY, cX + cW, pY + HEAD_H, C_HEAD);
		g.fill(cX, pY + HEAD_H - 1, cX + cW, pY + HEAD_H, C_LINE);
		g.text(f, PAGES[page], cX + 8, pY + 10, C_TEXT);
		int bx = cX + cW - 8;
		bx -= 20;
		button(g, bx, pY + 5, 20, 18, "X", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, this::onClose);
		bx -= 54;
		button(g, bx, pY + 5, 50, 18, "Apply", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, this::apply);
		bx -= 64;
		button(g, bx, pY + 5, 60, 18, "Reset all", mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE, true, () -> {
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
		g.fill(cX, listBottom, cX + cW, pY + pH, C_HEAD);
		String hint;
		if (System.currentTimeMillis() - appliedAt < 2500) hint = "Applied!";
		else if (page == LIBRARY) hint = "Preview works after Apply  |  Drop .ogg files onto this window to import";
		else hint = "Double-click a slider to reset  |  Click Apply to save and reload sounds";
		g.text(f, fit(hint, cW - 16), cX + 8, listBottom + 4,
				System.currentTimeMillis() - appliedAt < 2500 ? C_ACCENT : C_MUTED);

		drawPopup(g, mouseX, mouseY);
		if (popupKey != null && !popAnchorFound) popupKey = null;
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		g.fill(pX, pY, pX + sideW, pY + pH, C_SIDE);
		g.fill(pX + sideW, pY, pX + sideW + 1, pY + pH, C_LINE);
		g.text(f, "Better Sounds", pX + 10, pY + 10, C_TEXT);
		g.text(f, "Java port  |  by MSAB14", pX + 10, pY + 22, C_MUTED);
		g.fill(pX, pY + 38, pX + sideW, pY + 39, C_LINE);

		g.text(f, "SOUNDS", pX + 10, pY + 48, C_MUTED);
		int y = pY + 62;
		for (int i = 0; i <= 4; i++) {
			sideItem(g, i, y, mx, my);
			y += 20;
		}
		y += 10;
		g.text(f, "OTHERS", pX + 10, y, C_MUTED);
		y += 14;
		sideItem(g, LIBRARY, y, mx, my);
	}

	private void sideItem(GuiGraphicsExtractor g, int idx, int y, int mx, int my) {
		Font f = this.minecraft.font;
		boolean sel = page == idx;
		boolean hov = mx >= pX && mx < pX + sideW && my >= y && my < y + 18;
		if (sel || hov) g.fill(pX, y, pX + sideW, y + 18, sel ? C_SEL : C_ROW_A);
		if (sel) g.fill(pX, y, pX + 2, y + 18, C_ACCENT);
		g.text(f, PAGES[idx], pX + 14, y + 5, sel ? C_TEXT : C_MUTED);
		hits.add(new Hit(pX, y, pX + sideW, y + 18, Integer.MIN_VALUE, Integer.MAX_VALUE, () -> {
			page = idx;
			scroll = 0;
			popupKey = null;
		}, null, null));
	}

	private void drawSounds(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		int usable = cW - 16 - 6;
		int gap = 10, playW = 14;
		int nameW = usable * 28 / 100;
		int dropW = Math.max(80, usable * 22 / 100);
		int sliderW = Math.max(30, (usable - nameW - dropW - playW - 4 * gap) / 2);
		int nameX = cX + 8;
		int volX = nameX + nameW + gap;
		int pitX = volX + sliderW + gap;
		int playX = pitX + sliderW + gap;
		int dropX = playX + playW + 2;

		// judul kolom
		g.fill(cX, pY + HEAD_H, cX + cW, pY + HEAD_H + COL_H, C_SIDE);
		g.text(f, "Sound Name", nameX, pY + HEAD_H + 4, C_MUTED);
		g.text(f, "Volume", volX + (sliderW - f.width("Volume")) / 2, pY + HEAD_H + 4, C_MUTED);
		g.text(f, "Pitch", pitX + (sliderW - f.width("Pitch")) / 2, pY + HEAD_H + 4, C_MUTED);
		g.text(f, "Playback", playX, pY + HEAD_H + 4, C_MUTED);

		List<String> keys = visibleKeys();
		int viewH = listBottom - listTop;
		int contentH = keys.size() * ROW_H;
		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		g.enableScissor(cX, listTop, cX + cW, listBottom);
		List<String> choices = new ArrayList<>();
		choices.add("default");
		choices.addAll(SoundConfig.library());

		for (int i = 0; i < keys.size(); i++) {
			int y = listTop - scroll + i * ROW_H;
			if (y + ROW_H < listTop || y > listBottom) continue;
			final String key = keys.get(i);
			final SoundConfig.Entry e = cfg.get(key);
			if (!choices.contains(e.choice)) e.choice = "default";

			g.fill(cX, y, cX + cW, y + ROW_H, i % 2 == 0 ? C_ROW_A : C_ROW_B);
			if (!e.isDefault()) g.fill(cX, y, cX + 2, y + ROW_H, C_ACCENT);
			g.text(f, fit(pretty(key), nameW), nameX, y + 9, C_TEXT);

			slider(g, volX, y, sliderW, e.volume, 0f, 1f, Math.round(e.volume * 100f) + "%",
					v -> e.volume = Math.round(Math.max(0f, Math.min(1f, (float) v)) * 100f) / 100f,
					() -> e.volume = 1.0f);
			slider(g, pitX, y, sliderW, e.pitch, 0.5f, 2.0f, String.format(Locale.ROOT, "%.2fx", e.pitch),
					v -> e.pitch = Math.round(Math.max(0.5f, Math.min(2f, (float) v)) * 20f) / 20f,
					() -> e.pitch = 1.0f);

			button(g, playX, y + 5, playW, 16, ">", mx, my, listTop, listBottom, true, () -> preview(key));

			// dropdown pilihan suara
			boolean open = key.equals(popupKey);
			boolean hov = mx >= dropX && mx < dropX + dropW && my >= Math.max(y + 5, listTop) && my < Math.min(y + 21, listBottom);
			g.fill(dropX, y + 5, dropX + dropW, y + 21, open || hov ? C_BTN_H : C_BTN);
			String shown = "default".equals(e.choice) ? "Default" : stem(e.choice);
			g.text(f, fit(shown, dropW - 18), dropX + 4, y + 9, C_TEXT);
			g.text(f, "v", dropX + dropW - 10, y + 9, C_MUTED);
			hits.add(new Hit(dropX, y + 5, dropX + dropW, y + 21, listTop, listBottom, () -> {
				popupKey = key.equals(popupKey) ? null : key;
				popupScroll = 0;
			}, null, null));
			if (open) {
				popAx = dropX;
				popAy = y + 5;
				popAw = dropW;
				popAh = 16;
				popAnchorFound = true;
			}
		}
		g.disableScissor();

		if (keys.isEmpty()) {
			g.text(f, "No sounds found.", nameX, listTop + 8, C_MUTED);
		}
		drawScrollbar(g, contentH, viewH);
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int contentH, int viewH) {
		if (contentH <= viewH) return;
		int x = cX + cW - 5;
		g.fill(x, listTop, x + 4, listBottom, C_TRACK);
		int thumbH = Math.max(16, viewH * viewH / contentH);
		int thumbY = listTop + (int) ((long) (viewH - thumbH) * scroll / Math.max(1, maxScroll));
		g.fill(x, thumbY, x + 4, thumbY + thumbH, C_BTN_H);
	}

	private void drawLibrary(GuiGraphicsExtractor g, int mx, int my) {
		Font f = this.minecraft.font;
		List<String> files = SoundConfig.library();
		int infoY = pY + HEAD_H;
		g.fill(cX, infoY, cX + cW, listTop, C_SIDE);
		g.text(f, fit("Drop .ogg (Vorbis) files onto this window to import them.", cW - 16), cX + 8, infoY + 7, C_TEXT);
		g.text(f, fit("Folder: config/bettersounds/library   |   " + files.size() + " file(s)", cW - 16), cX + 8, infoY + 21, C_MUTED);

		int viewH = listBottom - listTop;
		int contentH = files.size() * LIB_ROW_H;
		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		g.enableScissor(cX, listTop, cX + cW, listBottom);
		for (int i = 0; i < files.size(); i++) {
			int y = listTop - scroll + i * LIB_ROW_H;
			if (y + LIB_ROW_H < listTop || y > listBottom) continue;
			final String file = files.get(i);
			g.fill(cX, y, cX + cW, y + LIB_ROW_H, i % 2 == 0 ? C_ROW_A : C_ROW_B);
			int btnRight = cX + cW - 12;
			g.text(f, fit(stem(file), cW - 16 - 100), cX + 8, y + 8, C_TEXT);
			button(g, btnRight - 54, y + 3, 54, 18, "Remove", mx, my, listTop, listBottom, true, () -> {
				SoundConfig.removeFromLibrary(file);
				for (SoundConfig.Entry e : cfg.entries.values()) {
					if (file.equals(e.choice)) e.choice = "default";
				}
			});
			button(g, btnRight - 54 - 4 - 36, y + 3, 36, 18, "Play", mx, my, listTop, listBottom, true,
					() -> playEvent(Identifier.fromNamespaceAndPath("bettersounds", "lib." + stem(file))));
		}
		g.disableScissor();
		if (files.isEmpty()) {
			g.text(f, "Library is empty. Drop some .ogg files here.", cX + 8, listTop + 8, C_MUTED);
		}
		drawScrollbar(g, contentH, viewH);
	}

	private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
		if (popupKey == null || !popAnchorFound) return;
		Font f = this.minecraft.font;
		final SoundConfig.Entry e = cfg.get(popupKey);
		List<String> choices = new ArrayList<>();
		choices.add("default");
		choices.addAll(SoundConfig.library());

		int itemH = 14;
		int vis = Math.min(choices.size(), 8);
		popupScroll = Math.max(0, Math.min(popupScroll, choices.size() - vis));
		int w = Math.max(popAw, 110);
		int h = vis * itemH + 2;
		int x = popAx;
		if (x + w > this.width - 4) x = this.width - 4 - w;
		int y = popAy + popAh + 1;
		if (y + h > pY + pH) y = popAy - h - 1;

		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, C_LINE);
		g.fill(x, y, x + w, y + h, C_SIDE);
		for (int i = 0; i < vis; i++) {
			final String choice = choices.get(popupScroll + i);
			int iy = y + 1 + i * itemH;
			boolean hov = mx >= x && mx < x + w && my >= iy && my < iy + itemH;
			boolean sel = choice.equals(e.choice);
			if (hov || sel) g.fill(x, iy, x + w, iy + itemH, hov ? C_BTN_H : C_SEL);
			if (sel) g.fill(x, iy, x + 2, iy + itemH, C_ACCENT);
			g.text(f, fit("default".equals(choice) ? "Default" : stem(choice), w - 12), x + 6, iy + 3, C_TEXT);
			hits.add(new Hit(x, iy, x + w, iy + itemH, Integer.MIN_VALUE, Integer.MAX_VALUE, () -> {
				e.choice = choice;
				popupKey = null;
			}, null, null));
		}
	}

	// ---------------- aksi ----------------
	private void apply() {
		try {
			cfg.save();
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
					h.slide.accept(mx);
				}
			} else if ((btn == 0 || btn == 1) && h.click != null) {
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
		SoundConfig.importFiles(paths);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
