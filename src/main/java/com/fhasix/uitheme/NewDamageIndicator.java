package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Health Indicator (was "Damage Indicator"): draws a health bar + number above the head of players
 * and mobs you can see. Done as a HUD overlay - the entity's head position is projected onto the
 * screen from the camera - so it works for every living entity, not only for nametags.
 * The bar is a row of real vanilla hearts (container + full / half sprites).
 * Settings: heart bar on/off, show as health (20) or hearts (10), colour by health, players,
 * mobs / animals, and the maximum distance. First-person view only.
 */
public final class NewDamageIndicator {

    static final String ID = "damage_indicator";

    private static final int MAX_DRAWN = 40;
    private static final int HEART = 9;
    private static final int HEART_STEP = 8;
    private static final Identifier HEART_CONTAINER = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.fromNamespaceAndPath("minecraft", "hud/heart/half");

    private NewDamageIndicator() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "health_indicator"),
                NewSafe.hud("health_indicator", (context, tickCounter) -> render(context, tickCounter.getGameTimeDeltaPartialTick(false)))
        );
    }

    private static void render(GuiGraphicsExtractor g, float tickProgress) {
        Minecraft c = Minecraft.getInstance();
        if (c.level == null || c.player == null || c.gui.screen() != null
                || !NewModSettings.isEnabled(c, ID) || !c.options.getCameraType().isFirstPerson()) {
            return;
        }
        Entity camEntity = c.getCameraEntity() != null ? c.getCameraEntity() : c.player;
        Vec3 eye = camEntity.getPosition(tickProgress).add(0.0, camEntity.getEyeHeight(), 0.0);

        // camera basis from the camera's own yaw/pitch (so freelook is respected)
        double yaw = Math.toRadians(c.player.getYRot());
        double pitch = Math.toRadians(c.player.getXRot());
        double cosP = Math.cos(pitch);
        double fx = -Math.sin(yaw) * cosP;
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * cosP;
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -fy * rz; // up = right x forward (right.y = 0)
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        double tanHalf = Math.tan(Math.toRadians(c.options.fov().get()) / 2.0);
        double aspect = sw / (double) sh;
        int cap = NewPerf.overlayCap(MAX_DRAWN);
        if (cap == 0) {
            return;
        }
        double range = NewPerf.overlayRange(NewModSettings.opt(c, ID, "range"));
        boolean players = NewModSettings.optBool(c, ID, "players");
        boolean mobs = NewModSettings.optBool(c, ID, "mobs");

        int drawn = 0;
        for (Entity e : c.level.getEntities((Entity) null, c.player.getBoundingBox().inflate(range + 2.0))) {
            if (drawn >= cap) {
                break;
            }
            if (!(e instanceof LivingEntity le) || e == camEntity || e == c.player || !le.isAlive()
                    || e instanceof ArmorStand) {
                continue;
            }
            if (e instanceof Player ? !players : !mobs) {
                continue;
            }
            Vec3 head = le.getPosition(tickProgress).add(0.0, le.getBbHeight() + 0.4, 0.0);
            double dx = head.x - eye.x;
            double dy = head.y - eye.y;
            double dz = head.z - eye.z;
            if (dx * dx + dy * dy + dz * dz > range * range || !c.player.hasLineOfSight(le)) {
                continue;
            }
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.3) {
                continue;
            }
            double nx = (dx * rx + dz * rz) / (depth * tanHalf * aspect);
            double ny = (dx * ux + dy * uy + dz * uz) / (depth * tanHalf);
            if (Math.abs(nx) > 1.2 || Math.abs(ny) > 1.2) {
                continue;
            }
            drawOne(g, c, le, Math.round((float) (sw / 2.0 * (1.0 + nx))), Math.round((float) (sh / 2.0 * (1.0 - ny))));
            drawn++;
        }
    }

    /** Bar + number centred on x, with the bottom of the whole thing at y. */
    private static void drawOne(GuiGraphicsExtractor g, Minecraft c, LivingEntity e, int x, int y) {
        float hp = e.getHealth() + e.getAbsorptionAmount();
        float max = Math.max(1f, e.getMaxHealth());
        float frac = Math.max(0f, Math.min(1f, e.getHealth() / max));
        int color = 0xFFE73C40;
        if (NewModSettings.optBool(c, ID, "colored")) {
            color = frac > 0.5f ? NewUi.lerp(0xFFFFD43B, 0xFF3FBF4D, (frac - 0.5f) * 2f)
                    : NewUi.lerp(0xFFE73C40, 0xFFFFD43B, frac * 2f);
        }
        String num = "\u2665 " + (NewModSettings.opt(c, ID, "format") == 1 ? trim(hp / 2f) : String.valueOf(Math.round(hp)));
        int textW = c.font.width(num);
        boolean bar = NewModSettings.optBool(c, ID, "bar");

        int textY = y - c.font.lineHeight;
        if (bar) {
            // real vanilla hearts: 10 of them (fewer for low max health), each one = max / count health
            int n = Math.max(1, Math.min(10, (int) Math.ceil(max / 2f)));
            float per = max / n;
            int rowW = (n - 1) * HEART_STEP + HEART;
            int hx = x - rowW / 2;
            int hy = y - HEART;
            textY = hy - c.font.lineHeight - 2;
            for (int i = 0; i < n; i++) {
                float v = e.getHealth() - i * per;
                int px = hx + i * HEART_STEP;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, px, hy, HEART, HEART);
                if (v >= per * 0.99f) {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, px, hy, HEART, HEART);
                } else if (v > per * 0.5f - 0.01f) {
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, px, hy, HEART, HEART);
                }
            }
        }
        g.text(c.font, num, x - textW / 2, textY, 0xFF000000 | (color & 0xFFFFFF), true);
    }

    private static String trim(float v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
