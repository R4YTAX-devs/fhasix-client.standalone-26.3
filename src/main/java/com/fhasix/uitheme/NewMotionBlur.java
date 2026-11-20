package com.fhasix.uitheme;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Motion Blur (26.3).
 *
 * The game has one "post effect" slot that is rendered after the world and before the HUD, so the HUD stays sharp.
 * This mod puts its own effect there (post_effect/motion_blur_1..5.json, five strengths). The effect keeps the previous
 * frame in a persistent buffer and blends it with the new one: movement leaves a short smooth trail. It is one extra
 * full-screen pass and does not touch the world renderer, so it works on OpenGL and on the Vulkan renderer.
 *
 * 26.3 moved the GPU buffer classes (they are now in com.mojang.renderpearl), so this version uses a fixed blend per
 * strength step instead of changing the blend every frame. The slot fields are looked up by name at run time and
 * checked: if something is missing the effect simply stays off and fhasix-blur-diag.txt says why; the game never
 * crashes because of it.
 */
public final class NewMotionBlur {

    static final String ID = "motion_blur";
    private static final Identifier[] STEPS = {
            Identifier.fromNamespaceAndPath("fhasixuitheme", "motion_blur_1"),
            Identifier.fromNamespaceAndPath("fhasixuitheme", "motion_blur_2"),
            Identifier.fromNamespaceAndPath("fhasixuitheme", "motion_blur_3"),
            Identifier.fromNamespaceAndPath("fhasixuitheme", "motion_blur_4"),
            Identifier.fromNamespaceAndPath("fhasixuitheme", "motion_blur_5")
    };

    private static boolean active = false;
    private static boolean resolved = false;
    private static Field slotId;
    private static Field slotActive;

    private NewMotionBlur() {
    }

    private static void resolve(Object renderer) {
        resolved = true;
        StringBuilder diag = new StringBuilder("Fhasix motion blur diagnostics (26.3)\n\nGameRenderer fields:\n");
        for (Field f : renderer.getClass().getDeclaredFields()) {
            diag.append("  ").append(f.getType().getSimpleName()).append(' ').append(f.getName()).append('\n');
            if (slotId == null && f.getType() == Identifier.class) {
                slotId = f;
            }
        }
        for (String name : new String[]{"effectActive", "postEffectActive", "postProcessorEnabled"}) {
            try {
                Field f = renderer.getClass().getDeclaredField(name);
                if (f.getType() == boolean.class) {
                    slotActive = f;
                    break;
                }
            } catch (NoSuchFieldException ignored) {
                // try the next name
            }
        }
        try {
            if (slotId != null) {
                slotId.setAccessible(true);
            }
            if (slotActive != null) {
                slotActive.setAccessible(true);
            }
        } catch (RuntimeException e) {
            slotId = null;
            slotActive = null;
        }
        diag.append("\nid field: ").append(slotId == null ? "NOT FOUND" : slotId.getName());
        diag.append("\nactive field: ").append(slotActive == null ? "NOT FOUND" : slotActive.getName()).append('\n');
        try {
            Files.write(new File(Minecraft.getInstance().gameDirectory, "fhasix-blur-diag.txt").toPath(),
                    diag.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // diagnostics only
        }
    }

    static void tick(Minecraft c) {
        boolean want = c.level != null && c.player != null && NewModSettings.isEnabled(c, ID);
        if (!want && !active) {
            return;
        }
        Object gr = c.gameRenderer;
        if (!resolved) {
            resolve(gr);
        }
        if (slotId == null || slotActive == null) {
            return; // this Minecraft version has a slot we do not know: stay off
        }
        try {
            Identifier cur = (Identifier) slotId.get(gr);
            if (want) {
                if (cur != null && !ours(cur)) {
                    return; // the game (spectating a mob, for example) is using the slot
                }
                Identifier target = STEPS[stepIndex(c)];
                if (!target.equals(cur)) {
                    slotId.set(gr, target);
                }
                slotActive.setBoolean(gr, true);
                active = true;
            } else if (active) {
                if (cur != null && ours(cur)) {
                    slotActive.setBoolean(gr, false);
                    slotId.set(gr, null);
                }
                active = false;
            }
        } catch (Exception | LinkageError t) {
            NewSafe.failed("motion_blur", t);
        }
    }

    private static boolean ours(Identifier id) {
        for (Identifier p : STEPS) {
            if (p.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The strength slider (10 to 90) picks one of the five prepared effects. */
    private static int stepIndex(Minecraft c) {
        int s = Math.max(10, Math.min(90, NewModSettings.opt(c, ID, "strength")));
        return Math.min(STEPS.length - 1, (s - 10) * STEPS.length / 81);
    }
}
