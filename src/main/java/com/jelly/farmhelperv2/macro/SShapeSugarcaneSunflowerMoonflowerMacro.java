package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.HumanRotation;
import com.jelly.farmhelperv2.util.MovementUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Fabric 1.21 port of the legacy 1.8.9 S-Shape Sugarcane macro, adapted for the MTEU minimal macro framework.
 *
 * Behavior (high-level):
 * - Locks yaw to the closest diagonal (45° increments) and pitch ~0.
 * - Strafes along the lane while attacking.
 * - Detects lane ends primarily via "stuck while strafing" (keys pressed but sideways movement ~0).
 * - At a lane end: steps forward into the next lane briefly, then swaps strafe direction.
 *
 * This is designed for Sugarcane/Sunflower/Moonflower style farms where the player slides along lane edges.
 */
public final class SShapeSugarcaneSunflowerMoonflowerMacro implements Macro {

    private enum Phase {
        STRAFING,
        STEP_INTO_NEXT_LANE,
        PAUSE_BEFORE_ROTATE,
        ROTATING,
        NUDGE_FORWARD_AFTER_ROTATE
    }

    private static final int STEP_FORWARD_TICKS = 7;  // ~0.35s at 20 TPS
    private static final int PAUSE_TICKS = 3;         // small debounce before rotation
    private static final int INITIAL_GRACE_TICKS = 40;
    /** Forward nudge after a rotation that came from the \"slipped through gap\" path (ticks). */
    private static final int NUDGE_FORWARD_TICKS = 5; // ~0.25s at 20 TPS

    private static final int STUCK_TICKS_THRESHOLD = 5;     // ~0.25s
    private static final double STUCK_SPEED_EPSILON = 0.001;
    /** How many consecutive ticks we must see forward motion while strafing before treating it as "already slipped into next lane". */
    private static final int FORWARD_WHILE_STRAFING_TICKS_THRESHOLD = 3;
    /** Tiny threshold: any sustained forward/back drift above this while holding A counts as slipping through the gap. */
    private static final double FORWARD_SPEED_EPSILON = 0.005;

    private Phase phase = Phase.STRAFING;
    private int phaseTicks = 0;
    private int initialGraceTicks = INITIAL_GRACE_TICKS;

    private double lastPosX = 0.0;
    private double lastPosZ = 0.0;
    private int stuckTicks = 0;
    /** Counts consecutive ticks where we see non-trivial forward motion while holding A in STRAFING. */
    private int forwardWhileStrafingTicks = 0;

    private float lockedYaw = 0.0f;
    private float lockedPitch = 0.0f;
    private float rotateToYaw = 0.0f;
    private HumanRotation.RotationSession rotationSession = null;
    /** True when the current rotation was triggered by the \"forward while strafing\" shortcut. */
    private boolean rotationFromSlipShortcut = false;

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        phase = Phase.STRAFING;
        phaseTicks = 0;
        stuckTicks = 0;
        forwardWhileStrafingTicks = 0;
        initialGraceTicks = INITIAL_GRACE_TICKS;
        rotationFromSlipShortcut = false;

        lockedYaw = snapYawToNearestDiagonal(client.player.getYaw());
        lockedPitch = (float) (Math.random() * 1.0 - 0.5);

        client.player.setYaw(lockedYaw);
        client.player.setPitch(lockedPitch);
        client.player.setHeadYaw(lockedYaw);
        client.player.setBodyYaw(lockedYaw);

        lastPosX = client.player.getX();
        lastPosZ = client.player.getZ();

