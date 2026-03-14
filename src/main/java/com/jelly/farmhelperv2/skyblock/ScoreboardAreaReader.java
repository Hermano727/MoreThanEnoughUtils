package com.jelly.farmhelperv2.skyblock;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reads the sidebar scoreboard to infer the current SkyBlock area.
 */
public final class ScoreboardAreaReader {

    private ScoreboardAreaReader() {
    }

    public enum Area {
        UNKNOWN,
        GARDEN,
        OTHER
    }

    public static Area getCurrentArea(MinecraftClient client) {
        if (client == null || client.world == null) {
            return Area.UNKNOWN;
        }

        List<String> lines = getSidebarLines(client);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            int idx = line.indexOf("Area:");
            if (idx == -1) continue;

            String areaName = line.substring(idx + "Area:".length()).trim();
            String lower = areaName.toLowerCase();
            if (lower.contains("garden") || lower.contains("barn")) {
                return Area.GARDEN;
            }

            // We found an explicit Area line that is not Garden/Barn.
            return Area.OTHER;
        }

        // No explicit Area line yet; treat as UNKNOWN so we don't overreact.
        return Area.UNKNOWN;
    }

    public static boolean isInGarden(MinecraftClient client) {
        Area area = getCurrentArea(client);
        return area == Area.GARDEN || area == Area.UNKNOWN;
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
            String line = getEntryDisplayText(entry);
            if (line != null && !line.isEmpty()) {
                result.add(line);
            }
        }

        return result;
    }

    /**
     * Gets the visible text for a scoreboard entry (1.21+: display override or name).
     * Strips Minecraft formatting codes (§x) so "§f Area: §bGarden" can be matched.
     */
    private static String getEntryDisplayText(ScoreboardEntry entry) {
        net.minecraft.text.Text text = entry.display() != null ? entry.display() : entry.name();
        if (text == null) return "";
        return stripFormatting(text.getString());
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

