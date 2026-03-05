package com.jelly.farmhelperv2.config;

import com.jelly.farmhelperv2.gui.config.MoreThanEnoughTilsConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Config screen entrypoint for the 1.21 Fabric port.
 * Uses vanilla Minecraft screens (no LibGui).
 */
public final class FarmHelperConfigScreen {

    private FarmHelperConfigScreen() {
    }

    /**
     * Creates a new config screen instance.
     */
    public static Screen create(Screen parent) {
        return new MoreThanEnoughTilsConfigScreen(parent);
    }

    /**
     * Opens the MoreThanEnoughUtils config screen.
     */
    public static void open(Screen parent) {
        MinecraftClient.getInstance().setScreen(create(parent));
    }
}