        applyKeys(client);
        if (ModConfig.isVerboseLogging()) {
            client.player.sendMessage(
                    ChatUtils.success("S-Shape Sugarcane ENABLED (holding A; rotating 180° at lane end)"),
                    false
            );
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Keep pitch stable; yaw is either locked or being smoothly rotated.
        client.player.setPitch(lockedPitch);

        switch (phase) {
            case STRAFING:
                client.player.setYaw(lockedYaw);
                client.player.setHeadYaw(lockedYaw);
                client.player.setBodyYaw(lockedYaw);
                if (initialGraceTicks > 0) {
                    initialGraceTicks--;
                    stuckTicks = 0;
                } else {
                    updateMovementDetection(client);
                    // High-priority path: we are holding A but have started to move forward/back noticeably
                    // -> we likely slipped through the end gap into the next lane already, so rotate immediately.
                    if (forwardWhileStrafingTicks >= FORWARD_WHILE_STRAFING_TICKS_THRESHOLD) {
                        forwardWhileStrafingTicks = 0;
                        stuckTicks = 0;
                        rotationFromSlipShortcut = true;
                        beginRotation180(client);
                        phase = Phase.ROTATING;
                        phaseTicks = 0;
                        initialGraceTicks = 6;
                        if (ModConfig.isVerboseLogging() && client.player != null) {
                            client.player.sendMessage(ChatUtils.info("Sugarcane: forward while strafing → rotate now"), false);
                        }
                    } else if (stuckTicks >= STUCK_TICKS_THRESHOLD || isLaneEndByBlocks(client)) {
                        phase = Phase.STEP_INTO_NEXT_LANE;
                        phaseTicks = 0;
                        stuckTicks = 0;
                        if (ModConfig.isVerboseLogging() && client.player != null) {
                            client.player.sendMessage(ChatUtils.info("Sugarcane: lane end → step forward"), false);
                        }
                    }
                }
                break;
            case STEP_INTO_NEXT_LANE:
                client.player.setYaw(lockedYaw);
                client.player.setHeadYaw(lockedYaw);
                client.player.setBodyYaw(lockedYaw);
                phaseTicks++;
                if (phaseTicks >= STEP_FORWARD_TICKS) {
                    phase = Phase.PAUSE_BEFORE_ROTATE;
                    phaseTicks = 0;
                }
                break;
            case PAUSE_BEFORE_ROTATE:
                client.player.setYaw(lockedYaw);
                client.player.setHeadYaw(lockedYaw);
                client.player.setBodyYaw(lockedYaw);
                phaseTicks++;
                if (phaseTicks >= PAUSE_TICKS) {
                    beginRotation180(client);
                    phase = Phase.ROTATING;
                    phaseTicks = 0;
                    stuckTicks = 0;
                    if (ModConfig.isVerboseLogging() && client.player != null) {
                        client.player.sendMessage(ChatUtils.info("Sugarcane: rotating to " + yawLabel(rotateToYaw)), false);
                    }
                }
                break;
            case ROTATING:
                if (rotationSession == null || rotationSession.tick(client)) {
                    lockedYaw = rotateToYaw;
                    if (rotationFromSlipShortcut) {
                        // After a slip-triggered rotation, nudge forward briefly before resuming A-strafe.
                        phase = Phase.NUDGE_FORWARD_AFTER_ROTATE;
                        phaseTicks = 0;
                    } else {
                        phase = Phase.STRAFING;
                        phaseTicks = 0;
                    }
                    phaseTicks = 0;
                    initialGraceTicks = 6; // grace after rotation so we don't immediately re-trigger on the walkway
                    stuckTicks = 0;
                }
                break;
            case NUDGE_FORWARD_AFTER_ROTATE:
                client.player.setYaw(lockedYaw);
                client.player.setHeadYaw(lockedYaw);
                client.player.setBodyYaw(lockedYaw);
                phaseTicks++;
                if (phaseTicks >= NUDGE_FORWARD_TICKS) {
                    rotationFromSlipShortcut = false;
                    phase = Phase.STRAFING;
                    phaseTicks = 0;
                    initialGraceTicks = 6;
                    stuckTicks = 0;
                    forwardWhileStrafingTicks = 0;
                }
                break;
        }

        applyKeys(client);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        MovementUtils.stopAll(client.options);
        if (client.player != null) {
            client.player.sendMessage(ChatUtils.warning("S-Shape Sugarcane DISABLED"), false);
        }
    }

    private void applyKeys(MinecraftClient client) {
        GameOptions opt = client.options;
        opt.attackKey.setPressed(true);
        opt.sprintKey.setPressed(true);

        switch (phase) {
            case STRAFING:
                opt.forwardKey.setPressed(false);
                opt.backKey.setPressed(false);
                opt.leftKey.setPressed(true);   // Always hold A; direction changes via rotation.
                opt.rightKey.setPressed(false);
                break;
            case STEP_INTO_NEXT_LANE:
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(false);
                opt.forwardKey.setPressed(true);
                opt.backKey.setPressed(false);
                break;
            case PAUSE_BEFORE_ROTATE:
                opt.forwardKey.setPressed(false);
                opt.backKey.setPressed(false);
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(false);
                break;
            case ROTATING:
                // Stand still while rotating (mouse-like rotation).
                opt.forwardKey.setPressed(false);
                opt.backKey.setPressed(false);
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(false);
                break;
            case NUDGE_FORWARD_AFTER_ROTATE:
                // Briefly walk forward into the lane before resuming A-only strafe.
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(false);
                opt.forwardKey.setPressed(true);
                opt.backKey.setPressed(false);
                break;
        }
    }

