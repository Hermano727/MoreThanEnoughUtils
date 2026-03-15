package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.render.RewarpRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * MoreThanEnoughUtils — Fabric 1.21 client entry point.
 * Migration from 1.8.9 Forge; see MIGRATION_ROADMAP.md in the project root.
 */
public class FarmHelperFabric implements ClientModInitializer {

    public static final String MOD_ID = "farmhelperv2";
    public static final String VERSION = "1.0.0-alpha";
    public static final Logger LOGGER = LoggerFactory.getLogger("MoreThanEnoughUtils");

    @Override
    public void onInitializeClient() {
        LOGGER.info("MoreThanEnoughUtils v{} (Fabric 1.21) loaded. Migration in progress — see MIGRATION_ROADMAP.md", VERSION);

        Path configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID + ".json");
        ModConfig.setConfigPath(configPath);
        ModConfig.load();

        FarmHelperClient.init();
        RewarpRenderer.register();
        MteuCommands.register();
    }
}
