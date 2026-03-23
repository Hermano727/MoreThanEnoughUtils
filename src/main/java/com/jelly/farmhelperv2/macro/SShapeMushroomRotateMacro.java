package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.MovementUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * S-Shape Mushroom Rotate macro (Fabric 1.21 MTEU port of Forge 1.8.9 SShapeMushroomRotateMacro).
 *
 * <p>Unlike {@link SShapeMushroomMacro} (which faces 45° diagonally and strafes), this variant
 * faces ±30° off the cardinal row axis and always moves forward. Direction changes are achieved
 * by rotating the yaw to the opposite ±30° offset, not by changing which strafe key is held.
 *
 * <p>State switching logic (per Forge original):
 * <ul>
 *   <li>In {@code LEFT} state: if right is walkable → switch to RIGHT; else if left is still
 *       walkable → stay LEFT; else recalculate.</li>
 *   <li>In {@code RIGHT} state: if left is walkable → switch to LEFT; else if right is still
 *       walkable → stay RIGHT; else recalculate.</li>
 * </ul>
 *
 * <p>Ports removed vs. Forge original:
 * <ul>
 *   <li>{@code RotationConfiguration.easeTo} — yaw is set directly (no easing animation).</li>
 *   <li>DROPPING state — garden mushroom plots are flat.</li>
 *   <li>{@code doAfterRewarpRotation} / {@code shouldRotateAfterWarp} — MTEU rewarp is
 *       handled externally in {@code FarmHelperClient}.</li>
 *   <li>Repeated per-tick yaw noise — yaw noise is applied only on actual state transitions
 *       to avoid oscillation when called at 20Hz.</li>
 * </ul>
 *
 * <p>Important: this macro intentionally changes yaw on direction transitions, so the
 * rotation lock in {@code FarmHelperClient} must be disabled for this crop type (same
 * pattern as S-Shape Sugarcane).
 */
public class SShapeMushroomRotateMacro implements Macro {

    private enum State { NONE, LEFT, RIGHT }

    private State state = State.NONE;

    /**
     * Nearest cardinal (0°/90°/180°/270°) snapped from player yaw at enable time.
     * Used as the reference axis for wall scans and yaw offset calculations.
     */
    private float closest90Yaw = 0f;

    /** Current applied yaw (closest90Yaw ± 30° + random noise). Updated on state transitions. */
    private float currentFacingYaw = 0f;

    private float pitch = 0f;