    /**
     * Updates movement-derived counters:
     * - stuckTicks: how long sideways movement has been ~0 while strafing
     * - forwardWhileStrafingTicks: how long we have seen noticeable forward/back motion while strafing
     */
    private void updateMovementDetection(MinecraftClient client) {
        double x = client.player.getX();
        double z = client.player.getZ();
        double dx = x - lastPosX;
        double dz = z - lastPosZ;
        lastPosX = x;
        lastPosZ = z;

        double sidewaysSpeed = Math.abs(projectOntoStrafeAxis(dx, dz, true));
        if (sidewaysSpeed < STUCK_SPEED_EPSILON) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        double forwardSpeed = Math.abs(projectOntoForwardAxis(dx, dz));
        // Forward drift while holding A is what we care about for "already slipped into next lane".
        if (forwardSpeed > FORWARD_SPEED_EPSILON) {
            forwardWhileStrafingTicks++;
        } else {
            forwardWhileStrafingTicks = 0;
        }

        // Optional debug: log speeds occasionally so it's easier to tune thresholds.
        if (ModConfig.isVerboseLogging() && client.player != null && phase == Phase.STRAFING && phaseTicks % 10 == 0) {
            client.player.sendMessage(
                    ChatUtils.info(String.format(
                            "Sugarcane debug: side=%.5f, fwd=%.5f, stuckTicks=%d, fwdTicks=%d",
                            sidewaysSpeed, forwardSpeed, stuckTicks, forwardWhileStrafingTicks
                    )),
                    false
            );
        }
    }

    /**
     * Projects the player delta vector onto the intended strafe direction.
     * Uses the player's snapped yaw so it stays consistent even if head jitter happens.
     */
    private double projectOntoStrafeAxis(double dx, double dz, boolean leftKey) {
        float normalized = BlockUtils.normalizeYaw360(lockedYaw);

        // Unit vector for "left" relative to facing (matching the approach in SShapeVerticalCropMacro).
        double leftX;
        double leftZ;
        if (normalized < 45.0f || normalized >= 315.0f) {         // facing South (+Z)
            leftX = 1.0;
            leftZ = 0.0;
        } else if (normalized >= 45.0f && normalized < 135.0f) {  // facing West (-X)
            leftX = 0.0;
            leftZ = 1.0;
        } else if (normalized >= 135.0f && normalized < 225.0f) { // facing North (-Z)
            leftX = -1.0;
            leftZ = 0.0;
        } else {                                                  // facing East (+X)
            leftX = 0.0;
            leftZ = -1.0;
        }

        double dirX = leftKey ? leftX : -leftX;
        double dirZ = leftKey ? leftZ : -leftZ;
        return dx * dirX + dz * dirZ;
    }

    /**
     * Projects the player delta vector onto the forward/back axis relative to lockedYaw.
     * Used to detect when we are suddenly moving down the lane while supposedly only strafing.
     */
    private double projectOntoForwardAxis(double dx, double dz) {
        float normalized = BlockUtils.normalizeYaw360(lockedYaw);

        // Forward unit vectors for the four cardinal sectors (same convention as BlockUtils.getUnitZ/X).
        double fx;
        double fz;
        if (normalized < 45.0f || normalized >= 315.0f) {         // facing South (+Z)
            fx = 0.0;
            fz = 1.0;
        } else if (normalized >= 45.0f && normalized < 135.0f) {  // facing West (-X)
            fx = -1.0;
            fz = 0.0;
        } else if (normalized >= 135.0f && normalized < 225.0f) { // facing North (-Z)
            fx = 0.0;
            fz = -1.0;
        } else {                                                  // facing East (+X)
            fx = 1.0;
            fz = 0.0;
        }
        return dx * fx + dz * fz;
    }

    private void beginRotation180(MinecraftClient client) {
        rotateToYaw = snapYawToNearestDiagonal(lockedYaw + 180.0f);
        rotationSession = HumanRotation.rotateToDegrees(client, rotateToYaw, 750L, 1000L);
    }

