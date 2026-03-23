package com.jelly.farmhelperv2.macro;

import net.minecraft.util.math.MathHelper;

/**
 * Composable direction memory for strafing macros.
 *
 * <p>A macro holds one instance of this class and calls {@link #update(float)} in
 * {@code onEnable}, and {@link #flipDirection()} on each lane change.
 *
 * <p>Internally the state tracks a set of <em>yaw groups</em>: each group is a
 * pair of candidate yaws representing the two diagonal offsets for one farm
 * orientation (e.g. {@code {16f, -16f}} for a south-facing, east-west farm).
 * On every {@link #update(float)} call the helper:
 * <ol>
 *   <li>Finds the nearest candidate yaw across all groups.</li>
 *   <li>If the detected group matches the previous run, flips both
 *       {@link #isGoingPrimary()} and the yaw index — mirroring the start
 *       direction so the next run traverses from the opposite end.</li>
 *   <li>Otherwise initialises fresh (first enable, or player turned to a
 *       different farm orientation).</li>
 * </ol>
 *
 * <p>The macro retains any crop-specific logic that depends on the active group
 * (key selection, stuck-detection axis, pitch, etc.); it reads the group via
 * {@link #groupIndex()} and the current snapped yaw via {@link #currentYaw()}.
 */
public final class MacroDirectionState {

    /**
     * Each element is a two-element array {@code {yawA, yawB}} for one orientation.
     * Index 0 → primary direction (goingPrimary = true); index 1 → secondary.
     */
    private final float[][] yawGroups;

    /** Active group index. -1 until the first {@link #update(float)} call. */
    private int groupIndex = -1;

    /** 0 or 1 — which entry within the active group is being used. */
    private int yawIndex = 0;

    /** True when moving in the "primary" (index 0) direction of the active group. */
    private boolean goingPrimary = true;

    /**
     * @param yawGroups Each element must be exactly two floats: the positive-offset
     *                  and negative-offset yaw for that orientation.
     *                  Example for mushroom macro:
     *                  <pre>
     *                  new float[][]{ {16f,-16f}, {164f,-164f}, {-74f,-106f}, {74f,106f} }
     *                  </pre>
     */
    public MacroDirectionState(float[]... yawGroups) {
        if (yawGroups == null || yawGroups.length == 0) {
            throw new IllegalArgumentException("yawGroups must not be empty");
        }
        for (float[] group : yawGroups) {
            if (group == null || group.length != 2) {
                throw new IllegalArgumentException("Each yaw group must have exactly 2 entries");
            }
        }
        this.yawGroups = yawGroups;
    }

    // -------------------------------------------------------------------------
    // Core API
    // -------------------------------------------------------------------------

    /**
     * Call once per {@code onEnable}. Detects the nearest yaw group and either
     * flips direction (same group as last run) or initialises fresh (first enable
     * or orientation change).
     *
     * @param rawYaw the player's current raw yaw (degrees)
     */
    public void update(float rawYaw) {
        int bestGroup = 0, bestIdx = 0;
        float bestDist = Float.MAX_VALUE;
        for (int g = 0; g < yawGroups.length; g++) {
            for (int i = 0; i < yawGroups[g].length; i++) {
                float dist = Math.abs(MathHelper.wrapDegrees(rawYaw - yawGroups[g][i]));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestGroup = g;
                    bestIdx = i;
                }
            }
        }

        if (bestGroup == groupIndex) {
            // Same farm orientation as last run: mirror start direction.
            goingPrimary = !goingPrimary;
            yawIndex = 1 - yawIndex;
        } else {
            // First enable, or player changed orientation: start fresh.
            yawIndex = bestIdx;
            goingPrimary = (yawIndex == 0);
        }
        groupIndex = bestGroup;
    }

    /**
     * Flip the strafe direction. Call this on each lane change so the next
     * lane uses the opposite key (A ↔ D).
     */
    public void flipDirection() {
        goingPrimary = !goingPrimary;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * The snapped yaw to lock to for the current group + index.
     *
     * @throws IllegalStateException if {@link #update(float)} has never been called
     */
    public float currentYaw() {
        checkInitialized();
        return yawGroups[groupIndex][yawIndex];
    }

    /**
     * 0-based index of the active yaw group (i.e., the index into the array
     * passed to the constructor). The macro uses this to look up any
     * group-specific behaviour (pitch, tracked axis, key polarity, etc.).
     *
     * @throws IllegalStateException if {@link #update(float)} has never been called
     */
    public int groupIndex() {
        checkInitialized();
        return groupIndex;
    }

    /**
     * True when the macro should move in the "primary" direction (group index 0).
     * Flipped on every lane change and on same-orientation restarts.
     *
     * @throws IllegalStateException if {@link #update(float)} has never been called
     */
    public boolean isGoingPrimary() {
        checkInitialized();
        return goingPrimary;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void checkInitialized() {
        if (groupIndex < 0) {
            throw new IllegalStateException("MacroDirectionState.update() has not been called yet");
        }
    }
}
