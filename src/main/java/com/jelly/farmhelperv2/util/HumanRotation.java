package com.jelly.farmhelperv2.util;

import net.minecraft.client.MinecraftClient;

/**
 * Small, reusable helper for smooth, human-like yaw rotations in macros.
 *
 * Usage:
 * - Create a session with {@link #rotateToDegrees(MinecraftClient, float, long, long)}
 * - Call {@link RotationSession#tick(MinecraftClient)} every tick until it returns true (done)
 *
 * Notes:
 * - Only rotates yaw (keeps pitch as-is).
 * - Uses ease-in-out + tiny jitter to avoid looking "snappy".
 */
public final class HumanRotation {

    private HumanRotation() {
    }

    public static RotationSession rotateToDegrees(MinecraftClient client, float targetYawDegrees, long minDurationMs, long maxDurationMs) {
        if (client == null || client.player == null) {
            return new RotationSession(0f, targetYawDegrees, 0L, 0L);
        }
        long now = System.currentTimeMillis();
        long min = Math.max(50L, Math.min(minDurationMs, maxDurationMs));
        long max = Math.max(min, Math.max(minDurationMs, maxDurationMs));
        long duration = min + (long) (Math.random() * (double) (max - min));

        float from = client.player.getYaw();
        float to = normalizeYaw180(targetYawDegrees);
        return new RotationSession(from, to, now, duration);
    }

    public static final class RotationSession {
        private final float fromYaw;
        private final float toYaw;
        private final long startTimeMs;
        private final long durationMs;

        private RotationSession(float fromYaw, float toYaw, long startTimeMs, long durationMs) {
            this.fromYaw = normalizeYaw180(fromYaw);
            this.toYaw = normalizeYaw180(toYaw);
            this.startTimeMs = startTimeMs;
            this.durationMs = durationMs;
        }

        /** @return true when rotation is finished */
        public boolean tick(MinecraftClient client) {
            if (client == null || client.player == null) {
                return true;
            }
            if (durationMs <= 0L) {
                applyYaw(client, toYaw);
                return true;
            }

            long now = System.currentTimeMillis();
            float t = (float) (now - startTimeMs) / (float) durationMs;
            if (t >= 1.0f) {
                applyYaw(client, toYaw);
                return true;
            }
            if (t <= 0.0f) {
                applyYaw(client, fromYaw);
                return false;
            }

            float eased = easeInOutCubic(t);
            float yaw = lerpYawShortest(fromYaw, toYaw, eased);
            yaw += jitter(now, t);
            applyYaw(client, yaw);
            return false;
        }
    }

    private static void applyYaw(MinecraftClient client, float yaw) {
        if (client == null || client.player == null) return;
        client.player.setYaw(yaw);
        client.player.setHeadYaw(yaw);
        client.player.setBodyYaw(yaw);
    }

    private static float easeInOutCubic(float t) {
        // 0..1 -> 0..1
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float lerpYawShortest(float from, float to, float t) {
        float a = normalizeYaw360(from);
        float b = normalizeYaw360(to);
        float delta = b - a;
        if (delta > 180.0f) delta -= 360.0f;
        if (delta < -180.0f) delta += 360.0f;
        float out = a + delta * t;
        return normalizeYaw180(out);
    }

    private static float jitter(long nowMs, float t) {
        // Small periodic jitter to resemble micro mouse corrections.
        // Tapers off near the end so we land cleanly on the target.
        float taper = 1.0f - clamp01((t - 0.7f) / 0.3f);
        float wave = (float) Math.sin((double) nowMs * 0.015);
        return wave * 0.12f * taper; // ~±0.12°
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /** Normalize to [0, 360). */
    private static float normalizeYaw360(float yaw) {
        float n = yaw % 360f;
        if (n < 0f) n += 360f;
        return n;
    }

    /** Normalize to (-180, 180]. */
    private static float normalizeYaw180(float yaw) {
        float n = normalizeYaw360(yaw);
        if (n > 180f) n -= 360f;
        if (n <= -180f) n += 360f;
        return n;
    }
}

