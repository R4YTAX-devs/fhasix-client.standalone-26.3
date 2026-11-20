package com.fhasix.uitheme;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Account Switcher: a list of offline names you can switch between on the title screen. An offline name works in
 * singleplayer and on offline-mode servers (the same way an offline launcher account does). Servers that check your
 * real Minecraft account will not accept it: use the real account from your launcher there.
 */
public final class NewAccounts {

    private static final List<String> NAMES = new ArrayList<>();
    private static boolean loaded = false;

    private NewAccounts() {
    }

    private static Path file(Minecraft c) {
        return c.gameDirectory.toPath().resolve("fhasix_accounts.txt");
    }

    static List<String> names(Minecraft c) {
        if (!loaded) {
            loaded = true;
            try {
                if (Files.exists(file(c))) {
                    for (String l : Files.readAllLines(file(c), StandardCharsets.UTF_8)) {
                        if (valid(l.trim()) && !NAMES.contains(l.trim())) {
                            NAMES.add(l.trim());
                        }
                    }
                }
            } catch (IOException ignored) {
                // an unreadable list is just an empty list
            }
            String cur = c.getUser().getName();
            if (!NAMES.contains(cur) && valid(cur)) {
                NAMES.add(0, cur);
            }
        }
        return NAMES;
    }

    static boolean valid(String n) {
        return n.length() >= 3 && n.length() <= 16 && n.matches("[A-Za-z0-9_]+");
    }

    private static void save(Minecraft c) {
        try {
            Files.write(file(c), NAMES, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // not being able to save must never break anything
        }
    }

    static boolean add(Minecraft c, String name) {
        names(c);
        String n = name.trim();
        if (!valid(n) || NAMES.contains(n)) {
            return false;
        }
        NAMES.add(n);
        save(c);
        return true;
    }

    static void remove(Minecraft c, String name) {
        names(c);
        NAMES.remove(name);
        save(c);
    }

    /**
     * Switches the game to the offline account with this name. The session object is built and swapped in through
     * reflection (its constructor and field are looked up by shape), so a changed signature only means "could not switch".
     */
    static boolean use(Minecraft c, String name) {
        try {
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            Object user = null;
            for (Constructor<?> k : Class.forName("net.minecraft.client.User").getConstructors()) {
                Class<?>[] p = k.getParameterTypes();
                if (p.length >= 3 && p[0] == String.class && p[1] == UUID.class && p[2] == String.class) {
                    Object[] args = new Object[p.length];
                    args[0] = name;
                    args[1] = id;
                    args[2] = "0";
                    for (int i = 3; i < p.length; i++) {
                        args[i] = p[i] == Optional.class ? Optional.empty() : null;
                    }
                    user = k.newInstance(args);
                    break;
                }
            }
            if (user == null) {
                return false;
            }
            Field f = null;
            for (Field cand : Minecraft.class.getDeclaredFields()) {
                if (cand.getType() == user.getClass()) {
                    f = cand;
                    break;
                }
            }
            if (f == null) {
                return false;
            }
            f.setAccessible(true);
            f.set(c, user);
            return true;
        } catch (Exception | LinkageError t) {
            NewSafe.failed("accounts", t);
            return false;
        }
    }
}
