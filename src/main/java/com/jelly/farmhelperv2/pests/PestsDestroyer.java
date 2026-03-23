package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.pathfinder.SimplePathfinder;
import com.jelly.farmhelperv2.skyblock.ScoreboardAreaReader;
import com.jelly.farmhelperv2.util.InputUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * 1.21 Pest Destroyer:
 * - Reads Garden pest count from the scoreboard.
 * - Uses PestDetection to find the nearest pest cluster (armor stand + mob).
 * - Walks toward it using SimplePathfinder and attacks when close.
 */
public final class PestsDestroyer {

    private static ArmorStandEntity currentTarget;
    private static long lastNoPestsMessageTime = 0L;

    private static final long NO_PESTS_MESSAGE_COOLDOWN_MS = 10_000L;
    private static final long RETARGET_COOLDOWN_MS = 2_000L;

    // Vacuum range configuration (mirrors 1.8.9 tiers with a small default).
    private static final double DEFAULT_VACUUM_RANGE = 8.0D;
    private static final double VACUUM_STOP_BUFFER = 1.5D;
    private static final double MAX_VERTICAL_DELTA = 3.0D;
    private static final double RANGE_HYSTERESIS_MULTIPLIER = 1.25D;

    // Fly this many blocks above the pest's Y to avoid landing on crops/terrain.
    private static final double HOVER_HEIGHT = 3.0D;

    // FOLLOW_TRAIL tuning.
    private static final long FOLLOW_TRAIL_LEFT_CLICK_INTERVAL_MS = 800L;
    private static final long FOLLOW_TRAIL_TIMEOUT_MS = 15_000L;
    /**
     * How long to sprint toward the computed heading before stopping to re-click.
     */
    private static final long FOLLOW_TRAIL_MOVE_DURATION_MS = 3_000L;
    /** How long to pause (stand still and click) before moving again. */
    private static final long FOLLOW_TRAIL_PAUSE_DURATION_MS = 1_000L;

    // Stuck detection: flag as stuck if the player moves less than this distance
    // per check.
    private static final long STUCK_CHECK_INTERVAL_MS = 1_500L;
    private static final double STUCK_DISTANCE_THRESHOLD = 0.3D;

    // If ALIGN_Y cannot reach hover altitude within this window, the Y path is
    // obstructed.
    private static final long ALIGN_Y_TIMEOUT_MS = 5_000L;

    private enum State {
        IDLE,
        SEARCH_TARGET,
        /**
         * No entity locked yet. Left-click the vacuum periodically to emit its particle
         * trail cue,
         * navigate toward the last known pest position, and keep scanning for a
         * lockable entity.
         * Detects STUCK and logs it verbosely.
         */
        FOLLOW_TRAIL,
        /** Move toward target horizontally only; Y is not adjusted yet. */
        MOVE_TO_TARGET,
        /**
         * XZ is correct; stop and adjust Y to hover HOVER_HEIGHT above the target.
         * If Y cannot be reached within ALIGN_Y_TIMEOUT_MS, transitions to
         * FOUND_PEST_STUCK.
         */
        ALIGN_Y,
        ATTACK_TARGET,
        /**
         * XZ was correct but Y could not be aligned — something is blocking vertical
         * movement.
         * Holds position and logs "found pest: stuck". Recovery logic TBD.
         */
        FOUND_PEST_STUCK,
        RECOVER
    }

    private static State state = State.IDLE;
    private static long lastStateChangeTimeMs = 0L;
    private static long lastAttackTimeMs = 0L;
    private static final long ATTACK_COOLDOWN_MS = 500L;
    private static int vacuumHotbarSlot = -1;
    private static double currentVacuumRange = -1.0D;

    private static long lastDebugMessageTimeMs = 0L;
    private static final long DEBUG_MESSAGE_COOLDOWN_MS = 500L;

