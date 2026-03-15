package com.jelly.farmhelperv2.macro;

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

    /** Alternate starting direction each time the macro is re-enabled. */
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
    /** MelonkingDE: only trigger end-of-lane after we've seen pumpkin/melon in front at least once since last swap. */
    private boolean sawPumpkinOrMelonSinceSwap = false;
    /** MelonkingDE: true when current WALK_FORWARD is "step into new lane" after a swap (then we apply grace). */
    private boolean walkingIntoLaneAfterSwap = false;
    private static final int HOLD_TICKS = 5;           // ~0.25s at 20 TPS (side block)
    private static final int WALK_FORWARD_TICKS = 10;  // ~0.5s at 20 TPS (end of lane)
    private static final int PAUSE_TICKS = 5;         // ~0.25s at 20 TPS
    private static final int POST_SWAP_COOLDOWN_TICKS = 10;
    /** After a lane swap in MelonkingDE, ignore "end of lane" for this long so we don't re-trigger on the walkway. */
    private static final int POST_SWAP_GRACE_TICKS_END_OF_LANE = 50;  // ~2.5s at 20 TPS
    private static final int INITIAL_GRACE_TICKS = 40;
    /** Debug: log WalkableHelper state every this many ticks when in NORMAL (0 = disabled). */
    private static final int DEBUG_INTERVAL_TICKS = 40;

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
        sawPumpkinOrMelonSinceSwap = true;   // so first end-of-lane can trigger; after swap we set false until we see pumpkin again
        walkingIntoLaneAfterSwap = false;

        if (client.player != null) {
            float currentYaw = client.player.getYaw();
            float targetYaw = snapYawToNearest90(currentYaw);
            float targetPitch = 0.0f;

            client.player.setYaw(targetYaw);
            client.player.setPitch(targetPitch);
            client.player.setHeadYaw(targetYaw);
            client.player.setBodyYaw(targetYaw);
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
                    if (useEndOfLaneDetection()) {
                        boolean endOfLane = WalkableHelper.isAtEndOfLaneMelonkingde(client);
                        if (!endOfLane) {
                            sawPumpkinOrMelonSinceSwap = true;
                        }
                        if (sawPumpkinOrMelonSinceSwap && endOfLane) {
                            directionState = DirectionState.WALK_FORWARD;
                            stateTicks = 0;
                            debugState(client, "end of lane → WALK_FORWARD (front not pumpkin/melon)");
                        } else if (DEBUG_INTERVAL_TICKS > 0 && stateTicks > 0 && stateTicks % DEBUG_INTERVAL_TICKS == 0) {
                            debugState(client, "NORMAL check: endOfLaneMelonkingde=" + endOfLane + ", sawPumpkinSinceSwap=" + sawPumpkinOrMelonSinceSwap);
                        }
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
                        // Walk forward into the new lane so "block in front" becomes pumpkin; then we apply grace in WALK_FORWARD completion
                        sawPumpkinOrMelonSinceSwap = false;
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
     * True if there is a solid collision block immediately to the strafing side of the player.
     * Used for nether wart vertical: hit side → hold 0.25s → swap.
     */
    private boolean isStrafingSideBlocked(MinecraftClient client, boolean checkLeftKey) {
        if (client.player == null || client.world == null) return false;

        BlockPos base = client.player.getBlockPos();
        float normalized = normalizeYaw360(client.player.getYaw());

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
        float normalized = normalizeYaw360(client.player.getYaw());

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

    private static float snapYawToNearest90(float yaw) {
        float normalized = normalizeYaw360(yaw);
        float nearest = Math.round(normalized / 90.0f) * 90.0f;
        if (nearest >= 360.0f) nearest = 0.0f;
        return nearest;
    }

    private static float normalizeYaw360(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) normalized += 360.0f;
        return normalized;
    }
}
