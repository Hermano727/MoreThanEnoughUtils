package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.MovementUtils;
import net.minecraft.client.MinecraftClient;

/**
 * S-Shape Mushroom macro (Fabric 1.21 MTEU).
 *
 * <p>Four face modes, auto-detected from player facing at enable time:
 * <ul>
 *   <li><b>SOUTH</b> (yaw ≈ ±16°):    east/west farm — D=west, A=east.</li>
 *   <li><b>NORTH</b> (yaw ≈ ±164°):   east/west farm — A=west, D=east.</li>
 *   <li><b>EAST</b>  (yaw ≈ −74/−106°): north/south farm — D=south, A=north.</li>
 *   <li><b>WEST</b>  (yaw ≈ +74/+106°): north/south farm — A=south, D=north.</li>
 * </ul>
 *
 * <p>Pitch is fixed at {@value #PITCH_EW}° for SOUTH/NORTH farms and
 * {@value #PITCH_NS}° for EAST/WEST farms. Yaw is snapped once on enable
 * and held constant — no rotation occurs during lane changes.
 * Lane changes swap the strafe key (A ↔ D) and hold S for
 * {@value #BACKUP_TICKS} ticks between lanes.
 *
 * <p>On restart with the same farm orientation, {@link MacroDirectionState}
 * automatically mirrors the start direction so the macro traverses from the
 * opposite end of the farm.
 *
 * <p>Lane change trigger: position-delta on the relevant axis (X for
 * SOUTH/NORTH, Z for EAST/WEST) falls below {@value #STUCK_THRESHOLD}
 * blocks/tick for {@value #STUCK_TICKS} consecutive ticks.
 */
public class SShapeMushroomMacro implements Macro {

    /** Pitch for SOUTH/NORTH (east-west) farms. */
    static final float PITCH_EW = 6.7f;

    /** Pitch for EAST/WEST (north-south) farms. */
    static final float PITCH_NS = 6.7f;

    // Yaw pairs per farm orientation — passed to MacroDirectionState.
    // Group 0 = SOUTH, 1 = NORTH, 2 = EAST, 3 = WEST.
    private static final float[][] YAW_GROUPS = {
        {  16f,  -16f },   // 0 SOUTH: east-west farm
        { 164f, -164f },   // 1 NORTH: east-west farm
        { -74f, -106f },   // 2 EAST:  north-south farm
        {  74f,  106f },   // 3 WEST:  north-south farm
    };

    private static final FaceMode[] GROUP_TO_FACE = {
        FaceMode.SOUTH, FaceMode.NORTH, FaceMode.EAST, FaceMode.WEST
    };

    /** ~1 second at 20 TPS — hold S between lanes. */
    private static final int BACKUP_TICKS = 20;

    /** Grace ticks after a lane change before stuck detection re-arms. */
    private static final int POST_SWAP_GRACE = 15;

    /** Grace ticks after enable to let the player start moving. */
    private static final int INITIAL_GRACE = 20;

    /**
     * Minimum per-tick displacement (blocks) on the tracked axis considered "moving".
     * Normal walk-strafe ≈ 0.215 blocks/tick; threshold ≈ 23 % of that.
     */
    private static final double STUCK_THRESHOLD = 0.05;

    /** Consecutive sub-threshold ticks required to trigger a lane change. */
    private static final int STUCK_TICKS = 4;

    private enum State { MOVING, BACKING_UP }
    private enum FaceMode { SOUTH, NORTH, EAST, WEST }

    private State    state    = State.MOVING;
    private FaceMode faceMode = FaceMode.SOUTH;

    /**
     * Owns face-group detection, yaw-index selection, and the restart direction-flip.
     * The macro reads {@link MacroDirectionState#currentYaw()},
     * {@link MacroDirectionState#isGoingPrimary()}, and calls
     * {@link MacroDirectionState#flipDirection()} on lane change.
     */
    private final MacroDirectionState direction = new MacroDirectionState(YAW_GROUPS);

    private int    stateTicks     = 0;
    private int    graceTicks     = 0;
    private double lastTrackedPos = 0.0;
    private int    stuckTicks     = 0;

