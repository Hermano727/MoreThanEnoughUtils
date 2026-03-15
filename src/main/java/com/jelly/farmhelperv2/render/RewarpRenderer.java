package com.jelly.farmhelperv2.render;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.config.RewarpPoint;
import com.jelly.farmhelperv2.skyblock.ScoreboardAreaReader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Renders a light blue outline box at each rewarp point when in Garden (Fabric 1.21).
 * Uses Fabric WorldRenderEvents and vanilla DebugRenderer via reflection so the module
 * compiles without requiring fabric-rendering-v1 on the compile classpath.
 */
public final class RewarpRenderer {

    /** Light blue / cyan, similar to FarmHelper rewarp color (0, 255, 217). */
    private static final float R = 0.0f;
    private static final float G = 1.0f;
    private static final float B = 0.85f;
    private static final float A = 0.9f;

    private static final double MAX_DISTANCE = 50.0;

    public static void register() {
        try {
            Class<?> eventClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents");
            Object afterEntities = eventClass.getField("AFTER_ENTITIES").get(null);
            Method registerMethod = afterEntities.getClass().getMethod("register", Object.class);
            Class<?> listenerClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents$AfterEntities");
            Object proxy = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[] { listenerClass },
                    (proxyInstance, method, args) -> {
                        if (args != null && args.length > 0) drawRewarps(args[0]);
                        return null;
                    });
            registerMethod.invoke(afterEntities, proxy);
        } catch (Throwable t) {
            FarmHelperFabric.LOGGER.warn("Could not register rewarp world render (Fabric rendering API not available?). Rewarp boxes will not be drawn.", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void drawRewarps(Object context) {
        try {
            Method getCamera = context.getClass().getMethod("camera");
            Method getMatrices = context.getClass().getMethod("matrixStack");
            Method getConsumers = context.getClass().getMethod("consumers");
            Camera camera = (Camera) getCamera.invoke(context);
            MatrixStack matrices = (MatrixStack) getMatrices.invoke(context);
            VertexConsumerProvider consumers = (VertexConsumerProvider) getConsumers.invoke(context);
            if (camera == null || matrices == null || consumers == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            if (!ScoreboardAreaReader.isInGarden(mc)) return;

            var rewarps = ModConfig.getRewarps();
            if (rewarps.isEmpty()) return;

            Vec3d camPos = camera.getPos();
            for (RewarpPoint r : rewarps) {
                double dist = Math.sqrt(
                        Math.pow(r.x - camPos.x, 2) + Math.pow(r.y - camPos.y, 2) + Math.pow(r.z - camPos.z, 2));
                if (dist > MAX_DISTANCE) continue;

                matrices.push();
                matrices.translate(r.x - camPos.x, r.y - camPos.y, r.z - camPos.z);
                drawBox(matrices, consumers, new Box(0, 0, 0, 1, 1, 1), R, G, B, A);
                matrices.pop();
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
