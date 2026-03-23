package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.*;

/**
 * Pest detection for the Garden.
 *
 * Mirrors the core idea from 1.8.9:
 * - Pests are represented by an armor stand with a specific skull texture and
 *   nearby mob/name entities.
 *
 * For 1.21 we keep this lightweight:
 * - Scan all armor stands occasionally.
 * - Match by skull texture OR by name substring.
 * - Attach the closest mob entity within a small radius as the "real" pest, if present.
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

    /**
     * Base64-encoded skull textures copied from the 1.8.9 PestsDestroyer.
     * These values are used to robustly identify pest armor stands even if their
     * displayed name changes.
     */
    private static final Map<String, String> PEST_TEXTURES = new HashMap<>();

    static {
        // The second element of each Tuple in 1.8.9 PestsDestroyer.pests is the texture value.
        PEST_TEXTURES.put("Beetle", "ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTc4OTkzNCwKICAicHJvZmlsZUlkIiA6ICJlMjc5NjliODYyNWY0NDg1YjkyNmM5NTBhMDljMWMwMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLRVZJTktFTE9LRSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83MGExZTgzNmJmMTk2OGIyZWFhNDgzNzIyN2ExOTIwNGYxNzI5NWQ4NzBlZTllNzU0YmQ2YjZkNjBkZGJlZDNjIgogICAgfQogIH0KfQ");
        PEST_TEXTURES.put("Cricket", "ewogICJ0aW1lc3RhbXAiIDogMTcyMzE3OTgxMTI2NCwKICAicHJvZmlsZUlkIiA6ICJjZjc4YzFkZjE3ZTI0Y2Q5YTIxYmU4NWQ0NDk5ZWE4ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXR0c0FybW9yU3RhbmRzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyNGM2OWY5NmNlNTU2MjIxZTE5NWM4ZWYyYmZhZDcxZWJmN2Y5NWY1YWU5MTRhNDg0YThkMGVjMjE2NzI2NzQiCiAgICB9CiAgfQp9");
        PEST_TEXTURES.put("Earthworm", "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ1OTc0NywKICAicHJvZmlsZUlkIiA6ICIyNTBlNzc5MjZkNDM0ZDIyYWM2MTQ4N2EyY2M3YzAwNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMdW5hMTIxMDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQwM2JhNDAyN2EzMzNkOGQyZmQzMmFiNTlkMWNmZGJhYTdkOTA4ZDgwZDIzODFkYjJhNjljYmU2NTQ1MGFkOCIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Fly", "ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTA2MzI4MSwKICAicHJvZmlsZUlkIiA6ICJjN2FmMWNkNjNiNTE0Y2YzOGY4NWQ2ZDUxNzhjYThlNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtb25zdGVyZ2FtZXIzMTUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ5MGU3Nzc4MjZhNTI0NjEzNjhlMjZkMWIyZTE5YmZhMWJhNTgyZDYwMjQ4M2U1NDVmNDEyNGQwZjczMTg0MiIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Locust", "ewogICJ0aW1lc3RhbXAiIDogMTY5NzU1NzA3NzAzNywKICAicHJvZmlsZUlkIiA6ICI0YjJlMGM1ODliZjU0ZTk1OWM1ZmJlMzg5MjQ1MzQzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJfTmVvdHJvbl8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGIyNGE0ODJhMzJkYjFlYTc4ZmI5ODA2MGIwYzJmYTRhMzczY2JkMThhNjhlZGRkZWI3NDE5NDU1YTU5Y2RhOSIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Mite", "ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQxOTcyNSwKICAicHJvZmlsZUlkIiA6ICJkYjYzNWE3MWI4N2U0MzQ5YThhYTgwOTMwOWFhODA3NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJFbmdlbHMxNzQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmU2YmFmNjQzMWE5ZGFhMmNhNjA0ZDVhM2MyNmU5YTc2MWQ1OTUyZjA4MTcxNzRhNGZlMGI3NjQ2MTZlMjFmZiIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Mosquito", "ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTAyOTQ2MSwKICAicHJvZmlsZUlkIiA6ICI3NTE0NDQ4MTkxZTY0NTQ2OGM5NzM5YTZlMzk1N2JlYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGFua3NNb2phbmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTJhOWZlMDViYzY2M2VmY2QxMmU1NmEzY2NjNWVjMDM1YmY1NzdiNzg3MDg1NDhiNmY0ZmZjZjFkMzBlY2NmZSIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Moth", "ewogICJ0aW1lc3RhbXAiIDogMTY5Njg3MDQwNTk1NCwKICAicHJvZmlsZUlkIiA6ICJiMTUyZDlhZTE1MTM0OWNmOWM2NmI0Y2RjMTA5NTZjOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaXNxdW90aCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82NTQ4NWM0YjM0ZTViNTQ3MGJlOTRkZTEwMGU2MWY3ODE2ZjgxYmM1YTExZGZkZjBlY2NmODkwMTcyZGE1ZDBhIgogICAgfQogIH0KfQ");
        PEST_TEXTURES.put("Rat", "ewogICJ0aW1lc3RhbXAiIDogMTYxODQxOTcwMTc1MywKICAicHJvZmlsZUlkIiA6ICI3MzgyZGRmYmU0ODU0NTVjODI1ZjkwMGY4OGZkMzJmOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJCdUlJZXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYThhYmI0NzFkYjBhYjc4NzAzMDExOTc5ZGM4YjQwNzk4YTk0MWYzYTRkZWMzZWM2MWNiZWVjMmFmOGNmZmU4IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        PEST_TEXTURES.put("Slug", "ewogICJ0aW1lc3RhbXAiIDogMTY5NzQ3MDQ0MzA4MiwKICAicHJvZmlsZUlkIiA6ICJkOGNkMTNjZGRmNGU0Y2IzODJmYWZiYWIwOGIyNzQ4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJaYWNoeVphY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E3OWQwZmQ2NzdiNTQ1MzA5NjExMTdlZjg0YWRjMjA2ZTJjYzUwNDVjMTM0NGQ2MWQ3NzZiZjhhYzJmZTFiYSIKICAgIH0KICB9Cn0");
        PEST_TEXTURES.put("Praying Mantis", "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxOTYxMiwKICAicHJvZmlsZUlkIiA6ICI0OWIzODUyNDdhMWY0NTM3YjBmN2MwZTFmMTVjMTc2NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJiY2QyMDMzYzYzZWM0YmY4IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFlMDRiYjYzNjdjYWE0ZTg4ZjVmZDBlZTgwZjA3NDVkMTM3YTYwNjAyMjNkYmJjNDJhMTY0NzFmZGY2NGJiODMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        PEST_TEXTURES.put("Firefly", "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQyMjEzNiwKICAicHJvZmlsZUlkIiA6ICIzNDY4Y2VjMWFlOTY0YWRmYWQyNjEzMGEwZGQ0NjRkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJzdXJlZWxta18iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlNzllOTBhZGYzNDcxOGYzMTNlYzI0ZDZjNjEzNWI2OWIzNzg4YzYxODQ5ODQ0NmNjYzgzY2E2NDBjMGIxNCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        PEST_TEXTURES.put("Dragonfly", "ewogICJ0aW1lc3RhbXAiIDogMTc2MDQ1MDQxODQzNywKICAicHJvZmlsZUlkIiA6ICIwNjY5Y2E1MGYyZWU0NTQxODhlYWQ3YTM3NTkzNDRlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDcjR6eWNsb3duVFYiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0YWZmNGMwYjJkY2UzYTY3MjM0OWNjMGVlOWU2ZjNhOWRlZWJlNGIzNTU2ZTg0NjExZWNhMjUwYTc4MjFiZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
    }

    /**
     * Represents a detected pest cluster around an armor stand.
     */
    public static final class DetectedPest {
        public final ArmorStandEntity armorStand;
        public final MobEntity mob;

        private DetectedPest(ArmorStandEntity armorStand, MobEntity mob) {
            this.armorStand = armorStand;
            this.mob = mob;
        }

        public Entity getPrimaryEntity() {
            return mob != null ? mob : armorStand;
        }

        public Vec3d getPosition() {
            Entity primary = getPrimaryEntity();
            return new Vec3d(primary.getX(), primary.getY(), primary.getZ());
        }
    }

    private static long lastFullScanTick = -1;
    private static final long FULL_SCAN_INTERVAL_TICKS = 10L;
    private static final List<DetectedPest> cachedPests = new ArrayList<>();

    private PestDetection() {
    }

    /**
     * Finds the nearest detected pest (armor stand + optional mob) to the player.
     */
    public static Optional<ArmorStandEntity> findNearestPest(MinecraftClient client) {
        DetectedPest nearest = findNearestDetectedPest(client);
        if (nearest == null) {
            return Optional.empty();
        }
        return Optional.of(nearest.armorStand);
    }

    public static DetectedPest findNearestDetectedPest(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return null;
        }

        long currentTick = client.world.getTime();
        if (lastFullScanTick < 0 || currentTick - lastFullScanTick >= FULL_SCAN_INTERVAL_TICKS) {
            cachedPests.clear();
            cachedPests.addAll(scanForPests(client));
            lastFullScanTick = currentTick;

            if (ModConfig.isPestsDebugLogging()) {
                FarmHelperFabric.LOGGER.debug("PestDetection: scanned and found {} pests", cachedPests.size());
            }
        }

        if (cachedPests.isEmpty()) {
            return null;
        }

        DetectedPest nearest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (DetectedPest pest : cachedPests) {
            double distSq = pest.getPrimaryEntity().squaredDistanceTo(client.player);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                nearest = pest;
            }
        }
        return nearest;
    }

    private static List<DetectedPest> scanForPests(MinecraftClient client) {
        List<DetectedPest> result = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity)) {
                continue;
            }

            ArmorStandEntity armorStand = (ArmorStandEntity) entity;

            if (!isPestArmorStand(armorStand)) {
                continue;
            }

            MobEntity mob = findCompanionMob(client, armorStand);
            result.add(new DetectedPest(armorStand, mob));
        }

        return result;
    }

    private static boolean isPestArmorStand(ArmorStandEntity armorStand) {
        // Match by name first (cheap).
        String name = armorStand.getName().getString();
        if (name != null && !name.isEmpty() && isPestName(name)) {
            return true;
        }

        // Then by skull texture if present (via 1.21 data components).
        ItemStack headStack = armorStand.getEquippedStack(EquipmentSlot.HEAD);
        if (headStack == null || headStack.isEmpty() || !headStack.isOf(Items.PLAYER_HEAD)) {
            return false;
        }

        try {
            ProfileComponent profileComponent = headStack.get(DataComponentTypes.PROFILE);
            if (profileComponent == null) {
                return false;
            }
            Object gameProfile = profileComponent.getGameProfile();
            if (gameProfile == null) {
                return false;
            }
            PropertyMap properties = getProfileProperties(gameProfile);
            if (properties == null) {
                return false;
            }
            Collection<Property> textures = properties.get("textures");
            if (textures == null || textures.isEmpty()) {
                return false;
            }
            for (Property property : textures) {
                String value = property.value();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                for (String texture : PEST_TEXTURES.values()) {
                    if (texture.equals(value)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (ModConfig.isPestsDebugLogging()) {
                FarmHelperFabric.LOGGER.debug("Failed to inspect pest skull texture profile", e);
            }
        }

        return false;
    }

    /**
     * Authlib mappings changed across versions (record accessor vs getter).
     * Use reflection to support both:
     * - GameProfile#properties()
     * - GameProfile#getProperties()
     */
    private static PropertyMap getProfileProperties(Object gameProfile) {
        try {
            try {
                return (PropertyMap) gameProfile.getClass().getMethod("properties").invoke(gameProfile);
            } catch (NoSuchMethodException ignored) {
                return (PropertyMap) gameProfile.getClass().getMethod("getProperties").invoke(gameProfile);
            }
        } catch (Exception e) {
            if (ModConfig.isPestsDebugLogging()) {
                FarmHelperFabric.LOGGER.debug("Failed to read GameProfile properties via reflection", e);
            }
            return null;
        }
    }

    private static MobEntity findCompanionMob(MinecraftClient client, ArmorStandEntity armorStand) {
        double radius = 3.0D;
        Box box = new Box(
                armorStand.getX() - radius, armorStand.getY() - radius, armorStand.getZ() - radius,
                armorStand.getX() + radius, armorStand.getY() + radius, armorStand.getZ() + radius
        );
        MobEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (Entity e : client.world.getOtherEntities(armorStand, box, entity -> entity instanceof MobEntity)) {
            MobEntity mob = (MobEntity) e;
            double distSq = mob.squaredDistanceTo(armorStand);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = mob;
            }
        }
        return closest;
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

