package com.fhasix.loader;

import net.fabricmc.api.ClientModInitializer;

/** Entrypoint: starts the update check in the background as soon as the game starts. */
public final class FhasixLoader implements ClientModInitializer {
    public static final Updater UPDATER = new Updater();

    /** Set once the update gate has taken over the first title screen. */
    public static volatile boolean gateDone = false;

    @Override
    public void onInitializeClient() {
        UPDATER.startCheck();
    }
}
