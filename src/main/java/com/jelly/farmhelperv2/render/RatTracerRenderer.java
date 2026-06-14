package com.jelly.farmhelperv2.render;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.bingo.RatDetector;
import com.jelly.farmhelperv2.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Draws world-space tracers to detected rat targets.
 */
public final class RatTracerRenderer {
    private static final float R = 1.0f;
    private static final float G = 0.2f;
    private static final float B = 0.8f;
    private static final float A = 0.95f;

    private static boolean warnedNullMatrices;
    /** When rat detector debug is on: log once that the render phase ran with targets. */
    private static boolean renderDebugLoggedOnce;

    private static final RenderLayer TRACER_LINES = RenderLayer.of(
            "farmhelperv2_rat_tracer",
            RenderSetup.builder(RenderPipelines.LINES).build());

    private RatTracerRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(RatTracerRenderer::drawTracers);
    }

    private static void drawTracers(WorldRenderContext context) {
        if (!ModConfig.isRatDetectorEnabled() || !ModConfig.isRatTracerEnabled()) {
            return;
        }
        List<RatDetector.RatTarget> targets = RatDetector.getTargets();
        if (targets.isEmpty()) {
            return;
        }

        if (ModConfig.isRatDetectorDebugLogging()) {
            if (!renderDebugLoggedOnce) {
                renderDebugLoggedOnce = true;
                FarmHelperFabric.LOGGER.info(
                        "[MTEU Rat] BEFORE_DEBUG_RENDER: drawing {} tracer target(s) (one-time log per debug session)",
                        targets.size());
            }
        } else {
            renderDebugLoggedOnce = false;
        }

        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            if (!warnedNullMatrices) {
                warnedNullMatrices = true;
                FarmHelperFabric.LOGGER.warn("[MTEU Rat] WorldRenderContext.matrices() is null; tracers skipped.");
            }
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }

        Camera camera = context.gameRenderer().getCamera();
        VertexConsumerProvider consumers = context.consumers();
        if (camera == null || consumers == null) {
            return;
        }

        Vec3d camPos = camera.getCameraPos();
        Vec3d fromWorld = camPos.add(0.0D, -0.1D, 0.0D);

        int ri = Math.min(255, Math.max(0, Math.round(R * 255.0f)));
        int gi = Math.min(255, Math.max(0, Math.round(G * 255.0f)));
        int bi = Math.min(255, Math.max(0, Math.round(B * 255.0f)));
        int ai = Math.min(255, Math.max(0, Math.round(A * 255.0f)));

        matrices.push();
        try {
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
            VertexConsumer lines = consumers.getBuffer(TRACER_LINES);
            for (RatDetector.RatTarget target : targets) {
                drawLineWorld(matrices, lines, fromWorld, target.pos(), ri, gi, bi, ai);
            }
        } finally {
            matrices.pop();
        }
    }

    private static void drawLineWorld(
            MatrixStack matrices,
            VertexConsumer consumer,
            Vec3d fromWorld,
            Vec3d toWorld,
            int r,
            int g,
            int b,
            int a) {
        MatrixStack.Entry entry = matrices.peek();
        Vec3d dir = toWorld.subtract(fromWorld);
        double len = dir.length();
        if (len < 1.0e-4D) {
            return;
        }
        Vec3d n = dir.multiply(1.0D / len);
        float nx = (float) n.x;
        float ny = (float) n.y;
        float nz = (float) n.z;

        consumer
                .vertex(entry, (float) fromWorld.x, (float) fromWorld.y, (float) fromWorld.z)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
        consumer
                .vertex(entry, (float) toWorld.x, (float) toWorld.y, (float) toWorld.z)
                .color(r, g, b, a)
                .normal(entry, nx, ny, nz);
    }
}