    // Last observed pest position; used by FOLLOW_TRAIL when no entity is locked.
    private static Vec3d lastKnownPestPos = null;

    // FOLLOW_TRAIL per-run state.
    private static Vec3d stuckCheckPos = null;
    private static long stuckCheckTimeMs = 0L;
    private static long lastFollowClickTimeMs = 0L;
    /**
     * Timestamp of when the current FOLLOW_TRAIL run began; drives the move/pause
     * cycle.
     */
    private static long followTrailPhaseStartMs = 0L;

    private PestsDestroyer() {
    }

    public static void stop(MinecraftClient client) {
        SimplePathfinder.stop(client);
        currentTarget = null;
        state = State.IDLE;
        lastStateChangeTimeMs = System.currentTimeMillis();
        vacuumHotbarSlot = -1;
        currentVacuumRange = -1.0D;
        lastKnownPestPos = null;
        resetFollowTrailState();
        VacuumParticleTracker.setActive(false);
        VacuumParticleTracker.reset();
        InputUtils.resetAll(client, true, true);
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;
        long now = System.currentTimeMillis();

        int pestCount = ScoreboardPestReader.getGardenPestCount(client);
        int tabAlive = TablistPestReader.getAlivePests(client);
        int effectivePests = Math.max(pestCount, tabAlive);
        if (effectivePests <= 0) {
            handleNoPests(client, player, now);
            return;
        }

        // Ensure we have a vacuum available in the hotbar before doing anything else.
        if (vacuumHotbarSlot == -1) {
            vacuumHotbarSlot = findVacuumSlot(player);
            if (vacuumHotbarSlot == -1) {
                handleNoVacuum(client, player, now);
                return;
            }
            ItemStack vacuumStack = player.getInventory().getStack(vacuumHotbarSlot);
            currentVacuumRange = getVacuumRangeForStack(vacuumStack);
        }

        // Drive a small explicit state machine for clarity.
        switch (state) {
            case IDLE:
                handleIdle(client, now);
                break;
            case SEARCH_TARGET:
                handleSearchTarget(client, now);
                break;
            case FOLLOW_TRAIL:
                handleFollowTrail(client, player, options, now);
                break;
            case MOVE_TO_TARGET:
                handleMoveToTarget(client, player, options, now);
                break;
            case ALIGN_Y:
                handleAlignY(client, player, now);
                break;
            case ATTACK_TARGET:
                handleAttackTarget(client, player, options, now);
                break;
            case FOUND_PEST_STUCK:
                handleFoundPestStuck(client, player, options, now);
                break;
            case RECOVER:
                handleRecover(client, now);
                break;
        }
    }

    private static void handleNoPests(MinecraftClient client, ClientPlayerEntity player, long now) {
        SimplePathfinder.stop(client);
        currentTarget = null;
        state = State.IDLE;
        lastStateChangeTimeMs = now;
        InputUtils.resetAll(client, true, true);

        if (now - lastNoPestsMessageTime >= NO_PESTS_MESSAGE_COOLDOWN_MS) {
            lastNoPestsMessageTime = now;
            player.sendMessage(Text.literal("[MTEU] No pests detected on the Garden."), false);
        }
    }

    private static void handleNoVacuum(MinecraftClient client, ClientPlayerEntity player, long now) {
        SimplePathfinder.stop(client);
        currentTarget = null;
        state = State.IDLE;
        lastStateChangeTimeMs = now;
        InputUtils.resetAll(client, true, true);
        if (player != null) {
            player.sendMessage(Text.literal("[MTEU] No vacuum detected in hotbar!"), false);
        }
        // Do not spam the message; rely on the existing cooldown for no-pests messages.
    }

    private static void handleIdle(MinecraftClient client, long now) {
        // We know pestCount > 0 at this point; begin searching.
        transitionTo(State.SEARCH_TARGET, now);
        handleSearchTarget(client, now);
    }

