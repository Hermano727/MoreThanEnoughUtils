package com.jelly.farmhelperv2.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

/**
 * Mod Menu hook that exposes the MoreThanEnoughUtils config screen from the
 * Mod Menu "Configure" button.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (Screen parent) -> FarmHelperConfigScreen.create(parent);
    }
}

