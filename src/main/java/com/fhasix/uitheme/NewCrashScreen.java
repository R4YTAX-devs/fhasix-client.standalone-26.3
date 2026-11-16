package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Shown by No Crash instead of closing the game. */
public final class NewCrashScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 620;
    private static final int PH = 330;

    private final String detail;
    private final boolean showDetail;
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] button;

    NewCrashScreen(String detail, boolean showDetail) {
        super(Component.literal("Your game has crashed!"));
        this.detail = detail;
        this.showDetail = showDetail;
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
        button = rect(px + 40, py + PH - 40 - 72, PW - 80, 72);
    }

    private void toTitle() {
        NewNoCrash.reset();
        minecraft.gui.setScreen(new TitleScreen());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // own background in render()
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
        c.fillGradient(0, 0, width, height, 0xFF0B0C0F, 0xFF190A0C);
        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(16)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);

        int pad = sl(40);
        int y = panel[1] + sl(44);
        NewFont.draw(c, font, Component.literal("Your game has crashed!").withStyle(st -> st.withBold(true)),
                panel[0] + pad, y, 0xFFE73C40, false);
        y += font.lineHeight + sl(14);
        NewFont.draw(c, font, "No Crash caught the error so the game stayed open.", panel[0] + pad, y, NewUi.TXT, false);
        y += font.lineHeight + sl(10);
        if (showDetail) {
            int max = panel[2] - pad * 2;
            String d = NewFont.width(font, detail) > max
                    ? font.plainSubstrByWidth(detail, Math.max(0, max - NewFont.width(font, ".."))) + ".."
                    : detail;
            NewFont.draw(c, font, d, panel[0] + pad, y, NewUi.TXT_DIM, false);
            y += font.lineHeight + sl(6);
            NewFont.draw(c, font, "Full details are in the game log.", panel[0] + pad, y, NewUi.TXT_DIM, false);
        }

        boolean h = NewUi.in(mx, my, button[0], button[1], button[2], button[3]);
        NewUi.rrect(c, button[0], button[1], button[2], button[3], Math.max(3, sl(10)),
                h ? NewUi.redHT() : NewUi.redT(), h ? NewUi.redHB() : NewUi.redB());
        Component label = Component.literal("RETURN TO TITLE SCREEN").withStyle(st -> st.withBold(true));
        NewFont.draw(c, font, label, button[0] + (button[2] - NewFont.width(font, label)) / 2,
                button[1] + (button[3] - font.lineHeight) / 2, NewUi.TXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT
                && NewUi.in(click.x(), click.y(), button[0], button[1], button[2], button[3])) {
            toTitle();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE || keyEvent.key() == InputConstants.KEY_RETURN) { // Esc or Enter
            toTitle();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        toTitle();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