    // -------------------------------------------------------------------------
    // Macro interface
    // -------------------------------------------------------------------------

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player == null) return;

        direction.update(client.player.getYaw());
        faceMode = GROUP_TO_FACE[direction.groupIndex()];

        state         = State.MOVING;
        stateTicks    = 0;
        graceTicks    = INITIAL_GRACE;
        lastTrackedPos = trackedPos(client);
        stuckTicks    = 0;
        applyRotation(client);

        if (ModConfig.isVerboseLogging()) {
            client.player.sendMessage(
                    ChatUtils.success("Mushroom ENABLED (face=" + faceMode
                            + " yaw=" + direction.currentYaw()
                            + " dir=" + dirName()
                            + " key=" + (isPressRightKey() ? "D" : "A") + ")"),
                    false);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        switch (state) {
            case MOVING: {
                applyRotation(client);
                applyMovingKeys(client);
                stateTicks++;

                double pos   = trackedPos(client);
                double delta = Math.abs(pos - lastTrackedPos);
                lastTrackedPos = pos;

                if (graceTicks > 0) {
                    graceTicks--;
                    stuckTicks = 0;
                } else if (delta < STUCK_THRESHOLD) {
                    stuckTicks++;
                    if (stuckTicks >= STUCK_TICKS) {
                        startLaneChange(client);
                    }
                } else {
                    stuckTicks = 0;
                }
                break;
            }
            case BACKING_UP: {
                applyRotation(client);
                applyBackupKeys(client);
                stateTicks++;

                if (stateTicks >= BACKUP_TICKS) {
                    graceTicks     = POST_SWAP_GRACE;
                    stuckTicks     = 0;
                    lastTrackedPos = trackedPos(client);
                    state          = State.MOVING;
                    stateTicks     = 0;
                    if (ModConfig.isVerboseLogging()) {
                        client.player.sendMessage(
                                ChatUtils.info("Mushroom: lane changed → yaw=" + direction.currentYaw()
                                        + " dir=" + dirName()
                                        + " key=" + (isPressRightKey() ? "D" : "A")),
                                false);
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        MovementUtils.stopAll(client.options);
        if (client.player != null) {
            client.player.sendMessage(ChatUtils.warning("Mushroom DISABLED"), false);
        }
    }

    // -------------------------------------------------------------------------
    // Lane change — key swap only, no yaw rotation
    // -------------------------------------------------------------------------

    private void startLaneChange(MinecraftClient client) {
        stuckTicks = 0;
        direction.flipDirection();  // swap A ↔ D; yaw stays the same
        state      = State.BACKING_UP;
        stateTicks = 0;

        if (ModConfig.isVerboseLogging()) {
            client.player.sendMessage(
                    ChatUtils.info("Mushroom: wall → backing up → key="
                            + (isPressRightKey() ? "D" : "A")),
                    false);
        }
    }

    // -------------------------------------------------------------------------
    // Key application
    // -------------------------------------------------------------------------

    private void applyMovingKeys(MinecraftClient client) {
        boolean right = isPressRightKey();
        client.options.attackKey.setPressed(true);
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.rightKey.setPressed(right);
        client.options.leftKey.setPressed(!right);
    }

    private void applyBackupKeys(MinecraftClient client) {
        client.options.backKey.setPressed(true);
        client.options.forwardKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.attackKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
    }

    /**
     * Returns {@code true} when D (strafe right) should be pressed, {@code false} for A.
     *
     * <pre>
     * SOUTH + goingPrimary  → D  (yaw≈+16, D moves west)
     * SOUTH + !goingPrimary → A  (yaw≈+16, A moves east)
     * NORTH + goingPrimary  → A  (yaw≈+164, A moves west)
     * NORTH + !goingPrimary → D  (yaw≈+164, D moves east)
     * EAST  + goingPrimary  → D  (yaw≈-74,  D moves south)
     * EAST  + !goingPrimary → A  (yaw≈-74,  A moves north)
     * WEST  + goingPrimary  → A  (yaw≈+74,  A moves south)
     * WEST  + !goingPrimary → D  (yaw≈+74,  D moves north)
     * </pre>
     *
     * Pattern: D when facing SOUTH or EAST (primary faces) AND goingPrimary,
     * or facing NORTH or WEST (secondary faces) AND !goingPrimary.
     */
    private boolean isPressRightKey() {
        boolean primaryFace = (faceMode == FaceMode.SOUTH || faceMode == FaceMode.EAST);
        return primaryFace == direction.isGoingPrimary();
    }

    // -------------------------------------------------------------------------
    // Rotation / position helpers
    // -------------------------------------------------------------------------

    private void applyRotation(MinecraftClient client) {
        MovementUtils.applyRotation(client, direction.currentYaw(), getCurrentPitch());
    }

    private float getCurrentPitch() {
        return (faceMode == FaceMode.EAST || faceMode == FaceMode.WEST) ? PITCH_NS : PITCH_EW;
    }

    /** Axis to track for wall detection: X for east/west farms, Z for north/south farms. */
    private double trackedPos(MinecraftClient client) {
        return (faceMode == FaceMode.EAST || faceMode == FaceMode.WEST)
                ? client.player.getZ()
                : client.player.getX();
    }

    private String dirName() {
        return switch (faceMode) {
            case SOUTH, NORTH -> direction.isGoingPrimary() ? "WEST"  : "EAST";
            case EAST,  WEST  -> direction.isGoingPrimary() ? "SOUTH" : "NORTH";
        };
    }
}