    // -------------------------------------------------------------------------
    // Macro interface
    // -------------------------------------------------------------------------

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player == null) return;

        pitch = (float) (Math.random() * 2 - 1);

        float rawYaw = client.player.getYaw();
        closest90Yaw = BlockUtils.snapYawToNearest90(rawYaw);

        // Default to a small RIGHT offset; corrected on the first updateState tick via calculateDirection.
        currentFacingYaw = closest90Yaw + 30f + (float) (Math.random() * 4 - 2);
        applyYaw(client, currentFacingYaw);

        state = State.NONE;

        if (ModConfig.isVerboseLogging()) {
            client.player.sendMessage(ChatUtils.success("S-Shape Mushroom Rotate ENABLED"), false);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        updateState(client);
        applyKeys(client);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        MovementUtils.stopAll(client.options);
        state = State.NONE;
        if (client.player != null) {
            client.player.sendMessage(ChatUtils.warning("S-Shape Mushroom Rotate DISABLED"), false);
        }
    }

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    private void updateState(MinecraftClient client) {
        switch (state) {
            case LEFT: {
                if (isRightWalkable(client)) {
                    transitionTo(State.RIGHT, client);
                } else if (!isLeftWalkable(client)) {
                    transitionTo(calculateDirection(client), client);
                }
                // else: left still walkable, stay LEFT — no yaw update needed
                break;
            }
            case RIGHT: {
                if (isLeftWalkable(client)) {
                    transitionTo(State.LEFT, client);
                } else if (!isRightWalkable(client)) {
                    transitionTo(calculateDirection(client), client);
                }
                // else: right still walkable, stay RIGHT — no yaw update needed
                break;
            }
            case NONE: {
                transitionTo(calculateDirection(client), client);
                break;
            }
        }
    }

    /**
     * Applies the new state and updates yaw/pitch only when the state actually changes.
     * This avoids the random-noise oscillation that would occur if yaw were re-applied every tick.
     */
    private void transitionTo(State newState, MinecraftClient client) {
        state = newState;
        switch (newState) {
            case LEFT: {
                pitch = (float) (Math.random() * 2 - 1);
                currentFacingYaw = closest90Yaw - 30f + (float) (Math.random() * 4 - 2);
                applyYaw(client, currentFacingYaw);
                if (ModConfig.isVerboseLogging() && client.player != null) {
                    client.player.sendMessage(ChatUtils.info("Mushroom Rotate: → LEFT"), false);
                }
                break;
            }
            case RIGHT: {
                pitch = (float) (Math.random() * 2 - 1);
                currentFacingYaw = closest90Yaw + 30f + (float) (Math.random() * 4 - 2);
                applyYaw(client, currentFacingYaw);
                if (ModConfig.isVerboseLogging() && client.player != null) {
                    client.player.sendMessage(ChatUtils.info("Mushroom Rotate: → RIGHT"), false);
                }
                break;
            }
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Key application
    // -------------------------------------------------------------------------

    /**
     * Always hold forward + attack in LEFT/RIGHT states — direction is controlled by yaw,
     * not by strafe keys. This differs from {@link SShapeMushroomMacro} which strafes.
     */
    private void applyKeys(MinecraftClient client) {
        switch (state) {
            case LEFT:
            case RIGHT: {
                client.options.forwardKey.setPressed(true);
                client.options.attackKey.setPressed(true);
                client.options.leftKey.setPressed(false);
                client.options.rightKey.setPressed(false);
                client.options.backKey.setPressed(false);
                client.options.sprintKey.setPressed(false);
                client.options.jumpKey.setPressed(false);
                client.options.sneakKey.setPressed(false);
                break;
            }
            default: {
                MovementUtils.stopAll(client.options);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Direction calculation
    // -------------------------------------------------------------------------

    /**
     * Scans along {@code closest90Yaw} in both directions to find the nearest wall and returns
     * the appropriate starting state. Wall on the right → LEFT; wall on the left → RIGHT.
     */
    private State calculateDirection(MinecraftClient client) {
        if (client.player == null || client.world == null) return State.NONE;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        for (int i = 1; i < 180; i++) {
            BlockPos posR = BlockUtils.getRelativeBlockPos(i, 0, 0, closest90Yaw, px, py, pz);
            BlockPos posL = BlockUtils.getRelativeBlockPos(-i, 0, 0, closest90Yaw, px, py, pz);
            if (!BlockUtils.canWalkThrough(client.world, posR)) return State.LEFT;
            if (!BlockUtils.canWalkThrough(client.world, posL)) return State.RIGHT;
        }
        return State.NONE;
    }

    // -------------------------------------------------------------------------
    // Walkability helpers (relative to closest90Yaw, not the player's facing yaw)
    // -------------------------------------------------------------------------

    private boolean isLeftWalkable(MinecraftClient client) {
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        BlockPos pos = BlockUtils.getRelativeBlockPos(-1, 0, 0, closest90Yaw, px, py, pz);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    private boolean isRightWalkable(MinecraftClient client) {
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        BlockPos pos = BlockUtils.getRelativeBlockPos(1, 0, 0, closest90Yaw, px, py, pz);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    // -------------------------------------------------------------------------
    // Yaw helpers
    // -------------------------------------------------------------------------

    private void applyYaw(MinecraftClient client, float yaw) {
        MovementUtils.applyRotation(client, yaw, pitch);
    }
}
