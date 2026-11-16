package com.fhasix.uitheme;

import java.awt.Desktop;
import java.net.URI;

/** Opens a link/folder in the system browser or file manager. Minecraft 26.3 removed Util.getPlatform().openUri. */
final class OpenUri {
    private OpenUri() {
    }

    static void open(Object target) {
        try {
            URI uri = target instanceof URI u ? u
                    : target instanceof java.nio.file.Path p ? p.toUri()
                    : URI.create(String.valueOf(target));
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (Throwable ignored) {
            // no browser or file manager on this runtime - nothing to do
        }
    }
}
