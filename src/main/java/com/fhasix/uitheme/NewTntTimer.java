package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * TNT Timer: a small countdown above every lit TNT showing the seconds left until it blows up.
 * Drawn as a HUD overlay from the TNT's projected position. First-person view only. No settings.
 */
final class NewTntTimer {

    static final String ID = "tnt_timer";

    private static final double RANGE = 64.0;
    private static final int MAX_DRAWN = 24;
    private static final NewProjection PROJ = new NewProjection();

    private NewTntTimer() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "tnt_timer"),
                NewSafe.hud("tnt_timer", (context, tickCounter) -> render(context, tickCounter.getGameTimeDeltaPartialTick(false)))
        );
    }

    private static void render(GuiGraphicsExtractor g, float tickProgress) {
        Minecraft c = Minecraft.getInstance();
        if (c.level == null || c.player == null || c.gui.screen() != null || !NewModSettings.isEnabled(c, ID)
                || !PROJ.begin(c, g, tickProgress)) {
            return;
        }
        int cap = NewPerf.overlayCap(MAX_DRAWN);
        double range = NewPerf.overlayRange(RANGE);
        int drawn = 0;
        for (Entity e : c.level.getEntities((Entity) null, c.player.getBoundingBox().inflate(range + 2.0))) {
            if (drawn >= cap) {
                break;
            }
            if (!(e instanceof PrimedTnt tnt) || tnt.isRemoved()) {
                continue;
            }
            Vec3 p = tnt.getPosition(tickProgress).add(0.0, tnt.getBbHeight() + 0.5, 0.0);
            if (PROJ.distanceSq(p.x, p.y, p.z) > range * range) {
                continue;
            }
            int[] s = PROJ.project(p.x, p.y, p.z);
            if (s == null) {
                continue;
            }
            int fuse = Math.max(0, tnt.getFuse());
            String text = String.format(java.util.Locale.ROOT, "%.1fs", fuse / 20f);
            int col = fuse > 40 ? NewUi.lerp(0xFFFFD43B, 0xFF3FBF4D, Math.min(1f, (fuse - 40) / 40f))
                    : NewUi.lerp(0xFFE73C40, 0xFFFFD43B, fuse / 40f);
            int tw = c.font.width(text);
            int bw = tw + 10;
            int bh = c.font.lineHeight + 6;
            NewUi.frame(g, s[0] - bw / 2, s[1] - bh, bw, bh, 4, 0xFF3A3F4A, 0xD00C0D11, 0xD008090C);
            g.text(c.font, text, s[0] - tw / 2, s[1] - bh + 4, col, false);
            drawn++;
        }
    }
}
