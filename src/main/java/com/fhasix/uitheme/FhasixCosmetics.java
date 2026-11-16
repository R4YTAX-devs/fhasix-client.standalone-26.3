package com.fhasix.uitheme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client-side ownership/equip state for FHASIX cosmetics. Capes are identified by an id; the cape texture of an
 * id lives at textures/gui/cosmetics/&lt;id&gt;_cape.png. Only ONE cape is equipped at a time.
 */
public final class FhasixCosmetics {
    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("fhasix_cosmetics.json");

    public static final String FIRST_OPTIX = "first_optix";
    public static final String RED_CHARMS = "red_charms";
    public static final String CRIMSON_FADE = "crimson_fade";
    public static final String CYBER_CIRCUIT = "cyber_circuit";
    public static final String VOID_RIFT = "void_rift";
    public static final String AURORA = "aurora";
    public static final String SOLAR_FLARE = "solar_flare";

    private static boolean loaded;
    private static final Set<String> owned = new LinkedHashSet<>();
    private static String equipped = "";

    private FhasixCosmetics() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(STATE_FILE)) {
                String json = Files.readString(STATE_FILE, StandardCharsets.UTF_8);
                JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                if (object.has("owned") && object.get("owned").isJsonArray()) {
                    for (JsonElement e : object.getAsJsonArray("owned")) {
                        owned.add(e.getAsString());
                    }
                    equipped = object.has("equipped") ? object.get("equipped").getAsString() : "";
                } else {
                    // older save file: two fixed capes with their own flags
                    boolean first = object.has("first_optix_owned") && object.get("first_optix_owned").getAsBoolean();
                    boolean second = object.has("second_cape_owned") && object.get("second_cape_owned").getAsBoolean();
                    if (first) owned.add(FIRST_OPTIX);
                    if (second) owned.add(RED_CHARMS);
                    boolean firstOn = first && object.has("first_optix_equipped") && object.get("first_optix_equipped").getAsBoolean();
                    boolean secondOn = second && object.has("second_cape_equipped") && object.get("second_cape_equipped").getAsBoolean();
                    equipped = secondOn ? RED_CHARMS : (firstOn ? FIRST_OPTIX : "");
                }
                if (!equipped.isEmpty() && !owned.contains(equipped)) equipped = "";
            }
        } catch (Exception ignored) {
            owned.clear();
            equipped = "";
        }
    }

    // ------------------------------------------------------------------ generic cape API
    public static boolean owns(String id) {
        ensureLoaded();
        return owned.contains(id);
    }

    public static void unlock(String id) {
        ensureLoaded();
        if (owned.add(id)) save();
    }

    /** Takes a cosmetic away again (used when it is sold); a worn cape is taken off too. */
    public static void revoke(String id) {
        ensureLoaded();
        if (owned.remove(id)) {
            if (equipped.equals(id)) equipped = "";
            save();
        }
    }

    public static boolean isEquipped(String id) {
        ensureLoaded();
        return !equipped.isEmpty() && equipped.equals(id);
    }

    /** The id of the equipped cape, or "" when none is equipped. */
    public static String equippedCape() {
        ensureLoaded();
        return equipped;
    }

    public static boolean hasEquippedCape() {
        ensureLoaded();
        return !equipped.isEmpty();
    }

    /** Equips the cape (replacing any other one) or, when it is already worn, takes it off. */
    public static void toggleEquip(String id) {
        ensureLoaded();
        if (!owned.contains(id)) return;
        equipped = equipped.equals(id) ? "" : id;
        save();
    }

    // ------------------------------------------------------------------ the two original capes (unchanged API)
    public static boolean ownsFirstOptix() {
        return owns(FIRST_OPTIX);
    }

    public static boolean isFirstOptixEquipped() {
        return isEquipped(FIRST_OPTIX);
    }

    public static void unlockFirstOptix() {
        unlock(FIRST_OPTIX);
    }

    public static boolean ownsSecondCape() {
        return owns(RED_CHARMS);
    }

    public static boolean isSecondCapeEquipped() {
        return isEquipped(RED_CHARMS);
    }

    public static void unlockSecondCape() {
        unlock(RED_CHARMS);
    }

    public static void toggleSecondCape() {
        toggleEquip(RED_CHARMS);
    }

    public static void toggleFirstOptix() {
        toggleEquip(FIRST_OPTIX);
    }

    private static void save() {
        JsonObject object = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String id : owned) arr.add(id);
        object.add("owned", arr);
        object.addProperty("equipped", equipped);
        try {
            Files.createDirectories(STATE_FILE.getParent());
            Files.writeString(STATE_FILE, object.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Cosmetic state is client-side convenience data; rendering continues if persistence fails.
        }
    }
}
