package com.fhasix.uitheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A rounded-corner, gradient-filled text button. Rendering itself lives in
 * a1 (shared with c3/d4) - this class just picks
 * normal vs hover colors (instant swap, no fade) and draws the label on top.
 * Opacity defaults to fully solid; the Account pill uses the translucent
 * overload to match the icon row's 90%-opacity look.
 */
public class b2 extends Button {

    public static final int[] NAVY_NORMAL_TOP_LEFT     = a1.NAVY_NORMAL_TOP_LEFT;
    public static final int[] NAVY_NORMAL_BOTTOM_RIGHT = a1.NAVY_NORMAL_BOTTOM_RIGHT;
    public static final int[] NAVY_HOVER_TOP_LEFT      = a1.NAVY_HOVER_TOP_LEFT;
    public static final int[] NAVY_HOVER_BOTTOM_RIGHT  = a1.NAVY_HOVER_BOTTOM_RIGHT;
    public static final int[] RED_NORMAL_LEFT  = a1.RED_NORMAL_LEFT;
    public static final int[] RED_NORMAL_RIGHT = a1.RED_NORMAL_RIGHT;
    public static final int[] RED_HOVER_LEFT   = a1.RED_HOVER_LEFT;
    public static final int[] RED_HOVER_RIGHT  = a1.RED_HOVER_RIGHT;

    private final int[] normalLeft;
    private final int[] normalRight;
    private final int[] hoverLeft;
    private final int[] hoverRight;
    private final int alpha;

    /** Art mode (title-screen main buttons): drawn in code by NewButtonArt, icon + Inter label on top. */
    private int artKind = -1;          // 0 = navy, 1 = red, -1 = normal text button
    private Identifier artIcon;
    private String artLabel;
    private boolean artUpper;

    // hover fade + click ripple (art mode only)
    private OnPress realPress;      // the real action, run after the ripple has played
    private boolean pending;
    private long pendingAtNs;
    private long rippleStartNs = -1;
    private float rippleX, rippleY;     // click point, button-local GUI units
    private float hoverT;
    private long lastNs;
    private int lastMouseX, lastMouseY;
    private static final float RIPPLE_S = 0.25f;     // how long the circle takes to cover the button
    private static final float ACTION_DELAY_S = 0.22f; // when the real action runs after the click
    private static final float RIPPLE_HOLD_S = 1.2f;   // safety: how long the lighter overlay may stay

    public b2(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress,
                NAVY_NORMAL_TOP_LEFT, NAVY_NORMAL_BOTTOM_RIGHT, NAVY_HOVER_TOP_LEFT, NAVY_HOVER_BOTTOM_RIGHT, 255);
    }

    public b2(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress,
                                  int[] normalLeft, int[] normalRight, int[] hoverLeft, int[] hoverRight) {
        this(x, y, width, height, message, onPress, normalLeft, normalRight, hoverLeft, hoverRight, 255);
    }

    public b2(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress,
                                  int[] normalLeft, int[] normalRight, int[] hoverLeft, int[] hoverRight, int alpha) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.normalLeft = normalLeft;
        this.normalRight = normalRight;
        this.hoverLeft = hoverLeft;
        this.hoverRight = hoverRight;
        this.alpha = alpha;
    }

    /** Main title-screen button: drawn in code (no images) with a small icon and an upper-case Inter label. */
    public b2(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress,
              int artKind, Identifier artIcon, String artLabel, boolean artUpper) {
        this(x, y, width, height, message,
                btn -> { if (btn instanceof b2) ((b2) btn).startRipple(); },
                NAVY_NORMAL_TOP_LEFT, NAVY_NORMAL_BOTTOM_RIGHT, NAVY_HOVER_TOP_LEFT, NAVY_HOVER_BOTTOM_RIGHT, 255);
        this.realPress = onPress;
        this.artKind = artKind;
        this.artIcon = artIcon;
        this.artLabel = artLabel;
        this.artUpper = artUpper;
    }

    /** Called when the button is pressed: start the ripple and run the real action a moment later. */
    private void startRipple() {
        if (pending) return;
        long now = System.nanoTime();
        pending = true;
        pendingAtNs = now;
        rippleStartNs = now;
        rippleX = Math.max(0f, Math.min(this.getWidth(), lastMouseX - this.getX()));
        rippleY = Math.max(0f, Math.min(this.getHeight(), lastMouseY - this.getY()));
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();
        if (artKind >= 0) {
            int ax = this.getX(), ay = this.getY(), aw = this.getWidth(), ah = this.getHeight();
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 0f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
            lastNs = now;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            hoverT = hovered ? 1f : 0f; // no hover fade: the hover look switches on / off at once

            if (pending && (now - pendingAtNs) / 1_000_000_000f >= ACTION_DELAY_S) {
                pending = false;
                if (realPress != null) realPress.onPress(this);
            }

            if (artKind == 1) {
                NewButtonArt.red(context, ax, ay, aw, ah, hoverT);
            } else {
                NewButtonArt.navy(context, ax, ay, aw, ah, hoverT);
            }
            if (rippleStartNs > 0) {
                float t = (now - rippleStartNs) / 1_000_000_000f;
                if (t > RIPPLE_HOLD_S && !pending) {
                    rippleStartNs = -1;
                } else {
                    NewButtonArt.ripple(context, ax, ay, aw, ah, rippleX, rippleY, Math.min(1f, t / RIPPLE_S));
                }
            }
            NewButtonArt.label(context, ax, ay, aw, ah, artIcon, artLabel, artUpper, artKind == 1);
            return;
        }
        int[] topLeft = hovered ? hoverLeft : normalLeft;
        int[] bottomRight = hovered ? hoverRight : normalRight;
        a1.drawRoundedGradientRect(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                topLeft, bottomRight, alpha);

        // 1.21.x: ButtonWidget.drawIcon draws the label too, so since we override it
        // without calling super, the label must be drawn here (opaque ARGB or it is skipped).
        Font tr = Minecraft.getInstance().font;
        String label = this.getMessage().getString();
        int tx = this.getX() + (this.getWidth() - NewFont.width(tr, label)) / 2;
        int ty = this.getY() + (this.getHeight() - tr.lineHeight) / 2 + NewFont.Y_FIX;
        NewFont.draw(context, tr, label, tx, ty, 0xFFFFFFFF, true);
    }

}
