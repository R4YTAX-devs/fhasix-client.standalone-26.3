package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ChunkPos;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NEW. Performance: the FPS modes (Particles, Culling, Lazy Chunk Loading, Render Distance,
 * Simulation Distance, Entity Detail, Visual Details) and the device profiles (High / Medium / Low end)
 * chosen on first launch. Every mode has an On/Off switch and a value: Small, Medium or High, where
 * High = the most FPS. State lives in the normal mod settings ("perf.<mode>.on" / "perf.<mode>.lvl").
 *
 * Modes that change a Minecraft video option remember the player's own value and put it back when
 * the mode is switched off. Particles / Culling / Lazy chunks are checked live by small mixins.
 */
public final class NewPerf {

    static final String MOD = "perf";

    static final int PARTICLES = 0;
    static final int CULLING = 1;
    static final int LAZY = 2;
    static final int RENDER = 3;
    static final int SIM = 4;
    static final int ENTITY = 5;
    static final int VISUAL = 6;
    static final int FRAME = 7;
    static final int EFFECTS = 8;
    static final int HUD = 9;
    static final int BACKGROUND = 10;
    static final int TEXTURE = 11;
    static final int MENU = 12;
    static final int FOG = 13;
    static final int COUNT = 14;

    static final String[] IDS = {"particles", "culling", "lazy", "render", "sim", "entity", "visual",
            "frame", "effects", "hud", "background", "texture", "menu", "fog"};
    static final String[] LABELS = {"Particles", "Culling", "Lazy Chunk Loading", "Render Distance",
            "Simulation Distance", "Entity Detail", "Visual Details", "Frame Rate", "Effects & Animations",
            "HUD Optimizer", "Background Limiter", "Texture Detail", "Menu FPS Saver", "Fog Optimizer"};
    static final String[] LEVELS = {"Small", "Medium", "High"};
    static final String[][] DESC = {
            {"Hides potion and spell effect particles.",
                    "Also hides water, lava, smoke and fire particles.",
                    "Hides every single particle in the game. Most FPS."},
            {"Fast leaves: leaf faces you cannot see are skipped.",
                    "Also skips entities far away and far dropped items.",
                    "Skips every far entity, armor stand, item frame and item. Most FPS."},
            {"New chunks show up about 3 seconds later than normal.",
                    "New chunks show up about 6 seconds later than normal.",
                    "Far chunks wait until you move into the next chunk. Most FPS."},
            {"Caps the render distance at 10 chunks.",
                    "Caps the render distance at 6 chunks.",
                    "Caps the render distance at 2 chunks. Most FPS."},
            {"Caps the simulation distance at 10 chunks.",
                    "Caps the simulation distance at 6 chunks.",
                    "Caps the simulation distance at 5 chunks. Most FPS."},
            {"Entities are only drawn a bit closer.",
                    "Entities only nearby, no entity shadows.",
                    "Very short entity distance, no shadows. Most FPS."},
            {"Fast clouds and softer biome blending.",
                    "Fast clouds, no smooth lighting, little biome blending.",
                    "No clouds, no smooth lighting, no biome blending. Most FPS."},
            {"Turns VSync off.",
                    "VSync off and no frame limit.",
                    "VSync off and no frame limit. Most FPS."},
            {"No view bobbing.",
                    "No bobbing, FOV effects, distortion effects or menu blur.",
                    "Everything from Medium, plus no enchant glint animation. Most FPS."},
            {"No UI fade animations in the Fhasix menus. Overlays keep working.",
                    "Also draws a few fewer things in the overlays (health, TNT timer). They never switch off.",
                    "Overlays use a slightly shorter range. Hitboxes, health and every HUD mod you turned on keep working."},
            {"Lowers the priority of loading and download threads a little.",
                    "Lowers background loading threads a lot so the game thread comes first.",
                    "Background loading gets the lowest priority there is. Most FPS."},
            {"Texture mipmap level 2.",
                    "Texture mipmap level 1.",
                    "No mipmaps: the cheapest textures. Textures reload once when you change this. Most FPS."},
            {"Caps the frame rate at 60 while a menu is open, so menus use less power.",
                    "Caps the frame rate at 30 while a menu is open.",
                    "Caps the frame rate at 15 while a menu is open. Gameplay FPS is not touched."},
            {"Brings the fog in to 60% of its distance, with the normal fog colour, so far chunks are hidden early. More FPS.",
                    "Brings the fog in to 60% of its distance, with the normal fog colour, so far chunks are hidden early. More FPS.",
                    "Brings the fog in to 60% of its distance, with the normal fog colour, so far chunks are hidden early. More FPS."}
    };
    /** Which tab of the Performance screen a mode is on: 1 = World, 2 = Video, 3 = System. */
    static final int[] TAB = {1, 1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 2, 3, 2};
    static final int[] ICON = {37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 49};
    static final boolean[] NEWBADGE = {false, false, false, false, false, false, false, true, true, true, true, true, true, false};

