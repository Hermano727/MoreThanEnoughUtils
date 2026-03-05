package com.jelly.farmhelperv2.skyblock;

import com.jelly.farmhelperv2.FarmHelperFabric;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/**
 * Port of the Odin Auto Experiments feature (Chronomatron / Ultrasequencer)
 * to Fabric 1.21.
 *
 * This implementation intentionally adds verbose debug logging so we can
 * validate item / sound differences between 1.8.9 and 1.21 while iterating.
 */
public final class AutoExperiments {

    private static final Logger LOGGER = FarmHelperFabric.LOGGER;

    // Config-like constants.
    private static final boolean AUTO_CLOSE = true;
    private static final int SERUM_COUNT = 0;
    private static final boolean GET_MAX_XP = false;

    private static final Map<Integer, Integer> ultrasequencerOrder = new HashMap<>();
    private static final List<Integer> chronomatronOrder = new ArrayList<>(28);

    private static long lastClickTimeMs = 0L;
    private static boolean hasAdded = false;
    private static int lastAddedSlotId = 0;
    private static int clicks = 0;

    private static Item lastChronoCenterItem = null;
    private static Item lastUltraCenterItem = null;

    private static Boolean lastLoggedConfigEnabled = null;

    private AutoExperiments() {
    }

    public static void onClientTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        boolean configEnabled = ModConfig.isAutoExperimentsEnabled();
        lastLoggedConfigEnabled = configEnabled;

        Screen screen = mc.currentScreen;
        if (screen == null) {
            reset();
            return;
        }

        if (!(screen instanceof HandledScreen<?>)) {
            reset();
            return;
        }

        HandledScreen<?> handledScreen = (HandledScreen<?>) screen;

        Text title = handledScreen.getTitle();
        String titleString = title.getString();

        ScreenHandler handler = handledScreen.getScreenHandler();
        List<Slot> slots = handler.slots;

        if (titleString.startsWith("Chronomatron")) {
            solveChronomatron(mc, handler, slots, titleString);
        } else if (titleString.startsWith("Ultrasequencer")) {
            solveUltrasequencer(mc, handler, slots, titleString);
        }
    }

    private static void reset() {
        ultrasequencerOrder.clear();
        chronomatronOrder.clear();
        hasAdded = false;
        lastAddedSlotId = 0;
        clicks = 0;
        lastChronoCenterItem = null;
        lastUltraCenterItem = null;
    }

    private static void solveChronomatron(MinecraftClient mc, ScreenHandler handler, List<Slot> slots, String title) {
        int maxChronomatron = GET_MAX_XP ? 15 : 11 - SERUM_COUNT;

        if (slots.size() <= 49) {
            return;
        }

        Slot centerSlot = slots.get(49);
        ItemStack centerStack = centerSlot.getStack();
        Item centerItem = centerStack.getItem();

        if (centerItem != lastChronoCenterItem) {
            Identifier id = Registries.ITEM.getId(centerItem);
            lastChronoCenterItem = centerItem;
        }

        if (centerItem == Blocks.GLOWSTONE.asItem()
                && lastAddedSlotId >= 0
                && lastAddedSlotId < slots.size()) {
            ItemStack lastStack = slots.get(lastAddedSlotId).getStack();
            boolean lastGlint = lastStack.hasGlint();
            boolean lastEnch = lastStack.hasEnchantments();
            if (!lastStack.isEmpty() && !lastGlint && !lastEnch) {
                if (AUTO_CLOSE && chronomatronOrder.size() > maxChronomatron && mc.player != null) {
                    mc.player.closeHandledScreen();
                }
                hasAdded = false;
            }
        }

        if (!hasAdded && centerItem == Items.CLOCK) {
            for (Slot slot : slots) {
                int slotId = slot.id;
                if (slotId < 10 || slotId > 43) continue;
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                boolean hasGlint = stack.hasGlint();
                boolean hasEnchants = stack.hasEnchantments();

                if (hasGlint || hasEnchants) {
                    chronomatronOrder.add(slotId);
                    lastAddedSlotId = slotId;
                    hasAdded = true;
                    clicks = 0;
                    break;
                }
            }
        }

        if (hasAdded
                && centerItem == Items.CLOCK
                && chronomatronOrder.size() > clicks
                && System.currentTimeMillis() - lastClickTimeMs > ModConfig.getAutoExperimentsClickDelayMs()) {
            int targetSlotId = chronomatronOrder.get(clicks);
            clickSlot(mc, handler, targetSlotId);
            lastClickTimeMs = System.currentTimeMillis();
            clicks++;
        }
    }

    private static void solveUltrasequencer(MinecraftClient mc, ScreenHandler handler, List<Slot> slots, String title) {
        int maxUltrasequencer = GET_MAX_XP ? 20 : 9 - SERUM_COUNT;

        if (slots.size() <= 49) {
            return;
        }

        Slot centerSlot = slots.get(49);
        ItemStack centerStack = centerSlot.getStack();
        Item centerItem = centerStack.getItem();

        if (centerItem != lastUltraCenterItem) {
            lastUltraCenterItem = centerItem;
        }

        if (centerItem == Items.CLOCK) {
            hasAdded = false;
        }

        // When the center is glowstone, build the order from the special items in slots 9..44.
        if (!hasAdded && centerItem == Blocks.GLOWSTONE.asItem()) {
            Slot slot44 = slots.get(44);
            if (slot44.getStack().isEmpty()) {
                // This matches the Odin guard; if slot 44 is empty the game likely hasn't started yet.
                return;
            }

            List<int[]> candidates = new ArrayList<>();
            ultrasequencerOrder.clear();
            for (Slot slot : slots) {
                int slotId = slot.id;
                if (slotId < 9 || slotId > 44) continue;

                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                String path = itemId.getPath();

                if (path.contains("glass") || path.contains("pane")) {
                    continue;
                }

                candidates.add(new int[]{slotId, stack.getCount()});
            }

            candidates.sort(Comparator.comparingInt(a -> a[1]));

            int index = 0;
            for (int[] entry : candidates) {
                int slotId = entry[0];
                ultrasequencerOrder.put(index, slotId);
                index++;
            }

            hasAdded = true;
            clicks = 0;

            if (ultrasequencerOrder.size() > maxUltrasequencer && AUTO_CLOSE && mc.player != null) {
                mc.player.closeHandledScreen();
            }
        }

        if (centerItem == Items.CLOCK
                && ultrasequencerOrder.containsKey(clicks)
                && System.currentTimeMillis() - lastClickTimeMs > ModConfig.getAutoExperimentsClickDelayMs()) {
            Integer targetSlotId = ultrasequencerOrder.get(clicks);
            if (targetSlotId != null) {
                clickSlot(mc, handler, targetSlotId);
                lastClickTimeMs = System.currentTimeMillis();
                clicks++;
            }
        }
    }

    private static void clickSlot(MinecraftClient mc, ScreenHandler handler, int slotId) {
        if (mc.interactionManager == null || mc.player == null) return;
        try {
            mc.interactionManager.clickSlot(
                    handler.syncId,
                    slotId,
                    0, // button 0 = left click
                    SlotActionType.PICKUP,
                    mc.player
            );
        } catch (Exception e) {
            LOGGER.warn("AutoExperiments: Failed to click slot {} in handler {}.", slotId, handler, e);
        }
    }
}

