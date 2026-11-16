package com.fhasix.uitheme;

import net.fabricmc.api.ClientModInitializer;

public class e5 implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenshotManager.init();
        FhasixCatalog.init(); // UPDATE 46: loads the saved cosmetics catalog and quietly checks GitHub for a newer one
        NewHud.init(); // v30: Right Shift launcher + FPS/CPS HUD
        FhasixDiag.start(); // writes fhasix-diag.txt: real method names of every class the mixins hook
    }
}
