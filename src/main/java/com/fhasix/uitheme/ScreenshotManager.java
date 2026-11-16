package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/**
 * Capture side of the screenshot manager: a rebindable "Take Screenshot" key (shows up in
 * Options > Controls under "Fhasix UI Theme") that saves through vanilla's own
 * ScreenshotRecorder, so files land in <game dir>/screenshots exactly like F2 does.
 * ScreenshotsScreen (the title-screen chat icon) lists whatever is in that folder.
 *
 * VERIFIED against the 1.21.11 Yarn javadocs:
 *  - ScreenshotRecorder.saveScreenshot(File, Framebuffer, Consumer<Text>)
 *  - KeyBinding.Category.register(Identifier)  (1.21.9+ replaced the String category)
 */
public final class ScreenshotManager {

    private static KeyMapping captureKey;

    private ScreenshotManager() {
    }

    /** Call once from the client entrypoint. */
    public static void init() {
        if (captureKey != null) {
            return;
        }

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fhasixuitheme", "main"));
        captureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.screenshot",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F8,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("screenshots", minecraft -> {
            boolean fire = false;
            while (captureKey.consumeClick()) {
                fire = true;
            }
            if (fire) {
                capture(minecraft);
            }
        }));
    }

    public static void capture(Minecraft minecraft) {
        // While the startup/reload overlay is up, the frame is the full-screen loading art -
        // and if that texture hasn't resolved yet, the saved "screenshot" is a full-frame
        // magenta/black missing-texture checkerboard. Skip the press instead of saving junk.
        if (minecraft.gui.overlay() != null) {
            return;
        }
        try {
            Screenshot.grab(
                    minecraft.gameDirectory,
                    minecraft.gameRenderer.mainRenderTarget(),
                    message -> minecraft.execute(() -> ChatMsg.post(minecraft, message))
            );
        } catch (Throwable ignored) {
            // A failed capture must never take the client down.
        }
    }

    public static Path screenshotsDir(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("screenshots");
    }

    /** The key's current label for UI hints, e.g. "F8" - follows whatever the player rebinds it to. */
    public static String keyLabel() {
        if (captureKey == null) {
            return "F8";
        }
        try {
            return captureKey.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return "F8";
        }
    }
}
