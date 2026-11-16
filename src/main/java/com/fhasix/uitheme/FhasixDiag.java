package com.fhasix.uitheme;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Self-check. Mixins here are all "require = 0", so when Mojang renames something the feature just
 * goes quiet. This writes the real method names of every class the mixins touch (plus a "MISSING"
 * marker for each hook target that no longer exists) to fhasix-diag.txt, so a wrong name is
 * obvious from one file instead of guesswork. Runs once, on a background thread, and can never throw.
 */
final class FhasixDiag {
    private FhasixDiag() {
    }

    /** class -> methods the mixins hook ("*" = list all members of that class). */
    private static final String[][] TARGETS = {
            {"net.minecraft.client.Camera", "alignWithEntity", "setRotation", "getMaxZoom", "move", "*"},
            {"net.minecraft.world.entity.Entity", "turn", "*Rot*"},
            {"net.minecraft.client.player.LocalPlayer", "turn", "*Rot*"},
            {"net.minecraft.client.MouseHandler", "*"},
            {"net.minecraft.client.renderer.GameRenderer", "getFov", "*Fov*", "*fov*", "*Camera*", "*camera*"},
            {"net.minecraft.client.renderer.fog.FogRenderer", "*"},
            {"net.minecraft.client.renderer.FogRenderer", "*"},
            {"net.minecraft.client.particle.ParticleEngine", "createParticle", "add", "*"},
            {"net.minecraft.client.renderer.entity.EntityRenderer", "shouldRender", "*"},
            {"net.minecraft.client.renderer.entity.ItemEntityRenderer", "*"},
            {"net.minecraft.client.renderer.entity.state.ItemEntityRenderState", "*"},
            {"com.mojang.blaze3d.platform.Window", "calculateScale", "*Scale*"},
            {"net.minecraft.client.OptionInstance", "*"},
            {"net.minecraft.client.Options", "gamma", "save", "*amma*", "*ight*", "*ude*"},
            {"net.minecraft.client.multiplayer.ClientLevel", "*ime*", "*lock*", "*ntit*"},
            {"net.minecraft.client.multiplayer.ClientLevel$ClientLevelData", "*"},
            {"net.minecraft.client.renderer.LightTexture", "*"},
            {"net.minecraft.client.renderer.Lightmap", "*"},
            {"net.minecraft.client.renderer.LevelRenderer", "*"},
            {"net.minecraft.client.gui.Gui", "setScreen", "*creen*", "*hud*"},
            {"net.minecraft.client.gui.Hud", "*"},
            {"net.minecraft.client.Minecraft", "runTick", "run", "*ime*", "*lock*", "*Camera*"},
    };

    static void start() {
        Thread t = new Thread(FhasixDiag::run, "fhasix-diag");
        t.setDaemon(true);
        t.start();
    }

    private static boolean match(String name, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
            return name.contains(pattern.substring(1, pattern.length() - 1));
        }
        return name.equals(pattern);
    }

    private static void run() {
        try {
            File out = new File(FabricLoader.getInstance().getGameDir().toFile(), "fhasix-diag.txt");
            try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
                w.println("FHASIX DIAG  mod=" + FabricLoader.getInstance().getModContainer("fhasixuitheme")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
                for (String[] spec : TARGETS) {
                    dump(w, spec);
                }
                w.println();
                w.println("hooked names present?");
                String[][] hooks = {
                        {"net.minecraft.client.Camera", "alignWithEntity"},
                        {"net.minecraft.client.Camera", "setRotation"},
                        {"net.minecraft.client.Camera", "getMaxZoom"},
                        {"net.minecraft.client.Camera", "move"},
                        {"net.minecraft.world.entity.Entity", "turn"},
                        {"net.minecraft.client.renderer.GameRenderer", "getFov"},
                        {"net.minecraft.client.particle.ParticleEngine", "createParticle"},
                        {"net.minecraft.client.renderer.entity.EntityRenderer", "shouldRender"},
                        {"net.minecraft.client.renderer.entity.ItemEntityRenderer", "extractRenderState"},
                        {"net.minecraft.client.renderer.entity.ItemEntityRenderer", "submit"},
                        {"com.mojang.blaze3d.platform.Window", "calculateScale"},
                        {"net.minecraft.client.Minecraft", "runTick"},
                };
                for (String[] h : hooks) {
                    w.println((has(h[0], h[1]) ? "  OK       " : "  MISSING  ") + h[0] + "#" + h[1]);
                }
            }
            try { // phone launchers: also drop a copy where the file manager can reach it
                java.nio.file.Files.copy(out.toPath(), new File("/sdcard/Download/fhasix-diag.txt").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean has(String cls, String method) {
        try {
            for (Method m : Class.forName(cls).getDeclaredMethods()) {
                if (m.getName().equals(method)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void dump(PrintWriter w, String[] spec) {
        w.println();
        w.println("=== " + spec[0]);
        Class<?> c;
        try {
            c = Class.forName(spec[0]);
        } catch (Throwable t) {
            w.println("  CLASS MISSING");
            return;
        }
        w.println("  extends " + c.getSuperclass());
        boolean all = false;
        for (int i = 1; i < spec.length; i++) {
            all |= spec[i].equals("*");
        }
        try {
            Method[] ms = c.getDeclaredMethods();
            Arrays.sort(ms, Comparator.comparing(Method::getName));
            for (Method m : ms) {
                if (matches(spec, m.getName(), all)) {
                    w.println("  " + Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " "
                            + m.getName() + "(" + params(m.getParameterTypes()) + ")");
                }
            }
            Field[] fs = c.getDeclaredFields();
            Arrays.sort(fs, Comparator.comparing(Field::getName));
            for (Field f : fs) {
                if (matches(spec, f.getName(), all)) {
                    w.println("  field " + Modifier.toString(f.getModifiers()) + " " + f.getType().getSimpleName() + " " + f.getName());
                }
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (all) {
                    w.println("  new(" + params(k.getParameterTypes()) + ")");
                }
            }
        } catch (Throwable t) {
            w.println("  (could not read members: " + t + ")");
        }
    }

    private static boolean matches(String[] spec, String name, boolean all) {
        if (all) {
            return true;
        }
        for (int i = 1; i < spec.length; i++) {
            if (match(name, spec[i])) {
                return true;
            }
        }
        return false;
    }

    private static String params(Class<?>[] p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(p[i].getSimpleName());
        }
        return sb.toString();
    }
}
