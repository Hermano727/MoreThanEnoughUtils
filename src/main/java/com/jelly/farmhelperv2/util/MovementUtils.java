package com.jelly.farmhelperv2.util;

import net.minecraft.client.option.GameOptions;

/**
 * Small helper for common movement key combinations.
 */
public final class MovementUtils {

    private MovementUtils() {
    }

    /**
     * Clears all primary movement and interaction keys to a neutral state.
     */
    public static void stopAll(GameOptions options) {
        if (options == null) {
            return;
        }
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }
}

