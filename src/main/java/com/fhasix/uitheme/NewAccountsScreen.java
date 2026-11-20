package com.fhasix.uitheme;

import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Account Switcher: pick an offline account, add a name, remove one. Opened from the name at the top right. */
public final class NewAccountsScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 700;
    private static final int PH = 620;
    private static final int ROWS = 5;
    private static final int ROW_H = 64;

    private final Screen parent;
    private float k;
    private int ox;
    private int oy;
    private int page = 0;
    private String note = "";
    private int[] panel;
    private int[] addBtn;
    private int[] prevBtn;
    private int[] nextBtn;
    private int[] closeBtn;
    private EditBox nameField;

    public NewAccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
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
        int by = py + PH - 88;
        int[] f = rect(px + 32, by, 360, 56);
        nameField = new EditBox(font, f[0], f[1], f[2], f[3], Component.literal("Name"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.literal("New offline name"));
        addRenderableWidget(nameField);
        addBtn = rect(px + 408, by, 260, 56);
        nextBtn = rect(px + PW - 32 - 56, py + 24, 56, 44);
        prevBtn = rect(px + PW - 32 - 56 - 12 - 56, py + 24, 56, 44);
        closeBtn = rect(px + PW - 32 - 56 - 12 - 56 - 12 - 150, py + 24, 150, 44);
    }

    private int[] rowRect(int i) {
        int px = (DW - PW) / 2;
        int py = (DH - PH) / 2;
        return rect(px + 32, py + 112 + i * ROW_H, PW - 64, ROW_H - 8);
    }

    private int[] useRect(int i) {
        int[] r = rowRect(i);
        int w = sl(110);
        return new int[]{r[0] + r[2] - w * 2 - sl(20), r[1] + sl(8), w, r[3] - sl(16)};
    }

    private int[] delRect(int i) {
        int[] r = rowRect(i);
        int w = sl(110);
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
        int tw = NewFont.widthUi(font, label, NewFont.W_BOLD, 12f);
        NewFont.drawUi(c, font, label, r[0] + (r[2] - tw) / 2, r[1] + (r[3] - 12) / 2, NewUi.TXT, NewFont.W_BOLD, 12f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xB0000000);
        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(16)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);
        NewFont.drawUi(c, font, "ACCOUNTS", panel[0] + sl(32), panel[1] + sl(30), NewUi.TXT, NewFont.W_BOLD, 18f);
        NewFont.drawUi(c, font, "Offline accounts: singleplayer and offline-mode servers only.",
                panel[0] + sl(32), panel[1] + sl(78), NewUi.TXT_DIM, NewFont.W_MED, 10f);

        List<String> names = NewAccounts.names(minecraft);
        String current = minecraft.getUser().getName();
        int pages = Math.max(1, (names.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        button(c, closeBtn, "CLOSE", false, mx, my);
        button(c, prevBtn, "<", false, mx, my);
        button(c, nextBtn, ">", false, mx, my);
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= names.size()) {
                break;
            }
            String n = names.get(idx);
            boolean isCurrent = n.equals(current);
            int[] r = rowRect(i);
            NewUi.frame(c, r[0], r[1], r[2], r[3], Math.max(3, sl(8)), NewUi.BTN_BORDER,
                    isCurrent ? NewUi.ON_T : NewUi.BTN_T, isCurrent ? NewUi.ON_B : NewUi.BTN_B);
            NewFont.drawUi(c, font, n, r[0] + sl(22), r[1] + (r[3] - 14) / 2, NewUi.TXT, NewFont.W_BOLD, 14f);
            if (isCurrent) {
                NewFont.drawUi(c, font, "IN USE", useRect(i)[0] + sl(14), r[1] + (r[3] - 11) / 2, NewUi.TXT_ON, NewFont.W_BOLD, 11f);
            } else {
                button(c, useRect(i), "USE", false, mx, my);
            }
            button(c, delRect(i), "REMOVE", true, mx, my);
        }
        NewFont.drawUi(c, font, (page + 1) + " / " + pages, prevBtn[0] - sl(70), panel[1] + sl(36),
                NewUi.TXT_DIM, NewFont.W_MED, 12f);
        button(c, addBtn, "ADD NAME", true, mx, my);
        if (!note.isEmpty()) {
            NewFont.drawUi(c, font, note, panel[0] + sl(32), panel[1] + panel[3] - sl(130), NewUi.TXT_DIM, NewFont.W_MED, 10f);
        }
        super.extractRenderState(c, mx, my, delta);
    }

    private void addName() {
        String n = nameField.getValue();
        if (NewAccounts.add(minecraft, n)) {
            nameField.setValue("");
            note = "";
        } else {
            note = "Use 3 to 16 letters, numbers or _ (and not a name that is already in the list).";
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() == 0) {
            double x = click.x();
            double y = click.y();
            List<String> names = NewAccounts.names(minecraft);
            for (int i = 0; i < ROWS; i++) {
                int idx = page * ROWS + i;
                if (idx >= names.size()) {
                    break;
                }
                int[] u = useRect(i);
                int[] d = delRect(i);
                if (NewUi.in(x, y, u[0], u[1], u[2], u[3]) && !names.get(idx).equals(minecraft.getUser().getName())) {
                    if (NewAccounts.use(minecraft, names.get(idx))) {
                        minecraft.gui.setScreen(parent);
                    } else {
                        note = "Could not switch to that account.";
                    }
                    return true;
                }
                if (NewUi.in(x, y, d[0], d[1], d[2], d[3])) {
                    NewAccounts.remove(minecraft, names.get(idx));
                    return true;
                }
            }
            if (NewUi.in(x, y, addBtn[0], addBtn[1], addBtn[2], addBtn[3])) {
                addName();
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
        if (keyEvent.key() == 257 && nameField.isFocused()) {
            addName();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
