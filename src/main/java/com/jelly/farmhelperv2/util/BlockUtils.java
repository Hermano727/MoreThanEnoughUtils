package com.jelly.farmhelperv2.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Minimal block/world helpers for MTEU macros (Fabric 1.21).
 * Used for relative positions from player yaw and walkable checks.
 */
public final class BlockUtils {

    private BlockUtils() {
    }

    /** Unit vector X (east) from yaw in degrees. Yaw 0 = south (+Z), 90 = west (-X). */
    public static float getUnitX(float yawDeg) {
        double rad = Math.toRadians(normalizeYaw360(yawDeg));
        return (float) (-Math.sin(rad));
    }

    /** Unit vector Z (south) from yaw in degrees. */
    public static float getUnitZ(float yawDeg) {
        double rad = Math.toRadians(normalizeYaw360(yawDeg));
        return (float) Math.cos(rad);
    }

    public static float normalizeYaw360(float yaw) {
        float n = yaw % 360f;
        if (n < 0f) n += 360f;
        return n;
    }

    /**
     * Block position relative to player. x/z are in "player relative" coords:
     * x = left/right, z = forward/back (with respect to yaw).
     */
    public static BlockPos getRelativeBlockPos(double x, double y, double z, float yawDeg, double playerX, double playerY, double playerZ) {
        float ux = getUnitX(yawDeg);
        float uz = getUnitZ(yawDeg);
        double dx = ux * z + uz * (-1) * x;
        double dz = uz * z + ux * x;
        int bx = (int) Math.floor(playerX + dx);
        int by = (int) Math.floor(playerY + y);
        int bz = (int) Math.floor(playerZ + dz);
        return new BlockPos(bx, by, bz);
    }

    public static Block getBlock(World world, BlockPos pos) {
        if (world == null) return Blocks.AIR;
        return world.getBlockState(pos).getBlock();
    }

    /**
     * True if the block at pos (and the block above for player height) can be walked through
     * (no solid collision). Simplified for farm use: air, crops, stems, etc.
     */
    public static boolean canWalkThrough(World world, BlockPos pos) {
        if (world == null) return false;
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty();
    }

    /**
     * Snaps {@code yaw} to the nearest multiple of 90° (0, 90, 180, 270).
     * The result is in the [0, 360) range.
     */
    public static float snapYawToNearest90(float yaw) {
        float n = normalizeYaw360(yaw);
        float nearest = Math.round(n / 90f) * 90f;
        if (nearest >= 360f) nearest = 0f;
        return nearest;
    }
}
