package com.jelly.farmhelperv2.gui.config;

import com.jelly.farmhelperv2.config.CropMacroType;
import com.jelly.farmhelperv2.config.ModConfig;
import com.jelly.farmhelperv2.freecam.MteuFreecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Vanilla config screen for MoreThanEnoughUtils (no LibGui).
 */
public final class MoreThanEnoughUtilsConfigScreen extends Screen {

    private static final int[] DELAY_PRESETS_MS = { 50, 100, 250, 500, 750, 1000, 1500, 2000 };
    private static final double[] FREECAM_H_PRESETS = { 0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0 };
    private static final double[] FREECAM_V_PRESETS = { 0.25, 0.5, 0.6, 1.0, 1.5, 2.0, 3.0 };

    private final Screen parent;
    private CheckboxWidget pestDestroyerCheckbox;
    private CheckboxWidget autoExperimentsCheckbox;
    private CheckboxWidget freecamNotifyCheckbox;
    private ButtonWidget cropTypeButton;
    private ButtonWidget delayButton;
    private ButtonWidget freecamHorizontalButton;
    private ButtonWidget freecamVerticalButton;

    public MoreThanEnoughUtilsConfigScreen(Screen parent) {
        super(Text.literal("MoreThanEnoughUtils Config"));
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

        freecamHorizontalButton = ButtonWidget.builder(
                Text.literal("Freecam horizontal: " + formatSpeed(ModConfig.getFreecamHorizontalSpeed())),
                b -> {
                    double next = nextPreset(FREECAM_H_PRESETS, ModConfig.getFreecamHorizontalSpeed());
                    ModConfig.setFreecamHorizontalSpeed(next);
                    ModConfig.save();
                    freecamHorizontalButton.setMessage(Text.literal("Freecam horizontal: " + formatSpeed(next)));
                })
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build();
        addDrawableChild(freecamHorizontalButton);
        y += rowHeight + 4;

        freecamVerticalButton = ButtonWidget.builder(
                Text.literal("Freecam vertical: " + formatSpeed(ModConfig.getFreecamVerticalSpeed())),
                b -> {
                    double next = nextPreset(FREECAM_V_PRESETS, ModConfig.getFreecamVerticalSpeed());
                    ModConfig.setFreecamVerticalSpeed(next);
                    ModConfig.save();
                    freecamVerticalButton.setMessage(Text.literal("Freecam vertical: " + formatSpeed(next)));
                })
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build();
        addDrawableChild(freecamVerticalButton);
        y += rowHeight + 4;

        freecamNotifyCheckbox = CheckboxWidget.builder(
                Text.literal("Freecam: chat on toggle"),
                textRenderer)
                .pos(centerX - buttonWidth / 2, y)
                .checked(ModConfig.isFreecamNotifyMessages())
                .callback((checkbox, checked) -> {
                    ModConfig.setFreecamNotifyMessages(checked);
                    ModConfig.save();
                })
                .build();
        addDrawableChild(freecamNotifyCheckbox);
        y += rowHeight + 4;

        addDrawableChild(ButtonWidget.builder(Text.literal("Toggle Freecam"), b -> {
                    MinecraftClient c = MinecraftClient.getInstance();
                    if (c != null) {
                        MteuFreecam.toggle(c);
                    }
                })
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build());
        y += rowHeight + 4;

        delayButton = ButtonWidget.builder(
                Text.literal("Click Delay: " + ModConfig.getAutoExperimentsClickDelayMs() + " ms"),
                b -> {
                    int current = ModConfig.getAutoExperimentsClickDelayMs();
                    int currentIdx = 0;
                    while (currentIdx < DELAY_PRESETS_MS.length && DELAY_PRESETS_MS[currentIdx] < current) currentIdx++;
                    if (currentIdx >= DELAY_PRESETS_MS.length) currentIdx = DELAY_PRESETS_MS.length - 1;
                    int nextIdx = (currentIdx + 1) % DELAY_PRESETS_MS.length;
                    int next = DELAY_PRESETS_MS[nextIdx];
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
                Text.literal("Keybinds: Options -> Controls -> MoreThanEnoughUtils (Freecam unbound by default)"),
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
        return values[(current.ordinal() + 1) % values.length];
    }

    private static String formatSpeed(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double nextPreset(double[] presets, double current) {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] > current + 1.0e-6) {
                return presets[i];
            }
        }
        return presets[0];
    }
}