    /** Modes that are a single On/Off switch (no Low / Medium / High): the Fog Optimizer. */
    static final boolean[] TOGGLE_ONLY = new boolean[COUNT];

    static {
        TOGGLE_ONLY[FOG] = true;
    }

    /** One card per mode in the Performance view of the Mod Menu (ids are "perf_" + mode id). */
    static final java.util.List<NewModSettings.ModDef> DEFS = new java.util.ArrayList<>();

    static {
        for (int i = 0; i < COUNT; i++) {
            int tag = TAB[i] == 1 ? NewModSettings.TAG_HUD : TAB[i] == 2 ? NewModSettings.TAG_HYPIXEL : NewModSettings.TAG_PVP;
            DEFS.add(new NewModSettings.ModDef("perf_" + IDS[i], LABELS[i], tag, NEWBADGE[i], true, ICON[i]));
        }
    }

    static int indexOf(String perfId) {
        for (int i = 0; i < COUNT; i++) {
            if (("perf_" + IDS[i]).equals(perfId)) {
                return i;
            }
        }
        return -1;
    }

    static final int DEVICE_NONE = 0;
    static final int DEVICE_HIGH = 1;
    static final int DEVICE_MEDIUM = 2;
    static final int DEVICE_LOW = 3;
    static final String[] DEVICE_NAMES = {"Not chosen", "High End", "Medium", "Low End"};

    private NewPerf() {
    }

    // ------------------------------------------------------------------ state

    /**
     * Cached state per mode: -1 = off, 0..2 = on at that value. Refreshed every tick and on every change, so the
     * per-particle / per-entity checks are plain array reads (no settings lookup, no string building).
     */
    private static final int[] CACHE = new int[COUNT];

    static {
        java.util.Arrays.fill(CACHE, -1);
    }

    /** True while the Fog Optimizer switch is on (read by NewFog every frame: a plain array read). */
    static boolean fogOptimizer() {
        return CACHE[FOG] >= 0;
    }

    private static void refreshCache(Minecraft c) {
        for (int m = 0; m < COUNT; m++) {
            CACHE[m] = on(c, m) ? level(c, m) : -1;
        }
    }

    static boolean on(Minecraft c, int mode) {
        return NewModSettings.optBool(c, MOD, IDS[mode] + ".on");
    }

    static int level(Minecraft c, int mode) {
        return Math.max(0, Math.min(2, NewModSettings.opt(c, MOD, IDS[mode] + ".lvl")));
    }

    static void setOn(Minecraft c, int mode, boolean v) {
        NewModSettings.setOpt(c, MOD, IDS[mode] + ".on", v ? 1 : 0);
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    static void setLevel(Minecraft c, int mode, int lvl) {
        NewModSettings.setOpt(c, MOD, IDS[mode] + ".lvl", Math.max(0, Math.min(2, lvl)));
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    static boolean setupDone(Minecraft c) {
        return NewModSettings.optBool(c, MOD, "setup");
    }

    static int device(Minecraft c) {
        return Math.max(0, Math.min(3, NewModSettings.opt(c, MOD, "device")));
    }

    // ------------------------------------------------------------------ init

    private static boolean selectorShown = false;

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("perf", NewPerf::tick));
        // first launch: ask what device this is, over the title screen
        ScreenEvents.AFTER_INIT.register((minecraft, screen, w, h) -> {
            NewSafe.run("perf_selector", () -> {
                if (screen instanceof TitleScreen && !selectorShown && !setupDone(minecraft)) {
                    selectorShown = true;
                    minecraft.execute(() -> minecraft.gui.setScreen(new NewDeviceSelectorScreen(null)));
                }
            });
        });
    }