    private static String yawLabel(float yaw) {
        float n = BlockUtils.normalizeYaw360(yaw);
        if (Math.abs(n - 45.0f) < 0.1f) return "45";
        if (Math.abs(n - 135.0f) < 0.1f) return "135";
        if (Math.abs(n - 225.0f) < 0.1f) return "-135";
        if (Math.abs(n - 315.0f) < 0.1f) return "-45";
        return String.format("%.1f", yaw);
    }

    /**
     * Secondary lane-end heuristic based on the legacy 1.8.9 idea:
     * if there is water slightly ahead on one diagonal side, and the near side wall isn't present,
     * we consider that a hint we should switch.
     *
     * This is intentionally conservative; movement-based stuck detection is the primary trigger.
     */
    private boolean isLaneEndByBlocks(MinecraftClient client) {
        World world = client.world;
        if (world == null || client.player == null) return false;

        // Check a small fan of blocks diagonally "forward" relative to our diagonal yaw.
        // We interpret yaw-45 and yaw+45 by sampling both side+front-ish offsets.
        boolean waterLeftDiag = isWaterAtRelative(world, rel(2, -1, 1, lockedYaw - 45))
                || isWaterAtRelative(world, rel(2, 0, 1, lockedYaw - 45))
                || isWaterAtRelative(world, rel(-1, -1, 1, lockedYaw - 45))
                || isWaterAtRelative(world, rel(-1, 0, 1, lockedYaw - 45));

        if (waterLeftDiag) {
            boolean frontWall = isBlockedAtRelative(world, rel(0, 0, 1, lockedYaw - 45));
            boolean sideWall = isBlockedAtRelative(world, rel(-1, 0, 0, lockedYaw - 45));
            if (!(frontWall && sideWall)) {
                return true;
            }
        }

        boolean waterRightDiag = isWaterAtRelative(world, rel(2, -1, 1, lockedYaw + 45))
                || isWaterAtRelative(world, rel(2, 0, 1, lockedYaw + 45))
                || isWaterAtRelative(world, rel(-1, -1, 1, lockedYaw + 45))
                || isWaterAtRelative(world, rel(-1, 0, 1, lockedYaw + 45));

        if (waterRightDiag) {
            boolean frontWall = isBlockedAtRelative(world, rel(0, 0, 1, lockedYaw + 45));
            boolean sideWall = isBlockedAtRelative(world, rel(1, 0, 0, lockedYaw + 45));
            if (!(frontWall && sideWall)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSideWallNear(MinecraftClient client, boolean checkLeft) {
        if (client.player == null || client.world == null) return false;
        // Sample up to 2 blocks to the side at the current locked yaw; if anything solid is there, treat as wall.
        for (int i = 1; i <= 2; i++) {
            BlockPos pos = rel(checkLeft ? -i : i, 0, 0, lockedYaw);
            if (isBlockedAtRelative(client.world, pos)) return true;
        }
        return false;
    }

    private BlockPos rel(int right, int up, int forward, float yaw) {
        if (MinecraftClient.getInstance().player == null) return BlockPos.ORIGIN;
        double px = MinecraftClient.getInstance().player.getX();
        double py = MinecraftClient.getInstance().player.getY();
        double pz = MinecraftClient.getInstance().player.getZ();
        // BlockUtils expects x=left/right, z=forward/back. Our params are right/forward, so x = -right.
        return BlockUtils.getRelativeBlockPos(-right, up, forward, yaw, px, py, pz);
    }

    private static boolean isBlockedAtRelative(World world, BlockPos pos) {
        if (world == null) return false;
        BlockState st = world.getBlockState(pos);
        return !st.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isWaterAtRelative(World world, BlockPos pos) {
        if (world == null) return false;
        BlockState st = world.getBlockState(pos);
        if (st.getBlock() == Blocks.WATER || st.getBlock() == Blocks.BUBBLE_COLUMN) return true;
        FluidState fs = st.getFluidState();
        return fs != null && !fs.isEmpty() && fs.isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    private static float snapYawToNearestDiagonal(float yaw) {
        float normalized = BlockUtils.normalizeYaw360(yaw);
        float nearest = Math.round(normalized / 45.0f) * 45.0f;
        if (nearest >= 360.0f) nearest = 0.0f;
        return nearest;
    }
}

