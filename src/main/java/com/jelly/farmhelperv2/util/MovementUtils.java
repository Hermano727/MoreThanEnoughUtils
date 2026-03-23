package com.jelly.farmhelperv2.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Small helper for common movement key combinations.
 */
public final class MovementUtils {

    private MovementUtils() {
    }

    /**
     * Snaps the player to the given yaw and pitch, updating head and body yaw as well.
     * No-op if the player is null.
     */
    public static void applyRotation(MinecraftClient client, float yaw, float pitch) {
        if (client.player == null) return;
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);
        client.player.setBodyYaw(yaw);
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
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }
}

