package com.fhasix.uitheme;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * Motion Blur (26.3, update 5.8).
 *
 * What changed in 26.3: the single "post effect slot" of the game renderer is gone. Post effects now belong to the
 * player (the same list the /posteffect command fills), the game applies them after the world and before the HUD, so
 * the HUD stays sharp. This mod puts its own effect (post_effect/motion_blur.json) into that list while Motion Blur is
 * on, and takes it out again when it is off. Nothing is drawn while the mod is off.
 *
 * The effect keeps the previous frame in a persistent buffer and blends it with the new one: movement leaves a short
 * smooth trail. Same design as the 1.21.11 version: the blend factor is worked out every frame from the real frame
 * time, so the trail lasts the same number of milliseconds at 30, 60 or 240 FPS and fades out by itself at very low
 * FPS (no ghosting at 13 FPS). One extra full-screen pass, nothing is touched in the world renderer, so it runs the
 * same on OpenGL and Vulkan (the shaders go through the game's own compiler).
 *
 * The per-frame blend factor is written into the effect's small uniform buffer right before the pass is drawn (see
 * mixin/NewPostPassMixin). 26.3 moved the GPU buffer classes into com.mojang.renderpearl, so every call into the
 * GPU / post-effect code is made by name at run time and checked: if something is missing the mod falls back to the five
 * fixed-strength effects (motion_blur_1..5) and fhasix-blur-diag.txt says exactly what was found. The game never
 * crashes because of Motion Blur.
 */
public final class NewMotionBlur {

    static final String ID = "motion_blur";
    /** Name of the uniform block in post_effect/motion_blur.json and in the shader. */
    public static final String UNIFORM = "FhasixBlur";

    private static final String NS = "fhasixuitheme";
    private static final Identifier DYNAMIC = Identifier.fromNamespaceAndPath(NS, "motion_blur");
    private static final Identifier[] PRESET = {
            Identifier.fromNamespaceAndPath(NS, "motion_blur_1"),
            Identifier.fromNamespaceAndPath(NS, "motion_blur_2"),
            Identifier.fromNamespaceAndPath(NS, "motion_blur_3"),
            Identifier.fromNamespaceAndPath(NS, "motion_blur_4"),
            Identifier.fromNamespaceAndPath(NS, "motion_blur_5")
    };

    // ---- state (all touched from the render thread only, except 'running') ----
    private static volatile boolean running = false;
    private static boolean active = false;
    private static boolean seed = true;
    private static long lastNs = 0L;
    private static double smoothDt = 0.0;
    private static int ticksActive = 0;
    private static boolean useStatic = false;
    private static int blendCalls = 0;
    private static int bufferUpdates = 0;
    private static float lastBlend = 0f;

    // ---- the player's post-effect list (LocalPlayer#getActivePostEffects / setActivePostEffects) ----
    private static boolean slotResolved = false;
    private static Method slotGet;
    private static Method slotSet;
    private static boolean slotAsSet;
    private static int resets = 0;
    private static int resetWindow = 0;

    // ---- the uniform buffer of our pass ----
    private static Class<?> passClass;
    private static List<Field> passMaps = new ArrayList<>();
    private static Object own;
    private static float ownBlend = -1f;
    /** 0 = replacing the buffer on every change works only, 1 = writing into our own buffer works, -1 = not tried yet. */
    private static int mapMode = -1;
    private static final ArrayDeque<Object> RETIRED = new ArrayDeque<>();
    private static Method deviceGetter;
    private static Method createBufferMethod;
    private static int usageFlags = -1;

    private NewMotionBlur() {
    }

    // =====================================================================================================
    // tick: switch the effect on / off in the player's post-effect list
    // =====================================================================================================

