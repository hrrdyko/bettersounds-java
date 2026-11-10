package bettersounds.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Warna dan helper gambar bevel bersama (dipakai layar editor grup). */
final class Ui {
	static final int OUT = 0xFF000000;
	static final int BG = 0xFF2A2A2B, BG_HI = 0xFF4B4C4E, BG_LO = 0xFF161617;
	static final int HEAD = 0xFF323334, SIDE = 0xFF212122;
	static final int ROW_A = 0xFF2D2D2E, ROW_B = 0xFF343435, ROW_HI = 0xFF3F4041, ROW_LO = 0xFF222223;
	static final int BTN = 0xFF4D4E50, BTN_HI = 0xFF7D7E81, BTN_LO = 0xFF2E2F30, BTN_H = 0xFF5F6063, BTN_HI_H = 0xFF909194;
	static final int G_FACE = 0xFF3C8527, G_HI = 0xFF72C24D, G_LO = 0xFF22501A, G_OUT = 0xFF0E2A0A, G_FACE_H = 0xFF4A9A31;
	static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFA8A8A8, ACCENT = 0xFF5DBB3D, ERROR = 0xFFE06060;

	private Ui() {}

	static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int face, int hi, int lo, int outline) {
		g.fill(x, y, x + w, y + h, outline);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
	}

	static void groove(GuiGraphicsExtractor g, int x1, int x2, int y) {
		g.fill(x1, y, x2, y + 1, 0xFF0D0D0E);
		g.fill(x1, y + 1, x2, y + 2, 0xFF434445);
	}
}
