package com.jelly.farmhelperv2.pathfinder;

import com.jelly.farmhelperv2.util.MovementUtils;
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

    /** Vertical tolerance (blocks) before we consider Y aligned; avoids jitter when nearly level. */
    public static final double VERT_ADJUST_THRESHOLD = 1.0D;

    private SimplePathfinder() {
    }

    /**
     * Moves toward target XZ only. Sneak and jump keys are explicitly cleared so that
     * vertical state from a prior alignY() call does not persist into horizontal movement.
     */
    public static void moveTowardsXZOnly(MinecraftClient client, Vec3d target) {
        if (client == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;

        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();

        if (dx != 0 || dz != 0) {
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
            player.setYaw(yaw);
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);

            options.forwardKey.setPressed(true);
            options.backKey.setPressed(false);
            options.leftKey.setPressed(false);
            options.rightKey.setPressed(false);
            options.sprintKey.setPressed(true);
        } else {
            options.forwardKey.setPressed(false);
            options.backKey.setPressed(false);
            options.leftKey.setPressed(false);
            options.rightKey.setPressed(false);
            options.sprintKey.setPressed(false);
        }

        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
    }

    /**
     * Adjusts vertical position only (for flying). All horizontal movement keys are cleared.
     * Use this as a dedicated phase before horizontal approach so that sneak is never
     * held simultaneously with an attack or use action.
     */
    public static void alignY(MinecraftClient client, double targetY) {
        if (client == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;

        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);

        double dy = targetY - player.getY();
        if (dy > VERT_ADJUST_THRESHOLD) {
            options.jumpKey.setPressed(true);
            options.sneakKey.setPressed(false);
        } else if (dy < -VERT_ADJUST_THRESHOLD) {
            options.jumpKey.setPressed(false);
            options.sneakKey.setPressed(true);
        } else {
            options.jumpKey.setPressed(false);
            options.sneakKey.setPressed(false);
        }
    }

    /** @deprecated Use {@link #moveTowardsXZOnly} + {@link #alignY} via the ALIGN_Y state instead. */
    @Deprecated
    public static void moveTowards(MinecraftClient client, Vec3d target) {
        moveTowardsXZOnly(client, target);
    }

    public static void stop(MinecraftClient client) {
        if (client == null) {
            return;
        }
        GameOptions options = client.options;
        MovementUtils.stopAll(options);
    }
}

