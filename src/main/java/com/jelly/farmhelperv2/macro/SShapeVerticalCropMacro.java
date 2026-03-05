package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.FarmHelperFabric;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Minimal 1.21 version of the old S-shape vertical crop macro.
 */
public class SShapeVerticalCropMacro implements Macro {

    /** Alternate starting direction each time the macro is re-enabled. */
    private static boolean nextStartLeft = true;

    private boolean goingLeft = true;
    private int debugCounter = 0;
    private int directionChangeCooldownTicks = 0;
    private static final int DIRECTION_CHANGE_COOLDOWN_TICKS = 20;
    private boolean pendingDirectionChange = false;

    @Override
    public void onEnable(MinecraftClient client) {
        directionChangeCooldownTicks = 0;
        pendingDirectionChange = false;
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
            client.player.sendMessage(Text.literal("[MTEU] S-Shape Vertical macro ENABLED (holding " + (goingLeft ? "A" : "D") + ")"), false);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        GameOptions options = client.options;

        // Core movement: always walk forward and attack, strafe left/right depending on
        // state.
        options.attackKey.setPressed(true);
        options.sprintKey.setPressed(true);

        options.leftKey.setPressed(goingLeft);
        options.rightKey.setPressed(!goingLeft);

        if (directionChangeCooldownTicks > 0) {
            directionChangeCooldownTicks--;
            if (directionChangeCooldownTicks == 0 && pendingDirectionChange) {
                goingLeft = !goingLeft;
                pendingDirectionChange = false;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("[MTEU] S-Shape: switching direction to " + (goingLeft ? "LEFT (A)" : "RIGHT (D)")),
                            false);
                }
            }
        } else if (isStrafingSideBlocked(client, goingLeft)) {
            pendingDirectionChange = true;
            directionChangeCooldownTicks = DIRECTION_CHANGE_COOLDOWN_TICKS;
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("[MTEU] S-Shape: side block hit, keeping " + (goingLeft ? "A" : "D") + " for 1s then switching"),
                        false);
            }
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        GameOptions options = client.options;

        options.attackKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);

        if (client.player != null) {
            client.player.sendMessage(Text.literal("[MTEU] S-Shape Vertical macro DISABLED"), false);
        }
    }

    /**
     * Snap the yaw to the nearest "farm direction" (0 or 180 degrees) based on
     * where the player is currently facing.
     */
    private static float snapYawToNearestFarmDirection(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }

        float distTo0 = Math.min(normalized, 360.0f - normalized);
        float distTo180 = Math.abs(normalized - 180.0f);

        return distTo0 <= distTo180 ? 0.0f : 180.0f;
    }

    /**
     * Returns true if the given yaw corresponds to looking roughly towards +Z
     * (south) rather than -Z (north).
     */
    private static boolean isFacingPositiveZ(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized < 90.0f || normalized > 270.0f;
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
