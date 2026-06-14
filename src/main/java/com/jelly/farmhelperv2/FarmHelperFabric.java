package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.render.JawbusWarningHud;
import com.jelly.farmhelperv2.render.RatTracerRenderer;
import com.jelly.farmhelperv2.render.RewarpRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * MoreThanEnoughUtils — Fabric 1.21 client entry point.
 * Migration from 1.8.9 Forge; see MIGRATION_ROADMAP.md in the project root.
 */
public class FarmHelperFabric implements ClientModInitializer {

    public static final String MOD_ID = "farmhelperv2";
    public static final Logger LOGGER = LoggerFactory.getLogger("MoreThanEnoughUtils");
    public static String VERSION = "unknown";
    public static String BUILD_FINGERPRINT = "unknown";

    @Override
    public void onInitializeClient() {
        VERSION = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        BUILD_FINGERPRINT = computeBuildFingerprint();

        LOGGER.info(
                "MoreThanEnoughUtils v{} loaded (build fingerprint: {}). Migration in progress — see MIGRATION_ROADMAP.md",
                VERSION,
                BUILD_FINGERPRINT
        );

        Path configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID + ".json");
        ModConfig.setConfigPath(configPath);
        ModConfig.load();

        FarmHelperClient.init();
        RewarpRenderer.register();
        RatTracerRenderer.register();
        JawbusWarningHud.register();
        MteuCommands.register();
    }

    private static String computeBuildFingerprint() {
        try {
            ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
            if (container == null) return "no-mod-container";

            Path originPath = container.getOrigin().getPaths().isEmpty()
                    ? null
                    : container.getOrigin().getPaths().get(0);
            String originName = originPath == null ? "unknown-origin" : originPath.getFileName().toString();
            String modified = originPath == null
                    ? "unknown-time"
                    : DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                            .withZone(ZoneOffset.UTC)
                            .format(Instant.ofEpochMilli(Files.getLastModifiedTime(originPath).toMillis()));
            return originName + "@" + modified + "Z";
        } catch (Throwable ignored) {
            // Fallback for environments where origin path isn't available.
            try {
                URL location = FarmHelperFabric.class.getProtectionDomain().getCodeSource().getLocation();
                URI uri = location.toURI();
                File file = new File(uri);
                String name = file.getName().isEmpty() ? "dev-runtime" : file.getName();
                String modified = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.ofEpochMilli(file.lastModified()));
                return name + "@" + modified + "Z";
            } catch (Throwable t) {
                return "unknown-runtime";
            }
        }
    }
}
