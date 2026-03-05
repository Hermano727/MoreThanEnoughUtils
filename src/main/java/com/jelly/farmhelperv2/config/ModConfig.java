package com.jelly.farmhelperv2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jelly.farmhelperv2.FarmHelperFabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent config for MoreThanEnoughUtils 1.21.
 * Stored as JSON under config/farmhelperv2.json.
 * Keybinds are stored as GLFW key codes; crop selection as enum name.
 */
public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;

    // Main macro
    private static int mainToggleKeyCode = GLFW.GLFW_KEY_H;
    private static int openGuiKeyCode = GLFW.GLFW_KEY_F;
    private static String cropTypeName = CropMacroType.S_SHAPE_VERTICAL.name();

    // Pest Destroyer
    private static boolean pestDestroyerEnabled = false;
    private static int pestDestroyerKeyCode = GLFW.GLFW_KEY_P;

    // Auto Experiments (Chronomatron / Ultrasequencer)
    private static boolean autoExperimentsEnabled = false;
    /** Delay between Auto Experiments clicks, in milliseconds. */
    private static int autoExperimentsClickDelayMs = 500;

    private ModConfig() {
    }

    public static void setConfigPath(Path path) {
        configPath = path;
    }

    public static int getMainToggleKeyCode() {
        return mainToggleKeyCode;
    }

    public static void setMainToggleKeyCode(int keyCode) {
        mainToggleKeyCode = keyCode;
    }

    public static int getOpenGuiKeyCode() {
        return openGuiKeyCode;
    }

    public static void setOpenGuiKeyCode(int keyCode) {
        openGuiKeyCode = keyCode;
    }

    public static CropMacroType getCropType() {
        try {
            return CropMacroType.valueOf(cropTypeName);
        } catch (Exception e) {
            return CropMacroType.S_SHAPE_VERTICAL;
        }
    }

    public static void setCropType(CropMacroType type) {
        cropTypeName = type.name();
    }

    public static boolean isPestDestroyerEnabled() {
        return pestDestroyerEnabled;
    }

    public static void setPestDestroyerEnabled(boolean enabled) {
        pestDestroyerEnabled = enabled;
    }

    public static int getPestDestroyerKeyCode() {
        return pestDestroyerKeyCode;
    }

    public static void setPestDestroyerKeyCode(int keyCode) {
        pestDestroyerKeyCode = keyCode;
    }

    public static boolean isAutoExperimentsEnabled() {
        return autoExperimentsEnabled;
    }

    public static void setAutoExperimentsEnabled(boolean enabled) {
        autoExperimentsEnabled = enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(
                    Text.literal("[MTEU] Auto Experiments " + (enabled ? "ENABLED" : "DISABLED")),
                    false
            );
        }
    }

    public static int getAutoExperimentsClickDelayMs() {
        return autoExperimentsClickDelayMs;
    }

    public static void setAutoExperimentsClickDelayMs(int delayMs) {
        // Simple safety clamp.
        if (delayMs < 50) delayMs = 50;
        if (delayMs > 2000) delayMs = 2000;
        autoExperimentsClickDelayMs = delayMs;
    }

    public static void load() {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return;
        }
        try {
            String json = Files.readString(configPath);
            Data data = GSON.fromJson(json, Data.class);
            if (data != null) {
                if (data.mainToggleKeyCode != 0) mainToggleKeyCode = data.mainToggleKeyCode;
                if (data.openGuiKeyCode != 0) openGuiKeyCode = data.openGuiKeyCode;
                if (data.cropTypeName != null) cropTypeName = data.cropTypeName;
                pestDestroyerEnabled = data.pestDestroyerEnabled;
                if (data.pestDestroyerKeyCode != 0) pestDestroyerKeyCode = data.pestDestroyerKeyCode;
                autoExperimentsEnabled = data.autoExperimentsEnabled;
                if (data.autoExperimentsClickDelayMs > 0) autoExperimentsClickDelayMs = data.autoExperimentsClickDelayMs;
            }
        } catch (Exception e) {
            FarmHelperFabric.LOGGER.warn("Failed to load config from {}", configPath, e);
        }
    }

    public static void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Data data = new Data();
            data.mainToggleKeyCode = mainToggleKeyCode;
            data.openGuiKeyCode = openGuiKeyCode;
            data.cropTypeName = cropTypeName;
            data.pestDestroyerEnabled = pestDestroyerEnabled;
            data.pestDestroyerKeyCode = pestDestroyerKeyCode;
            data.autoExperimentsEnabled = autoExperimentsEnabled;
            data.autoExperimentsClickDelayMs = autoExperimentsClickDelayMs;
            Files.writeString(configPath, GSON.toJson(data));
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed to save config to {}", configPath, e);
        }
    }

    /** Updates a KeyBinding's bound key from a GLFW key code. */
    public static void applyKeyToBinding(KeyBinding binding, int keyCode) {
        if (keyCode == 0) {
            binding.setBoundKey(InputUtil.UNKNOWN_KEY);
        } else {
            binding.setBoundKey(InputUtil.fromKeyCode(new KeyInput(keyCode, 0, 0)));
        }
    }

    public static class Data {
        public int mainToggleKeyCode = GLFW.GLFW_KEY_H;
        public int openGuiKeyCode = GLFW.GLFW_KEY_F;
        public String cropTypeName = CropMacroType.S_SHAPE_VERTICAL.name();
        public boolean pestDestroyerEnabled = false;
        public int pestDestroyerKeyCode = GLFW.GLFW_KEY_P;
        public boolean autoExperimentsEnabled = false;
        public int autoExperimentsClickDelayMs = 500;
    }
}
