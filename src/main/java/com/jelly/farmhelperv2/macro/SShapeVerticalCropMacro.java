package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.MovementUtils;
import com.jelly.farmhelperv2.util.WalkableHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

/**
 * Minimal 1.21 version of the old S-shape vertical crop macro.
 * Strafes left/right only (no W during strafing).
 * <ul>
 *   <li><b>Nether wart (default):</b> When a side block is hit, keeps direction ~0.25s, pauses, then swaps.</li>
 *   <li><b>MelonkingDE (subclass):</b> When block in front is not pumpkin/melon (end of lane), walks forward ~0.5s, then swaps.</li>
 * </ul>
 * On start, direction is chosen by: block on right → go left; block on left → go right; else alternate.
 */
public class SShapeVerticalCropMacro implements Macro {

    /** Alternate starting direction each time the macro is re-enabled across toggles. */
    private static boolean nextStartLeft = true;

    private boolean goingLeft = true;

    private enum DirectionState {
        NORMAL,
        HOLD_SIDE,
        WALK_FORWARD,
        PAUSE_BEFORE_SWITCH
    }

    private DirectionState directionState = DirectionState.NORMAL;
    private int stateTicks = 0;
    private int postSwapCooldownTicks = 0;
    private int initialGraceTicks = 0;
    /** Rewarp-only hook: when true, allow one side-block-based A/D swap check at farm start. */
    private boolean pendingRewarpSideSwapCheck = false;
    /** MelonkingDE: counts ticks where sideways movement has effectively stopped while strafing. */
    private int stoppedStrafingTicks = 0;
    /** MelonkingDE: previous player position, used to measure sideways movement. */
    private double lastPosX = 0.0;
    private double lastPosZ = 0.0;
    /** MelonkingDE: true when current WALK_FORWARD is "step into new lane" after a swap (then we apply grace). */
    private boolean walkingIntoLaneAfterSwap = false;
    private static final int HOLD_TICKS = 5;           // ~0.25s at 20 TPS (side block)
    private static final int WALK_FORWARD_TICKS = 10;  // ~0.5s at 20 TPS (end of lane)
    private static final int PAUSE_TICKS = 5;         // ~0.25s at 20 TPS
    private static final int POST_SWAP_COOLDOWN_TICKS = 10;
    /** After a lane swap in MelonkingDE, ignore "end of lane" for this long so we don't re-trigger on the walkway. */
    private static final int POST_SWAP_GRACE_TICKS_END_OF_LANE = 50;  // ~2.5s at 20 TPS
    private static final int INITIAL_GRACE_TICKS = 40;
    /** MelonkingDE: how long strafing must be effectively stopped before we treat it as "possible lane end". */
    private static final int STOPPED_STRAFING_TICKS_THRESHOLD = 4; // ~0.2s at 20 TPS
    /** MelonkingDE: minimum number of crop blocks (pumpkin/melon/stems) ahead to consider "still in lane". */
    private static final int MIN_CROP_BLOCKS_AHEAD = 3;
    /** Debug: log WalkableHelper state every this many ticks when in NORMAL (0 = disabled). */
    private static final int DEBUG_INTERVAL_TICKS = 40;
    /** Look pitch for nether wart / carrot / potato style vertical rows (degrees). */
    private static final float VERTICAL_MACRO_PITCH = 2.6f;

    /**
     * If true, use end-of-lane detection (front walkable → walk forward → swap).
     * If false, use side-block detection (side blocked → hold 0.25s → swap). Default is false (nether wart).
     */
    protected boolean useEndOfLaneDetection() {
        return false;
    }