    static void tick(Minecraft c) {
        LocalPlayer player = c.player;
        boolean want = c.level != null && player != null && NewModSettings.isEnabled(c, ID);
        running = want;
        if (!want && !active) {
            return;
        }
        if (player == null) {
            active = false; // left the world: the old player and its effect list are gone
            ticksActive = 0;
            seed = true;
            return;
        }
        if (!slotResolved) {
            resolveSlot();
            Diag.flush(c);
        }
        if (slotSet == null) {
            running = false;
            Diag.flush(c);
            return; // this Minecraft build has a post-effect list we cannot use safely; the diagnostics file says why
        }
        try {
            if (want) {
                ticksActive++;
                if (!useStatic && ticksActive == 120 && bufferUpdates == 0) {
                    useStatic = true; // the per-frame blend could not be attached: use the fixed-strength versions
                    Diag.put("mode", "STATIC fixed-strength effects (the per-frame blend could not be attached)");
                }
                Identifier target = useStatic ? PRESET[stepIndex(c)] : DYNAMIC;
                if (apply(player, target)) {
                    seed = true;
                    if (++resetWindow > 0 && ++resets > 12) {
                        Diag.put("slot.status", "STOPPED: the game keeps resetting the post-effect list");
                        slotSet = null;
                        running = false;
                        return;
                    }
                }
                active = true;
            } else {
                apply(player, null);
                active = false;
                ticksActive = 0;
                seed = true;
            }
            if (ticksActive % 400 == 0) {
                resets = 0;
                resetWindow = 0;
            }
            if (ticksActive % 40 == 0 || !want) {
                Diag.counters(c);
            }
        } catch (Exception | LinkageError t) {
            Diag.error(t);
            NewSafe.failed("motion_blur_slot", t);
            running = false;
            active = false;
            Diag.flush(c);
        }
    }

    private static void resolveSlot() {
        slotResolved = true;
        Diag.put("mod", "Fhasix Client 5.8 for Minecraft 26.3");
        slotGet = findMethod(LocalPlayer.class, "getActivePostEffects", 0);
        slotSet = findMethod(LocalPlayer.class, "setActivePostEffects", 1);
        Diag.put("slot.get", slotGet == null ? "NOT FOUND" : slotGet.toGenericString());
        Diag.put("slot.set", slotSet == null ? "NOT FOUND" : slotSet.toGenericString());
        if (slotGet == null || slotSet == null) {
            slotSet = null;
            Diag.put("slot.status", "UNUSABLE: LocalPlayer has no getActivePostEffects / setActivePostEffects");
            return;
        }
        Class<?> param = slotSet.getParameterTypes()[0];
        if (param.isAssignableFrom(LinkedHashSet.class)) {
            slotAsSet = true;
        } else if (param.isAssignableFrom(ArrayList.class)) {
            slotAsSet = false;
        } else {
            slotSet = null;
            Diag.put("slot.status", "UNUSABLE: the setter takes " + param.getName());
            return;
        }
        Class<?> element = elementType(slotSet.getGenericParameterTypes()[0]);
        if (element == null) {
            element = elementType(slotGet.getGenericReturnType());
        }
        if (element != null && !element.isAssignableFrom(Identifier.class)) {
            // the list does not hold plain identifiers: stay off instead of risking a crash inside the game's own code
            slotSet = null;
            Diag.put("slot.status", "UNUSABLE: the list holds " + element.getName());
            return;
        }
        Diag.put("slot.status", "ok (" + (slotAsSet ? "set" : "list") + " of "
                + (element == null ? "unknown" : element.getSimpleName()) + ")");
    }

