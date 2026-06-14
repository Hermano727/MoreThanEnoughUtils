package com.jelly.farmhelperv2.bingo;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Rat detection for Bingo / exploration:
 * - Scans loaded entities in range (not only armor stands). Hypixel often puts the nametag on
 *   the mob, or the client may expose the label via {@link Entity#getDisplayName()} even when
 *   {@link Entity#getCustomName()} is null.
 * - Matches normalized text containing "rat" with a small false-positive guard.
 */
public final class RatDetector {
    private static final long SCAN_INTERVAL_TICKS = 5L;
    private static final double COMPANION_RADIUS = 2.5D;
    private static final double DEDUPE_RADIUS = 2.0D;
    private static final List<RatTarget> TARGETS = new ArrayList<>();
    private static long lastScanTick = -1L;
    private static long localTickCounter = 0L;
    private static long scanCounter = 0L;
    /** Last scan stats for HUD / logs (updated when detector is enabled). */
    private static volatile String lastDebugSummary = "";

    private RatDetector() {
    }

    public static String getLastDebugSummary() {
        return lastDebugSummary;
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            clear();
            return;
        }
        if (!ModConfig.isRatDetectorEnabled()) {
            clear();
            return;
        }

        localTickCounter++;
        if (lastScanTick >= 0 && localTickCounter - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScanTick = localTickCounter;
        scanCounter++;

        int range = ModConfig.getRatDetectionRange();
        double rangeSq = (double) range * range;

        int inRange = 0;
        int armorStandsInRange = 0;
        int livingInRange = 0;
        int withCustomName = 0;
        List<String> nameSamples = new ArrayList<>();
        List<RatCandidate> candidates = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (shouldSkipEntity(entity, client)) {
                continue;
            }
            if (entity.squaredDistanceTo(client.player) > rangeSq) {
                continue;
            }
            inRange++;

            if (entity instanceof ArmorStandEntity) {
                armorStandsInRange++;
            }
            if (entity instanceof LivingEntity) {
                livingInRange++;
            }
            if (entity.getCustomName() != null) {
                withCustomName++;
            }

            String rawLabel = labelTextForMatching(entity);
            if (rawLabel == null || rawLabel.isEmpty()) {
                continue;
            }
            if (nameSamples.size() < 8) {
                String shortSample = truncate(rawLabel.replace('\n', ' '), 72);
                if (!shortSample.isBlank()) {
                    nameSamples.add(entity.getClass().getSimpleName() + ": " + shortSample);
                }
            }

            String normalized = normalize(rawLabel);
            if (!matchesRatNormalized(normalized)) {
                continue;
            }

            Entity focus = resolveFocusEntity(client, entity);
            Vec3d pos = targetPosFor(focus);
            candidates.add(new RatCandidate(pos, rawLabel));
        }

        List<RatTarget> next = dedupeCandidates(candidates);

        TARGETS.clear();
        TARGETS.addAll(next);

        if (ModConfig.isRatDetectorDebugLogging()) {
            String summary = String.format(Locale.ROOT,
                    "scan #%d | inRange=%d (armorStands=%d, living=%d, customName=%d) | matches=%d",
                    scanCounter, inRange, armorStandsInRange, livingInRange, withCustomName, next.size());
            lastDebugSummary = summary + " | " + String.join(" | ", nameSamples);
            FarmHelperFabric.LOGGER.info("[MTEU Rat] {} | nameSamples=[{}]", summary, String.join("; ", nameSamples));

            if (scanCounter % 8 == 0 && client.player != null) {
                client.player.sendMessage(
                        com.jelly.farmhelperv2.util.ChatUtils.info(
                                "[Rat debug] inRange=" + inRange + " matches=" + next.size()
                                        + " | see latest.log for samples"),
                        false);
            }
        } else {
            lastDebugSummary = "";
        }
    }

    public static List<RatTarget> getTargets() {
        return Collections.unmodifiableList(TARGETS);
    }

    public static void clear() {
        TARGETS.clear();
        lastScanTick = -1L;
        localTickCounter = 0L;
        lastDebugSummary = "";
    }

    private static boolean shouldSkipEntity(Entity entity, MinecraftClient client) {
        if (client.player != null && entity.getId() == client.player.getId()) {
            return true;
        }
        if (entity == null || entity.isRemoved()) {
            return true;
        }
        if (entity instanceof PlayerEntity) {
            return true;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
            return true;
        }
        return false;
    }

    /**
     * Prefer custom name text; fall back to full display name (often closer to what you see above the mob).
     */
    private static String labelTextForMatching(Entity entity) {
        Text custom = entity.getCustomName();
        if (custom != null) {
            String s = custom.getString();
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        Text display = entity.getDisplayName();
        if (display != null) {
            String s = display.getString();
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static boolean matchesRatNormalized(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        if (!normalized.contains("rat")) {
            return false;
        }
        return !normalized.contains("crate");
    }

    private static Entity resolveFocusEntity(MinecraftClient client, Entity matched) {
        if (matched instanceof ArmorStandEntity stand) {
            Entity mob = findNearestCompanionMob(client, stand);
            if (mob != null) {
                return mob;
            }
        }
        return matched;
    }

    private static Entity findNearestCompanionMob(MinecraftClient client, ArmorStandEntity stand) {
        Box box = new Box(
                stand.getX() - COMPANION_RADIUS, stand.getY() - COMPANION_RADIUS, stand.getZ() - COMPANION_RADIUS,
                stand.getX() + COMPANION_RADIUS, stand.getY() + COMPANION_RADIUS, stand.getZ() + COMPANION_RADIUS
        );
        Entity nearest = null;
        double closest = Double.MAX_VALUE;
        for (Entity e : client.world.getOtherEntities(stand, box,
                entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity))) {
            double distSq = e.squaredDistanceTo(stand);
            if (distSq < closest) {
                closest = distSq;
                nearest = e;
            }
        }
        return nearest;
    }

    private static Vec3d targetPosFor(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return new Vec3d(living.getX(), living.getY() + (living.getHeight() * 0.5D), living.getZ());
        }
        return new Vec3d(entity.getX(), entity.getY() + 0.5D, entity.getZ());
    }

    private static List<RatTarget> dedupeCandidates(List<RatCandidate> candidates) {
        List<RatTarget> out = new ArrayList<>();
        double dedupeSq = DEDUPE_RADIUS * DEDUPE_RADIUS;
        for (RatCandidate c : candidates) {
            boolean skip = false;
            for (RatTarget existing : out) {
                if (existing.pos().squaredDistanceTo(c.pos) <= dedupeSq) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                out.add(new RatTarget(c.pos, c.sourceName));
            }
        }
        return out;
    }

    private static String normalize(String text) {
        return text
                .replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

    private static final class RatCandidate {
        final Vec3d pos;
        final String sourceName;
        RatCandidate(Vec3d pos, String sourceName) {
            this.pos = pos;
            this.sourceName = sourceName;
        }
    }

    public static final class RatTarget {
        private final Vec3d pos;
        private final String sourceName;

        public RatTarget(Vec3d pos, String sourceName) {
            this.pos = pos;
            this.sourceName = sourceName;
        }

        public Vec3d pos() {
            return pos;
        }

        public String sourceName() {
            return sourceName;
        }
    }
}
