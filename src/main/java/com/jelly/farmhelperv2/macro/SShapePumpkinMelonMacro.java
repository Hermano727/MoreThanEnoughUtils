package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.util.BlockUtils;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.MovementUtils;
import com.jelly.farmhelperv2.util.WalkableHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;

/**
 * Standard FarmHelper S-Shape Pumpkin/Melon macro (Fabric 1.21).
 * When hitting a wall, walks forward or backward to switch lane, then continues
 * left/right.
 * Does not swap direction on "bump into side" like the vertical crop macro.
 */
public class SShapePumpkinMelonMacro implements Macro {

    private static final float ROTATION_DEGREE = 45f;
    private static final float TARGET_PITCH = -59f;

    private enum State {
        NONE, LEFT, RIGHT, SWITCHING_LANE
    }

    private enum ChangeLaneDirection {
        FORWARD, BACKWARD
    }

    private State state = State.NONE;
    private ChangeLaneDirection changeLaneDirection = null;
    private float closest90Yaw = 0f;
    private float pitch = TARGET_PITCH;

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player == null)
            return;

        client.player.sendMessage(ChatUtils.success("On 59f version!"), false);
        closest90Yaw = BlockUtils.snapYawToNearest90(client.player.getYaw());
        enforcePitch(client);
        state = calculateDirection(client);
        if (state == State.NONE) {
            client.player.sendMessage(ChatUtils.warning("Pumpkin/Melon: no direction found"), false);
            return;
        }

        float extra = state == State.LEFT ? (-ROTATION_DEGREE - (float) (Math.random() * 2))
                : (ROTATION_DEGREE + (float) (Math.random() * 2));
        MovementUtils.applyRotation(client, closest90Yaw + extra, pitch);
        enforcePitch(client);

        changeLaneDirection = null;
        applyKeys(client, state);
        if (com.jelly.farmhelperv2.config.ModConfig.isVerboseLogging()) {
            client.player.sendMessage(
                    ChatUtils.success(
                            "S-Shape Pumpkin/Melon ENABLED (" + (state == State.LEFT ? "LEFT" : "RIGHT") + ")"),
                    false);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null)
            return;

        enforcePitch(client);
        updateState(client);
        enforcePitch(client);
        applyKeys(client, state);
        enforcePitch(client);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        MovementUtils.stopAll(client.options);
        if (client.player != null) {
            client.player.sendMessage(ChatUtils.warning("S-Shape Pumpkin/Melon DISABLED"), false);
        }
    }

    private void updateState(MinecraftClient client) {
        if (state == State.NONE) {
            state = calculateDirection(client);
            return;
        }

        if (state == State.LEFT || state == State.RIGHT) {
            BlockPos posL = WalkableHelper.getRelativeBlockPos(client, -1, 0, 0);
            BlockPos posR = WalkableHelper.getRelativeBlockPos(client, 1, 0, 0);
            Block blockLeft = BlockUtils.getBlock(client.world, posL);
            Block blockRight = BlockUtils.getBlock(client.world, posR);

            if (blockLeft == Blocks.MELON || blockLeft == Blocks.PUMPKIN) {
                state = State.LEFT;
            } else if (blockRight == Blocks.MELON || blockRight == Blocks.PUMPKIN) {
                state = State.RIGHT;
            } else if (WalkableHelper.isFrontWalkable(client)) {
                if (changeLaneDirection == ChangeLaneDirection.BACKWARD) {
                    return; // stuck, keep current state
                }
                changeLaneDirection = ChangeLaneDirection.FORWARD;
                pitch = TARGET_PITCH;
                float add = state == State.RIGHT ? -((float) (Math.random() * 0.4 + 0.2))
                        : ((float) (Math.random() * 0.4 + 0.2));
                client.player.setYaw(closest90Yaw + add);
                client.player.setPitch(pitch);
                state = State.SWITCHING_LANE;
            } else if (WalkableHelper.isBackWalkable(client)) {
                if (changeLaneDirection == ChangeLaneDirection.FORWARD) {
                    return;
                }
                changeLaneDirection = ChangeLaneDirection.BACKWARD;
                pitch = TARGET_PITCH;
                float add = state == State.RIGHT ? -((float) (Math.random() * 0.4 + 0.2))
                        : ((float) (Math.random() * 0.4 + 0.2));
                client.player.setYaw(closest90Yaw + add);
                client.player.setPitch(pitch);
                state = State.SWITCHING_LANE;
            } else {
                if (WalkableHelper.isLeftWalkable(client))
                    state = State.LEFT;
                else if (WalkableHelper.isRightWalkable(client))
                    state = State.RIGHT;
                else
                    state = State.NONE;
            }
            return;
        }

        if (state == State.SWITCHING_LANE) {
            if (WalkableHelper.isRightWalkable(client)) {
                state = State.RIGHT;
                pitch = TARGET_PITCH;
                MovementUtils.applyRotation(client, closest90Yaw + (ROTATION_DEGREE + (float) (Math.random() * 2)),
                        pitch);
            } else if (WalkableHelper.isLeftWalkable(client)) {
                state = State.LEFT;
                pitch = TARGET_PITCH;
                MovementUtils.applyRotation(client, closest90Yaw - (ROTATION_DEGREE + (float) (Math.random() * 2)),
                        pitch);
            } else if (WalkableHelper.isFrontWalkable(client)) {
                if (changeLaneDirection == ChangeLaneDirection.BACKWARD)
                    return;
                // stay SWITCHING_LANE
            } else if (WalkableHelper.isBackWalkable(client)) {
                if (changeLaneDirection == ChangeLaneDirection.FORWARD)
                    return;
                // stay SWITCHING_LANE
            } else {
                state = State.NONE;
            }
        }
    }

    private void applyKeys(MinecraftClient client, State s) {
        GameOptions opt = client.options;
        opt.attackKey.setPressed(true);
        opt.sprintKey.setPressed(s == State.SWITCHING_LANE);

        switch (s) {
            case LEFT:
                opt.leftKey.setPressed(true);
                opt.rightKey.setPressed(false);
                opt.forwardKey.setPressed(true);
                opt.backKey.setPressed(false);
                break;
            case RIGHT:
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(true);
                opt.forwardKey.setPressed(true);
                opt.backKey.setPressed(false);
                break;
            case SWITCHING_LANE:
                opt.leftKey.setPressed(false);
                opt.rightKey.setPressed(false);
                opt.forwardKey.setPressed(true);
                opt.backKey.setPressed(false);
                break;
            default:
                MovementUtils.stopAll(opt);
                break;
        }
    }

    private void enforcePitch(MinecraftClient client) {
        if (client.player == null) return;
        pitch = TARGET_PITCH;
        if (Math.abs(client.player.getPitch() - TARGET_PITCH) > 0.01f) {
            client.player.setPitch(TARGET_PITCH);
        }
    }

    private State calculateDirection(MinecraftClient client) {
        if (client.player == null || client.world == null)
            return State.NONE;
        float yaw = closest90Yaw;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        for (int i = 0; i < 180; i++) {
            BlockPos pr = BlockUtils.getRelativeBlockPos(i, 0, 0, yaw, px, py, pz);
            BlockPos pl = BlockUtils.getRelativeBlockPos(-i, 0, 0, yaw, px, py, pz);
            Block br = BlockUtils.getBlock(client.world, pr);
            Block bl = BlockUtils.getBlock(client.world, pl);
            if (br == Blocks.PUMPKIN || br == Blocks.MELON)
                return State.RIGHT;
            if (bl == Blocks.PUMPKIN || bl == Blocks.MELON)
                return State.LEFT;
            if (!BlockUtils.canWalkThrough(client.world, pr))
                return State.LEFT;
            if (!BlockUtils.canWalkThrough(client.world, pl))
                return State.RIGHT;
        }
        return State.NONE;
    }

}