    private static void handleSearchTarget(MinecraftClient client, long now) {
        if (!ensureTargetValid(client, now)) {
            // No entity in detection range yet; use the vacuum trail to navigate toward
            // pests.
            if (now - lastStateChangeTimeMs > RETARGET_COOLDOWN_MS) {
                resetFollowTrailState();
                transitionTo(State.FOLLOW_TRAIL, now);
            }
            return;
        }

        // Approach XZ first; Y is aligned only once we are in the correct horizontal
        // position.
        transitionTo(State.MOVE_TO_TARGET, now);
    }

    private static void handleFollowTrail(MinecraftClient client, ClientPlayerEntity player, GameOptions options,
            long now) {
        // Activate particle tracking as long as we are in this state.
        VacuumParticleTracker.setActive(true);
        VacuumParticleTracker.purgeExpired();

        // Initialise phase clock on first tick of this FOLLOW_TRAIL run.
        if (followTrailPhaseStartMs == 0L) {
            followTrailPhaseStartMs = now;
        }

        // ── Lock-on check ─────────────────────────────────────────────────────
        Optional<ArmorStandEntity> nearest = PestDetection.findNearestPest(client);
        if (nearest.isPresent()) {
            currentTarget = nearest.get();
            FarmHelperFabric.LOGGER.info("Pest Destroyer: FOLLOW_TRAIL locked onto pest entity");
            resetFollowTrailState();
            transitionTo(State.MOVE_TO_TARGET, now);
            return;
        }

        if (now - lastStateChangeTimeMs > FOLLOW_TRAIL_TIMEOUT_MS) {
            if (ModConfig.isVerboseLogging()) {
                player.sendMessage(Text.literal("[MTEU] FOLLOW_TRAIL: timeout — recovering"), false);
            }
            resetFollowTrailState();
            transitionTo(State.RECOVER, now);
            return;
        }

        // ── Move / pause phase ────────────────────────────────────────────────
        long cycleLen = FOLLOW_TRAIL_PAUSE_DURATION_MS + FOLLOW_TRAIL_MOVE_DURATION_MS;
        long phase = (now - followTrailPhaseStartMs) % cycleLen;
        boolean paused = phase < FOLLOW_TRAIL_PAUSE_DURATION_MS;

        // ── Equip vacuum ──────────────────────────────────────────────────────
        if (vacuumHotbarSlot >= 0 && vacuumHotbarSlot <= 8
                && player.getInventory().getSelectedSlot() != vacuumHotbarSlot) {
            player.getInventory().setSelectedSlot(vacuumHotbarSlot);
        }

        // ── Pause phase: stand still, left-click vacuum ───────────────────────
        if (paused) {
            SimplePathfinder.stop(client);

            boolean clickThisTick = (now - lastFollowClickTimeMs >= FOLLOW_TRAIL_LEFT_CLICK_INTERVAL_MS);
            if (clickThisTick) {
                lastFollowClickTimeMs = now;
                options.sneakKey.setPressed(false);
                options.useKey.setPressed(false);
                options.attackKey.setPressed(true);
                player.swingHand(Hand.MAIN_HAND);
            } else {
                options.attackKey.setPressed(false);
            }

            if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
                lastDebugMessageTimeMs = now;
                player.sendMessage(Text.literal(String.format(
                        "[MTEU] FOLLOW_TRAIL: paused — clicking vacuum | trail obs=%d (%s)",
                        VacuumParticleTracker.getObservationCount(),
                        VacuumParticleTracker.getDebugSummary())), false);
            }
            return;
        }

        // ── Move phase ────────────────────────────────────────────────────────
        options.attackKey.setPressed(false);

