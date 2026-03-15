package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.CropMacroType;
import com.jelly.farmhelperv2.config.FarmHelperConfigScreen;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.config.RewarpPoint;
import com.jelly.farmhelperv2.macro.Macro;
import com.jelly.farmhelperv2.macro.SShapePumpkinMelonMacro;
import com.jelly.farmhelperv2.macro.SShapeVerticalCropMacro;
import com.jelly.farmhelperv2.macro.SShapeVerticalMelonkingdeMacro;
import com.jelly.farmhelperv2.pests.PestsDestroyer;
import com.jelly.farmhelperv2.render.RewarpRenderer;
import com.jelly.farmhelperv2.skyblock.AutoExperiments;
import com.jelly.farmhelperv2.skyblock.ScoreboardAreaReader;
import com.jelly.farmhelperv2.util.ChatUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side state and controls for the MoreThanEnoughUtils 1.21 Fabric port.
 *
 * Responsibilities:
 * - Register and drive keybindings (main macro toggle, open config GUI, Pest Destroyer toggle).
 * - Manage the active macro lifecycle.
 * - Enforce pitch/yaw lock with an alarm while the macro is enabled.
 * - Tick the minimal Pest Destroyer feature.
 */
public final class FarmHelperClient {

    private static boolean enabled = false;
    private static KeyBinding toggleKeyBinding;
    private static KeyBinding openGuiKeyBinding;
    private static KeyBinding pestDestroyerKeyBinding;
    private static Macro currentMacro;

    private static float lockedYaw;
    private static float lockedPitch;
    private static boolean rotationLockActive = false;
    private static long lastAlarmTimeMs = 0L;

    private static final long ALARM_COOLDOWN_MS = 1500L;
    private static final float ROTATION_EPSILON_DEGREES = 0.5f;

    /** Custom keybind category so MTEU keybinds appear under "MoreThanEnoughUtils" in Controls. */
    private static final KeyBinding.Category MTEU_CATEGORY = new KeyBinding.Category(Identifier.of("farmhelperv2", "mteu"));

    /** Max number of chat shortcut slots; keybinds are registered once at init and cannot be re-registered. */
    private static final int MAX_CHAT_SHORTCUTS = 20;
    private static final List<KeyBinding> chatShortcutKeyBindings = new ArrayList<>(MAX_CHAT_SHORTCUTS);
    private static ScoreboardAreaReader.Area lastArea = ScoreboardAreaReader.Area.UNKNOWN;

    /** Cooldown after triggering rewarp to avoid spamming /warp garden (ms). */
    private static final long REWARP_COOLDOWN_MS = 10_000;
    private static long lastRewarpTriggerTime = 0;
    /** Last tick we were standing on a rewarp block; only trigger /warp when we *enter* a rewarp (move onto it). */
    private static boolean wasOnRewarpLastTick = false;

    private FarmHelperClient() {
    }

