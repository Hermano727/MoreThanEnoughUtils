package com.jelly.farmhelperv2.config;

import com.jelly.farmhelperv2.gui.config.ChatShortcutsEditorScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * YACL option definitions for the MoreThanEnoughUtils config screen.
 */
public final class ModOptions {

    private ModOptions() {
    }

    public static ConfigCategory category() {
        return ConfigCategory.createBuilder()
                .name(Text.literal("MoreThanEnoughUtils"))
                .group(macroGroup())
                .group(pestsGroup())
                .group(experimentsGroup())
                .group(chatShortcutsGroup())
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
                        .controller(option -> EnumControllerBuilder.create(option)
                                .enumClass(CropMacroType.class)
                                .valueFormatter(v -> Text.literal(v.getDisplayName())))
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