        // Stuck detection (only relevant while moving).
        boolean shouldJump = false;
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (stuckCheckPos == null) {
            stuckCheckPos = currentPos;
            stuckCheckTimeMs = now;
        } else if (now - stuckCheckTimeMs >= STUCK_CHECK_INTERVAL_MS) {
            double moved = stuckCheckPos.distanceTo(currentPos);
            if (moved < STUCK_DISTANCE_THRESHOLD) {
                shouldJump = true;
                if (ModConfig.isVerboseLogging()) {
                    player.sendMessage(Text.literal(String.format(
                            "[MTEU] FOLLOW_TRAIL: STUCK (moved=%.2f in %.1fs) — jumping to clear",
                            moved, STUCK_CHECK_INTERVAL_MS / 1000.0)), false);
                }
                FarmHelperFabric.LOGGER.warn("Pest Destroyer: FOLLOW_TRAIL STUCK (moved {} blocks in {}ms)",
                        String.format("%.2f", moved), STUCK_CHECK_INTERVAL_MS);
            } else if (ModConfig.isVerboseLogging()) {
                player.sendMessage(Text.literal(String.format(
                        "[MTEU] FOLLOW_TRAIL: moving %.2f blocks/%.1fs",
                        moved, STUCK_CHECK_INTERVAL_MS / 1000.0)), false);
            }
            stuckCheckPos = currentPos;
            stuckCheckTimeMs = now;
        }

