package com.jelly.farmhelperv2;

import com.jelly.farmhelperv2.config.FarmHelperConfigScreen;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.macro.Macro;
import com.jelly.farmhelperv2.macro.SShapeVerticalCropMacro;
import com.jelly.farmhelperv2.pests.PestsDestroyer;
import com.jelly.farmhelperv2.skyblock.AutoExperiments;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

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
                        KeyBinding.Category.MISC
                )
        );

        // Open config GUI key.
        openGuiKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.farmhelperv2.open_config",
                        InputUtil.Type.KEYSYM,
                        ModConfig.getOpenGuiKeyCode(),
                        KeyBinding.Category.MISC
                )
        );

        // Pest Destroyer toggle key.
        pestDestroyerKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.farmhelperv2.pests_toggle",
                        InputUtil.Type.KEYSYM,
                        ModConfig.getPestDestroyerKeyCode(),
                        KeyBinding.Category.MISC
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            // Handle keybind presses.
            handleToggleKey(mc);
            handleOpenGuiKey(mc);
            handlePestDestroyerKey(mc);

            // Drive the active macro every tick while enabled.
            if (enabled && currentMacro != null) {
                currentMacro.onTick(mc);
                enforceRotationLock(mc);
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
        });

    }

    private static void handleToggleKey(MinecraftClient mc) {
        while (toggleKeyBinding.wasPressed()) {
            // Turning on Pest Destroyer and the macro at the same time can cause
            // conflicting movement; for now, prefer the macro and leave Pest
            // Destroyer as a separate helper.
            enabled = !enabled;
            Text message = Text.literal("[MTEU] Macro " + (enabled ? "enabled" : "disabled"));
            mc.player.sendMessage(message, false);
            FarmHelperFabric.LOGGER.info("MTEU main toggle set to {}", enabled);

            if (enabled) {
                // For now, always run SShapeVerticalCropMacro when enabled.
                currentMacro = new SShapeVerticalCropMacro();
                currentMacro.onEnable(mc);
                captureRotationLock(mc);
            } else {
                if (currentMacro != null) {
                    currentMacro.onDisable(mc);
                    currentMacro = null;
                }
                rotationLockActive = false;
            }
        }
    }

    private static void handleOpenGuiKey(MinecraftClient mc) {
        while (openGuiKeyBinding.wasPressed()) {
            FarmHelperConfigScreen.open(null);
        }
    }

    private static void handlePestDestroyerKey(MinecraftClient mc) {
        while (pestDestroyerKeyBinding.wasPressed()) {
            boolean newValue = !ModConfig.isPestDestroyerEnabled();
            ModConfig.setPestDestroyerEnabled(newValue);
            ModConfig.save();

            Text message = Text.literal("Pest Destroyer: " + (newValue ? "enabled" : "disabled"));
            mc.player.sendMessage(message, false);
            FarmHelperFabric.LOGGER.info("Pest Destroyer toggle set to {}", newValue);
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

    public static boolean isEnabled() {
        return enabled;
    }
}