    // ------------------------------------------------------------------ live video-option overrides

    private static final int[] applied = new int[COUNT];

    static {
        java.util.Arrays.fill(applied, -1);
    }

    private static Integer origView;
    private static Integer origSim;
    private static Double origEntityDist;
    private static Boolean origShadows;
    private static Boolean origAo;
    private static Integer origBlend;
    private static CloudStatus origClouds;
    private static GraphicsPreset origGraphics;
    private static Boolean origVsync;
    private static Integer origMaxFps;
    private static Boolean origBob;
    private static Double origFovFx;
    private static Double origDistort;
    private static Integer origMenuBlur;
    private static Double origGlintStr;
    private static Double origGlintSpeed;
    private static Integer origMip;
    private static Integer menuPrev;
    private static int tickCount = 0;
    private static boolean menuMigrated = false;

    static void tick(Minecraft c) {
        if (c == null || c.options == null) {
            return;
        }
        if (!menuMigrated) {
            menuMigrated = true;
            NewSafe.run("perf_menu_migrate", () -> {
                // one-time: switch Menu FPS Saver off for players who already picked a device before this update
                if (NewModSettings.opt(c, MOD, "menu_off_v1") == 0) {
                    NewModSettings.setOpt(c, MOD, IDS[MENU] + ".on", 0);
                    NewModSettings.setOpt(c, MOD, "menu_off_v1", 1);
                    NewModSettings.save(c);
                }
            });
        }
        refreshCache(c);
        // never touch video options while the game is still loading (changing them starts a reload)
        if (c.gui.overlay() != null) {
            return;
        }
        for (int mode : OVERRIDES) {
            int sig = on(c, mode) ? 1 + level(c, mode) : 0;
            if (sig != applied[mode]) {
                applied[mode] = sig;
                final int fm = mode;
                NewSafe.run("perf_" + IDS[mode], () -> apply(c, fm, sig));
            }
        }
        NewSafe.run("perf_lazy_release", () -> releaseHeld(c));
        NewSafe.run("perf_menu", () -> menuSaver(c));
        if (++tickCount % 100 == 0 || tickCount == 1) {
            NewSafe.run("perf_background", () -> limitBackground(c));
        }
    }

    private static final int[] OVERRIDES = {CULLING, RENDER, SIM, ENTITY, VISUAL, FRAME, EFFECTS, TEXTURE};

