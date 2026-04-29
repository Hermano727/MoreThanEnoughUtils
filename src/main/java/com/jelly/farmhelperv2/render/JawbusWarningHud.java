package com.jelly.farmhelperv2.render;

import com.jelly.farmhelperv2.FarmHelperFabric;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Screen-space warning overlay shown briefly when Jawbus chat text is detected.
 */
public final class JawbusWarningHud {
    private static final String WARNING_TEXT = "Jawbus found! Look in chat";
    private static final long WARNING_DURATION_MS = 3000L;

    private static volatile long warningUntilMs = 0L;
    /** When true, any non-empty chat/game line triggers the warning (for debugging). */
    private static volatile boolean debugTriggerOnAnyChat = false;

    private JawbusWarningHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
        FarmHelperFabric.LOGGER.info("Jawbus warning HUD registered (HudRenderCallback)");
    }

    public static void triggerWarning() {
        warningUntilMs = System.currentTimeMillis() + WARNING_DURATION_MS;
        // Use vanilla title rendering as a reliable center-screen fallback.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.execute(() -> {
                client.inGameHud.setTitle(Text.literal(WARNING_TEXT));
                // fadeIn, stay, fadeOut (ticks). 5 + 60 + 5 ~= 3.5s visible.
                client.inGameHud.setTitleTicks(5, 60, 5);
            });
        }
    }

    public static boolean isDebugTriggerOnAnyChat() {
        return debugTriggerOnAnyChat;
    }

    public static void setDebugTriggerOnAnyChat(boolean enabled) {
        debugTriggerOnAnyChat = enabled;
    }

    public static void toggleDebugTriggerOnAnyChat() {
        debugTriggerOnAnyChat = !debugTriggerOnAnyChat;
    }

    private static void render(DrawContext context) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        if (now > warningUntilMs) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.getWindow() == null) return;

        TextRenderer tr = client.textRenderer;
        Text text = Text.literal(WARNING_TEXT);
        int width = tr.getWidth(text);
        int x = (client.getWindow().getScaledWidth() - width) / 2;
        int y = client.getWindow().getScaledHeight() / 2 - 20;
        context.drawTextWithShadow(tr, text, x, y, 0xFFFF55);
    }
}
