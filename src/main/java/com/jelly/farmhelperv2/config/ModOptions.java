package com.jelly.farmhelperv2.config;

import com.jelly.farmhelperv2.gui.config.ChatShortcutsEditorScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.text.Text;

/**
 * YACL option definitions for the MoreThanEnoughUtils config screen.
 */
public final class ModOptions {

    private ModOptions() {
    }

    /** Single category (legacy); prefer farmingCategory() + otherCategory() for structured GUI. */
    public static ConfigCategory category() {
        return ConfigCategory.createBuilder()
                .name(Text.literal("MoreThanEnoughUtils"))
                .group(macroGroup())
                .group(rewarpGroup())
                .group(pestsGroup())
                .group(fishGroup())
                .group(freecamGroup())
                .group(experimentsGroup())
                .group(chatShortcutsGroup())
                .build();
    }

    /** Farming section: Macro, Rewarp, Pests. Expand to see options. */
    public static ConfigCategory farmingCategory() {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Farming"))
                .group(macroGroup())
                .group(rewarpGroup())
                .group(pestsGroup())
                .build();
    }

    /** Fish section: Auto Fishing options. */
    public static ConfigCategory fishCategory() {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Fish"))
                .group(fishGroup())
                .build();
    }

    /** Other section: Logging, Enchanting Experiments, Chat Shortcuts. */
    public static ConfigCategory otherCategory() {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Other"))
                .group(loggingGroup())
                .group(freecamGroup())
                .group(experimentsGroup())
                .group(chatShortcutsGroup())
                .build();
    }

