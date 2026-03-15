package com.jelly.farmhelperv2.config;

import net.minecraft.util.math.BlockPos;

/**
 * A saved block position; when the player walks over it, /warp garden can be triggered.
 */
public final class RewarpPoint {

    public int x;
    public int y;
    public int z;

    @SuppressWarnings("unused")
    public RewarpPoint() {
        // for Gson
    }

    public RewarpPoint(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public RewarpPoint(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public double getDistance(BlockPos to) {
        return Math.sqrt(
                Math.pow(to.getX() - x, 2) + Math.pow(to.getY() - y, 2) + Math.pow(to.getZ() - z, 2));
    }

    public boolean isTheSameAs(BlockPos pos) {
        return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    @Override
    public String toString() {
        return "Rewarp(" + x + ", " + y + ", " + z + ")";
    }
}
