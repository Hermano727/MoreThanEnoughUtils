package com.jelly.farmhelperv2.gui.config;

import com.jelly.farmhelperv2.config.CropMacroType;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;

/**
 * Vanilla config screen for MoreThanEnoughTils (no LibGui).
 */
public final class MoreThanEnoughTilsConfigScreen extends Screen {

    private static final int[] DELAY_PRESETS_MS = { 50, 100, 250, 500, 750, 1000, 1500, 2000 };

    private final Screen parent;
    private CheckboxWidget pestDestroyerCheckbox;
    private CheckboxWidget autoExperimentsCheckbox;
    private ButtonWidget cropTypeButton;
    private ButtonWidget delayButton;

    public MoreThanEnoughTilsConfigScreen(Screen parent) {
        super(Text.literal("MoreThanEnoughTils Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = 40;
        int rowHeight = 24;
        int buttonWidth = 220;

        // Crop / Macro Type
        cropTypeButton = ButtonWidget.builder(
                Text.literal("Crop: " + ModConfig.getCropType().getDisplayName()),
                b -> {
                    CropMacroType next = nextCropType(ModConfig.getCropType());
                    ModConfig.setCropType(next);
                    ModConfig.save();
                    cropTypeButton.setMessage(Text.literal("Crop: " + next.getDisplayName()));
                })
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build();
        addDrawableChild(cropTypeButton);
        y += rowHeight + 4;

        pestDestroyerCheckbox = CheckboxWidget.builder(
                Text.literal("Pest Destroyer"),
                textRenderer)
                .pos(centerX - buttonWidth / 2, y)
                .checked(ModConfig.isPestDestroyerEnabled())
                .callback((checkbox, checked) -> {
                    ModConfig.setPestDestroyerEnabled(checked);
                    ModConfig.save();
                })
                .build();
        addDrawableChild(pestDestroyerCheckbox);
        y += rowHeight + 4;

        autoExperimentsCheckbox = CheckboxWidget.builder(
                Text.literal("Auto Enchanting Experiments"),
                textRenderer)
                .pos(centerX - buttonWidth / 2, y)
                .checked(ModConfig.isAutoExperimentsEnabled())
                .callback((checkbox, checked) -> {
                    ModConfig.setAutoExperimentsEnabled(checked);
                    ModConfig.save();
                })
                .build();
        addDrawableChild(autoExperimentsCheckbox);
        y += rowHeight + 4;

        delayButton = ButtonWidget.builder(
                Text.literal("Click Delay: " + ModConfig.getAutoExperimentsClickDelayMs() + " ms"),
                b -> {
                    int current = ModConfig.getAutoExperimentsClickDelayMs();
                    int idx = 0;
                    for (int i = 0; i < DELAY_PRESETS_MS.length; i++) {
                        if (DELAY_PRESETS_MS[i] >= current) {
                            idx = i;
                            break;
                        }
                        idx = i + 1;
                    }
                    if (idx >= DELAY_PRESETS_MS.length) idx = DELAY_PRESETS_MS.length - 1;
                    idx = (idx + 1) % DELAY_PRESETS_MS.length;
                    int next = DELAY_PRESETS_MS[idx];
                    ModConfig.setAutoExperimentsClickDelayMs(next);
                    ModConfig.save();
                    delayButton.setMessage(Text.literal("Click Delay: " + next + " ms"));
                })
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build();
        addDrawableChild(delayButton);
        y += rowHeight + 16;

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(centerX - 100, Math.min(y, height - 28), 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw dark background without blur to avoid "Can only blur once per frame"
        // when another mod (e.g. Architectury) already blurred the screen.
        context.fill(0, 0, width, height, 0xC0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Keybinds: Options -> Controls"),
                width / 2,
                72,
                0xA0A0A0
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private static CropMacroType nextCropType(CropMacroType current) {
        CropMacroType[] values = CropMacroType.values();
        if (values.length == 0) return CropMacroType.S_SHAPE_VERTICAL;
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                idx = (i + 1) % values.length;
                return values[idx];
            }
        }
        return values[0];
    }
}
