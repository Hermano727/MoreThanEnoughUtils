package com.jelly.farmhelperv2.pests;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Minimal pest detection for the Garden.
 *
 * The original 1.8.9 implementation matches armor stand head textures;
 * for the 1.21 alpha we start with a simpler heuristic:
 * - Look for named armor stands whose name contains a known pest name.
 */
public final class PestDetection {

    private static final Set<String> PEST_NAMES = new HashSet<>(Arrays.asList(
            "Beetle",
            "Cricket",
            "Earthworm",
            "Fly",
            "Locust",
            "Mite",
            "Mosquito",
            "Moth",
            "Rat",
            "Slug",
            "Praying Mantis",
            "Firefly",
            "Dragonfly"
    ));

    private PestDetection() {
    }

    /**
     * Finds the nearest armor stand that looks like a pest, based on its custom name.
     */
    public static Optional<ArmorStandEntity> findNearestPest(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return Optional.empty();
        }

        ArmorStandEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity)) {
                continue;
            }

            ArmorStandEntity armorStand = (ArmorStandEntity) entity;
            String name = armorStand.getName().getString();
            if (name == null || name.isEmpty()) {
                continue;
            }

            if (!isPestName(name)) {
                continue;
            }

            double distSq = armorStand.squaredDistanceTo(client.player);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = armorStand;
            }
        }

        return Optional.ofNullable(closest);
    }

    private static boolean isPestName(String name) {
        for (String pest : PEST_NAMES) {
            if (name.contains(pest)) {
                return true;
            }
        }
        return false;
    }
}

