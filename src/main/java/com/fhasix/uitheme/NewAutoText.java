package com.fhasix.uitheme;

import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

/**
 * Auto Component: sends a short chat message for you when a game ends ("gg") and, optionally, when one starts ("gl hf").
 * It watches the server's own announcements (Hypixel style), waits a moment, and never repeats within 20 seconds.
 */
public final class NewAutoText {

    static final String ID = "auto_text";
    private static final String[] END = {
            "winner!", "victory!", "you won!", "game over", "1st killer", "1st place", "winners -", "has won the game",
            "wins the game", "congratulations, you", "you have been eliminated"
    };
    private static final String[] START = {"the game starts in 1 second", "the game has started"};
    private static final String[] GG = {"gg", "GG", "gg wp", "good game"};
    private static final String[] GL = {"gl hf", "good luck", "glhf"};
    private static final long COOLDOWN_MS = 20_000L;

    private static String pending = null;
    private static long dueAt = 0L;
    private static long lastEnd = 0L;
    private static long lastStart = 0L;

    private NewAutoText() {
    }

    static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                NewSafe.run("auto_text", () -> onMessage(message.getString(), overlay)));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("auto_text", NewAutoText::tick));
    }

    private static boolean has(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static void onMessage(String raw, boolean overlay) {
        Minecraft c = Minecraft.getInstance();
        if (overlay || c.player == null || !NewModSettings.isEnabled(c, ID)) {
            return;
        }
        String text = raw.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (NewModSettings.optBool(c, ID, "gg") && now - lastEnd > COOLDOWN_MS && has(text, END)) {
            lastEnd = now;
            queue(c, GG[Math.max(0, Math.min(GG.length - 1, NewModSettings.opt(c, ID, "ggtext")))]);
        } else if (NewModSettings.optBool(c, ID, "start") && now - lastStart > COOLDOWN_MS && has(text, START)) {
            lastStart = now;
            queue(c, GL[Math.max(0, Math.min(GL.length - 1, NewModSettings.opt(c, ID, "gltext")))]);
        }
    }

    private static void queue(Minecraft c, String message) {
        pending = message;
        dueAt = System.currentTimeMillis() + Math.max(1, NewModSettings.opt(c, ID, "delay")) * 1000L;
    }

    private static void tick(Minecraft c) {
        if (pending == null || System.currentTimeMillis() < dueAt) {
            return;
        }
        String message = pending;
        pending = null;
        if (c.player != null && c.player.connection != null && NewModSettings.isEnabled(c, ID)) {
            send(c.player.connection, message);
        }
    }

    /** The chat-send method is looked up by name, so a rename in a newer Minecraft just means nothing is sent. */
    private static void send(Object connection, String message) {
        for (String name : new String[]{"sendChat", "sendChatMessage"}) {
            try {
                connection.getClass().getMethod(name, String.class).invoke(connection, message);
                return;
            } catch (NoSuchMethodException ignored) {
                // try the next name
            } catch (Exception e) {
                NewSafe.failed("auto_text", e);
                return;
            }
        }
    }
}
