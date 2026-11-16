package com.fhasix.uitheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Standalone banner screen used by the FHASIX title-screen top-bar buttons.
 * Kept outside the TitleScreen mixin so Mixin does not relocate a nested
 * screen class into TitleScreen and produce conflicting InnerClasses metadata.
 */
public final class WorldOnlyBannerScreen extends Screen {
    private final Screen parent;

    public WorldOnlyBannerScreen(Screen parent) {
        super(Component.literal("FHASIX"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x88000000);

        int w = Math.round(width * 0.38f);
        int h = Math.round(height * 0.22f);
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        a1.drawRoundedGradientRect(context, x, y, w, h,
                new int[]{14, 17, 22}, new int[]{9, 10, 14}, 250);
        NewFont.drawCentered(context, font,
                "This is only accessible in world", width / 2, y + 42, 0xFFFFFFFF);
        NewFont.drawCentered(context, font,
                "Click anywhere to return", width / 2, y + h - 28, 0xFF7F8388);
    }

    // PORTING NOTE (v18.2): 1.21.9 replaced Element's old mouseClicked(double,double,int)/
    // keyPressed(int,int,int) with mouseClicked(Click,boolean)/keyPressed(KeyEvent) - the old
    // signatures still compile (Screen/Element keep them as unrelated dead methods) but the
    // game no longer calls them, which is why every hand-built button in this mod stopped
    // responding to clicks after the 1.21.11 port even though it still rendered correctly.
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        minecraft.gui.setScreen(parent);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
