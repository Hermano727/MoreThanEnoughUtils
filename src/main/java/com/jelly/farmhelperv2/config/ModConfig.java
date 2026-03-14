package com.jelly.farmhelperv2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    // Chat shortcuts
    private static final List<ChatShortcut> chatShortcuts = new ArrayList<>();
    private static boolean chatShortcutsDirty = false;

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
            Text msg = enabled
                    ? ChatUtils.success("Auto Experiments ENABLED")
                    : ChatUtils.warning("Auto Experiments DISABLED");
            client.player.sendMessage(msg, false);
        }
    }

    public static int getAutoExperimentsClickDelayMs() {
        return autoExperimentsClickDelayMs;
    }

    public static void setAutoExperimentsClickDelayMs(int delayMs) {
        if (delayMs < 50) delayMs = 50;
        if (delayMs > 2000) delayMs = 2000;
        autoExperimentsClickDelayMs = delayMs;
    }

    public static List<ChatShortcut> getChatShortcuts() {
        return Collections.unmodifiableList(chatShortcuts);
    }

    public static void setChatShortcuts(List<ChatShortcut> newShortcuts) {
        chatShortcuts.clear();
        if (newShortcuts != null) {
            chatShortcuts.addAll(newShortcuts);
        }
        chatShortcutsDirty = true;
    }

    /**
     * Returns a mutable list of messages for use in the YACL list editor.
     */
    public static List<String> getChatShortcutMessages() {
        List<String> messages = new ArrayList<>(chatShortcuts.size());
        for (ChatShortcut shortcut : chatShortcuts) {
            messages.add(shortcut.message != null ? shortcut.message : "");
        }
        return messages;
    }

    /**
     * Applies a list of messages coming back from the YACL list editor.
     * Preserves existing ids and key codes by index where possible.
     */
    public static void applyChatShortcutMessages(List<String> messages) {
        List<ChatShortcut> rebuilt = new ArrayList<>();
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                String msg = messages.get(i);
                if (msg == null || msg.trim().isEmpty()) {
                    continue;
                }
                ChatShortcut existing = i < chatShortcuts.size() ? chatShortcuts.get(i) : null;
                ChatShortcut s = new ChatShortcut();
                s.id = existing != null ? existing.id : (i + 1);
                s.keyCode = existing != null ? existing.keyCode : 0;
                s.label = existing != null && existing.label != null && !existing.label.isEmpty()
                        ? existing.label
                        : "Shortcut #" + s.id;
                s.message = msg;
                rebuilt.add(s);
            }
        }
        chatShortcuts.clear();
        chatShortcuts.addAll(rebuilt);
        chatShortcutsDirty = true;
    }

    public static boolean consumeChatShortcutsDirty() {
        if (!chatShortcutsDirty) {
            return false;
        }
        chatShortcutsDirty = false;
        return true;
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
                if (data.chatShortcuts != null) {
                    chatShortcuts.clear();
                    chatShortcuts.addAll(data.chatShortcuts);
                }
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
            data.chatShortcuts = new ArrayList<>(chatShortcuts);
            Files.writeString(configPath, GSON.toJson(data));
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed to save config to {}", configPath, e);
        }
    }

    public static class Data {
        public int mainToggleKeyCode;
        public int openGuiKeyCode;
        public String cropTypeName;
        public boolean pestDestroyerEnabled;
        public int pestDestroyerKeyCode;
        public boolean autoExperimentsEnabled;
        public int autoExperimentsClickDelayMs;
        public List<ChatShortcut> chatShortcuts;
    }

    public static class ChatShortcut {
        public int id;
        public String label;
        public String message;
        public int keyCode;
    }
}
