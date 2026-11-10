package bettersounds.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Editor suara di dalam game: ganti nama, potong (trim), kecepatan, dan volume (sampai 1000%). */
public class SoundEditorScreen extends Screen {
	private static final int C_OUT = 0xFF000000;
	private static final int C_BG = 0xFF2A2A2B, C_BG_HI = 0xFF4B4C4E, C_BG_LO = 0xFF161617;
	private static final int C_HEAD = 0xFF323334;
	private static final int C_BTN = 0xFF4D4E50, C_BTN_HI = 0xFF7D7E81, C_BTN_LO = 0xFF2E2F30;
	private static final int C_BTN_H = 0xFF5F6063, C_BTN_HI_H = 0xFF909194;
	private static final int C_INSET = 0xFF121213, C_INSET_DK = 0xFF040404, C_INSET_LT = 0xFF3E3F41;
	private static final int G_FACE = 0xFF3C8527, G_HI = 0xFF72C24D, G_LO = 0xFF22501A, G_OUT = 0xFF0E2A0A, G_FACE_H = 0xFF4A9A31;
	private static final int C_TEXT = 0xFFFFFFFF, C_MUTED = 0xFFA8A8A8, C_ACCENT = 0xFF5DBB3D, C_ERROR = 0xFFE06060;
	private static final int C_HANDLE = 0xFFC8C9CB, C_HANDLE_HI = 0xFFF4F5F6, C_HANDLE_LO = 0xFF77787A;

	private static final class Hit {
		final int x1, y1, x2, y2;
		final Runnable click;
		final DoubleConsumer slide;
		final boolean quiet;

		Hit(int x1, int y1, int x2, int y2, Runnable click, DoubleConsumer slide) {
			this(x1, y1, x2, y2, click, slide, false);
		}

