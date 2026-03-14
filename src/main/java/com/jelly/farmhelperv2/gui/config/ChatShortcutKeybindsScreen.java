package com.jelly.farmhelperv2.gui.config;

import com.jelly.farmhelperv2.FarmHelperClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Screen to set keybinds for chat shortcuts from within the config.
 * Click a shortcut's button then press the key (or mouse button) to bind.
 * Escape while listening unbinds the key for that slot; Escape when not listening closes the screen.
 */
public final class ChatShortcutKeybindsScreen extends Screen {

    private final Screen parent;
    private final List<KeyBinding> bindings;
    private ButtonWidget[] keyButtons;
    /** When >= 0, we are listening for the next key/mouse press for this slot index. */
    private int listeningSlot = -1;

    public ChatShortcutKeybindsScreen(Screen parent) {
        super(Text.literal("Chat Shortcut Keybinds"));
        this.parent = parent;
        this.bindings = FarmHelperClient.getChatShortcutKeyBindings();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int rowHeight = 22;
        int keyButtonWidth = 120;
        int labelWidth = 100;
        int totalRowWidth = labelWidth + 8 + keyButtonWidth;
        int y = 40;
        int maxRows = Math.min(20, bindings.size());
        keyButtons = new ButtonWidget[maxRows];

        for (int i = 0; i < maxRows; i++) {
            final int slot = i;
            KeyBinding binding = bindings.get(i);
            Text label = Text.translatable("key.farmhelperv2.chat_shortcut." + (i + 1));
            ButtonWidget keyBtn = ButtonWidget.builder(
                    getKeyButtonText(binding),
                    b -> startListening(slot))
                    .dimensions(centerX - totalRowWidth / 2 + labelWidth + 8, y, keyButtonWidth, 20)
                    .build();
            keyButtons[i] = keyBtn;
            addDrawableChild(keyBtn);
            y += rowHeight;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(centerX - 100, height - 28, 200, 20)
                .build());
    }

    private void startListening(int slot) {
        listeningSlot = slot;
        if (slot >= 0 && slot < keyButtons.length) {
            keyButtons[slot].setMessage(Text.literal("Press key..."));
        }
    }

    private void stopListening(boolean updateButton) {
        if (updateButton && listeningSlot >= 0 && listeningSlot < keyButtons.length && keyButtons[listeningSlot] != null) {
            keyButtons[listeningSlot].setMessage(getKeyButtonText(bindings.get(listeningSlot)));
        }
        listeningSlot = -1;
    }

    private static Text getKeyButtonText(KeyBinding binding) {
        return binding.isUnbound()
                ? Text.literal("Unbound")
                : binding.getBoundKeyLocalizedText();
    }

    private void setKeyAndSave(int slot, InputUtil.Key key) {
        if (slot < 0 || slot >= bindings.size()) return;
        KeyBinding binding = bindings.get(slot);
        binding.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
        stopListening(true);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningSlot >= 0) {
            if (input.isEscape()) {
                setKeyAndSave(listeningSlot, InputUtil.UNKNOWN_KEY);
                return true;
            }
            InputUtil.Key key = InputUtil.fromKeyCode(input);
            setKeyAndSave(listeningSlot, key);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listeningSlot >= 0) {
            InputUtil.Key key = InputUtil.Type.MOUSE.createFromCode(click.button());
            setKeyAndSave(listeningSlot, key);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        if (listeningSlot >= 0) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("Press a key or mouse button... (Esc to unbind)"),
                    width / 2,
                    height / 2 - 20,
                    0xFFAAAA00
            );
        }
        int centerX = width / 2;
        int labelWidth = 100;
        int totalRowWidth = labelWidth + 8 + 120;
        int y = 40;
        int maxRows = Math.min(20, bindings.size());
        for (int i = 0; i < maxRows; i++) {
            Text label = Text.translatable("key.farmhelperv2.chat_shortcut." + (i + 1));
            context.drawTextWithShadow(textRenderer, label, centerX - totalRowWidth / 2, y + 5, 0xE0E0E0);
            y += 22;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
