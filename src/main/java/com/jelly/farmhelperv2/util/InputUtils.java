package com.jelly.farmhelperv2.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Small helper for resetting client-side input state in the 1.21 Fabric port.
 *
 * This mirrors the intent of KeyBindUtils.stopMovement() from the 1.8.9 module:
 * clear all movement keys and (optionally) mouse buttons so features like
 * Pest Destroyer cannot leave the player \"stuck\" moving or clicking.
 */
public final class InputUtils {

    private InputUtils() {
    }

    public static void resetAll(MinecraftClient client, boolean resetUseKey, boolean resetAttackKey) {
        if (client == null) return;
        GameOptions options = client.options;
        if (options == null) return;

        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);

        if (resetUseKey) {
            options.useKey.setPressed(false);
        }
        if (resetAttackKey) {
            options.attackKey.setPressed(false);
        }
    }
}

