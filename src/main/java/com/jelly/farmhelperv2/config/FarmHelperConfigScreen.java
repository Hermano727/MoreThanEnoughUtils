package com.jelly.farmhelperv2.config;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Config screen entrypoint for the 1.21 Fabric port.
 * Now uses a YACL-generated config screen.
 */
public final class FarmHelperConfigScreen {

    private FarmHelperConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("MoreThanEnoughUtils"))
                .category(ModOptions::farmingCategory)
                .category(ModOptions::fishCategory)
                .category(ModOptions::otherCategory)
                .category(ModOptions::bingoCategory)
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }
}
