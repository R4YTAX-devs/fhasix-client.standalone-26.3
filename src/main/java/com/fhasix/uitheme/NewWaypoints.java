package com.fhasix.uitheme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

/**
 * Waypoints: named places you save per server / world and dimension. They show in the world as a small marker with
 * the name and the distance, and on the Minimap. Keys: B = save a waypoint here, N = open the list.
 * Saved in fhasix_waypoints.txt (one line per waypoint) next to the other Fhasix settings.
 */
public final class NewWaypoints {

    static final String ID = "waypoints";
    static final int[] COLORS = {0xE73C40, 0x3FBF4D, 0x4DA3FF, 0xF2B632, 0xB45CFF, 0x2EE6D6, 0xFF7A3D, 0xFFFFFF};

    static final class Wp {
        String name;
        final String world;
        final String dim;
        int x;
        int y;
        int z;
        int rgb;

        Wp(String name, String world, String dim, int x, int y, int z, int rgb) {
            this.name = name;
            this.world = world;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
        }
    }

    private static final NewProjection PROJ = new NewProjection();
    private static final List<Wp> ALL = new ArrayList<>();
    private static boolean loaded = false;
    private static KeyMapping addKey;
    private static KeyMapping listKey;

    private NewWaypoints() {
    }

    static void init(KeyMapping.Category category) {
        addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.waypoint_add", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
        listKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.waypoint_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, category));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("waypoints", NewWaypoints::tick));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "waypoints_hud"),
                NewSafe.hud("waypoints", (context, tickCounter) -> render(context, tickCounter.getGameTimeDeltaPartialTick(false))));
    }

    private static void tick(Minecraft c) {
        while (addKey != null && addKey.consumeClick()) {
            if (c.player != null && c.gui.screen() == null && NewModSettings.isEnabled(c, ID)) {
                add(c, "");
            }
        }
        while (listKey != null && listKey.consumeClick()) {
            if (c.player != null && c.gui.screen() == null && NewModSettings.isEnabled(c, ID)) {
                c.gui.setScreen(new NewWaypointsScreen());
            }
        }
    }

    // ---------------------------------------------------------------- data

    private static Path file(Minecraft c) {
        return c.gameDirectory.toPath().resolve("fhasix_waypoints.txt");
    }

    private static void ensureLoaded(Minecraft c) {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path f = file(c);
            if (!Files.exists(f)) {
                return;
            }
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", 7);
                if (p.length == 7) {
                    ALL.add(new Wp(p[6], p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                            Integer.parseInt(p[4]), Integer.parseInt(p[5])));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // a damaged file must never break the game
        }
    }

    private static void save(Minecraft c) {
        try {
            List<String> lines = new ArrayList<>();
            for (Wp w : ALL) {
                lines.add(w.world + "\t" + w.dim + "\t" + w.x + "\t" + w.y + "\t" + w.z + "\t" + w.rgb + "\t"
                        + w.name.replace('\t', ' ').replace('\n', ' '));
            }
            Files.write(file(c), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // not being able to save must never break anything
        }
    }

    /** The key of the current server / world, so waypoints of different servers do not mix. */
    static String worldKey(Minecraft c) {
        net.minecraft.client.multiplayer.ServerData si = c.getCurrentServer();
        if (si != null) {
            return si.ip;
        }
        return c.isLocalServer() ? "singleplayer" : "other";
    }

    static String dimension(Minecraft c) {
        return c.level == null ? "" : c.level.dimension().toString();
    }

    /** Waypoints of the current world and dimension. */
    static List<Wp> here(Minecraft c) {
        ensureLoaded(c);
        List<Wp> out = new ArrayList<>();
        if (c.level == null) {
            return out;
        }
        String wk = worldKey(c);
        String dim = dimension(c);
        for (Wp w : ALL) {
            if (w.world.equals(wk) && w.dim.equals(dim)) {
                out.add(w);
            }
        }
        return out;
    }

    static void add(Minecraft c, String name) {
        ensureLoaded(c);
        if (c.player == null || c.level == null) {
            return;
        }
        List<Wp> mine = here(c);
        String n = name == null || name.isBlank() ? "Waypoint " + (mine.size() + 1) : name.trim();
        if (n.length() > 24) {
            n = n.substring(0, 24);
        }
        ALL.add(new Wp(n, worldKey(c), dimension(c), (int) Math.floor(c.player.getX()), (int) Math.floor(c.player.getY()), (int) Math.floor(c.player.getZ()),
                COLORS[mine.size() % COLORS.length]));
        save(c);
    }

    static void remove(Minecraft c, Wp w) {
        ALL.remove(w);
        save(c);
    }

    // ---------------------------------------------------------------- in-world markers

    private static void render(GuiGraphicsExtractor g, float tickProgress) {
        Minecraft c = Minecraft.getInstance();
        if (c.level == null || c.player == null || c.gui.screen() != null || !NewModSettings.isEnabled(c, ID)
                || !NewModSettings.optBool(c, ID, "markers") || !PROJ.begin(c, g, tickProgress)) {
            return;
        }
        double range = NewModSettings.opt(c, ID, "range");
        for (Wp w : here(c)) {
            double wx = w.x + 0.5;
            double wy = w.y + 1.0;
            double wz = w.z + 0.5;
            double d = Math.sqrt(PROJ.distanceSq(wx, wy, wz));
            if (d > range || d < 2.0) {
                continue;
            }
            int[] pt = PROJ.project(wx, wy, wz);
            if (pt == null) {
                continue;
            }
            int color = 0xFF000000 | w.rgb;
            diamond(g, pt[0], pt[1], 5, color);
            String label = w.name + "  " + Math.round(d) + " m";
            int tw = NewFont.widthUi(c.font, label, NewFont.W_MED, 9f);
            int tx = pt[0] - tw / 2;
            int ty = pt[1] - 18;
            g.fill(tx - 3, ty - 2, tx + tw + 3, ty + 11, 0x99000000);
            NewFont.drawUi(g, c.font, label, tx, ty, 0xFFFFFFFF, NewFont.W_MED, 9f);
        }
    }

    static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
    }
}