		Hit(int x1, int y1, int x2, int y2, Runnable click, DoubleConsumer slide, boolean quiet) {
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.click = click; this.slide = slide;
			this.quiet = quiet;
		}

		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}
	}

	private final Screen parent;
	private final SoundConfig cfg;
	private String file;
	private final String group; // null kalau file tunggal
	private final String ext;
	private final String baseName;
	private final SoundConfig.Edit w = new SoundConfig.Edit();
	private final List<Hit> hits = new ArrayList<>();
	private AudioPipeline.Pcm pcm;
	private String error;
	private String status;
	private EditBox nameBox;
	private float[] peaks;
	private int peaksW = -1;
	private Hit dragging;
	private int pX, pY, pW, pH, curMx = -1, curMy = -1;

	public SoundEditorScreen(Screen parent, SoundConfig cfg, String file) {
		super(Component.literal("Edit sound"));
		this.parent = parent;
		this.cfg = cfg;
		this.file = file;
		int slash = file.lastIndexOf('/');
		this.group = slash >= 0 ? file.substring(0, slash) : null;
		String leaf = file.substring(slash + 1);
		this.baseName = SoundConfig.stem(leaf);
		this.ext = leaf.substring(baseName.length());
		this.w.copyFrom(cfg.edit(file));
		try {
			this.pcm = AudioPipeline.decodeCached(file);
		} catch (Exception e) {
			this.error = "Cannot read this file: " + e.getClass().getSimpleName();
			SoundConfig.LOGGER.warn("Gagal membaca {}", file, e);
		}
	}

	private void layout() {
		pW = Math.min(this.width - 16, 400);
		pH = 252;
		pX = (this.width - pW) / 2;
		pY = Math.max(8, (this.height - pH) / 2);
	}

	@Override
	protected void init() {
		layout();
		String old = nameBox == null ? baseName : nameBox.getValue();
		int extW = this.minecraft.font.width(ext) + 6;
		nameBox = new EditBox(this.minecraft.font, pX + 52, pY + 28, pW - 52 - 10 - extW, 16, Component.literal("Name"));
		nameBox.setMaxLength(40);
		nameBox.setValue(old);
		this.addRenderableWidget(nameBox);
	}

	// ---------------- gambar ----------------
	private void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int face, int hi, int lo, int outline) {
		g.fill(x, y, x + w, y + h, outline);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
	}

	private void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int face) {
		g.fill(x, y, x + w, y + h, C_OUT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, C_INSET_DK);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, C_INSET_DK);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, C_INSET_LT);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, C_INSET_LT);
	}

	private void groove(GuiGraphicsExtractor g, int x1, int x2, int y) {
		g.fill(x1, y, x2, y + 1, 0xFF0D0D0E);
		g.fill(x1, y + 1, x2, y + 2, 0xFF434445);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int bw, int bh, String label, boolean primary, Runnable click) {
		button(g, x, y, bw, bh, label, primary, false, click);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int bw, int bh, String label, boolean primary, boolean quiet,
			Runnable click) {
		Font f = this.minecraft.font;
		boolean hov = curMx >= x && curMx < x + bw && curMy >= y && curMy < y + bh;
		if (primary) bevel(g, x, y, bw, bh, hov ? G_FACE_H : G_FACE, G_HI, G_LO, G_OUT);
		else bevel(g, x, y, bw, bh, hov ? C_BTN_H : C_BTN, hov ? C_BTN_HI_H : C_BTN_HI, C_BTN_LO, C_OUT);
		g.text(f, label, x + (bw - f.width(label)) / 2, y + (bh - 8) / 2, C_TEXT);
		hits.add(new Hit(x, y, x + bw, y + bh, click, null, quiet));
	}

	private void slider(GuiGraphicsExtractor g, int x, int y, int sw, String label, String valueText,
			float value, float min, float max, DoubleConsumer setter) {
		Font f = this.minecraft.font;
		g.text(f, label, pX + 12, y + 3, C_MUTED);
		int ty = y + 7;
		float t = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
		int hx = x + Math.round(t * sw);
		inset(g, x - 1, ty - 2, sw + 2, 6, C_INSET);
		if (hx > x) {
			g.fill(x, ty - 1, hx, ty + 2, G_FACE);
			g.fill(x, ty - 1, hx, ty, G_HI);
		}
		boolean hov = curMx >= hx - 5 && curMx < hx + 5 && curMy >= ty - 8 && curMy < ty + 9;
		boolean drag = dragging != null && dragging.x1 == x - 3 && dragging.y1 == ty - 6;
		if (drag) bevel(g, hx - 3, ty - 6, 7, 12, G_FACE, G_HI, G_LO, C_OUT);
		else bevel(g, hx - 3, ty - 6, 7, 12, hov ? 0xFFDADBDC : C_HANDLE, C_HANDLE_HI, C_HANDLE_LO, C_OUT);
		g.text(f, valueText, x + sw + 10, y + 3, C_TEXT);
		hits.add(new Hit(x - 3, ty - 6, x + sw + 3, ty + 8, null, mx -> {
			double tt = Math.max(0.0, Math.min(1.0, (mx - x) / (double) sw));
			setter.accept(min + tt * (max - min));
		}));
	}

	private double totalMs() {
		return pcm == null ? 0 : pcm.durationMs();
	}

	private double endEff() {
		return w.endMs <= 0.5 ? totalMs() : w.endMs;
	}

	private void computePeaks(int width) {
		if (pcm == null || peaksW == width) return;
		float[] m = pcm.mono();
		peaks = new float[width];
		for (int i = 0; i < width; i++) {
			int a = (int) ((long) i * m.length / width);
			int b = Math.max(a + 1, (int) ((long) (i + 1) * m.length / width));
			float mx = 0;
			for (int k = a; k < b && k < m.length; k++) mx = Math.max(mx, Math.abs(m[k]));
			peaks[i] = mx;
		}
		peaksW = width;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Font f = this.minecraft.font;
		layout();
		curMx = mouseX;
		curMy = mouseY;
		hits.clear();

		g.fill(0, 0, this.width, this.height, 0xB0101010);
		g.fill(pX + 4, pY + 4, pX + pW + 4, pY + pH + 4, 0x40000000);
		g.fill(pX + 2, pY + 2, pX + pW + 2, pY + pH + 2, 0x50000000);
		bevel(g, pX, pY, pW, pH, C_BG, C_BG_HI, C_BG_LO, C_OUT);

		// judul + nama
		g.fill(pX + 1, pY + 1, pX + pW - 1, pY + 22, C_HEAD);
		groove(g, pX + 1, pX + pW - 1, pY + 22);
		g.text(f, "Edit: " + (group == null ? file : file.substring(group.length() + 1)), pX + 10, pY + 8, C_TEXT);
		g.text(f, "Name", pX + 10, pY + 32, C_MUTED);
		nameBox.extractRenderState(g, mouseX, mouseY, partialTick);
		g.text(f, ext, pX + pW - 10 - f.width(ext) - 2, pY + 32, C_MUTED);

		int by = pY + pH - 26;
		int wx = pX + 10, wy = pY + 52, ww = pW - 20, wh = 70;
		if (pcm == null) {
			g.text(f, error == null ? "No audio" : error, wx, wy + 10, C_MUTED);
			drawBottom(g, by, false);
			return;
		}

		// waveform
		inset(g, wx - 1, wy - 1, ww + 2, wh + 2, C_INSET);
		computePeaks(ww);
		int cy = wy + wh / 2;
		g.fill(wx, cy, wx + ww, cy + 1, 0xFF2A3A2A);
		for (int i = 0; i < ww; i++) {
			int h = Math.max(1, Math.round(peaks[i] * (wh / 2 - 2)));
			g.fill(wx + i, cy - h, wx + i + 1, cy + h + 1, C_ACCENT);
		}
		double total = totalMs();
		int xs = wx + (int) Math.round(w.startMs / total * ww);
		int xe = wx + (int) Math.round(endEff() / total * ww);
		g.fill(wx, wy, xs, wy + wh, 0xAA000000);
		g.fill(xe, wy, wx + ww, wy + wh, 0xAA000000);
		bevel(g, xs - 2, wy - 3, 5, wh + 6, G_FACE, G_HI, G_LO, G_OUT);
		bevel(g, xe - 2, wy - 3, 5, wh + 6, G_FACE, G_HI, G_LO, G_OUT);
		hits.add(new Hit(xs - 5, wy - 3, xs + 6, wy + wh + 3, null, mx -> {
			double ms = (mx - wx) / (double) ww * total;
			w.startMs = Math.max(0, Math.min(ms, endEff() - 20));
		}));
		hits.add(new Hit(xe - 5, wy - 3, xe + 6, wy + wh + 3, null, mx -> {
			double ms = (mx - wx) / (double) ww * total;
			ms = Math.max(w.startMs + 20, Math.min(ms, total));
			w.endMs = ms >= total - 1 ? 0 : ms;
		}));

		// info waktu
		double lenMs = Math.max(0, endEff() - w.startMs);
		double outMs = lenMs / Math.max(0.25, w.speed);
		String info = String.format(Locale.ROOT, "Start %.2fs   End %.2fs   Length %.2fs   Result %.2fs",
				w.startMs / 1000.0, endEff() / 1000.0, lenMs / 1000.0, outMs / 1000.0);
		g.text(f, info, wx, wy + wh + 8, C_MUTED);

		// slider kecepatan & volume
		int sx = pX + 70, sw = pW - 70 - 70;
		slider(g, sx, pY + 148, sw, "Speed", String.format(Locale.ROOT, "%.2fx", w.speed), w.speed, 0.25f, 4.0f,
				v -> w.speed = Math.max(0.25f, Math.min(4f, Math.round((float) v * 20f) / 20f)));
		slider(g, sx, pY + 172, sw, "Volume", Math.round(w.gain) + "%", w.gain, 0f, 1000f,
				v -> w.gain = Math.max(0f, Math.min(1000f, Math.round((float) v / 5f) * 5f)));
		g.text(f, status != null ? status : "Above 100% is boosted (soft-clipped). Preview is slightly quieter.",
				wx, pY + 196, status != null ? C_ERROR : C_MUTED);

		drawBottom(g, by, true);
	}

	private void drawBottom(GuiGraphicsExtractor g, int by, boolean full) {
		int bx = pX + 10;
		if (full) {
			button(g, bx, by, 46, 18, "Play", true, true, () -> AudioPipeline.preview(pcm, w));
			button(g, bx + 50, by, 46, 18, "Stop", false, true, AudioPipeline::stopPreview);
			button(g, bx + 100, by, 52, 18, "Reset", false, () -> w.copyFrom(new SoundConfig.Edit()));
		}
		button(g, pX + pW - 10 - 56 - 4 - 56, by, 56, 18, "Cancel", false, this::onClose);
		button(g, pX + pW - 10 - 56, by, 56, 18, "Save", true, this::save);
	}

	private void save() {
		cfg.edit(file).copyFrom(w);
		String typed = nameBox.getValue().trim();
		if (!typed.isEmpty() && !SoundConfig.sanitize(typed).replace('.', '_').equals(baseName)) {
			String res = group == null ? cfg.renameItem(file, typed) : cfg.renameMember(group, file, typed);
			if (res == null) {
				status = "That name is empty or already used";
				return;
			}
			file = res;
		}
		onClose();
	}

	// ---------------- input ----------------
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int btn = event.button();
		if (nameBox != null && nameBox.isMouseOver(mx, my)) return super.mouseClicked(event, doubleClick);
		if (nameBox != null) nameBox.setFocused(false);
		if (btn != 0 && btn != 1) return true;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (!h.contains(mx, my)) continue;
			if (h.slide != null) {
				dragging = h;
				h.slide.accept(mx);
			} else if (h.click != null) {
				if (!h.quiet) UiSounds.click();
				h.click.run();
			}
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
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		AudioPipeline.stopPreview();
		this.minecraft.gui.setScreen(parent);
	}
}