    @Override
    public void onEnable(MinecraftClient client) {
        directionState = DirectionState.NORMAL;
        stateTicks = 0;
        postSwapCooldownTicks = 0;
        initialGraceTicks = INITIAL_GRACE_TICKS;
        pendingRewarpSideSwapCheck = false;
        stoppedStrafingTicks = 0;
        walkingIntoLaneAfterSwap = false;

        if (client.player != null) {
            float targetYaw = BlockUtils.snapYawToNearest90(client.player.getYaw());
            MovementUtils.applyRotation(client, targetYaw, VERTICAL_MACRO_PITCH);

            // Initialize last position for MelonkingDE movement-based detection.
            lastPosX = client.player.getX();
            lastPosZ = client.player.getZ();
        }

        // Initial direction: solid block on right → go left; solid block on left → go right; else alternate.
        // Ignore water/lava so we don't treat a water channel as "block on left".
        boolean blockOnRight = isStrafingSideBlockedForStart(client, false);
        boolean blockOnLeft = isStrafingSideBlockedForStart(client, true);
        if (blockOnRight) {
            goingLeft = true;
        } else if (blockOnLeft) {
            goingLeft = false;
        } else {
            goingLeft = nextStartLeft;
            nextStartLeft = !nextStartLeft;
        }

        GameOptions options = client.options;
        options.attackKey.setPressed(true);
        options.sprintKey.setPressed(true);
        options.leftKey.setPressed(goingLeft);
        options.rightKey.setPressed(!goingLeft);

        if (client.player != null) {
            if (com.jelly.farmhelperv2.config.ModConfig.isVerboseLogging()) {
                client.player.sendMessage(
                        ChatUtils.success("S-Shape Vertical macro ENABLED (holding " + (goingLeft ? "A" : "D") + ")"),
                        false
                );
            }
            String startReason = blockOnRight && blockOnLeft ? " [start: block left+right→alternate]" : blockOnRight ? " [start: block right→left]" : blockOnLeft ? " [start: block left→right]" : " [start: alternate]";
            debugState(client, "state=NORMAL (strafing " + (goingLeft ? "A" : "D") + ")" + startReason);
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
                options.forwardKey.setPressed(false);
                options.backKey.setPressed(false);
                options.leftKey.setPressed(left);
                options.rightKey.setPressed(right);

                if (initialGraceTicks > 0) {
                    initialGraceTicks--;
                } else if (postSwapCooldownTicks > 0) {
                    postSwapCooldownTicks--;
                } else {
                    if (pendingRewarpSideSwapCheck && useEndOfLaneDetection()) {
                        pendingRewarpSideSwapCheck = false;
                        if (isStrafingSideBlocked(client, goingLeft)) {
                            goingLeft = !goingLeft;
                            postSwapCooldownTicks = POST_SWAP_GRACE_TICKS_END_OF_LANE;
                            stoppedStrafingTicks = 0;
                            debugState(client, "rewarp start detected side block → swapping to "
                                    + (goingLeft ? "LEFT (A)" : "RIGHT (D)"));
                        }
                    }
                    if (useEndOfLaneDetection()) {
                        handleMelonkingEndOfLaneDetection(client, goingLeft, !goingLeft);
                    } else {
                        // Nether wart: side block hit → hold then swap
                        if (isStrafingSideBlocked(client, goingLeft)) {
                            directionState = DirectionState.HOLD_SIDE;
                            stateTicks = 0;
                            debugState(client, "side block hit → HOLD_SIDE (holding " + (goingLeft ? "A" : "D") + " 0.25s)");
                        } else if (DEBUG_INTERVAL_TICKS > 0 && stateTicks > 0 && stateTicks % DEBUG_INTERVAL_TICKS == 0) {
                            boolean side = isStrafingSideBlocked(client, goingLeft);
                            debugState(client, "NORMAL check: sideBlocked=" + side);
                        }
                    }
                }
                stateTicks++;
            }
            break;
            case HOLD_SIDE: {
                options.forwardKey.setPressed(false);
                options.backKey.setPressed(false);
                options.leftKey.setPressed(left);
                options.rightKey.setPressed(right);
                stateTicks++;
                if (stateTicks >= HOLD_TICKS) {
                    directionState = DirectionState.PAUSE_BEFORE_SWITCH;
                    stateTicks = 0;
                    debugState(client, "HOLD_SIDE done → PAUSE_BEFORE_SWITCH");
                }
            }
            break;
            case WALK_FORWARD: {
                options.forwardKey.setPressed(true);
                options.backKey.setPressed(false);
                options.leftKey.setPressed(false);
                options.rightKey.setPressed(false);
                stateTicks++;
                if (stateTicks >= WALK_FORWARD_TICKS) {
                    stateTicks = 0;
                    if (walkingIntoLaneAfterSwap) {
                        walkingIntoLaneAfterSwap = false;
                        directionState = DirectionState.NORMAL;
                        postSwapCooldownTicks = POST_SWAP_GRACE_TICKS_END_OF_LANE;
                        debugState(client, "WALK_FORWARD (into lane) done → NORMAL (grace " + postSwapCooldownTicks + "t)");
                    } else {
                        directionState = DirectionState.PAUSE_BEFORE_SWITCH;
                        debugState(client, "WALK_FORWARD done → PAUSE_BEFORE_SWITCH");
                    }
                }
            }
            break;
            case PAUSE_BEFORE_SWITCH: {
                options.forwardKey.setPressed(false);
                options.backKey.setPressed(false);
                options.leftKey.setPressed(false);
                options.rightKey.setPressed(false);
                stateTicks++;
                if (stateTicks >= PAUSE_TICKS) {
                    goingLeft = !goingLeft;
                    stateTicks = 0;
                    if (useEndOfLaneDetection()) {
                        // MelonkingDE: after swap, walk forward into the new lane; grace is applied on WALK_FORWARD completion.
                        walkingIntoLaneAfterSwap = true;
                        directionState = DirectionState.WALK_FORWARD;
                        debugState(client, "swapping direction → " + (goingLeft ? "LEFT (A)" : "RIGHT (D)") + " → WALK_FORWARD (into lane)");
                    } else {
                        directionState = DirectionState.NORMAL;
                        postSwapCooldownTicks = POST_SWAP_COOLDOWN_TICKS;
                        debugState(client, "swapping direction → " + (goingLeft ? "LEFT (A)" : "RIGHT (D)"));
                    }
                    if (client.player != null && com.jelly.farmhelperv2.config.ModConfig.isVerboseLogging()) {
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

    private void debugState(MinecraftClient client, String message) {
        if (client.player != null && com.jelly.farmhelperv2.config.ModConfig.isVerboseLogging()) {
            client.player.sendMessage(ChatUtils.info("Vertical: " + message), false);
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
     * Called by rewarp flow: enable one-time side-block check in NORMAL state.
     * Only consumed when end-of-lane detection mode is active (MelonkingDE).
     */
    public void requestRewarpSideSwapCheck() {
        pendingRewarpSideSwapCheck = true;
    }

    /**
     * True if there is a solid collision block immediately to the strafing side of the player.
     * Used for nether wart vertical: hit side → hold 0.25s → swap.
     */
    private boolean isStrafingSideBlocked(MinecraftClient client, boolean checkLeftKey) {
        if (client.player == null || client.world == null) return false;

        BlockPos base = client.player.getBlockPos();
        float normalized = BlockUtils.normalizeYaw360(client.player.getYaw());

        int dx = 0;
        int dz = 0;
        // Yaw 0° = South (+Z forward); right = -X, left = +X. Match 1.8.9 getRelativeBlockPos(±1,0,0) convention.
        if (normalized < 45.0f || normalized >= 315.0f) {
            dx = checkLeftKey ? 1 : -1;
        } else if (normalized >= 45.0f && normalized < 135.0f) {
            dz = checkLeftKey ? 1 : -1;
        } else if (normalized >= 135.0f && normalized < 225.0f) {
            dx = checkLeftKey ? -1 : 1;
        } else {
            dz = checkLeftKey ? -1 : 1;
        }

        BlockPos sidePos = base.add(dx, 0, dz);
        BlockState state = client.world.getBlockState(sidePos);
        return !state.getCollisionShape(client.world, sidePos).isEmpty();
    }

    /**
     * Like isStrafingSideBlocked but used only for choosing start direction.
     * Returns false for water, lava, and farm terrain/crops so we use alternate direction in the farm.
     */
    private boolean isStrafingSideBlockedForStart(MinecraftClient client, boolean checkLeftKey) {
        if (client.player == null || client.world == null) return false;

        BlockPos base = client.player.getBlockPos();
        float normalized = BlockUtils.normalizeYaw360(client.player.getYaw());

        int dx = 0;
        int dz = 0;
        // Same left/right convention as isStrafingSideBlocked (yaw 0° = South, right = -X).
        if (normalized < 45.0f || normalized >= 315.0f) {
            dx = checkLeftKey ? 1 : -1;
        } else if (normalized >= 45.0f && normalized < 135.0f) {
            dz = checkLeftKey ? 1 : -1;
        } else if (normalized >= 135.0f && normalized < 225.0f) {
            dx = checkLeftKey ? -1 : 1;
        } else {
            dz = checkLeftKey ? -1 : 1;
        }

        BlockPos sidePos = base.add(dx, 0, dz);
        Block block = client.world.getBlockState(sidePos).getBlock();
        if (isNonBlockingForStartDirection(block)) {
            return false;
        }
        return !client.world.getBlockState(sidePos).getCollisionShape(client.world, sidePos).isEmpty();
    }

    /**
     * MelonkingDE: movement-based end-of-lane detection.
     * - While strafing, measure sideways movement per tick.
     * - If sideways movement is ~0 for a few ticks, treat as "possible lane end".
     * - Then scan directly ahead for crops; only if there are none do we start lane swap.
     */
    private void handleMelonkingEndOfLaneDetection(MinecraftClient client, boolean leftKey, boolean rightKey) {
        if (client.player == null || client.world == null) return;

        // Compute sideways movement component based on current yaw and direction (left/right).
        double currentX = client.player.getX();
        double currentZ = client.player.getZ();
        double dx = currentX - lastPosX;
        double dz = currentZ - lastPosZ;

        float yaw = client.player.getYaw();
        float normalized = BlockUtils.normalizeYaw360(yaw);

        // Unit vector for "left" relative to facing.
        double leftX = 0.0;
        double leftZ = 0.0;
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

        double dirX = leftKey ? leftX : -leftX; // right is opposite of left
        double dirZ = leftKey ? leftZ : -leftZ;

        double sidewaysDelta = dx * dirX + dz * dirZ;
        double sidewaysSpeed = Math.abs(sidewaysDelta);

        // Threshold: if sideways speed is tiny, treat as "stopped strafing".
        if (sidewaysSpeed < 0.001) {
            stoppedStrafingTicks++;
        } else {
            stoppedStrafingTicks = 0;
        }

        lastPosX = currentX;
        lastPosZ = currentZ;

        if (stoppedStrafingTicks >= STOPPED_STRAFING_TICKS_THRESHOLD) {
            // We've effectively stopped sliding along the lane wall → check if there are any crops directly ahead.
            boolean cropsAhead = hasCropsDirectlyAhead(client);
            if (!cropsAhead) {
                directionState = DirectionState.WALK_FORWARD;
                stateTicks = 0;
                stoppedStrafingTicks = 0;
                debugState(client, "end of lane (stopped strafing, no crops ahead) → WALK_FORWARD");
            } else {
                // Still crops ahead; we're likely just slowed down by water/lag/gap.
                stoppedStrafingTicks = 0;
                if (DEBUG_INTERVAL_TICKS > 0 && stateTicks > 0 && stateTicks % DEBUG_INTERVAL_TICKS == 0) {
                    debugState(client, "stopped strafing but crops still ahead; staying in lane");
                }
            }
        } else if (DEBUG_INTERVAL_TICKS > 0 && stateTicks > 0 && stateTicks % DEBUG_INTERVAL_TICKS == 0) {
            debugState(client, "NORMAL strafing: sidewaysSpeed=" + String.format("%.5f", sidewaysSpeed) + ", stoppedStrafingTicks=" + stoppedStrafingTicks);
        }
    }

    /**
     * MelonkingDE: scan a small column directly in front of the player for pumpkins/melons (and stems).
     * Only uses 1–2 blocks forward, no side fan-out, matching the idea of "directly in front".
     */
    private boolean hasCropsDirectlyAhead(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos base = client.player.getBlockPos();
        net.minecraft.util.math.Direction facing = client.player.getHorizontalFacing();

        int fx = facing.getOffsetX();
        int fz = facing.getOffsetZ();

        int cropCount = 0;
        for (int dist = 1; dist <= 2; dist++) {
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos pos = base.add(fx * dist, dy, fz * dist);
                Block block = client.world.getBlockState(pos).getBlock();
                if (block == Blocks.PUMPKIN || block == Blocks.MELON || block == Blocks.CARVED_PUMPKIN
                        || block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM
                        || block == Blocks.ATTACHED_PUMPKIN_STEM || block == Blocks.ATTACHED_MELON_STEM) {
                    cropCount++;
                }
            }
        }
        return cropCount > 0;
    }

    /**
     * True if this block should not count as "blocking" when choosing start direction,
     * so we use alternate logic instead of "block on right → left" / "block on left → right".
     * Includes water, lava, and farm blocks (crops, stems, farmland, farm floor).
     */
    private static boolean isNonBlockingForStartDirection(Block block) {
        if (block == Blocks.WATER || block == Blocks.LAVA) {
            return true;
        }
        // Crops and stems (have collision but are part of the lane)
        if (block == Blocks.PUMPKIN || block == Blocks.MELON || block == Blocks.CARVED_PUMPKIN
                || block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM
                || block == Blocks.ATTACHED_PUMPKIN_STEM || block == Blocks.ATTACHED_MELON_STEM
                || block == Blocks.NETHER_WART) {
            return true;
        }
        // Farm terrain (so we alternate when standing in/next to farmland)
        if (block == Blocks.FARMLAND || block == Blocks.DIRT || block == Blocks.GRASS_BLOCK
                || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) {
            return true;
        }
        return false;
    }

}