    public static void init() {
        MinecraftClient client = MinecraftClient.getInstance();

        // Main MTEU toggle key.
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.farmhelperv2.toggle",
                        InputUtil.Type.KEYSYM,
                        ModConfig.getMainToggleKeyCode(),
                        MTEU_CATEGORY
                )
        );

        // Open config GUI key.
        openGuiKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.farmhelperv2.open_config",
                        InputUtil.Type.KEYSYM,
                        ModConfig.getOpenGuiKeyCode(),
                        MTEU_CATEGORY
                )
        );

        // Pest Destroyer toggle key.
        pestDestroyerKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.farmhelperv2.pests_toggle",
                        InputUtil.Type.KEYSYM,
                        ModConfig.getPestDestroyerKeyCode(),
                        MTEU_CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            // Debug: log and show when our inferred SkyBlock area changes.
            ScoreboardAreaReader.Area currentArea = ScoreboardAreaReader.getCurrentArea(mc);
            if (currentArea != lastArea) {
                FarmHelperFabric.LOGGER.info("MTEU area changed: {} -> {}", lastArea, currentArea);
                if (mc.player != null) {
                    mc.player.sendMessage(
                            ChatUtils.info("Area changed: " + lastArea + " \u2192 " + currentArea),
                            false
                    );
                }
                lastArea = currentArea;
            }

            // Handle keybind presses.
            handleToggleKey(mc);
            handleOpenGuiKey(mc);
            handlePestDestroyerKey(mc);

            // Drive the active macro every tick while enabled.
            if (enabled && currentMacro != null) {
                // Area failsafe: only run macros in the Garden/barn area.
                if (!ScoreboardAreaReader.isInGarden(mc)) {
                    disableMacro(mc, ChatUtils.warning("Macro disabled: left Garden area"));
                } else {
                    currentMacro.onTick(mc);
                    enforceRotationLock(mc);
                }
            } else {
                rotationLockActive = false;
            }

            // Run minimal Pest Destroyer logic when enabled in config.
            if (ModConfig.isPestDestroyerEnabled()) {
                PestsDestroyer.tick(mc);
            }

            // Run Auto Experiments helper only when enabled in config.
            if (ModConfig.isAutoExperimentsEnabled()) {
                try {
                    AutoExperiments.onClientTick(mc);
                } catch (Throwable t) {
                    FarmHelperFabric.LOGGER.error("AutoExperiments: Uncaught error in onClientTick", t);
                }
            }

            // Rewarp: only when player *moves onto* a saved point (not when adding or standing still on it), run /warp garden.
            if (!ModConfig.getRewarps().isEmpty() && mc.player != null && mc.world != null) {
                BlockPos playerPos = mc.player.getBlockPos();
                boolean onRewarpNow = false;
                for (RewarpPoint r : ModConfig.getRewarps()) {
                    if (r.getDistance(playerPos) <= 2) {
                        onRewarpNow = true;
                        break;
                    }
                }
                long now = System.currentTimeMillis();
                if (onRewarpNow && !wasOnRewarpLastTick && now - lastRewarpTriggerTime >= REWARP_COOLDOWN_MS) {
                    mc.getNetworkHandler().sendChatMessage("/warp garden");
                    lastRewarpTriggerTime = now;
                    mc.player.sendMessage(ChatUtils.info("Rewarp: ran /warp garden"), false);
                }
                wasOnRewarpLastTick = onRewarpNow;
            } else {
                wasOnRewarpLastTick = false;
            }

            // Consume dirty flag (config was saved); we do NOT re-register keybinds — Fabric only allows that at init.
            ModConfig.consumeChatShortcutsDirty();

            handleChatShortcutKeys(mc);
        });

        // Disable macros on server reboot warnings in chat.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("Scheduled Reboot") || text.contains("server will restart soon")) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && enabled && currentMacro != null) {
                    disableMacro(mc, ChatUtils.error("Macro disabled: server reboot scheduled"));
                }
            }
        });

        registerChatShortcutKeyBindingsOnce();
    }

    private static void handleToggleKey(MinecraftClient mc) {
        while (toggleKeyBinding.wasPressed()) {
            // Turning on Pest Destroyer and the macro at the same time can cause
            // conflicting movement; for now, prefer the macro and leave Pest
            // Destroyer as a separate helper.
            boolean newValue = !enabled;
            if (newValue) {
                currentMacro = createMacroForCurrentCropType();
                if (currentMacro == null) {
                    if (mc.player != null) {
                        mc.player.sendMessage(ChatUtils.error("No macro for selected crop type"), false);
                    }
                    return;
                }
                enabled = true;
                currentMacro.onEnable(mc);
                captureRotationLock(mc);
                if (mc.player != null) {
                    mc.player.sendMessage(ChatUtils.success("Macro enabled"), false);
                }
            } else {
                disableMacro(mc, ChatUtils.warning("Macro disabled"));
            }
            FarmHelperFabric.LOGGER.info("MTEU main toggle set to {}", enabled);
        }
    }

    private static void handleOpenGuiKey(MinecraftClient mc) {
        while (openGuiKeyBinding.wasPressed()) {
            mc.setScreen(FarmHelperConfigScreen.create(mc.currentScreen));
        }
    }

    private static Macro createMacroForCurrentCropType() {
        switch (ModConfig.getCropType()) {
            case S_SHAPE_VERTICAL:
                return new SShapeVerticalCropMacro();
            case S_SHAPE_PUMPKIN_MELON:
                return new SShapePumpkinMelonMacro();
            case S_SHAPE_PUMPKIN_MELON_MELONKINGDE:
                return new SShapeVerticalMelonkingdeMacro();
            default:
                return new SShapeVerticalCropMacro();
        }
    }

    private static void handlePestDestroyerKey(MinecraftClient mc) {
        while (pestDestroyerKeyBinding.wasPressed()) {
            boolean newValue = !ModConfig.isPestDestroyerEnabled();
            ModConfig.setPestDestroyerEnabled(newValue);
            ModConfig.save();

            if (mc.player != null) {
                mc.player.sendMessage(
                        newValue
                                ? ChatUtils.success("Pest Destroyer enabled")
                                : ChatUtils.warning("Pest Destroyer disabled"),
                        false
                );
            }
            FarmHelperFabric.LOGGER.info("Pest Destroyer toggle set to {}", newValue);
        }
    }

    /**
     * Registers a fixed number of chat shortcut keybinds once at init.
     * Fabric does not allow registering keybinds after GameOptions has been initialised,
     * so we register MAX_CHAT_SHORTCUTS slots; each slot sends the message at that index when pressed.
     */
    private static void registerChatShortcutKeyBindingsOnce() {
        chatShortcutKeyBindings.clear();
        for (int i = 0; i < MAX_CHAT_SHORTCUTS; i++) {
            int index = i + 1;
            KeyBinding binding = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(
                            "key.farmhelperv2.chat_shortcut." + index,
                            InputUtil.Type.KEYSYM,
                            InputUtil.UNKNOWN_KEY.getCode(),
                            MTEU_CATEGORY
                    )
            );
            chatShortcutKeyBindings.add(binding);
        }
    }

    private static void captureRotationLock(MinecraftClient mc) {
        if (mc.player == null) return;
        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();
        rotationLockActive = true;
        lastAlarmTimeMs = 0L;
    }

    private static void enforceRotationLock(MinecraftClient mc) {
        if (!rotationLockActive || mc.player == null) return;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float deltaYaw = Math.abs(currentYaw - lockedYaw);
        float deltaPitch = Math.abs(currentPitch - lockedPitch);

        boolean outOfBounds = deltaYaw > ROTATION_EPSILON_DEGREES || deltaPitch > ROTATION_EPSILON_DEGREES;
        if (!outOfBounds) {
            return;
        }

        // Snap the player back to the locked rotation.
        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);
        mc.player.setHeadYaw(lockedYaw);
        mc.player.setBodyYaw(lockedYaw);

        long now = System.currentTimeMillis();
        if (now - lastAlarmTimeMs >= ALARM_COOLDOWN_MS) {
            lastAlarmTimeMs = now;
            // Play a bell-like sound as an alarm.
            if (mc.world != null && mc.player != null) {
                mc.world.playSound(
                        mc.player,
                        mc.player.getX(),
                        mc.player.getY(),
                        mc.player.getZ(),
                        SoundEvents.BLOCK_BELL_USE,
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.0f
                );
            } else if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(
                        PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_USE, 1.0f)
                );
            }
        }
    }

    private static void handleChatShortcutKeys(MinecraftClient mc) {
        if (mc.player == null) return;
        List<ModConfig.ChatShortcut> shortcuts = ModConfig.getChatShortcuts();
        for (int i = 0; i < chatShortcutKeyBindings.size() && i < shortcuts.size(); i++) {
            KeyBinding binding = chatShortcutKeyBindings.get(i);
            while (binding.wasPressed()) {
                String message = shortcuts.get(i).message;
                if (message != null && !message.isEmpty()) {
                    mc.getNetworkHandler().sendChatMessage(message);
                }
            }
        }
    }

    /**
     * Returns the list of chat shortcut keybindings for use by the config screen
     * (e.g. to display and set keybinds in the GUI). Do not modify the list.
     */
    public static List<KeyBinding> getChatShortcutKeyBindings() {
        return chatShortcutKeyBindings;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void disableMacro(MinecraftClient mc, Text reason) {
        enabled = false;
        if (currentMacro != null) {
            currentMacro.onDisable(mc);
            currentMacro = null;
        }
        rotationLockActive = false;
        if (mc.player != null && reason != null) {
            mc.player.sendMessage(reason, false);
        }
    }
}

