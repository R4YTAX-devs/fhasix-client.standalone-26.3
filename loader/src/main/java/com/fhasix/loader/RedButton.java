package com.fhasix.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Flat red/black button matching the FHASIX theme. Drawn and clicked by UpdateScreen itself. */
public class RedButton {
    final int x, y, w, h;
    final Runnable action;
    Component label;
    boolean visible = true;
    boolean active = true;

    public RedButton(int x, int y, int w, int h, Component label, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.action = action;
    }

    boolean contains(double mx, double my) {
        return visible && active && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    void draw(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean hover = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int border = active ? 0xFFB3262B : 0xFF3A3A3E;
        int base = active ? (hover ? 0xFF8E1D21 : 0xFF1A0E0F) : 0xFF141416;
        context.fill(x, y, x + w, y + h, border);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
        Font tr = Minecraft.getInstance().font;
        int color = active ? 0xFFFFFFFF : 0xFF77777C;
        context.text(tr, label, x + (w - tr.width(label)) / 2, y + (h - tr.lineHeight) / 2 + 1, color, true);
    }
}
