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

/** Edit grup suara: ganti nama folder, putar/ganti nama/edit/hapus tiap file di dalamnya. */
public class GroupEditorScreen extends Screen {
	private static final int ROW_H = 22;

	private static final class Hit {
		final int x1, y1, x2, y2, clipT, clipB;
		final Runnable click;
		final boolean quiet;

		Hit(int x1, int y1, int x2, int y2, int clipT, int clipB, Runnable click, boolean quiet) {
			this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
			this.clipT = clipT; this.clipB = clipB; this.click = click; this.quiet = quiet;
		}

		boolean contains(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= Math.max(y1, clipT) && my < Math.min(y2, clipB);
		}
	}

	private final Screen parent;
	private final SoundConfig cfg;
	private final String group;
	private final List<Hit> hits = new ArrayList<>();
	private EditBox nameBox;
	private String status;
	private int pX, pY, pW, pH, listTop, listBottom, scroll, maxScroll, curMx = -1, curMy = -1;

	public GroupEditorScreen(Screen parent, SoundConfig cfg, String group) {
		super(Component.literal("Edit group"));
		this.parent = parent;
		this.cfg = cfg;
		this.group = group;
	}

	private void layout() {
		pW = Math.min(this.width - 16, 420);
		pH = Math.min(this.height - 16, 270);
		pX = (this.width - pW) / 2;
		pY = (this.height - pH) / 2;
		listTop = pY + 56;
		listBottom = pY + pH - 36;
	}

	@Override
	protected void init() {
		layout();
		SoundConfig.invalidateLibrary();
		String old = nameBox == null ? group : nameBox.getValue();
		nameBox = new EditBox(this.minecraft.font, pX + 52, pY + 28, pW - 62, 16, Component.literal("Name"));
		nameBox.setMaxLength(40);
		nameBox.setValue(old);
		this.addRenderableWidget(nameBox);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean primary, int clipT,
			int clipB, Runnable click) {
		Font f = this.minecraft.font;
		boolean hov = curMx >= x && curMx < x + w && curMy >= Math.max(y, clipT) && curMy < Math.min(y + h, clipB);
		if (primary) Ui.bevel(g, x, y, w, h, hov ? Ui.G_FACE_H : Ui.G_FACE, Ui.G_HI, Ui.G_LO, Ui.G_OUT);
		else Ui.bevel(g, x, y, w, h, hov ? Ui.BTN_H : Ui.BTN, hov ? Ui.BTN_HI_H : Ui.BTN_HI, Ui.BTN_LO, Ui.OUT);
		g.text(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, Ui.TEXT);
		hits.add(new Hit(x, y, x + w, y + h, clipT, clipB, click, false));
	}

