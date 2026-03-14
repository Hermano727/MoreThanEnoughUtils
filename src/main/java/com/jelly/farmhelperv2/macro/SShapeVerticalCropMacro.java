package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.MovementUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

/**
 * Minimal 1.21 version of the old S-shape vertical crop macro.
 */
public class SShapeVerticalCropMacro implements Macro {

    /** Alternate starting direction each time the macro is re-enabled. */
    private static boolean nextStartLeft = true;

    private boolean goingLeft = true;

    private enum DirectionState {
        NORMAL,
        HOLD_SIDE,
        PAUSE_BEFORE_SWITCH
    }

    private DirectionState directionState = DirectionState.NORMAL;
    private int stateTicks = 0;
    private static final int HOLD_TICKS = 10;  // ~0.5s at 20 TPS
    private static final int PAUSE_TICKS = 10; // ~0.5s at 20 TPS

    @Override
    public void onEnable(MinecraftClient client) {
        directionState = DirectionState.NORMAL;
        stateTicks = 0;
        goingLeft = nextStartLeft;
        nextStartLeft = !nextStartLeft;

        if (client.player != null) {
            float currentYaw = client.player.getYaw();
            float targetYaw = snapYawToNearestFarmDirection(currentYaw);
            float targetPitch = 0.0f;

            client.player.setYaw(targetYaw);
            client.player.setPitch(targetPitch);
            client.player.setHeadYaw(targetYaw);
            client.player.setBodyYaw(targetYaw);
        }

        GameOptions options = client.options;
        options.attackKey.setPressed(true);
        options.sprintKey.setPressed(true);
        options.leftKey.setPressed(goingLeft);
        options.rightKey.setPressed(!goingLeft);

        if (client.player != null) {
            client.player.sendMessage(
                    ChatUtils.success("S-Shape Vertical macro ENABLED (holding " + (goingLeft ? "A" : "D") + ")"),
                    false
            );
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;

        options.attackKey.setPressed(true);
        options.sprintKey.setPressed(true);
        boolean left = goingLeft;
        boolean right = !goingLeft;

        switch (directionState) {
            case NORMAL: {
                options.leftKey.setPressed(left);
                options.rightKey.setPressed(right);
                if (isStrafingSideBlocked(client, goingLeft)) {
                    directionState = DirectionState.HOLD_SIDE;
                    stateTicks = 0;
                    if (client.player != null) {
                        client.player.sendMessage(
                                ChatUtils.info("S-Shape: side block hit, keeping " + (goingLeft ? "A" : "D") + " for 0.5s"),
                                false
                        );
                    }
                }
            }
            break;
            case HOLD_SIDE: {
                options.leftKey.setPressed(left);
                options.rightKey.setPressed(right);
                stateTicks++;
                if (stateTicks >= HOLD_TICKS) {
                    directionState = DirectionState.PAUSE_BEFORE_SWITCH;
                    stateTicks = 0;
                }
            }
            break;
            case PAUSE_BEFORE_SWITCH: {
                // Pause strafing for a brief moment before switching.
                options.leftKey.setPressed(false);
                options.rightKey.setPressed(false);
                stateTicks++;
                if (stateTicks >= PAUSE_TICKS) {
                    goingLeft = !goingLeft;
                    directionState = DirectionState.NORMAL;
                    stateTicks = 0;
                    if (client.player != null) {
                        client.player.sendMessage(
                                ChatUtils.info("S-Shape: switching direction to " + (goingLeft ? "LEFT (A)" : "RIGHT (D)")),
                                false
                        );
                    }
                }
            }
            break;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        GameOptions options = client.options;

        MovementUtils.stopAll(options);

        if (client.player != null) {
            client.player.sendMessage(
                    ChatUtils.warning("S-Shape Vertical macro DISABLED"),
                    false
            );
        }
    }

    /**
     * Snap the yaw to the nearest "farm direction" (0 or 180 degrees) based on
     * where the player is currently facing.
     */
    private static float snapYawToNearestFarmDirection(float yaw) {
        float normalized = normalizeYaw360(yaw);

        float distTo0 = Math.min(normalized, 360.0f - normalized);
        float distTo180 = Math.abs(normalized - 180.0f);

        return distTo0 <= distTo180 ? 0.0f : 180.0f;
    }

    /**
     * Returns true if the given yaw corresponds to looking roughly towards +Z
     * (south) rather than -Z (north).
     */
    private static boolean isFacingPositiveZ(float yaw) {
        float normalized = normalizeYaw360(yaw);
        return normalized < 90.0f || normalized > 270.0f;
    }

    /**
     * Normalizes a yaw angle to the range [0, 360).
     */
    private static float normalizeYaw360(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    /**
     * Returns true if there is a solid collision block immediately to the
     * strafing side of the player, taking into account which way the player is
     * facing (0 vs 180 degrees).
     */
    private boolean isStrafingSideBlocked(MinecraftClient client, boolean checkLeftKey) {
        if (client.player == null || client.world == null)
            return false;

        BlockPos base = client.player.getBlockPos();

        // Map the current strafe key (left/right) to a world X offset depending on
        // whether we're facing towards +Z or -Z.
        boolean facingPositiveZ = isFacingPositiveZ(client.player.getYaw());
        int dx;
        if (facingPositiveZ) {
            dx = checkLeftKey ? 1 : -1;
        } else {
            dx = checkLeftKey ? -1 : 1;
        }

        BlockPos sidePos = base.add(dx, 0, 0);

        BlockState state = client.world.getBlockState(sidePos);
        // Treat anything with a non-empty collision shape as "blocking".
        boolean solid = !state.getCollisionShape(client.world, sidePos).isEmpty();

        return solid;
    }
}
