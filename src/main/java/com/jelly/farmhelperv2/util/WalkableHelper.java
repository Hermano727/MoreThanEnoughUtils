package com.jelly.farmhelperv2.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Walkable checks for macros: front/back/left/right relative to player yaw (Fabric 1.21).
 */
public final class WalkableHelper {

    private WalkableHelper() {
    }

    public static BlockPos getRelativeBlockPos(MinecraftClient client, int x, int y, int z) {
        if (client.player == null || client.world == null) return BlockPos.ORIGIN;
        float yaw = client.player.getYaw();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        return BlockUtils.getRelativeBlockPos(x, y, z, yaw, px, py, pz);
    }

    public static boolean isFrontWalkable(MinecraftClient client) {
        BlockPos pos = getRelativeBlockPos(client, 0, 0, 1);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    public static boolean isBackWalkable(MinecraftClient client) {
        BlockPos pos = getRelativeBlockPos(client, 0, 0, -1);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    public static boolean isLeftWalkable(MinecraftClient client) {
        BlockPos pos = getRelativeBlockPos(client, -1, 0, 0);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    public static boolean isRightWalkable(MinecraftClient client) {
        BlockPos pos = getRelativeBlockPos(client, 1, 0, 0);
        return BlockUtils.canWalkThrough(client.world, pos);
    }

    /**
     * True when the block in front of the player is walkable (e.g. air, gap at end of lane).
     * Used by the vertical S-shape macro when collision-based detection is desired.
     */
    public static boolean isAtEndOfLane(MinecraftClient client) {
        return isFrontWalkable(client);
    }

    /**
     * True when the block in front is not pumpkin or melon (MelonkingDE end-of-lane).
     * At the end of a pumpkin/melon lane the space in front is typically walkway (snow, dirt, air).
     * This avoids requiring "canWalkThrough" so solid walkway blocks still count as end of lane.
     */
    public static boolean isAtEndOfLaneMelonkingde(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        BlockPos front = getRelativeBlockPos(client, 0, 0, 1);
        Block block = BlockUtils.getBlock(client.world, front);
        return block != Blocks.PUMPKIN && block != Blocks.MELON;
    }
}
