package com.jelly.farmhelperv2.pests;

import java.util.List;

/**
 * Encodes the Hypixel Skyblock Garden 5×5 plot layout and provides helpers for
 * computing the rough world-space heading from one plot toward another.
 *
 * Grid (row 0 = north, col 0 = west, -1 = barn):
 *
 *   Col:  0    1    2    3    4
 *  Row 0: 21   13    9   14   22
 *  Row 1: 15    5    1    6   16
 *  Row 2: 10    2  barn   3   11
 *  Row 3: 17    7    4    8   18
 *  Row 4: 23   19   12   20   24
 *
 * World-space assumption (calibrate WORLD_YAW_OFFSET_DEG if wrong):
 *   Increasing column → East  (+X)
 *   Increasing row    → South (+Z)
 *
 * Minecraft yaw convention: 0° = South, −90° = East, ±180° = North, 90° = West.
 */
public final class GardenPlotGrid {

    /** Sentinel value used where the barn occupies the grid cell. */
    public static final int BARN_PLOT_ID = 0;

    /**
     * Rotation offset (degrees) added to every computed yaw.
     * Adjust if the grid's "north" (row 0) does not match Minecraft north in this garden instance.
     */
    public static float WORLD_YAW_OFFSET_DEG = 0f;

    // 5×5 grid; 0 = barn, valid plot numbers are 1–24.
    private static final int[][] GRID = {
        { 21, 13,  9, 14, 22 },
        { 15,  5,  1,  6, 16 },
        { 10,  2,  0,  3, 11 },   // 0 = barn
        { 17,  7,  4,  8, 18 },
        { 23, 19, 12, 20, 24 },
    };

    private GardenPlotGrid() {
    }

    // -------------------------------------------------------------------------
    // Plot position queries
    // -------------------------------------------------------------------------

    /**
     * Returns {row, col} for the given plot number (1–24), or for the barn (pass
     * {@link #BARN_PLOT_ID}). Returns {@code null} if the plot is not in the grid.
     */
    public static int[] getPlotPosition(int plotNumber) {
        for (int r = 0; r < GRID.length; r++) {
            for (int c = 0; c < GRID[r].length; c++) {
                if (GRID[r][c] == plotNumber) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /** Returns {@code true} if the plot number is defined in the grid. */
    public static boolean isValidPlot(int plotNumber) {
        return getPlotPosition(plotNumber) != null;
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Computes the approximate Minecraft yaw (degrees) a player should face when
     * walking from {@code fromPlot} toward {@code toPlot}.
     *
     * <p>Returns {@link Float#NaN} when either plot is unrecognised or the plots
     * are the same.</p>
     */
    public static float getApproximateYaw(int fromPlot, int toPlot) {
        int[] from = getPlotPosition(fromPlot);
        int[] to   = getPlotPosition(toPlot);
        if (from == null || to == null) return Float.NaN;

        int dCol = to[1] - from[1]; // positive = east  (+X)
        int dRow = to[0] - from[0]; // positive = south (+Z)
        if (dCol == 0 && dRow == 0) return Float.NaN;

        // Minecraft: yaw = atan2(−dX, dZ), with dX = dCol, dZ = dRow
        float yaw = (float) (Math.atan2(-dCol, dRow) * (180.0 / Math.PI));
        return normalizeYaw(yaw + WORLD_YAW_OFFSET_DEG);
    }

    /**
     * From a list of infested plot numbers, returns the one closest to
     * {@code currentPlot} by Manhattan distance on the grid.
     *
     * @return the closest pest plot, or {@code -1} if none could be found.
     */
    public static int closestPestPlot(int currentPlot, List<Integer> pestPlots) {
        int[] cur = getPlotPosition(currentPlot);
        if (cur == null || pestPlots == null || pestPlots.isEmpty()) return -1;

        int bestPlot  = -1;
        int bestDist  = Integer.MAX_VALUE;

        for (int p : pestPlots) {
            int[] pos = getPlotPosition(p);
            if (pos == null) continue;
            int dist = Math.abs(pos[0] - cur[0]) + Math.abs(pos[1] - cur[1]);
            if (dist < bestDist) {
                bestDist = dist;
                bestPlot = p;
            }
        }
        return bestPlot;
    }

    /**
     * Returns a human-readable description of the direction from {@code fromPlot}
     * to {@code toPlot} (e.g. "north-west").  Useful for verbose debug messages.
     */
    public static String getDirectionLabel(int fromPlot, int toPlot) {
        int[] from = getPlotPosition(fromPlot);
        int[] to   = getPlotPosition(toPlot);
        if (from == null || to == null) return "unknown";

        int dCol = to[1] - from[1];
        int dRow = to[0] - from[0];

        String ns = dRow < 0 ? "north" : dRow > 0 ? "south" : "";
        String ew = dCol > 0 ? "east"  : dCol < 0 ? "west"  : "";

        if (ns.isEmpty()) return ew.isEmpty() ? "same" : ew;
        if (ew.isEmpty()) return ns;
        return ns + "-" + ew;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static float normalizeYaw(float yaw) {
        while (yaw >  180f) yaw -= 360f;
        while (yaw < -180f) yaw += 360f;
        return yaw;
    }
}
