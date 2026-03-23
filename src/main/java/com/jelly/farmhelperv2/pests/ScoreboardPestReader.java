package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Scoreboard reader for Garden pest counts on 1.21.
 *
 * Behaviour:
 * - Prefer parsing a dedicated Garden pest line when present.
 * - Fall back to 0 when clearly no pest information is available.
 * - Optionally logs raw sidebar lines when verbose logging is enabled.
 */
public final class ScoreboardPestReader {

    private ScoreboardPestReader() {
    }

    /**
     * Returns the total Garden pest count parsed from the sidebar scoreboard.
     * <p>
     * This method is deliberately defensive:
     * - If no sidebar or lines are available, returns 0.
     * - If a line clearly encodes a pest count (contains the pest symbol or ends with an \"x\" count), attempts to parse it.
     * - If parsing fails, logs (in verbose mode) and falls back to 0 instead of throwing.
     */
    public static int getGardenPestCount(MinecraftClient client) {
        if (client == null || client.world == null) {
            return 0;
        }

        List<String> lines = getSidebarLines(client);
        if (lines.isEmpty()) {
            return 0;
        }

        if (ModConfig.isPestsDebugLogging()) {
            FarmHelperFabric.LOGGER.debug("MTEU scoreboard lines: {}", lines);
        }

        // First pass: look for the most specific pattern – a Garden line with an \"xN\" count.
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.contains("The Garden")) {
                continue;
            }
            Integer value = extractTrailingCount(line);
            if (value != null) {
                return value;
            }
        }

        // Second pass: look for any line mentioning pests with a trailing count (tab widget, alternate formats).
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (!line.toLowerCase().contains("pest")) {
                continue;
            }
            Integer value = extractTrailingCount(line);
            if (value != null) {
                return value;
            }
        }

        // Third pass: very generic \"Nx\" or \"xN\" patterns at the end of the line.
        for (String rawLine : lines) {
            String line = rawLine.trim();
            Integer value = extractTrailingCount(line);
            if (value != null) {
                return value;
            }
        }

        return 0;
    }

    /**
     * Attempts to extract an integer pest count from the end of a scoreboard line.
     * Accepts formats like:
     * - \"15x\"
     * - \"x15\"
     * - \"Pests: 15x\"
     * - \"Pests 15\"
     */
    private static Integer extractTrailingCount(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] split = line.split(" ");
        if (split.length == 0) {
            return null;
        }
        // Look at the last non-empty token.
        String last = null;
        for (int i = split.length - 1; i >= 0; i--) {
            if (!split[i].isEmpty()) {
                last = split[i];
                break;
            }
        }
        if (last == null || last.isEmpty()) {
            return null;
        }
        // Strip common suffix/prefix markers like \"x\".
        String candidate = last.replace("x", "").replace("X", "");
        String digits = candidate.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                if (ModConfig.isPestsDebugLogging()) {
                    FarmHelperFabric.LOGGER.debug("Failed to parse pest count from line token '{}'", last, e);
                }
                return null;
            }
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
            Text text = entry.display() != null ? entry.display() : entry.name();
            if (text == null) {
                continue;
            }
            String line = stripFormatting(text.getString());
            if (!line.isEmpty()) {
                result.add(line);
            }
        }

        return result;
    }

    /** Removes § and the following character so "§f Area: §bGarden" becomes " Area: Garden". */
    private static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\u00a7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(s.charAt(i));
        }
        return out.toString();
    }
}

