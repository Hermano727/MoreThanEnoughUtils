package com.jelly.farmhelperv2.render;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.config.RewarpPoint;
import com.jelly.farmhelperv2.skyblock.ScoreboardAreaReader;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;

/**
 * Renders a light blue outline box at each rewarp point when in Garden (Fabric 1.21).
 */
public final class RewarpRenderer {

    /** Light blue / cyan, similar to FarmHelper rewarp color (0, 255, 217). */
    private static final float R = 0.0f;
    private static final float G = 1.0f;
    private static final float B = 0.85f;
    private static final float A = 0.9f;

    private static final double MAX_DISTANCE = 50.0;

    private static boolean warnedNullMatrices;

    private RewarpRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RewarpRenderer::drawRewarps);
    }

    private static void drawRewarps(WorldRenderContext context) {
        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            if (!warnedNullMatrices) {
                warnedNullMatrices = true;
                FarmHelperFabric.LOGGER.warn("[MTEU Rewarp] WorldRenderContext.matrices() is null; rewarp boxes skipped.");
            }
            return;
        }

        try {
            Camera camera = context.gameRenderer().getCamera();
            VertexConsumerProvider consumers = context.consumers();
            if (camera == null || consumers == null) {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                return;
            }
            if (!ScoreboardAreaReader.isInGarden(mc)) {
                return;
            }

            var rewarps = ModConfig.getRewarps();
            if (rewarps.isEmpty()) {
                return;
            }

            Vec3d camPos = camera.getCameraPos();
            for (RewarpPoint r : rewarps) {
                double dist = Math.sqrt(
                        Math.pow(r.x - camPos.x, 2) + Math.pow(r.y - camPos.y, 2) + Math.pow(r.z - camPos.z, 2));
                if (dist > MAX_DISTANCE) {
                    continue;
                }

                matrices.push();
                try {
                    matrices.translate(r.x - camPos.x, r.y - camPos.y, r.z - camPos.z);
                    drawBox(matrices, consumers, new Box(0, 0, 0, 1, 1, 1), R, G, B, A);
                } finally {
                    matrices.pop();
                }
            }
        } catch (Throwable t) {
            FarmHelperFabric.LOGGER.warn("Error drawing rewarp boxes", t);
        }
    }

    private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, Box box, float red, float green, float blue, float alpha) {
        try {
            Class<?> debugClass = Class.forName("net.minecraft.client.render.debug.DebugRenderer");
            Method drawBox = debugClass.getMethod("drawBox", MatrixStack.class, VertexConsumerProvider.class, Box.class, float.class, float.class, float.class, float.class);
            drawBox.invoke(null, matrices, consumers, box, red, green, blue, alpha);
        } catch (Throwable t) {
            FarmHelperFabric.LOGGER.debug("DebugRenderer.drawBox not available", t);
        }
    }
}