    private static OptionGroup freecamGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Freecam"))
                .description(OptionDescription.of(Text.literal(
                        "Detach the camera on the client only; your character stays still. "
                                + "Bind Toggle Freecam under Options → Controls → MoreThanEnoughUtils (unbound by default). "
                                + "May be against rules on some multiplayer servers — use at your own risk."
                )))
                .option(Option.<Double>createBuilder()
                        .name(Text.literal("Horizontal speed"))
                        .description(OptionDescription.of(Text.literal("How fast you move along the ground plane (WASD), relative to where you look.")))
                        .binding(
                                1.0,
                                ModConfig::getFreecamHorizontalSpeed,
                                ModConfig::setFreecamHorizontalSpeed
                        )
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(0.05, 5.0)
                                .step(0.05)
                                .formatValue(v -> Text.literal(String.format("%.2f", v))))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(Text.literal("Vertical speed"))
                        .description(OptionDescription.of(Text.literal("Up/down speed (jump / sneak) while in freecam.")))
                        .binding(
                                0.6,
                                ModConfig::getFreecamVerticalSpeed,
                                ModConfig::setFreecamVerticalSpeed
                        )
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(0.05, 5.0)
                                .step(0.05)
                                .formatValue(v -> Text.literal(String.format("%.2f", v))))
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Chat messages on toggle"))
                        .description(OptionDescription.of(Text.literal("Show short enable/disable lines in chat when you turn freecam on or off.")))
                        .binding(
                                true,
                                ModConfig::isFreecamNotifyMessages,
                                ModConfig::setFreecamNotifyMessages
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .build();
    }

    private static OptionGroup loggingGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Logging"))
                .description(OptionDescription.of(Text.literal("Show macro state and area change messages in chat. When off, only enable/disable and failure reasons are shown.")))
                .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Verbose logging"))
                        .description(OptionDescription.of(Text.literal("Show macro state changes (e.g. Vertical: ..., S-Shape: switching direction) and area change messages in chat.")))
                        .binding(
                                false,
                                ModConfig::isVerboseLogging,
                                ModConfig::setVerboseLogging
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .build();
    }

    /**
     * Chat Shortcuts: one group with a button that opens the unified editor.
     * Do not add a YACL ListOption here with .option(...) — ListOption must be added
     * to the category with .group(listOption), not inside an OptionGroup (throws otherwise).
     */
    private static OptionGroup chatShortcutsGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Chat Shortcuts"))
                .description(OptionDescription.of(Text.literal(
                        "Add chat commands and set a keybind for each. Open the editor to see each command with its key next to it; click the key to change it (Esc to unbind)."
                )))
                .option(ButtonOption.createBuilder()
                        .name(Text.literal("Edit Chat Shortcuts"))
                        .description(OptionDescription.of(Text.literal(
                                "Edit commands and keybinds in one place. Each row shows the command and its key; click the key then press a key to bind."
                        )))
                        .action((screen, option) -> {
                            if (MinecraftClient.getInstance().currentScreen != null) {
                                MinecraftClient.getInstance().setScreen(
                                        new ChatShortcutsEditorScreen(MinecraftClient.getInstance().currentScreen));
                            }
                        })
                        .build())
                .build();
    }

    private static OptionGroup macroGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Macro"))
                .description(OptionDescription.of(Text.literal("Choose which crop macro preset to use.")))
                .option(Option.<CropMacroType>createBuilder()
                        .name(Text.literal("Crop Preset"))
                        .description(OptionDescription.of(Text.literal("Select the crop macro type.")))
                        .binding(
                                CropMacroType.S_SHAPE_VERTICAL,
                                ModConfig::getCropType,
                                ModConfig::setCropType
                        )
                        .controller(option -> EnumDropdownControllerBuilder.create(option)
                                .formatValue(v -> Text.literal(v.getDisplayName())))
                        .build())
                .build();
    }

    private static OptionGroup rewarpGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Rewarp"))
                .description(OptionDescription.of(Text.literal(
                        "When you walk over any saved rewarp point, /warp garden is run automatically. Add points from your current position; remove the one closest to you."
                )))
                .option(ButtonOption.createBuilder()
                        .name(Text.literal("Add rewarp"))
                        .description(OptionDescription.of(Text.literal("Save your current block position as a rewarp point. Stand where you want the rewarp, then open config and click this.")))
                        .action((screen, option) -> {
                            if (ModConfig.addRewarpAtPlayer()) {
                                if (MinecraftClient.getInstance().player != null) {
                                    MinecraftClient.getInstance().player.sendMessage(
                                            Text.literal("§aAdded rewarp at current position. Points: " + ModConfig.getRewarps().size()),
                                            false
                                    );
                                }
                            } else {
                                if (MinecraftClient.getInstance().player != null) {
                                    MinecraftClient.getInstance().player.sendMessage(
                                            Text.literal("§cCould not add rewarp (already at a rewarp point or not in world)."),
                                            false
                                    );
                                }
                            }
                        })
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Text.literal("Remove rewarp closest to you"))
                        .description(OptionDescription.of(Text.literal("Removes the rewarp point nearest to your current position.")))
                        .action((screen, option) -> {
                            if (ModConfig.removeClosestRewarp()) {
                                if (MinecraftClient.getInstance().player != null) {
                                    MinecraftClient.getInstance().player.sendMessage(
                                            Text.literal("§aRemoved closest rewarp. Remaining: " + ModConfig.getRewarps().size()),
                                            false
                                    );
                                }
                            } else {
                                if (MinecraftClient.getInstance().player != null) {
                                    MinecraftClient.getInstance().player.sendMessage(
                                            Text.literal("§cNo rewarps to remove."),
                                            false
                                    );
                                }
                            }
                        })
                        .build())
                .build();
    }

    private static OptionGroup pestsGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Pests"))
                .description(OptionDescription.of(Text.literal("Basic Pest Destroyer toggle.")))
                .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Enable Pest Destroyer"))
                        .description(OptionDescription.of(Text.literal("Toggle the minimal Pest Destroyer helper.")))
                        .binding(
                                false,
                                ModConfig::isPestDestroyerEnabled,
                                ModConfig::setPestDestroyerEnabled
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .build();
    }

    private static OptionGroup fishGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Fish"))
                .description(OptionDescription.of(Text.literal("Auto fishing helper for bite detection, reel, and recast timing.")))
                .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Enable Auto Fishing"))
                        .description(OptionDescription.of(Text.literal("Run the fishing helper loop when toggled on with the fish keybind.")))
                        .binding(
                                false,
                                ModConfig::isAutoFishingEnabled,
                                ModConfig::setAutoFishingEnabled
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Text.literal("Set Toggle Keybind"))
                        .description(OptionDescription.of(Text.literal("Open Controls to set the Auto Fishing toggle keybind (default: O).")))
                        .action((screen, option) -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client != null) {
                                client.setScreen(new ControlsOptionsScreen(screen, client.options));
                            }
                        })
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Recast guard (ms)"))
                        .description(OptionDescription.of(Text.literal("Ignores duplicate bite triggers inside this window to avoid instant double-recasts.")))
                        .binding(
                                200,
                                ModConfig::getAutoFishingRecastGuardMs,
                                ModConfig::setAutoFishingRecastGuardMs
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(50, 2000)
                                .step(25)
                                .valueFormatter(val -> Text.literal(val + " ms")))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Reel delay min (ticks)"))
                        .description(OptionDescription.of(Text.literal("Minimum delay before reeling after a detected bite sound.")))
                        .binding(
                                3,
                                ModConfig::getAutoFishingReelDelayMinTicks,
                                ModConfig::setAutoFishingReelDelayMinTicks
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(0, 20)
                                .step(1)
                                .valueFormatter(val -> Text.literal(val + " ticks")))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Reel delay max (ticks)"))
                        .description(OptionDescription.of(Text.literal("Maximum delay before reeling after a detected bite sound.")))
                        .binding(
                                6,
                                ModConfig::getAutoFishingReelDelayMaxTicks,
                                ModConfig::setAutoFishingReelDelayMaxTicks
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(0, 20)
                                .step(1)
                                .valueFormatter(val -> Text.literal(val + " ticks")))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Recast delay min (ticks)"))
                        .description(OptionDescription.of(Text.literal("Minimum delay after reeling before recasting the rod.")))
                        .binding(
                                6,
                                ModConfig::getAutoFishingRecastDelayMinTicks,
                                ModConfig::setAutoFishingRecastDelayMinTicks
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(0, 40)
                                .step(1)
                                .valueFormatter(val -> Text.literal(val + " ticks")))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Recast delay max (ticks)"))
                        .description(OptionDescription.of(Text.literal("Maximum delay after reeling before recasting the rod.")))
                        .binding(
                                9,
                                ModConfig::getAutoFishingRecastDelayMaxTicks,
                                ModConfig::setAutoFishingRecastDelayMaxTicks
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(0, 40)
                                .step(1)
                                .valueFormatter(val -> Text.literal(val + " ticks")))
                        .build())
                .build();
    }

    private static OptionGroup experimentsGroup() {
        return OptionGroup.createBuilder()
                .name(Text.literal("Enchanting Experiments"))
                .description(OptionDescription.of(Text.literal("Configure the Auto Experiments helper.")))
                .option(Option.<Boolean>createBuilder()
                        .name(Text.literal("Enable Auto Experiments"))
                        .description(OptionDescription.of(Text.literal("Automatically click patterns for Chronomatron/Ultrasequencer.")))
                        .binding(
                                false,
                                ModConfig::isAutoExperimentsEnabled,
                                ModConfig::setAutoExperimentsEnabled
                        )
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Delay between clicks (ms)"))
                        .description(OptionDescription.of(Text.literal("Minimum time in milliseconds between each pattern click. Lower values are faster but may be less reliable (50–2000 ms).")))
                        .binding(
                                500,
                                ModConfig::getAutoExperimentsClickDelayMs,
                                ModConfig::setAutoExperimentsClickDelayMs
                        )
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(50, 2000)
                                .step(50)
                                .valueFormatter(val -> Text.literal(val + " ms")))
                        .build())
                .build();
    }

}

