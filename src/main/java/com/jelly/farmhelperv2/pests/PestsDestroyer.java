package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.pathfinder.SimplePathfinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Minimal 1.21 Pest Destroyer:
 * - Reads Garden pest count from the scoreboard.
 * - Finds the nearest pest armor stand by name.
 * - Walks toward it in a straight line and attacks when close.
 *
 * This is intentionally far less complex than the 1.8.9 implementation
 * and is meant as an alpha-only helper.
 */
public final class PestsDestroyer {

    private static ArmorStandEntity currentTarget;
    private static long lastNoPestsMessageTime = 0L;

    private static final long NO_PESTS_MESSAGE_COOLDOWN_MS = 10_000L;
    private static final double ATTACK_RANGE = 3.0D;

    private PestsDestroyer() {
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;

        int pestCount = ScoreboardPestReader.getGardenPestCount(client);
        if (pestCount <= 0) {
            // Nothing to do: stop movement and occasionally let the user know.
            SimplePathfinder.stop(client);
            currentTarget = null;

            long now = System.currentTimeMillis();
            if (now - lastNoPestsMessageTime >= NO_PESTS_MESSAGE_COOLDOWN_MS) {
                lastNoPestsMessageTime = now;
                player.sendMessage(Text.literal("[MTEU] No pests detected on the Garden."), false);
            }
            return;
        }

        // Ensure we have a valid target.
        if (currentTarget == null || currentTarget.isRemoved() || currentTarget.isDead()) {
            Optional<ArmorStandEntity> nearest = PestDetection.findNearestPest(client);
            currentTarget = nearest.orElse(null);
            if (currentTarget != null) {
                FarmHelperFabric.LOGGER.info("Pest Destroyer: targeting pest {}", currentTarget.getName().getString());
            }
        }

        if (currentTarget == null) {
            // Can't see any pests yet – just stand still.
            SimplePathfinder.stop(client);
            return;
        }

        Vec3d targetPos = new Vec3d(
                currentTarget.getX(),
                currentTarget.getY(),
                currentTarget.getZ()
        );
        double dx = player.getX() - targetPos.x;
        double dy = player.getY() - targetPos.y;
        double dz = player.getZ() - targetPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= ATTACK_RANGE) {
            // Close enough – stop and swing.
            SimplePathfinder.stop(client);
            options.attackKey.setPressed(true);

            if (client.interactionManager != null) {
                client.interactionManager.attackEntity(player, currentTarget);
                player.swingHand(Hand.MAIN_HAND);
            }
        } else {
            // Move toward the pest.
            options.attackKey.setPressed(false);
            SimplePathfinder.moveTowards(client, targetPos);
        }
    }
}

