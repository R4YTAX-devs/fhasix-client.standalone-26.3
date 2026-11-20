package com.fhasix.uitheme;

import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The Waypoints list: add one at your position (with a name) or delete one. */
public final class NewWaypointsScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 780;
    private static final int PH = 640;
    private static final int ROWS = 6;
    private static final int ROW_H = 64;

    private float k;
    private int ox;
    private int oy;
    private int page = 0;
    private int[] panel;
    private int[] addBtn;
    private int[] prevBtn;
    private int[] nextBtn;
    private int[] closeBtn;
    private EditBox nameField;

    NewWaypointsScreen() {
        super(Component.literal("Waypoints"));
    }

    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private int[] rect(int x, int y, int w, int h) {
        int x0 = sx(x);
        int y0 = sy(y);
        return new int[]{x0, y0, sx(x + w) - x0, sy(y + h) - y0};
    }

    @Override
    protected void init() {
        k = NewUi.scale(width, height);
        ox = (width - Math.round(DW * k)) / 2;
        oy = (height - Math.round(DH * k)) / 2;
        int px = (DW - PW) / 2;
        int py = (DH - PH) / 2;
        panel = rect(px, py, PW, PH);
        int by = py + PH - 96;
        int[] f = rect(px + 32, by, 400, 56);
        nameField = new EditBox(font, f[0], f[1], f[2], f[3], Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("Name (optional)"));
        addRenderableWidget(nameField);
        addBtn = rect(px + 448, by, 300, 56);
        prevBtn = rect(px + PW - 32 - 56 - 12 - 56, py + 24, 56, 44);
        nextBtn = rect(px + PW - 32 - 56, py + 24, 56, 44);
        closeBtn = rect(px + PW - 32 - 56 - 12 - 56 - 12 - 150, py + 24, 150, 44);
    }

    private int[] rowRect(int i) {
        int px = (DW - PW) / 2;
        int py = (DH - PH) / 2;
        return rect(px + 32, py + 92 + i * ROW_H, PW - 64, ROW_H - 8);
    }

    private int[] delRect(int i) {
        int[] r = rowRect(i);
        int w = sl(130);
        return new int[]{r[0] + r[2] - w - sl(10), r[1] + sl(8), w, r[3] - sl(16)};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // own background in render()
    }

    private void button(GuiGraphicsExtractor c, int[] r, String label, boolean red, int mx, int my) {
        boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
        int rad = Math.max(3, sl(10));
        if (red) {
            NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, h ? NewUi.redHT() : NewUi.redT(), h ? NewUi.redHB() : NewUi.redB());
        } else {
            NewUi.frame(c, r[0], r[1], r[2], r[3], rad, NewUi.BTN_BORDER, h ? NewUi.HOV_T : NewUi.BTN_T, h ? NewUi.HOV_B : NewUi.BTN_B);
        }
        int tw = NewFont.widthUi(font, label, NewFont.W_BOLD, 13f);
        NewFont.drawUi(c, font, label, r[0] + (r[2] - tw) / 2, r[1] + (r[3] - 13) / 2, NewUi.TXT, NewFont.W_BOLD, 13f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xB0000000);
        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(16)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);
        NewFont.drawUi(c, font, "WAYPOINTS", panel[0] + sl(32), panel[1] + sl(30), NewUi.TXT, NewFont.W_BOLD, 18f);

        List<NewWaypoints.Wp> list = NewWaypoints.here(minecraft);
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        button(c, closeBtn, "CLOSE", false, mx, my);
        button(c, prevBtn, "<", false, mx, my);
        button(c, nextBtn, ">", false, mx, my);

        if (list.isEmpty()) {
            NewFont.drawUi(c, font, "No waypoints here yet. Type a name and press ADD HERE, or press B in the world.",
                    panel[0] + sl(32), panel[1] + sl(110), NewUi.TXT_DIM, NewFont.W_MED, 11f);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= list.size()) {
                break;
            }
            NewWaypoints.Wp w = list.get(idx);
            int[] r = rowRect(i);
            NewUi.frame(c, r[0], r[1], r[2], r[3], Math.max(3, sl(8)), NewUi.BTN_BORDER, NewUi.BTN_T, NewUi.BTN_B);
            NewWaypoints.diamond(c, r[0] + sl(26), r[1] + r[3] / 2, Math.max(4, sl(9)), 0xFF000000 | w.rgb);
            NewFont.drawUi(c, font, w.name, r[0] + sl(56), r[1] + sl(8), NewUi.TXT, NewFont.W_BOLD, 13f);
            double d = minecraft.player == null ? 0 : Math.sqrt(minecraft.player.distanceToSqr(w.x + 0.5, w.y, w.z + 0.5));
            NewFont.drawUi(c, font, w.x + ", " + w.y + ", " + w.z + "   " + Math.round(d) + " m",
                    r[0] + sl(56), r[1] + sl(30), NewUi.TXT_DIM, NewFont.W_MED, 10f);
            button(c, delRect(i), "DELETE", true, mx, my);
        }
        NewFont.drawUi(c, font, (page + 1) + " / " + pages, prevBtn[0] - sl(70), panel[1] + sl(36),
                NewUi.TXT_DIM, NewFont.W_MED, 12f);
        button(c, addBtn, "ADD HERE", true, mx, my);
        super.extractRenderState(c, mx, my, delta); // the text field
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() == 0) {
            double x = click.x();
            double y = click.y();
            List<NewWaypoints.Wp> list = NewWaypoints.here(minecraft);
            for (int i = 0; i < ROWS; i++) {
                int idx = page * ROWS + i;
                if (idx < list.size()) {
                    int[] d = delRect(i);
                    if (NewUi.in(x, y, d[0], d[1], d[2], d[3])) {
                        NewWaypoints.remove(minecraft, list.get(idx));
                        return true;
                    }
                }
            }
            if (NewUi.in(x, y, addBtn[0], addBtn[1], addBtn[2], addBtn[3])) {
                NewWaypoints.add(minecraft, nameField.getValue());
                nameField.setValue("");
                return true;
            }
            if (NewUi.in(x, y, prevBtn[0], prevBtn[1], prevBtn[2], prevBtn[3])) {
                page = Math.max(0, page - 1);
                return true;
            }
            if (NewUi.in(x, y, nextBtn[0], nextBtn[1], nextBtn[2], nextBtn[3])) {
                page++;
                return true;
            }
            if (NewUi.in(x, y, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3])) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == 257 && nameField.isFocused()) { // Enter adds
            NewWaypoints.add(minecraft, nameField.getValue());
            nameField.setValue("");
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