    private static void apply(Minecraft c, int mode, int sig) {
        Options o = c.options;
        int lvl = sig - 1;
        switch (mode) {
            case CULLING:
                if (sig > 0) {
                    if (origGraphics == null) {
                        origGraphics = o.graphicsPreset().get();
                    }
                    o.graphicsPreset().set(GraphicsPreset.FAST); // fast leaves
                } else if (origGraphics != null) {
                    o.graphicsPreset().set(origGraphics);
                    origGraphics = null;
                }
                break;
            case RENDER:
                if (sig > 0) {
                    if (origView == null) {
                        origView = o.renderDistance().get();
                    }
                    o.renderDistance().set(Math.min(origView, new int[]{10, 6, 2}[lvl]));
                } else if (origView != null) {
                    o.renderDistance().set(origView);
                    origView = null;
                }
                break;
            case SIM:
                if (sig > 0) {
                    if (origSim == null) {
                        origSim = o.simulationDistance().get();
                    }
                    o.simulationDistance().set(Math.min(origSim, new int[]{10, 6, 5}[lvl]));
                } else if (origSim != null) {
                    o.simulationDistance().set(origSim);
                    origSim = null;
                }
                break;
            case ENTITY:
                if (sig > 0) {
                    if (origEntityDist == null) {
                        origEntityDist = o.entityDistanceScaling().get();
                        origShadows = o.entityShadows().get();
                    }
                    o.entityDistanceScaling().set(Math.min(origEntityDist, new double[]{0.75, 0.5, 0.5}[lvl]));
                    o.entityShadows().set(lvl == 0 ? origShadows : Boolean.FALSE);
                } else if (origEntityDist != null) {
                    o.entityDistanceScaling().set(origEntityDist);
                    o.entityShadows().set(origShadows);
                    origEntityDist = null;
                    origShadows = null;
                }
                break;
            case VISUAL:
                if (sig > 0) {
                    if (origClouds == null) {
                        origClouds = o.cloudStatus().get();
                        origAo = o.ambientOcclusion().get();
                        origBlend = o.biomeBlendRadius().get();
                    }
                    CloudStatus clouds = lvl == 2 ? CloudStatus.OFF
                            : (origClouds == CloudStatus.OFF ? CloudStatus.OFF : CloudStatus.FAST);
                    o.cloudStatus().set(clouds);
                    o.ambientOcclusion().set(lvl == 0 ? origAo : Boolean.FALSE);
                    o.biomeBlendRadius().set(Math.min(origBlend, new int[]{3, 1, 0}[lvl]));
                } else if (origClouds != null) {
                    o.cloudStatus().set(origClouds);
                    o.ambientOcclusion().set(origAo);
                    o.biomeBlendRadius().set(origBlend);
                    origClouds = null;
                    origAo = null;
                    origBlend = null;
                }
                break;
            case FRAME:
                if (sig > 0) {
                    if (origVsync == null) {
                        origVsync = o.enableVsync().get();
                        origMaxFps = o.framerateLimit().get();
                    }
                    o.enableVsync().set(Boolean.FALSE);
                    if (lvl >= 1) {
                        o.framerateLimit().set(260); // 260 = unlimited
                        if (menuPrev != null) {
                            menuPrev = 260;
                        }
                    }
                } else if (origVsync != null) {
                    o.enableVsync().set(origVsync);
                    o.framerateLimit().set(origMaxFps);
                    if (menuPrev != null) {
                        menuPrev = origMaxFps;
                    }
                    origVsync = null;
                    origMaxFps = null;
                }
                break;
            case EFFECTS:
                if (sig > 0) {
                    if (origBob == null) {
                        origBob = o.bobView().get();
                        origFovFx = o.fovEffectScale().get();
                        origDistort = o.screenEffectScale().get();
                        origMenuBlur = o.menuBackgroundBlurriness().get();
                        origGlintStr = o.glintStrength().get();
                        origGlintSpeed = o.glintSpeed().get();
                    }
                    o.bobView().set(Boolean.FALSE);
                    o.fovEffectScale().set(lvl >= 1 ? 0.0 : origFovFx);
                    o.screenEffectScale().set(lvl >= 1 ? 0.0 : origDistort);
                    o.menuBackgroundBlurriness().set(lvl >= 1 ? 0 : origMenuBlur);
                    o.glintStrength().set(lvl >= 2 ? 0.0 : origGlintStr);
                    o.glintSpeed().set(lvl >= 2 ? 0.0 : origGlintSpeed);
                } else if (origBob != null) {
                    o.bobView().set(origBob);
                    o.fovEffectScale().set(origFovFx);
                    o.screenEffectScale().set(origDistort);
                    o.menuBackgroundBlurriness().set(origMenuBlur);
                    o.glintStrength().set(origGlintStr);
                    o.glintSpeed().set(origGlintSpeed);
                    origBob = null;
                    origFovFx = null;
                    origDistort = null;
                    origMenuBlur = null;
                    origGlintStr = null;
                    origGlintSpeed = null;
                }
                break;
            case TEXTURE:
                if (sig > 0) {
                    if (origMip == null) {
                        origMip = o.mipmapLevels().get();
                    }
                    o.mipmapLevels().set(new int[]{2, 1, 0}[lvl]);
                } else if (origMip != null) {
                    o.mipmapLevels().set(origMip);
                    origMip = null;
                }
                break;
            default:
                break;
        }
    }

    /** Menu FPS Saver: caps the frame rate while any screen is open, then puts the previous cap back. */
    private static void menuSaver(Minecraft c) {
        Options o = c.options;
        boolean want = on(c, MENU) && c.gui.screen() != null;
        if (want) {
            int cap = new int[]{60, 30, 15}[level(c, MENU)];
            if (menuPrev == null) {
                menuPrev = o.framerateLimit().get();
            }
            if (o.framerateLimit().get() != cap) {
                o.framerateLimit().set(cap);
            }
        } else if (menuPrev != null) {
            o.framerateLimit().set(menuPrev);
            menuPrev = null;
        }
    }

