package com.jelly.farmhelperv2.fish;

import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.util.ChatUtils;
import com.jelly.farmhelperv2.util.InputUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Safe, tick-driven auto-fishing runtime.
 * Flow: detect bite sound -> delayed reel -> delayed recast.
 */
public final class AutoFishing {
    private static final String BITE_SOUND_SUFFIX = "block.note_block.pling";
    private static final double MAX_SOUND_DISTANCE = 16.0D;

    private static boolean running = false;
    private static int reelDelayTicks = -1;
    private static int recastDelayTicks = -1;
    private static long lastCastMs = 0L;
    private static long lastBiteMs = 0L;
    private static int castsSinceNudge = 0;
    private static int castsUntilNudge = randomBetweenInclusive(3, 8);
    private static int nudgeTicksRemaining = 0;
    private static float nudgeTargetYaw = 0.0f;
    private static float nudgeTargetPitch = 0.0f;

    private AutoFishing() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void setRunning(MinecraftClient client, boolean enabled) {
        running = enabled;
        clearPending();
        if (!enabled) {
            InputUtils.resetAll(client, true, true);
        }
    }

    public static void stop(MinecraftClient client) {
        setRunning(client, false);
    }

    public static void tick(MinecraftClient client) {
        if (!running || client == null || client.player == null || client.world == null) {
            return;
        }

        applyHeadNudgeStep(client.player);

        if (reelDelayTicks >= 0 && --reelDelayTicks <= 0) {
            useFishingRod(client);
            reelDelayTicks = -1;
            recastDelayTicks = randomBetweenInclusive(
                    ModConfig.getAutoFishingRecastDelayMinTicks(),
                    ModConfig.getAutoFishingRecastDelayMaxTicks()
            );
            return;
        }

        if (recastDelayTicks >= 0 && --recastDelayTicks <= 0) {
            if (useFishingRod(client)) {
                lastCastMs = System.currentTimeMillis();
                castsSinceNudge++;
                maybeQueueHeadNudge(client.player);
            }
            recastDelayTicks = -1;
        }
    }

    public static void onSound(MinecraftClient client, String soundId, double x, double y, double z) {
        if (!running || client == null || client.player == null || client.world == null || soundId == null) {
            return;
        }

        if (!isLikelyBiteSound(soundId)) {
            return;
        }

        // Ignore far away pling sounds to reduce false positives from other players.
        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        if (playerPos.squaredDistanceTo(x, y, z) > (MAX_SOUND_DISTANCE * MAX_SOUND_DISTANCE)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCastMs < ModConfig.getAutoFishingRecastGuardMs()) {
            return;
        }
        if (now - lastBiteMs < ModConfig.getAutoFishingRecastGuardMs()) {
            return;
        }
        if (reelDelayTicks >= 0 || recastDelayTicks >= 0) {
            return;
        }
        lastBiteMs = now;
        reelDelayTicks = randomBetweenInclusive(
                ModConfig.getAutoFishingReelDelayMinTicks(),
                ModConfig.getAutoFishingReelDelayMaxTicks()
        );
    }

    private static boolean useFishingRod(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            return false;
        }
        if (!isHoldingFishingRod(client.player)) {
            return false;
        }
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private static boolean isHoldingFishingRod(ClientPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        return held != null && !held.isEmpty() && held.isOf(Items.FISHING_ROD);
    }

    private static boolean isLikelyBiteSound(String soundId) {
        return soundId.endsWith(BITE_SOUND_SUFFIX) || soundId.endsWith("note.pling");
    }

    private static int randomBetweenInclusive(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static float randomBetweenFloat(float min, float max) {
        if (max < min) {
            float temp = min;
            min = max;
            max = temp;
        }
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static void maybeQueueHeadNudge(ClientPlayerEntity player) {
        if (player == null || nudgeTicksRemaining > 0 || castsSinceNudge < castsUntilNudge) {
            return;
        }

        castsSinceNudge = 0;
        castsUntilNudge = randomBetweenInclusive(3, 8);
        // Very slight random look delta to satisfy anti-AFK checks.
        float yawDelta = randomBetweenFloat(-0.18f, 0.18f);
        float pitchDelta = randomBetweenFloat(-0.09f, 0.09f);
        nudgeTargetYaw = normalizeYaw(player.getYaw() + yawDelta);
        nudgeTargetPitch = MathHelper.clamp(player.getPitch() + pitchDelta, -90.0f, 90.0f);
        nudgeTicksRemaining = 5;
    }

    private static void applyHeadNudgeStep(ClientPlayerEntity player) {
        if (player == null || nudgeTicksRemaining <= 0) {
            return;
        }

        float step = 1.0f / nudgeTicksRemaining;
        float newYaw = normalizeYaw(MathHelper.lerp(step, player.getYaw(), nudgeTargetYaw));
        float newPitch = MathHelper.clamp(MathHelper.lerp(step, player.getPitch(), nudgeTargetPitch), -90.0f, 90.0f);
        player.setYaw(newYaw);
        player.setPitch(newPitch);
        player.setHeadYaw(newYaw);
        player.setBodyYaw(newYaw);
        nudgeTicksRemaining--;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized > 180.0f) normalized -= 360.0f;
        if (normalized < -180.0f) normalized += 360.0f;
        return normalized;
    }

    private static void clearPending() {
        reelDelayTicks = -1;
        recastDelayTicks = -1;
        castsSinceNudge = 0;
        castsUntilNudge = randomBetweenInclusive(3, 8);
        nudgeTicksRemaining = 0;
    }
}
