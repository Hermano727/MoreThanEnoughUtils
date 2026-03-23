package com.jelly.farmhelperv2.pests;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Reads the Garden "tab widget" lines.
 *
 * Hypixel's Garden pest widget commonly appears in the player list (Tab menu),
 * not the sidebar scoreboard. This reader scrapes:
 * - Tab header/footer Text (if present)
 * - Player list entry display names (as rendered by PlayerListHud)
 *
 * and parses:
 * - Alive pests count (e.g. "Alive: 3")
 * - Infested plots list (e.g. "Plots: 8" or "Plots: 1,2,3")
 */
public final class TablistPestReader {

    private TablistPestReader() {
    }

    public static int getAlivePests(MinecraftClient client) {
        List<String> lines = getTabLines(client);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("alive")) continue;
            Integer value = extractTrailingInt(line);
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    /**
     * Returns the list of infested plot numbers parsed from the "Plots: 5, 13" tab line.
     * Returns an empty list when no plots are listed or parsing fails.
     */
    public static List<Integer> getInfestedPlots(MinecraftClient client) {
        String raw = getInfestedPlotsRaw(client);
        List<Integer> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String part : raw.split("[,\\s]+")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static String getInfestedPlotsRaw(MinecraftClient client) {
        List<String> lines = getTabLines(client);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("plots")) continue;
            int idx = line.indexOf(':');
            if (idx == -1) {
                continue;
            }
            return line.substring(idx + 1).trim();
        }
        return "";
    }

    /**
     * Returns a one-line debug string showing what the tab readers could find.
     * Intended for verbose in-game chat output.
     */
    public static String getDebugSummary(MinecraftClient client) {
        List<String> lines = getTabLines(client);
        String alive  = "?";
        String plots  = "?";
        for (String raw : lines) {
            String l = raw.trim().toLowerCase(Locale.ROOT);
            if (l.startsWith("alive"))  alive = raw.trim();
            if (l.startsWith("plots"))  plots = raw.trim();
        }
        return String.format("tab lines=%d | %s | %s", lines.size(), alive, plots);
    }

    private static Integer extractTrailingInt(String line) {
        int idx = line.indexOf(':');
        String tail = idx >= 0 ? line.substring(idx + 1) : line;
        String digits = tail.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> getTabLines(MinecraftClient client) {
        List<String> result = new ArrayList<>();
        if (client == null) return result;

        PlayerListHud playerListHud = client.inGameHud != null ? client.inGameHud.getPlayerListHud() : null;
        if (playerListHud == null) {
            return result;
        }

        // 1) Header/footer (no getters in Yarn 1.21.10; read fields reflectively)
        Text header = reflectTextField(playerListHud, "header");
        Text footer = reflectTextField(playerListHud, "footer");
        if (header != null) {
            addSplitLines(result, header.getString());
        }
        if (footer != null) {
            addSplitLines(result, footer.getString());
        }

        // 2) Player list entries (rendered names)
        ClientPlayNetworkHandler nh = client.getNetworkHandler();
        if (nh != null) {
            Collection<PlayerListEntry> entries = nh.getPlayerList();
            for (PlayerListEntry entry : entries) {
                Text t = playerListHud.getPlayerName(entry);
                if (t == null) continue;
                String s = t.getString();
                if (s == null || s.isEmpty()) continue;
                result.add(stripFormatting(s));
            }
        }

        if (ModConfig.isPestsDebugLogging()) {
            FarmHelperFabric.LOGGER.debug("MTEU tab lines: {}", result);
        }

        return result;
    }

    private static void addSplitLines(List<String> out, String text) {
        if (text == null || text.isEmpty()) return;
        String cleaned = stripFormatting(text);
        String[] split = cleaned.split("\\r?\\n");
        for (String s : split) {
            if (s != null && !s.trim().isEmpty()) {
                out.add(s);
            }
        }
    }

    private static Text reflectTextField(PlayerListHud hud, String fieldName) {
        try {
            Field f = PlayerListHud.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(hud);
            if (v instanceof Text) return (Text) v;
            if (v != null) {
                FarmHelperFabric.LOGGER.warn("MTEU TablistPestReader: field '{}' is {} not Text", fieldName, v.getClass().getSimpleName());
            }
            return null;
        } catch (NoSuchFieldException e) {
            // Log once so we know the Yarn field name changed.
            FarmHelperFabric.LOGGER.warn("MTEU TablistPestReader: PlayerListHud has no field '{}' — available fields: {}",
                    fieldName, getFieldNames(hud));
            return null;
        } catch (Exception e) {
            FarmHelperFabric.LOGGER.warn("MTEU TablistPestReader: could not read PlayerListHud.{}: {}", fieldName, e.toString());
            return null;
        }
    }

    private static String getFieldNames(PlayerListHud hud) {
        StringBuilder sb = new StringBuilder();
        for (java.lang.reflect.Field f : hud.getClass().getDeclaredFields()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName()).append('(').append(f.getType().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    /** Removes § and the following character so "§cPests" becomes "Pests". */
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

