package com.jelly.farmhelperv2.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Utility helpers for consistent MTEU chat styling.
 */
public final class ChatUtils {

    private ChatUtils() {
    }

    public static MutableText prefix(String body, Formatting bodyColor) {
        return Text.literal("")
                .append(Text.literal("[MTEU] ").formatted(Formatting.GOLD))
                .append(Text.literal(body).formatted(bodyColor));
    }

    public static MutableText info(String body) {
        return prefix(body, Formatting.AQUA);
    }

    public static MutableText success(String body) {
        return prefix(body, Formatting.GREEN);
    }

    public static MutableText warning(String body) {
        return prefix(body, Formatting.YELLOW);
    }

    public static MutableText error(String body) {
        return prefix(body, Formatting.RED);
    }
}