    /** Puts {@code target} into the player's effect list (and removes every other Fhasix blur step). Null = remove all. */
    private static boolean apply(LocalPlayer player, Identifier target) throws Exception {
        Object current;
        try {
            current = slotGet.invoke(player);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
        List<Object> next = new ArrayList<>();
        boolean changed = false;
        boolean has = false;
        if (current instanceof Collection<?> col) {
            for (Object o : col) {
                if (o instanceof Identifier id && ours(id)) {
                    if (id.equals(target) && !has) {
                        has = true;
                        next.add(o);
                    } else {
                        changed = true; // an old strength step, or a duplicate
                    }
                } else {
                    next.add(o);
                }
            }
        }
        if (target != null && !has) {
            next.add(target);
            changed = true;
        }
        if (!changed) {
            return false;
        }
        Object arg = slotAsSet ? new LinkedHashSet<Object>(next) : new ArrayList<Object>(next);
        try {
            slotSet.invoke(player, arg);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
        return true;
    }

    private static boolean ours(Identifier id) {
        if (DYNAMIC.equals(id)) {
            return true;
        }
        for (Identifier p : PRESET) {
            if (p.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The strength slider (10 to 90) picks one of the five prepared effects (only used by the fixed-strength fallback). */
    private static int stepIndex(Minecraft c) {
        int s = Math.max(10, Math.min(90, NewModSettings.opt(c, ID, "strength")));
        return Math.min(PRESET.length - 1, (s - 10) * PRESET.length / 81);
    }

    // =====================================================================================================
    // per frame: the blend factor
    // =====================================================================================================

    /** The blend factor for this frame (0 = no blur: show the new frame only). */
    public static float nextBlend() {
        blendCalls++;
        Minecraft c = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNs == 0L ? 0.0 : (now - lastNs) / 1.0E9;
        lastNs = now;
        if (seed || dt <= 0.0 || dt > 0.25) {
            seed = false; // first frame, or after a pause / lag spike: start the trail again
            smoothDt = 0.0;
            lastBlend = 0f;
            return 0f;
        }
        // Frame time, smoothed: a longer frame is taken at once (less blur right away, no ghost after a hitch), a
        // shorter one only slowly, so a jittery frame time cannot make the trail flicker.
        smoothDt = smoothDt <= 0.0 ? dt : (dt > smoothDt ? dt : smoothDt + (dt - smoothDt) * 0.15);
        double strength = Math.max(10, Math.min(90, NewModSettings.opt(c, ID, "strength"))) / 100.0;
        // strength describes the blend per frame at 60 FPS; other frame rates get the same trail length in time
        double blend = Math.pow(strength, Math.min(smoothDt, 0.1) * 60.0);
        if (NewModSettings.optBool(c, ID, "lowfps") && smoothDt > 1.0 / 40.0) {
            blend = Math.min(blend, 0.4); // under 40 FPS a long trail only looks like lag
        }
        lastBlend = Math.min(Math.round(blend * 64.0) / 64.0f, 0.95f);
        return lastBlend;
    }

    // =====================================================================================================
    // called by mixin/NewPostPassMixin right before a post pass is added to the frame
    // =====================================================================================================

    @SuppressWarnings("unchecked")
    public static void beforePass(Object pass) {
        if (!running || useStatic || NewSafe.disabled("motion_blur_inject")) {
            return;
        }
        try {
            Class<?> k = pass.getClass();
            if (k != passClass) {
                passClass = k;
                passMaps = mapFields(k);
                StringBuilder names = new StringBuilder();
                for (Field f : passMaps) {
                    names.append(f.getName()).append(' ');
                }
                Diag.put("inject.pass-class", k.getName());
                Diag.put("inject.map-fields", names.length() == 0 ? "NONE FOUND" : names.toString().trim());
            }
            for (Field f : passMaps) {
                Object o = f.get(pass);
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                Object current = m.get(UNIFORM);
                if (current == null) {
                    continue; // another pass (the copy passes have no blend uniform)
                }
                refresh((Map<Object, Object>) m, current);
                return;
            }
        } catch (Exception | LinkageError t) {
            Diag.error(t);
            NewSafe.failed("motion_blur_inject", t);
        }
    }

    private static void refresh(Map<Object, Object> map, Object current) throws Exception {
        float blend = nextBlend();
        if (current == own && blend == ownBlend) {
            return;
        }
        if (current == own && mapMode != 0) {
            if (writeMapped(own, blend)) {
                ownBlend = blend;
                mapMode = 1;
                bufferUpdates++;
                Diag.put("inject.write", "writing into our own buffer");
                return;
            }
            mapMode = 0; // mapped writes do not work on this build: replace the buffer when the value changes
            Diag.put("inject.write", "replacing the buffer on every change (mapped write not available)");
        }
        Object fresh = createBuffer(blend);
        if (fresh == null) {
            return;
        }
        map.put(UNIFORM, fresh);
        if (current == own) {
            RETIRED.add(current); // the GPU may still read it for a frame or two: close it a little later
            while (RETIRED.size() > 3) {
                closeQuietly(RETIRED.poll());
            }
        } else {
            closeQuietly(current); // the game's own first buffer
        }
        own = fresh;
        ownBlend = blend;
        bufferUpdates++;
        Diag.put("inject.buffer", fresh.getClass().getName());
    }

    // =====================================================================================================
    // GPU access by name (26.3 moved the classes into com.mojang.renderpearl)
    // =====================================================================================================

    private static Object device() throws Exception {
        if (deviceGetter == null) {
            deviceGetter = findMethod(Class.forName("com.mojang.blaze3d.systems.RenderSystem"), "getDevice", 0);
            if (deviceGetter == null) {
                throw new NoSuchMethodException("RenderSystem.getDevice");
            }
        }
        try {
            return deviceGetter.invoke(null);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        }
    }

    private static int usage() {
        if (usageFlags == -1) {
            int mapWrite = 0x02;
            int uniform = 0x80;
            for (String cn : new String[]{"com.mojang.renderpearl.api.buffers.GpuBuffer", "com.mojang.blaze3d.buffers.GpuBuffer"}) {
                try {
                    Class<?> k = Class.forName(cn);
                    mapWrite = k.getField("USAGE_MAP_WRITE").getInt(null);
                    uniform = k.getField("USAGE_UNIFORM").getInt(null);
                    break;
                } catch (Throwable ignored) {
                    // try the next class name; the numbers above are the long-standing values
                }
            }
            usageFlags = mapWrite | uniform;
        }
        return usageFlags;
    }

    private static Object createBuffer(float blend) {
        try {
            Object dev = device();
            if (createBufferMethod == null) {
                createBufferMethod = findCreateBuffer(dev.getClass());
                Diag.put("inject.create-buffer", createBufferMethod == null ? "NOT FOUND" : createBufferMethod.toGenericString());
            }
            if (createBufferMethod == null) {
                return null;
            }
            ByteBuffer data = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            data.putFloat(0, blend);
            Supplier<String> label = () -> "fhasix motion blur";
            return createBufferMethod.invoke(dev, label, usage(), data);
        } catch (InvocationTargetException e) {
            Diag.error(unwrap(e));
            NewSafe.failed("motion_blur_inject", unwrap(e));
            return null;
        } catch (Exception | LinkageError t) {
            Diag.error(t);
            NewSafe.failed("motion_blur_inject", t);
            return null;
        }
    }

    private static Method findCreateBuffer(Class<?> deviceClass) {
        for (Method m : allMethods(deviceClass)) {
            if (!m.getName().equals("createBuffer") || m.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p[0].isAssignableFrom(Supplier.class) && p[1] == int.class && p[2] == ByteBuffer.class) {
                trySetAccessible(m);
                return m;
            }
        }
        return null;
    }

    /** Writes the blend into a buffer made with the map-write flag. Returns false when this build cannot do that. */
    private static boolean writeMapped(Object buffer, float blend) {
        try {
            Object dev = device();
            Method enc = findMethod(dev.getClass(), "createCommandEncoder", 0);
            if (enc == null) {
                return false;
            }
            Object encoder = enc.invoke(dev);
            for (Method m : allMethods(encoder.getClass())) {
                if (!m.getName().equals("mapBuffer") || m.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p[1] != boolean.class || p[2] != boolean.class) {
                    continue;
                }
                Object arg = null;
                if (p[0].isInstance(buffer)) {
                    arg = buffer;
                } else {
                    Method slice = findMethod(buffer.getClass(), "slice", 0);
                    if (slice != null) {
                        Object s = slice.invoke(buffer);
                        if (p[0].isInstance(s)) {
                            arg = s;
                        }
                    }
                }
                if (arg == null) {
                    continue;
                }
                trySetAccessible(m);
                Object view = m.invoke(encoder, arg, false, true);
                try {
                    Method data = findMethod(view.getClass(), "data", 0);
                    ByteBuffer bb = (ByteBuffer) data.invoke(view);
                    bb.putFloat(0, blend);
                } finally {
                    closeQuietly(view);
                }
                return true;
            }
            return false;
        } catch (Exception | LinkageError t) {
            return false;
        }
    }

    private static void closeQuietly(Object o) {
        if (o == null) {
            return;
        }
        try {
            Method close = findMethod(o.getClass(), "close", 0);
            if (close != null) {
                close.invoke(o);
            }
        } catch (Exception | LinkageError ignored) {
            // a buffer that cannot be closed now is closed when its owner is
        }
    }

    // =====================================================================================================
    // small reflection helpers
    // =====================================================================================================

    private static List<Field> mapFields(Class<?> k) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (RuntimeException ignored) {
                        // not reachable: skip
                    }
                }
            }
        }
        return out;
    }

    private static List<Method> allMethods(Class<?> k) {
        List<Method> out = new ArrayList<>();
        for (Method m : k.getMethods()) {
            out.add(m);
        }
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                out.add(m);
            }
        }
        return out;
    }

    static Method findMethod(Class<?> k, String name, int params) {
        for (Method m : allMethods(k)) {
            if (m.getName().equals(name) && m.getParameterCount() == params && !m.isBridge()) {
                trySetAccessible(m);
                return m;
            }
        }
        return null;
    }

    private static void trySetAccessible(Method m) {
        try {
            m.setAccessible(true);
        } catch (RuntimeException ignored) {
            // public methods of public classes work without it
        }
    }

    private static Class<?> elementType(Type t) {
        if (t instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof WildcardType w && w.getUpperBounds().length > 0) {
                a = w.getUpperBounds()[0];
            }
            if (a instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    private static Exception unwrap(InvocationTargetException e) {
        return e.getCause() instanceof Exception ex ? ex : e;
    }

    // =====================================================================================================
    // fhasix-blur-diag.txt (game folder): what this Minecraft build offered. Written only when something changes.
    // =====================================================================================================

    private static final class Diag {
        private static final Map<String, String> VALUES = new LinkedHashMap<>();
        private static boolean dirty = false;
        private static int errors = 0;
        private static int counterBudget = 30;

        private Diag() {
        }

        static synchronized void put(String key, String value) {
            String old = VALUES.put(key, value);
            if (!value.equals(old)) {
                dirty = true;
            }
        }

        static synchronized void error(Throwable t) {
            if (errors++ < 3) {
                String msg = String.valueOf(t);
                put("error." + errors, msg.length() > 240 ? msg.substring(0, 240) : msg);
            }
        }

        static synchronized void counters(Minecraft c) {
            if (counterBudget > 0) {
                counterBudget--; // the counters are only refreshed for the first minute or so
                put("frames.blend-calls", String.valueOf(blendCalls));
                put("frames.buffer-updates", String.valueOf(bufferUpdates));
                put("blend.last", String.valueOf(lastBlend));
                put("mode", useStatic ? VALUES.getOrDefault("mode", "STATIC") : "dynamic (frame-time blend)");
            }
            flush(c);
        }

        static synchronized void flush(Minecraft c) {
            if (!dirty) {
                return;
            }
            dirty = false;
            StringBuilder sb = new StringBuilder("Fhasix motion blur diagnostics\n");
            sb.append("If the blur does not show, send this file.\n\n");
            for (Map.Entry<String, String> e : VALUES.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            try {
                Files.write(new File(c.gameDirectory, "fhasix-blur-diag.txt").toPath(),
                        sb.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // diagnostics only
            }
        }
    }
}
