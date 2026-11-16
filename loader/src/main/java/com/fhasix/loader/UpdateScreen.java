package com.fhasix.loader;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Full-screen update gate shown before the title screen. No cancel while an update is pending. */
public final class UpdateScreen extends Screen {
    private final Screen next;
    private final Updater up = FhasixLoader.UPDATER;
    private RedButton primary;
    private RedButton secondary;

    public UpdateScreen(Screen next) {
        super(Component.literal("FHASIX Update"));
        this.next = next;
    }

    @Override
    protected void init() {
        int w = 200, h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 + 24;
        primary = new RedButton(x, y, w, h, Component.literal("Update"), this::onPrimary);
        secondary = new RedButton(x, y + 26, w, h, Component.literal("Quit game"), () -> this.minecraft.stop());
        refreshButtons();
    }

    private void onPrimary() {
        switch (up.state) {
            case NEEDS_UPDATE -> up.startApply();
            case ERROR -> up.startCheck();
            case DONE -> this.minecraft.stop();
            default -> { }
        }
    }

    private void refreshButtons() {
        if (primary == null) return;
        Updater.State s = up.state;
        switch (s) {
            case NEEDS_UPDATE -> setPrimary(true, up.firstInstall ? "Install" : "Update");
            case ERROR -> setPrimary(true, "Retry");
            case DONE -> setPrimary(true, "Close game");
            default -> setPrimary(false, "");
        }
        secondary.visible = s == Updater.State.ERROR;
        secondary.active = secondary.visible;
    }

    private void setPrimary(boolean on, String label) {
        primary.visible = on;
        primary.active = on;
        if (on) primary.label = Component.literal(label);
    }

    @Override
    public void tick() {
        if (up.state == Updater.State.UP_TO_DATE) {
            this.minecraft.gui.setScreen(next);
            return;
        }
        refreshButtons();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF070708);
        int pw = Math.min(this.width - 20, 320);
        int x0 = this.width / 2 - pw / 2;
        int y0 = this.height / 2 - 78;
        int y1 = this.height / 2 + 84;
        context.fill(x0, y0, x0 + pw, y1, 0xFFB3262B);
        context.fill(x0 + 1, y0 + 1, x0 + pw - 1, y1 - 1, 0xFF101012);
        context.fill(x0 + 1, y0 + 1, x0 + pw - 1, y0 + 3, 0xFFE13B3E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        primary.draw(context, mouseX, mouseY);
        secondary.draw(context, mouseX, mouseY);
        int cx = this.width / 2;
        int pw = Math.min(this.width - 20, 320);

        center(context, "FHASIX CLIENT", cx, this.height / 2 - 62, 0xFFE13B3E);

        String line1;
        String line2 = "";
        int color2 = 0xFF9A9A9F;
        switch (up.state) {
            case CHECKING -> line1 = "Checking for updates...";
            case NEEDS_UPDATE -> {
                line1 = up.firstInstall ? "Install " + up.remoteVersion : "Update available: " + up.remoteVersion;
                line2 = up.firstInstall ? "FHASIX Client needs to be downloaded." : "An update is required to continue.";
            }
            case DOWNLOADING -> {
                line1 = "Downloading " + up.remoteVersion + "...  " + Math.round(up.progress * 100) + "%";
                line2 = "Please wait, do not close the game.";
            }
            case DONE -> {
                line1 = "Done!";
                line2 = "Restart the game to finish.";
            }
            case ERROR -> {
                line1 = "Something went wrong";
                line2 = up.message;
                color2 = 0xFFFF8A8A;
            }
            default -> line1 = "";
        }
        center(context, line1, cx, this.height / 2 - 44, 0xFFE6E8EB);

        if (!line2.isEmpty()) {
            List<FormattedCharSequence> lines = this.font.split(Component.literal(line2), pw - 30);
            int y = this.height / 2 - 30;
            for (FormattedCharSequence l : lines) {
                context.text(this.font, l, cx - this.font.width(l) / 2, y, color2, true);
                y += 11;
            }
        }

        if (up.state == Updater.State.DOWNLOADING || up.state == Updater.State.DONE) {
            int bw = pw - 40;
            int bx = cx - bw / 2;
            int by = this.height / 2 + 6;
            context.fill(bx, by, bx + bw, by + 6, 0xFF2A1416);
            context.fill(bx, by, bx + Math.round(bw * up.progress), by + 6, 0xFFE13B3E);
        }
    }

    private void center(GuiGraphicsExtractor c, String s, int cx, int y, int color) {
        Component t = Component.literal(s);
        c.text(this.font, t, cx - this.font.width(t) / 2, y, color, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.buttonInfo().button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (RedButton b : new RedButton[]{primary, secondary}) {
                if (b != null && b.contains(click.x(), click.y())) {
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