        // ── Priority 0: vacuum particle trail direction (real-time, highest) ──
        Vec3d trailDir = VacuumParticleTracker.getTrailDirection(player.getX(), player.getZ());
        if (trailDir != null) {
            // atan2(-dx, dz) is the standard MC yaw formula (0°=south, -90°=east).
            float trailYaw = (float) Math.toDegrees(Math.atan2(-trailDir.x, trailDir.z));
            if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
                lastDebugMessageTimeMs = now;
                player.sendMessage(Text.literal(String.format(
                        "[MTEU] FOLLOW_TRAIL: particle trail yaw=%.0f° (n=%d obs)",
                        trailYaw, VacuumParticleTracker.getObservationCount())), false);
            }
            player.setYaw(trailYaw);
            player.setHeadYaw(trailYaw);
            player.setBodyYaw(trailYaw);
            options.forwardKey.setPressed(true);
            options.sprintKey.setPressed(true);
            options.backKey.setPressed(false);
            options.leftKey.setPressed(false);
            options.rightKey.setPressed(false);
            options.sneakKey.setPressed(false);
            if (shouldJump)
                options.jumpKey.setPressed(true);
            return;
        }

        // Priority 1: navigate toward last known entity position.
        if (lastKnownPestPos != null) {
            double dx = lastKnownPestPos.x - player.getX();
            double dz = lastKnownPestPos.z - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) > 2.0D) {
                if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
                    lastDebugMessageTimeMs = now;
                    player.sendMessage(Text.literal("[MTEU] FOLLOW_TRAIL: moving toward last known pest pos"), false);
                }
                SimplePathfinder.moveTowardsXZOnly(client, lastKnownPestPos);
                if (shouldJump)
                    options.jumpKey.setPressed(true);
                return;
            }
        }

        // Priority 2: use the Garden plot grid to determine heading.
        int currentPlot = ScoreboardAreaReader.getCurrentPlot(client);
        List<Integer> pestPlots = TablistPestReader.getInfestedPlots(client);
        int targetPlot = GardenPlotGrid.closestPestPlot(currentPlot, pestPlots);
        float plotYaw = GardenPlotGrid.getApproximateYaw(currentPlot, targetPlot);

        if (!Float.isNaN(plotYaw)) {
            if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
                lastDebugMessageTimeMs = now;
                player.sendMessage(Text.literal(String.format(
                        "[MTEU] FOLLOW_TRAIL: plot %d → %d (%s) yaw=%.0f°",
                        currentPlot, targetPlot,
                        GardenPlotGrid.getDirectionLabel(currentPlot, targetPlot),
                        plotYaw)), false);
            }
            player.setYaw(plotYaw);
            player.setHeadYaw(plotYaw);
            player.setBodyYaw(plotYaw);
            options.forwardKey.setPressed(true);
            options.sprintKey.setPressed(true);
            options.backKey.setPressed(false);
            options.leftKey.setPressed(false);
            options.rightKey.setPressed(false);
            options.sneakKey.setPressed(false);
            if (shouldJump)
                options.jumpKey.setPressed(true);
            return;
        }

        // Priority 3: no usable direction — diagnose each data source.
        SimplePathfinder.stop(client);
        if (shouldJump)
            options.jumpKey.setPressed(true);
        if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
            lastDebugMessageTimeMs = now;

            // Particle trail diagnosis
            String trailStr = "particle trail: " + VacuumParticleTracker.getDebugSummary()
                    + " (need >=" + 4 + " to navigate)";

            // Scoreboard diagnosis
            String plotStr = currentPlot == -1
                    ? "scoreboard plot: not found (check sidebar has 'Plot - N')"
                    : "scoreboard plot: " + currentPlot;

            // Tablist diagnosis
            String tabStr = pestPlots.isEmpty()
                    ? "tablist plots: not found | " + TablistPestReader.getDebugSummary(client)
                    : "tablist plots: " + pestPlots;

            // Grid diagnosis (only if both sources have data)
            String gridStr = (currentPlot != -1 && !pestPlots.isEmpty())
                    ? "grid target: "
                            + (targetPlot == -1 ? "unresolved (plot not in grid?)" : String.valueOf(targetPlot))
                    : "";

            player.sendMessage(Text.literal("[MTEU] FOLLOW_TRAIL no direction:"), false);
            player.sendMessage(Text.literal("  " + trailStr), false);
            player.sendMessage(Text.literal("  " + plotStr), false);
            player.sendMessage(Text.literal("  " + tabStr), false);
            if (!gridStr.isEmpty()) {
                player.sendMessage(Text.literal("  " + gridStr), false);
            }
        }
    }

    private static void resetFollowTrailState() {
        stuckCheckPos = null;
        stuckCheckTimeMs = 0L;
        lastFollowClickTimeMs = 0L;
        followTrailPhaseStartMs = 0L;
        VacuumParticleTracker.reset();
    }

    private static void handleAlignY(MinecraftClient client, ClientPlayerEntity player, long now) {
        if (!isCurrentTargetUsable()) {
            transitionTo(State.FOLLOW_TRAIL, now);
            return;
        }

        // Timeout: XZ is correct but Y isn't moving — something is physically blocking
        // us.
        if (now - lastStateChangeTimeMs > ALIGN_Y_TIMEOUT_MS) {
            SimplePathfinder.stop(client);
            FarmHelperFabric.LOGGER.warn("Pest Destroyer: ALIGN_Y timed out — vertical path obstructed");
            transitionTo(State.FOUND_PEST_STUCK, now);
            return;
        }

        Vec3d targetPos = getTargetPosition();
        lastKnownPestPos = targetPos;
        double hoverVert = hoverVerticalDelta(player, targetPos);

        if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
            lastDebugMessageTimeMs = now;
            player.sendMessage(Text.literal(String.format(
                    "[MTEU] ALIGN_Y: hoverVert=%.1f (target Y %.1f + %.1f = %.1f)",
                    hoverVert, targetPos.y, HOVER_HEIGHT, targetPos.y + HOVER_HEIGHT)), false);
        }

        if (hoverVert <= SimplePathfinder.VERT_ADJUST_THRESHOLD) {
            SimplePathfinder.stop(client);
            transitionTo(State.ATTACK_TARGET, now);
            return;
        }

        SimplePathfinder.alignY(client, targetPos.y + HOVER_HEIGHT);
    }

    private static void handleMoveToTarget(MinecraftClient client, ClientPlayerEntity player, GameOptions options,
            long now) {
        if (!isCurrentTargetUsable()) {
            transitionTo(State.FOLLOW_TRAIL, now);
            return;
        }

        Vec3d targetPos = getTargetPosition();
        lastKnownPestPos = targetPos;
        double horiz = horizontalDistance(player, targetPos);

        double stopRange = Math.max(getMaxHorizontalRange() - VACUUM_STOP_BUFFER, 1.5D);

        if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
            lastDebugMessageTimeMs = now;
            double dist3d = new Vec3d(player.getX(), player.getY(), player.getZ()).distanceTo(targetPos);
            String msg = String.format("[MTEU] MOVE_XZ: horiz=%.1f (3d=%.1f) | stopRange=%.1f | %s",
                    horiz, dist3d, stopRange,
                    horiz <= stopRange ? "in position, aligning Y" : "approaching");
            player.sendMessage(Text.literal(msg), false);
        }

        if (horiz <= stopRange) {
            SimplePathfinder.stop(client);
            transitionTo(State.ALIGN_Y, now);
            return;
        }

        options.attackKey.setPressed(false);
        options.sneakKey.setPressed(false);
        SimplePathfinder.moveTowardsXZOnly(client, targetPos);
    }

    private static void handleAttackTarget(MinecraftClient client, ClientPlayerEntity player, GameOptions options,
            long now) {
        if (!isCurrentTargetUsable()) {
            releaseActionKeys(options);
            transitionTo(State.FOLLOW_TRAIL, now);
            return;
        }

        Vec3d targetPos = getTargetPosition();
        lastKnownPestPos = targetPos;
        double horiz = horizontalDistance(player, targetPos);
        double hoverVert = hoverVerticalDelta(player, targetPos);

        double maxHorizRange = getMaxHorizontalRange();
        double maxHorizHyst = maxHorizRange * RANGE_HYSTERESIS_MULTIPLIER;

        if (horiz > maxHorizHyst || hoverVert > MAX_VERTICAL_DELTA * RANGE_HYSTERESIS_MULTIPLIER) {
            // Drifted well out of range; release everything and go back to approaching.
            // MOVE_TO_TARGET will self-route to ALIGN_Y if hover Y has also drifted.
            releaseActionKeys(options);
            transitionTo(State.MOVE_TO_TARGET, now);
            return;
        }

        // On the inner edge of the hysteresis band — hold position but don't act yet.
        if (horiz > maxHorizRange || hoverVert > MAX_VERTICAL_DELTA) {
            releaseActionKeys(options);
            return;
        }

        if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
            lastDebugMessageTimeMs = now;
            player.sendMessage(Text.literal(String.format(
                    "[MTEU] Within range! horiz=%.1f hoverVert=%.1f — holding right click", horiz, hoverVert)), false);
        }

        SimplePathfinder.stop(client);

        if (vacuumHotbarSlot >= 0 && vacuumHotbarSlot <= 8
                && player.getInventory().getSelectedSlot() != vacuumHotbarSlot) {
            player.getInventory().setSelectedSlot(vacuumHotbarSlot);
        }

        if (now - lastAttackTimeMs < ATTACK_COOLDOWN_MS) {
            releaseActionKeys(options);
            return;
        }

        lastAttackTimeMs = now;
        // Sneak and left-click must never be held here: sneak+right-click can open
        // GUIs,
        // and sneak+left-click definitely does. Only hold right-click (use key) for the
        // vacuum.
        releaseActionKeys(options);
        options.useKey.setPressed(true);
    }

    /**
     * Releases all action keys that must not be held during a vacuum interaction.
     */
    private static void releaseActionKeys(GameOptions options) {
        options.sneakKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
    }

    private static void handleFoundPestStuck(MinecraftClient client, ClientPlayerEntity player, GameOptions options,
            long now) {
        SimplePathfinder.stop(client);
        releaseActionKeys(options);

        if (ModConfig.isVerboseLogging() && now - lastDebugMessageTimeMs >= DEBUG_MESSAGE_COOLDOWN_MS) {
            lastDebugMessageTimeMs = now;
            player.sendMessage(Text.literal("[MTEU] found pest: stuck"), false);
        }

        // Hold position briefly so the state is observable, then fall back to recovery.
        // Recovery logic (e.g. pathfind around the obstacle) will be added here.
        if (now - lastStateChangeTimeMs >= RETARGET_COOLDOWN_MS) {
            transitionTo(State.RECOVER, now);
        }
    }

    private static void handleRecover(MinecraftClient client, long now) {
        SimplePathfinder.stop(client);
        InputUtils.resetAll(client, true, true);
        // Give things a brief moment to settle, then re-search.
        if (now - lastStateChangeTimeMs >= RETARGET_COOLDOWN_MS) {
            currentTarget = null;
            transitionTo(State.SEARCH_TARGET, now);
        }
    }

    private static boolean ensureTargetValid(MinecraftClient client, long now) {
        if (isCurrentTargetUsable()) {
            return true;
        }

        Optional<ArmorStandEntity> nearest = PestDetection.findNearestPest(client);
        currentTarget = nearest.orElse(null);

        if (currentTarget != null) {
            FarmHelperFabric.LOGGER.info("Pest Destroyer: targeting pest {}", currentTarget.getName().getString());
            return true;
        }

        // No target yet.
        return false;
    }

    private static boolean isCurrentTargetUsable() {
        return currentTarget != null && !currentTarget.isRemoved() && !currentTarget.isDead();
    }

    private static Vec3d getTargetPosition() {
        return new Vec3d(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ());
    }

    private static double horizontalDistance(ClientPlayerEntity player, Vec3d targetPos) {
        double dx = player.getX() - targetPos.x;
        double dz = player.getZ() - targetPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Distance from the player's current Y to the hover altitude (pest Y +
     * HOVER_HEIGHT).
     */
    private static double hoverVerticalDelta(ClientPlayerEntity player, Vec3d targetPos) {
        return Math.abs(player.getY() - (targetPos.y + HOVER_HEIGHT));
    }

    /**
     * Maximum horizontal reach of the vacuum while hovering HOVER_HEIGHT above the
     * pest.
     * Derived from: sqrt(range² − HOVER_HEIGHT²) so that the 3-D distance equals
     * the vacuum range.
     */
    private static double getMaxHorizontalRange() {
        double range = getEffectiveVacuumRange();
        if (range <= HOVER_HEIGHT) {
            return 1.5D;
        }
        return Math.sqrt(range * range - HOVER_HEIGHT * HOVER_HEIGHT);
    }

    private static void transitionTo(State newState, long now) {
        if (state == newState) {
            return;
        }
        state = newState;
        lastStateChangeTimeMs = now;
    }

    private static int findVacuumSlot(ClientPlayerEntity player) {
        if (player == null)
            return -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String name = stack.getName().getString();
            if (name != null && name.contains("Vacuum")) {
                return slot;
            }
        }
        return -1;
    }

    private static double getVacuumRangeForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_VACUUM_RANGE;
        }
        String name = stack.getName().getString();
        if (name == null) {
            return DEFAULT_VACUUM_RANGE;
        }

        if (name.contains("Skymart Vacuum")) {
            return 5.0D;
        }
        if (name.contains("Turbo Vacuum")) {
            return 7.5D;
        }
        if (name.contains("Hyper Vacuum")) {
            return 10.0D;
        }
        if (name.contains("InfiniVacuum\u2122 Hooverius")) {
            return 15.0D;
        }
        if (name.contains("InfiniVacuum")) {
            return 12.5D;
        }

        return DEFAULT_VACUUM_RANGE;
    }

    private static double getEffectiveVacuumRange() {
        return currentVacuumRange > 0.0D ? currentVacuumRange : DEFAULT_VACUUM_RANGE;
    }
}
