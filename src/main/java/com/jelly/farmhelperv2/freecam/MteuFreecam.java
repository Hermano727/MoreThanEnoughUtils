package com.jelly.farmhelperv2.freecam;

import com.jelly.farmhelperv2.FarmHelperClient;
import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity.RemovalReason;
/**
 * Client-only freecam: camera detaches from the player while the player stays still on the server.
 */
public final class MteuFreecam {

    private static boolean active;
    private static FreeCameraEntity camera;
    private static Input savedPlayerInput;
    private static boolean savedChunkCulling;
    private static Perspective rememberedPerspective;
    private static Object worldWhenEnabled;

    private MteuFreecam() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void toggle(MinecraftClient client) {
        if (active) {
            disable(client);
        } else {
            enable(client);
        }
    }

    public static void enable(MinecraftClient client) {
        if (active || client == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }

        FarmHelperClient.prepareForFreecam(client);

        active = true;
        savedChunkCulling = client.chunkCullingEnabled;
        client.chunkCullingEnabled = false;

        rememberedPerspective = client.options.getPerspective();
        if (client.gameRenderer.getCamera().isThirdPerson()) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }

        savedPlayerInput = player.input;
        player.input = FrozenPlayerInput.INSTANCE;

        camera = new FreeCameraEntity(world, client);
        camera.refreshPositionAndAngles(
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch()
        );
        camera.setHeadYaw(player.getHeadYaw());
        camera.setBodyYaw(player.getBodyYaw());

        world.addEntity(camera);
        client.setCameraEntity(camera);
        worldWhenEnabled = world;

        if (ModConfig.isFreecamNotifyMessages()) {
            player.sendMessage(ChatUtils.success("Freecam enabled (client-only). Bind a key under Controls if needed."), false);
        }
        FarmHelperFabric.LOGGER.info("MTEU freecam enabled");
    }

    public static void disable(MinecraftClient client) {
        if (!active || client == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        try {
            if (camera != null && client.world != null) {
                client.world.removeEntity(camera.getId(), RemovalReason.DISCARDED);
            }
        } catch (Exception e) {
            FarmHelperFabric.LOGGER.warn("Freecam: error removing camera entity", e);
        }

        camera = null;
        client.chunkCullingEnabled = savedChunkCulling;
        if (player != null) {
            client.setCameraEntity(player);
            if (savedPlayerInput != null) {
                player.input = savedPlayerInput;
            } else {
                player.input = new net.minecraft.client.input.KeyboardInput(client.options);
            }
            if (ModConfig.isFreecamNotifyMessages()) {
                player.sendMessage(ChatUtils.warning("Freecam disabled."), false);
            }
        } else {
            client.setCameraEntity(null);
        }
        savedPlayerInput = null;
        active = false;
        worldWhenEnabled = null;

        if (rememberedPerspective != null) {
            client.options.setPerspective(rememberedPerspective);
            rememberedPerspective = null;
        }

        FarmHelperFabric.LOGGER.info("MTEU freecam disabled");
    }

    /**
     * Mouse still updates the real player; copy look to the camera each tick.
     */
    public static void syncLookFromPlayer(MinecraftClient client) {
        if (!active || camera == null || client.player == null) {
            return;
        }
        ClientPlayerEntity p = client.player;
        camera.setYaw(p.getYaw());
        camera.setPitch(p.getPitch());
        camera.setHeadYaw(p.getHeadYaw());
        camera.setBodyYaw(p.getBodyYaw());
    }

    /** Called when disconnecting or when the client world reference changes. */
    public static void disableIfNeeded(MinecraftClient client) {
        if (!active) {
            return;
        }
        if (client == null || client.player == null || client.world == null
                || worldWhenEnabled != client.world) {
            disable(client != null ? client : MinecraftClient.getInstance());
        }
    }
}