	private String fit(String s, int maxW) {
		Font f = this.minecraft.font;
		if (f.width(s) <= maxW) return s;
		while (!s.isEmpty() && f.width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
		return s + "...";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		layout();
		Font f = this.minecraft.font;
		curMx = mouseX;
		curMy = mouseY;
		hits.clear();

		g.fill(0, 0, this.width, this.height, 0xB0101010);
		g.fill(pX + 4, pY + 4, pX + pW + 4, pY + pH + 4, 0x40000000);
		g.fill(pX + 2, pY + 2, pX + pW + 2, pY + pH + 2, 0x50000000);
		Ui.bevel(g, pX, pY, pW, pH, Ui.BG, Ui.BG_HI, Ui.BG_LO, Ui.OUT);

		g.fill(pX + 1, pY + 1, pX + pW - 1, pY + 22, Ui.HEAD);
		Ui.groove(g, pX + 1, pX + pW - 1, pY + 22);
		g.text(f, "Edit group", pX + 10, pY + 8, Ui.TEXT);
		g.text(f, "Name", pX + 10, pY + 32, Ui.MUTED);
		nameBox.extractRenderState(g, mouseX, mouseY, partialTick);

		// daftar file di dalam grup
		List<String> members = SoundConfig.members(group);
		g.fill(pX + 1, listTop - 1, pX + pW - 1, listBottom + 1, Ui.SIDE);
		int viewH = listBottom - listTop;
		int contentH = members.size() * ROW_H;
		maxScroll = Math.max(0, contentH - viewH);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		g.enableScissor(pX + 1, listTop, pX + pW - 1, listBottom);
		for (int i = 0; i < members.size(); i++) {
			int y = listTop - scroll + i * ROW_H;
			if (y + ROW_H < listTop || y > listBottom) continue;
			final String rel = members.get(i);
			String leaf = rel.substring(rel.lastIndexOf('/') + 1);
			g.fill(pX + 1, y, pX + pW - 1, y + ROW_H, i % 2 == 0 ? Ui.ROW_A : Ui.ROW_B);
			g.fill(pX + 1, y, pX + pW - 1, y + 1, Ui.ROW_HI);
			g.fill(pX + 1, y + ROW_H - 1, pX + pW - 1, y + ROW_H, Ui.ROW_LO);

			int right = pX + pW - 12;
			int nameW = pW - 24 - 100;
			boolean hov = curMx >= pX + 10 && curMx < pX + 10 + nameW && curMy >= Math.max(y, listTop) && curMy < Math.min(y + ROW_H, listBottom);
			String label = cfg.edit(rel).isDefault() ? leaf : leaf + " *";
			g.text(f, fit(label, nameW), pX + 10, y + 7, hov ? Ui.ACCENT : Ui.TEXT);
			hits.add(new Hit(pX + 10, y, pX + 10 + nameW, y + ROW_H, listTop, listBottom, () -> {
				try {
					AudioPipeline.preview(AudioPipeline.decodeCached(rel), cfg.edit(rel));
				} catch (Exception ex) {
					SoundConfig.LOGGER.warn("Gagal memutar {}", rel, ex);
				}
			}, true));
			button(g, right - 52, y + 3, 52, 16, "Remove", false, listTop, listBottom, () -> cfg.removeMember(rel));
			button(g, right - 52 - 4 - 34, y + 3, 34, 16, "Edit", false, listTop, listBottom,
					() -> this.minecraft.gui.setScreen(new SoundEditorScreen(this, cfg, rel)));
		}
		g.disableScissor();
		if (members.isEmpty()) g.text(f, "This group is empty.", pX + 10, listTop + 8, Ui.MUTED);

		// scrollbar
		if (contentH > viewH) {
			int sx = pX + pW - 8;
			int thumbH = Math.max(16, viewH * viewH / contentH);
			int thumbY = listTop + (int) ((long) (viewH - thumbH) * scroll / Math.max(1, maxScroll));
			g.fill(sx, listTop, sx + 5, listBottom, 0xFF121213);
			Ui.bevel(g, sx, thumbY, 5, thumbH, Ui.G_FACE, Ui.G_HI, Ui.G_LO, Ui.G_OUT);
		}

		g.text(f, status != null ? status : "Click a name to play it", pX + 10, listBottom + 6,
				status != null ? Ui.ERROR : Ui.MUTED);
		int by = pY + pH - 26;
		button(g, pX + pW - 10 - 56 - 4 - 56, by, 56, 18, "Cancel", false, Integer.MIN_VALUE, Integer.MAX_VALUE, this::onClose);
		button(g, pX + pW - 10 - 56, by, 56, 18, "Save", true, Integer.MIN_VALUE, Integer.MAX_VALUE, this::save);
	}

	private void save() {
		String typed = nameBox.getValue().trim();
		if (!typed.isEmpty() && !SoundConfig.sanitize(typed).replace('.', '_').equals(group)) {
			if (cfg.renameItem(group, typed) == null) {
				status = "That name is empty or already used";
				return;
			}
		}
		onClose();
	}

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
			if (!h.quiet) UiSounds.click();
			h.click.run();
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (Math.signum(sy) * ROW_H)));
		return true;
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
