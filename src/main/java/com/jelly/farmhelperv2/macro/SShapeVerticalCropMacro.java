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
    /** Ticks after a direction swap during which we do not check for side block (avoids immediate re-detect oscillation). */
    private int postSwapCooldownTicks = 0;
    /** Ticks after macro enable during which we do not check for side block (avoids confusion when starting while hugging a block). */
    private int initialGraceTicks = 0;

    private static final int HOLD_TICKS = 5;   // ~0.25s at 20 TPS
    private static final int PAUSE_TICKS = 5;  // ~0.25s at 20 TPS
    private static final int POST_SWAP_COOLDOWN_TICKS = 10; // ~0.5s at 20 TPS
    private static final int INITIAL_GRACE_TICKS = 40;      // ~2s at 20 TPS

    @Override
    public void onEnable(MinecraftClient client) {
        directionState = DirectionState.NORMAL;
        stateTicks = 0;
        postSwapCooldownTicks = 0;
        initialGraceTicks = INITIAL_GRACE_TICKS;
        goingLeft = nextStartLeft;
        nextStartLeft = !nextStartLeft;

        if (client.player != null) {
            float currentYaw = client.player.getYaw();
            float targetYaw = snapYawToNearest90(currentYaw);
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
                if (initialGraceTicks > 0) {
                    initialGraceTicks--;
                } else if (postSwapCooldownTicks > 0) {
                    postSwapCooldownTicks--;
                } else if (isStrafingSideBlocked(client, goingLeft)) {
                    directionState = DirectionState.HOLD_SIDE;
                    stateTicks = 0;
                    if (client.player != null) {
                        client.player.sendMessage(
                                ChatUtils.info("S-Shape: side block hit, keeping " + (goingLeft ? "A" : "D") + " for 0.25s"),
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
                    postSwapCooldownTicks = POST_SWAP_COOLDOWN_TICKS;
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
     * Snap the yaw to the nearest 90 degrees (0, 90, 180, 270) so the macro
     * works for farms facing any cardinal direction.
     */
    private static float snapYawToNearest90(float yaw) {
        float normalized = normalizeYaw360(yaw);
        float nearest = Math.round(normalized / 90.0f) * 90.0f;
        if (nearest >= 360.0f) {
            nearest = 0.0f;
        }
        return nearest;
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
     * strafing side of the player, for any cardinal facing (0, 90, 180, 270).
     */
    private boolean isStrafingSideBlocked(MinecraftClient client, boolean checkLeftKey) {
        if (client.player == null || client.world == null)
            return false;

        BlockPos base = client.player.getBlockPos();
        float normalized = normalizeYaw360(client.player.getYaw());

        // World offset for the block to the left or right of the player depending on facing.
        // Right-hand rule: facing +Z (south), left = -X; facing -X (west), left = -Z;
        // facing -Z (north), left = +X; facing +X (east), left = +Z.
        int dx = 0;
        int dz = 0;
        if (normalized < 45.0f || normalized >= 315.0f) {
            dx = checkLeftKey ? -1 : 1;   // South: left = -X
        } else if (normalized >= 45.0f && normalized < 135.0f) {
            dz = checkLeftKey ? -1 : 1;   // West: left = -Z
        } else if (normalized >= 135.0f && normalized < 225.0f) {
            dx = checkLeftKey ? 1 : -1;   // North: left = +X
        } else {
            dz = checkLeftKey ? 1 : -1;   // East: left = +Z
        }

        BlockPos sidePos = base.add(dx, 0, dz);

        BlockState state = client.world.getBlockState(sidePos);
        boolean solid = !state.getCollisionShape(client.world, sidePos).isEmpty();

        return solid;
    }
}