    /** Gives the player's own video options back and forgets what was applied. */
    private static void resetOverrides(Minecraft c) {
        for (int mode : OVERRIDES) {
            apply(c, mode, 0);
            applied[mode] = -1;
        }
    }

    // ------------------------------------------------------------------ device profiles

    /** One video option failing (a different Minecraft build, another mod) must not stop the rest. */
    private static void safe(Runnable r) {
        NewSafe.run("perf_option", r);
    }

    /**
     * High end: Minecraft's default video settings, FPS modes Small. Medium: render distance 6 and
     * lighter settings, FPS modes Medium. Low end: the lowest of everything (best for Pojav / Mojo
     * launcher and phones), every FPS mode on High.
     */
    static void applyDevice(Minecraft c, int device) {
        safe(() -> resetOverrides(c));
        Options o = c.options;
        switch (device) {
            case DEVICE_HIGH:
                safe(() -> o.renderDistance().set(12));
                safe(() -> o.simulationDistance().set(12));
                safe(() -> o.entityDistanceScaling().set(1.0));
                safe(() -> o.entityShadows().set(Boolean.TRUE));
                safe(() -> o.ambientOcclusion().set(Boolean.TRUE));
                safe(() -> o.biomeBlendRadius().set(2));
                safe(() -> o.cloudStatus().set(CloudStatus.FANCY));
                safe(() -> o.particles().set(ParticleStatus.ALL));
                safe(() -> o.graphicsPreset().set(GraphicsPreset.FANCY));
                break;
            case DEVICE_MEDIUM:
                safe(() -> o.renderDistance().set(6));
                safe(() -> o.simulationDistance().set(6));
                safe(() -> o.entityDistanceScaling().set(0.75));
                safe(() -> o.entityShadows().set(Boolean.FALSE));
                safe(() -> o.biomeBlendRadius().set(1));
                safe(() -> o.cloudStatus().set(CloudStatus.FAST));
                safe(() -> o.particles().set(ParticleStatus.DECREASED));
                break;
            case DEVICE_LOW:
                safe(() -> o.renderDistance().set(2));
                safe(() -> o.simulationDistance().set(5));
                safe(() -> o.entityDistanceScaling().set(0.5));
                safe(() -> o.entityShadows().set(Boolean.FALSE));
                safe(() -> o.ambientOcclusion().set(Boolean.FALSE));
                safe(() -> o.biomeBlendRadius().set(0));
                safe(() -> o.cloudStatus().set(CloudStatus.OFF));
                safe(() -> o.particles().set(ParticleStatus.MINIMAL));
                safe(() -> o.graphicsPreset().set(GraphicsPreset.FAST));
                break;
            default:
                break;
        }
        safe(o::save);
        for (int m = 0; m < COUNT; m++) {
            boolean enable;
            int lvl;
            if (device == DEVICE_HIGH) {
                enable = m <= LAZY; // particles, culling, lazy chunks - small
                lvl = 0;
            } else if (device == DEVICE_MEDIUM) {
                enable = m != MENU; // Menu FPS Saver stays OFF by default - it made the menus laggy
                lvl = 1;
            } else {
                enable = m != MENU;
                lvl = 2;
            }
            NewModSettings.setOpt(c, MOD, IDS[m] + ".on", enable ? 1 : 0);
            NewModSettings.setOpt(c, MOD, IDS[m] + ".lvl", lvl);
        }
        NewModSettings.setOpt(c, MOD, "device", device);
        NewModSettings.setOpt(c, MOD, "setup", 1);
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    // ------------------------------------------------------------------ HUD optimizer / background limiter

    /** HUD Optimizer level (0..2), or -1 when the mode is off. */
    static int hudLevel(Minecraft c) {
        return CACHE[HUD];
    }

    /**
     * How many things a Fhasix overlay may draw. The HUD Optimizer can make this a bit smaller but it NEVER
     * reaches 0: a mod the player switched on (health indicator, TNT timer, hitbox...) must keep working.
     */
    public static int overlayCap(int normal) {
        int l = hudLevel(Minecraft.getInstance());
        return l < 1 ? normal : l == 1 ? Math.max(4, normal * 3 / 4) : Math.max(4, normal / 2);
    }

    /** Overlay range in blocks: only slightly shorter at the highest HUD Optimizer level. */
    public static double overlayRange(double normal) {
        int l = hudLevel(Minecraft.getInstance());
        return l < 2 ? normal : normal * 0.85;
    }

    /** Never true any more: the HUD Optimizer no longer switches overlays off. */
    public static boolean overlaysOff() {
        return false;
    }

    /** True while the HUD Optimizer is on: Fhasix UI fade animations jump straight to their end value. */
    public static boolean animationsOff() {
        return hudLevel(Minecraft.getInstance()) >= 0;
    }

    private static boolean bgApplied = false;

    /** Background Limiter: lowers the priority of the game's background worker threads. */
    private static void limitBackground(Minecraft c) {
        boolean wanted = on(c, BACKGROUND);
        if (!wanted && !bgApplied) {
            return;
        }
        int prio = !wanted ? Thread.NORM_PRIORITY : new int[]{4, 2, 1}[level(c, BACKGROUND)];
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Thread[] threads = new Thread[root.activeCount() + 32];
            int n = root.enumerate(threads, true);
            for (int i = 0; i < n; i++) {
                Thread t = threads[i];
                String name = t.getName();
                if (name != null && (name.startsWith("Worker-Main") || name.startsWith("Download-")
                        || name.startsWith("IO-Worker") || name.startsWith("Server-Worker"))
                        && t.getPriority() != prio) {
                    t.setPriority(prio);
                }
            }
        } catch (RuntimeException ignored) {
            // not allowed on this platform - the mode just does nothing
        }
        bgApplied = wanted;
    }

