package com.jelly.farmhelperv2.pests;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Minimal scoreboard reader for Garden pest counts on 1.21.
 *
 * The logic mirrors the original 1.8.9 implementation conceptually:
 * scan the sidebar scoreboard for a line containing both "The Garden"
 * and the special pest symbol, then parse the trailing "Nx" integer.
 */
public final class ScoreboardPestReader {

    private ScoreboardPestReader() {
    }

    /**
     * Returns the total Garden pest count parsed from the sidebar scoreboard,
     * or 0 if the scoreboard is unavailable or the line cannot be parsed.
     */
    public static int getGardenPestCount(MinecraftClient client) {
        if (client == null || client.world == null) {
            return 0;
        }

        List<String> lines = getSidebarLines(client);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.contains("The Garden") || !line.contains("ൠ")) {
                continue;
            }
            String[] split = line.split(" ");
            if (split.length == 0) {
                continue;
            }
            String last = split[split.length - 1];
            // Expected format is something like "15x" – strip non-digits.
            String digits = last.replace("x", "").replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                // Fall back to 0 if parsing fails.
                return 0;
            }
        }

        return 0;
    }

    private static List<String> getSidebarLines(MinecraftClient client) {
        List<String> result = new ArrayList<>();

        Scoreboard scoreboard = client.world.getScoreboard();
        if (scoreboard == null) {
            return result;
        }

        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return result;
        }

        Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);
        for (ScoreboardEntry entry : entries) {
            // entry.name() is already a Text with formatting; getString() gives us clean text.
            result.add(entry.name().getString());
        }

        return result;
    }
}

