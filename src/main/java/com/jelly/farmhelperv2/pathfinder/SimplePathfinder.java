package com.jelly.farmhelperv2.pathfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Very small helper that moves the player roughly toward a target position
 * by pressing movement keys and rotating the player.
 *
 * This is intentionally much simpler than the 1.8.9 FlyPathFinderExecutor
 * and is only meant to support the minimal Pest Destroyer alpha behavior.
 */
public final class SimplePathfinder {

    private SimplePathfinder() {
    }

    public static void moveTowards(MinecraftClient client, Vec3d target) {
        if (client == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;

        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();

        if (dx == 0 && dz == 0) {
            stop(client);
            return;
        }

        float yaw = (float) (MathHelper.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        // Simple "walk forward & sprint" toward the target.
        options.forwardKey.setPressed(true);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(true);
    }

    public static void stop(MinecraftClient client) {
        if (client == null) {
            return;
        }
        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }
}