    // ------------------------------------------------------------------ particles

    private static Set<ParticleType<?>> small;
    private static Set<ParticleType<?>> medium;

    private static void buildSets() {
        small = Set.<ParticleType<?>>of(ParticleTypes.EFFECT, ParticleTypes.ENTITY_EFFECT,
                ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH);
        medium = Set.<ParticleType<?>>of(ParticleTypes.EFFECT, ParticleTypes.ENTITY_EFFECT,
                ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH, ParticleTypes.SPLASH, ParticleTypes.BUBBLE,
                ParticleTypes.BUBBLE_POP, ParticleTypes.FALLING_WATER, ParticleTypes.DRIPPING_WATER,
                ParticleTypes.DRIPPING_LAVA, ParticleTypes.FALLING_LAVA, ParticleTypes.LANDING_LAVA,
                ParticleTypes.LAVA, ParticleTypes.RAIN, ParticleTypes.UNDERWATER, ParticleTypes.SMOKE,
                ParticleTypes.LARGE_SMOKE, ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.FLAME);
    }

    /** True at Particles High: no particle at all, including block break / block crack particles. */
    public static boolean blockAllParticles() {
        return CACHE[PARTICLES] >= 2;
    }

    /** True = do not spawn this particle. */
    public static boolean blockParticle(ParticleOptions effect) {
        int lvl = CACHE[PARTICLES];
        if (lvl < 0) {
            return false;
        }
        if (lvl >= 2) {
            return true;
        }
        try {
            if (small == null) {
                buildSets();
            }
            return (lvl == 0 ? small : medium).contains(effect.getType());
        } catch (Exception | LinkageError t) {
            NewSafe.failed("perf_particles", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ culling

    /** True = skip drawing this entity. Never the player, never the player's ride. */
    public static boolean cullEntity(Entity e) {
        int lvl = CACHE[CULLING];
        if (lvl < 1) {
            return false; // off, or Small (Small only changes the leaves)
        }
        try {
            return cullEntityImpl(e, lvl);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("perf_culling", t);
            return false;
        }
    }

    private static boolean cullEntityImpl(Entity e, int lvl) {
        Minecraft c = Minecraft.getInstance();
        if (c == null || c.player == null || e == c.player || e == c.player.getVehicle()) {
            return false;
        }
        double d = e.distanceToSqr(c.player);
        boolean item = e instanceof ItemEntity || e instanceof ExperienceOrb;
        if (lvl == 1) {
            return item ? d > 24 * 24 : (!(e instanceof Player) && d > 48 * 48);
        }
        if (e instanceof ArmorStand) {
            return d > 12 * 12;
        }
        if (e instanceof HangingEntity) {
            return d > 16 * 16;
        }
        if (item) {
            return d > 12 * 12;
        }
        return e instanceof Player ? d > 48 * 48 : d > 24 * 24;
    }

    // ------------------------------------------------------------------ lazy chunk loading

    /** Chunk X/Z of a chunk packet. The accessor names changed in 26.x, so try both; MIN_VALUE = unknown. */
    private static int chunkCoord(Object packet, String method, String field) {
        try {
            return ((Number) packet.getClass().getMethod(method).invoke(packet)).intValue();
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = packet.getClass().getMethod(field);
            return ((Number) m.invoke(packet)).intValue();
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = packet.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return ((Number) f.get(packet)).intValue();
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static final class Held {
        final ClientPacketListener handler;
        final ClientboundLevelChunkWithLightPacket packet;
        final int cx;
        final int cz;
        final long due;
        final int anchorX;
        final int anchorZ;
        final net.minecraft.client.multiplayer.ClientLevel level;

        Held(ClientPacketListener handler, ClientboundLevelChunkWithLightPacket packet, long due, int anchorX, int anchorZ,
             net.minecraft.client.multiplayer.ClientLevel world) {
            this.level = world;
            this.handler = handler;
            this.packet = packet;
            this.cx = chunkCoord(packet, "getX", "x");
            this.cz = chunkCoord(packet, "getZ", "z");
            this.due = due;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }
    }

    private static final ConcurrentLinkedQueue<Held> HELD = new ConcurrentLinkedQueue<>();
    private static boolean bypass = false;

    /** True = this chunk packet was taken and will be applied later. Chunks near the player always load at once. */
    public static boolean holdChunk(ClientPacketListener handler, ClientboundLevelChunkWithLightPacket packet) {
        return NewSafe.call("perf_lazy", () -> holdChunkImpl(handler, packet), Boolean.FALSE);
    }

    private static boolean holdChunkImpl(ClientPacketListener handler, ClientboundLevelChunkWithLightPacket packet) {
        Minecraft c = Minecraft.getInstance();
        // never hold chunks while joining: the player has to be in the world for a few seconds first,
        // otherwise the spawn chunks (far from the temporary 0,0 position) would never load
        if (bypass || c == null || !c.isSameThread() || c.player == null || c.level == null || !on(c, LAZY)
                || c.player.tickCount < 100) {
            return false;
        }
        if (chunkCoord(packet, "getX", "x") == Integer.MIN_VALUE || chunkCoord(packet, "getZ", "z") == Integer.MIN_VALUE) {
            return false;
        }
        ChunkPos pc = c.player.chunkPosition();
        int dist = Math.max(Math.abs(chunkCoord(packet, "getX", "x") - pc.x()), Math.abs(chunkCoord(packet, "getZ", "z") - pc.z()));
        if (dist <= 2) {
            return false;
        }
        int lvl = level(c, LAZY);
        long due = lvl == 0 ? System.currentTimeMillis() + 3500L
                : lvl == 1 ? System.currentTimeMillis() + 6000L : Long.MAX_VALUE;
        HELD.add(new Held(handler, packet, due, pc.x(), pc.z(), c.level));
        return true;
    }

    /** The server unloaded a chunk we are still holding: forget it instead of loading it later. */
    public static void dropHeld(int cx, int cz) {
        NewSafe.run("perf_lazy", () -> HELD.removeIf(h -> h.cx == cx && h.cz == cz));
    }

    private static void releaseHeld(Minecraft c) {
        if (HELD.isEmpty()) {
            return;
        }
        if (c.level == null || c.player == null || c.getConnection() == null) {
            HELD.clear();
            return;
        }
        boolean lazy = on(c, LAZY);
        int lvl = level(c, LAZY);
        ChunkPos pc = c.player.chunkPosition();
        long now = System.currentTimeMillis();
        Iterator<Held> it = HELD.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            int dist = Math.max(Math.abs(h.cx - pc.x()), Math.abs(h.cz - pc.z()));
            boolean crossed = lvl == 2 && (pc.x() != h.anchorX || pc.z() != h.anchorZ);
            if (!lazy || now >= h.due || dist <= 2 || crossed) {
                it.remove();
                if (h.handler == c.getConnection() && h.level == c.level) {
                    bypass = true;
                    try {
                        h.handler.handleLevelChunkWithLight(h.packet);
                    } finally {
                        bypass = false;
                    }
                }
            }
        }
    }
}
