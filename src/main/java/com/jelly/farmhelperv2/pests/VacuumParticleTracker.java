package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Observes HEART particles spawned near the player by the vacuum weapon and
 * accumulates them to infer the direction of the pest trail.
 *
 * Two complementary strategies:
 *  1. Velocity-sum:   When count==0 in the packet, offsetX/Z IS the exact
 *                     velocity of the particle.  Averaging these gives the
 *                     true heading even when all particles spawn at the same
 *                     position (player's feet).
 *  2. Position centroid: When velocity is negligible (count>0, random scatter),
 *                     the spawn positions cluster along the trail toward the
 *                     pest; the centroid relative to the player gives the
 *                     heading.
 *
 * Lifecycle:
 *   - Call {@link #setActive(boolean)} when entering / leaving FOLLOW_TRAIL.
 *   - Call {@link #onPacket} from the mixin on every particle packet received.
 *   - Call {@link #purgeExpired()} once per tick to drop stale observations.
 *   - Call {@link #getTrailDirection(double, double)} to query the current
 *     heading (returns null when there are too few observations).
 *   - Call {@link #reset()} to clear all observations (e.g. when restarting
 *     the FOLLOW_TRAIL phase).
 */
public final class VacuumParticleTracker {

    // ─── Tuning constants ────────────────────────────────────────────────────

    /** Milliseconds to keep an observation before discarding it. */
    private static final long OBSERVATION_TTL_MS = 3_000L;

    /**
     * Maximum horizontal (XZ) distance from the player within which a
     * particle is considered part of the vacuum trail.
     */
    private static final double MAX_TRACK_RADIUS_SQ = 25.0 * 25.0;

    /** Minimum number of valid observations needed to trust the direction. */
    private static final int MIN_OBSERVATIONS = 4;

    /**
     * When using the velocity strategy, the sum of horizontal velocity
     * magnitudes must exceed this threshold (per observation) to be trusted
     * over the position centroid.
     */
    private static final double VELOCITY_SIGNIFICANCE_THRESHOLD = 0.03;

    // ─── State ───────────────────────────────────────────────────────────────

    /** Whether the tracker should accept new observations. */
    private static boolean active = false;

    private static final List<Obs> observations = new ArrayList<>();

    /**
     * Last particle type string seen within tracking range (any type), used
     * for verbose debug logging when no direction can be computed.
     */
    private static String lastNearbyTypeSeen = null;
    private static int nearbyNonHeartCount = 0;

    // ─── Observation record ──────────────────────────────────────────────────

    private static final class Obs {
        final double x, z;
        /** Horizontal velocity components; 0 when the packet used count>0 (random). */
        final double vx, vz;
        final long timestamp;

        Obs(double x, double z, double vx, double vz) {
            this.x = x;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private VacuumParticleTracker() {}

    // ─── Control ─────────────────────────────────────────────────────────────

    public static void setActive(boolean a) {
        active = a;
    }

    public static boolean isActive() {
        return active;
    }

    /** Clears all accumulated observations. */
    public static void reset() {
        observations.clear();
        lastNearbyTypeSeen = null;
        nearbyNonHeartCount = 0;
    }

    // ─── Intake (called from mixin, always on the main client thread) ─────────

    /**
     * Called by {@code ParticleInterceptorMixin} for every particle packet
     * that passes through {@code ClientPlayNetworkHandler.onParticle} on the
     * main thread.
     *
     * @param type     the particle effect type
     * @param x, y, z  world position from the packet
     * @param vx, vz   horizontal velocity: pass the packet's offsetX/Z when
     *                 {@code count == 0}, otherwise pass 0 (random-velocity case)
     * @param playerX  player X used for distance filter
     * @param playerZ  player Z used for distance filter
     */
    public static void onPacket(ParticleEffect type,
                                double x, double y, double z,
                                double vx, double vz,
                                double playerX, double playerZ) {
        if (!active) return;

        double dx = x - playerX;
        double dz = z - playerZ;
        if (dx * dx + dz * dz > MAX_TRACK_RADIUS_SQ) return;

        if (!isVacuumTrailType(type)) {
            // Record for debug diagnostics but do not add to observations.
            String typeName = type.getType().toString();
            lastNearbyTypeSeen = typeName;
            nearbyNonHeartCount++;
            return;
        }

        observations.add(new Obs(x, z, vx, vz));
    }

    // ─── Maintenance ─────────────────────────────────────────────────────────

    /** Removes observations older than {@link #OBSERVATION_TTL_MS}. */
    public static void purgeExpired() {
        if (observations.isEmpty()) return;
        long cutoff = System.currentTimeMillis() - OBSERVATION_TTL_MS;
        observations.removeIf(o -> o.timestamp < cutoff);
    }

    // ─── Direction query ─────────────────────────────────────────────────────

    /**
     * Returns a normalised XZ unit Vec3d pointing in the direction the
     * vacuum trail is heading, or {@code null} when there are not enough
     * observations.
     *
     * @param playerX  current player X (used for position-centroid fallback)
     * @param playerZ  current player Z
     */
    public static Vec3d getTrailDirection(double playerX, double playerZ) {
        if (observations.size() < MIN_OBSERVATIONS) return null;

        // ── Strategy 1: velocity sum (preferred — direct heading signal) ──────
        double sumVx = 0, sumVz = 0;
        for (Obs o : observations) {
            sumVx += o.vx;
            sumVz += o.vz;
        }
        double velThreshold = VELOCITY_SIGNIFICANCE_THRESHOLD * observations.size();
        double velLen = Math.sqrt(sumVx * sumVx + sumVz * sumVz);
        if (velLen > velThreshold) {
            FarmHelperFabric.LOGGER.debug(
                    "VacuumParticleTracker: velocity strategy (n={}, |v|={})",
                    observations.size(), String.format("%.3f", velLen));
            return new Vec3d(sumVx / velLen, 0, sumVz / velLen);
        }

        // ── Strategy 2: position centroid relative to player ──────────────────
        double sumDx = 0, sumDz = 0;
        for (Obs o : observations) {
            sumDx += o.x - playerX;
            sumDz += o.z - playerZ;
        }
        double posLen = Math.sqrt(sumDx * sumDx + sumDz * sumDz);
        if (posLen > 0.5) {
            FarmHelperFabric.LOGGER.debug(
                    "VacuumParticleTracker: position centroid strategy (n={}, dist={})",
                    observations.size(), String.format("%.2f", posLen));
            return new Vec3d(sumDx / posLen, 0, sumDz / posLen);
        }

        return null;
    }

    /** Number of current valid observations (for debug logging). */
    public static int getObservationCount() {
        return observations.size();
    }

    /**
     * Human-readable summary for verbose diagnostic messages, e.g.
     * "12 HEART obs; also saw minecraft:enchant ×3 nearby".
     */
    public static String getDebugSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(observations.size()).append(" HEART obs");
        if (nearbyNonHeartCount > 0 && lastNearbyTypeSeen != null) {
            sb.append("; also saw ").append(lastNearbyTypeSeen)
              .append(" \u00d7").append(nearbyNonHeartCount).append(" nearby");
        }
        return sb.toString();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns true for the particle types used by the Hypixel Garden vacuum
     * trail indicator.  Based on in-game observation: HEART particles appear
     * as the directed trail cue when left-clicking the vacuum weapon.
     */
    private static boolean isVacuumTrailType(ParticleEffect type) {
        return type == ParticleTypes.HEART;
    }
}
