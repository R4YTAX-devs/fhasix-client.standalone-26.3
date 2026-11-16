package com.fhasix.uitheme;

/**
 * NewBlockOverlay: draws in the 3D world. Minecraft 26.3 replaced the old world-render events with the new
 * submit/gizmo renderer, so this feature is switched off in the 26.3 build until it is ported.
 * (The settings entry stays; init() only has to exist.)
 */
final class NewBlockOverlay {
    private NewBlockOverlay() {}

    static void init() {}
}
