package com.jelly.farmhelperv2.gui.config;

import com.jelly.farmhelperv2.FarmHelperClient;
import com.jelly.farmhelperv2.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified Chat Shortcuts editor: one screen showing each shortcut's command and keybind.
 * Add/remove/reorder shortcuts; set keybind by clicking the key button then pressing a key (Esc to unbind).
 * The list of rows matches the list of commands (dynamic).
 */
public final class ChatShortcutsEditorScreen extends Screen {

    private static final int MAX_SHORTCUTS = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int MESSAGE_WIDTH = 240;
    private static final int KEY_BUTTON_WIDTH = 90;
    private static final int REMOVE_BUTTON_WIDTH = 40;
    private static final int MOVE_BUTTON_WIDTH = 22;
    private static final int PAD = 4;

    private final Screen parent;
    /** Mutable copy; saved to ModConfig on Done. */
    private final List<ModConfig.ChatShortcut> workingCopy;
    private final List<KeyBinding> bindings;

    private List<TextFieldWidget> messageFields;
    private List<ButtonWidget> keyButtons;
    private List<ButtonWidget> removeButtons;
    private List<ButtonWidget> moveUpButtons;
    private List<ButtonWidget> moveDownButtons;
    private ButtonWidget addButton;
    private ButtonWidget doneButton;

    /** When >= 0, listening for the next key/mouse for this slot. */
    private int listeningSlot = -1;

    public ChatShortcutsEditorScreen(Screen parent) {
        super(Text.literal("Chat Shortcuts"));
        this.parent = parent;
        this.workingCopy = new ArrayList<>(ModConfig.getChatShortcuts());
        this.bindings = FarmHelperClient.getChatShortcutKeyBindings();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int totalRowWidth = MESSAGE_WIDTH + PAD + KEY_BUTTON_WIDTH + PAD + MOVE_BUTTON_WIDTH * 2 + PAD + REMOVE_BUTTON_WIDTH;
        int leftX = centerX - totalRowWidth / 2;
        int y = 36;

        messageFields = new ArrayList<>();
        keyButtons = new ArrayList<>();
        removeButtons = new ArrayList<>();
        moveUpButtons = new ArrayList<>();
        moveDownButtons = new ArrayList<>();

        int rows = Math.min(workingCopy.size(), bindings.size());
        for (int i = 0; i < rows; i++) {
            final int index = i;
            ModConfig.ChatShortcut shortcut = workingCopy.get(i);
            KeyBinding binding = bindings.get(i);

            TextFieldWidget messageField = new TextFieldWidget(
                    textRenderer,
                    leftX, y, MESSAGE_WIDTH, 20,
                    Text.literal("Message")
            );
            messageField.setMaxLength(256);
            messageField.setPlaceholder(Text.literal("/command"));
            messageField.setText(shortcut.message != null ? shortcut.message : "");
            messageField.setChangedListener(s -> {
                if (index < workingCopy.size()) workingCopy.get(index).message = s;
            });
            addDrawableChild(messageField);
            messageFields.add(messageField);

            int keyX = leftX + MESSAGE_WIDTH + PAD;
            ButtonWidget keyBtn = ButtonWidget.builder(
                    getKeyButtonText(binding),
                    b -> startListening(index))
                    .dimensions(keyX, y, KEY_BUTTON_WIDTH, 20)
                    .build();
            addDrawableChild(keyBtn);
            keyButtons.add(keyBtn);

            int moveX = keyX + KEY_BUTTON_WIDTH + PAD;
            ButtonWidget upBtn = ButtonWidget.builder(Text.literal("\u2191"), b -> moveRow(index, -1))
                    .dimensions(moveX, y, MOVE_BUTTON_WIDTH, 20)
                    .build();
            upBtn.active = index > 0;
            addDrawableChild(upBtn);
            moveUpButtons.add(upBtn);

            ButtonWidget downBtn = ButtonWidget.builder(Text.literal("\u2193"), b -> moveRow(index, 1))
                    .dimensions(moveX + MOVE_BUTTON_WIDTH, y, MOVE_BUTTON_WIDTH, 20)
                    .build();
            downBtn.active = index < rows - 1;
            addDrawableChild(downBtn);
            moveDownButtons.add(downBtn);

            ButtonWidget removeBtn = ButtonWidget.builder(Text.literal("X"), b -> removeRow(index))
                    .dimensions(moveX + MOVE_BUTTON_WIDTH * 2 + PAD, y, REMOVE_BUTTON_WIDTH, 20)
                    .build();
            addDrawableChild(removeBtn);
            removeButtons.add(removeBtn);

            y += ROW_HEIGHT;
        }

        addButton = ButtonWidget.builder(Text.literal("+ Add shortcut"), b -> addRow())
                .dimensions(leftX, y, 120, 20)
                .build();
        addButton.active = workingCopy.size() < MAX_SHORTCUTS;
        addDrawableChild(addButton);
        y += ROW_HEIGHT + 8;

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> saveAndClose())
                .dimensions(centerX - 100, height - 28, 200, 20)
                .build();
        addDrawableChild(doneButton);
    }

    private void addRow() {
        if (workingCopy.size() >= MAX_SHORTCUTS) return;
        ModConfig.ChatShortcut s = new ModConfig.ChatShortcut();
        s.id = workingCopy.size() + 1;
        s.label = "Shortcut #" + s.id;
        s.message = "";
        s.keyCode = 0;
        workingCopy.add(s);
        reinit();
    }

    private void removeRow(int index) {
        if (index < 0 || index >= workingCopy.size()) return;
        workingCopy.remove(index);
        reinit();
    }

    private void moveRow(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= workingCopy.size()) return;
        ModConfig.ChatShortcut a = workingCopy.get(index);
        ModConfig.ChatShortcut b = workingCopy.get(target);
        workingCopy.set(index, b);
        workingCopy.set(target, a);
        reinit();
    }

    private void reinit() {
        clearAndInit();
    }

    private void startListening(int slot) {
        listeningSlot = slot;
        if (slot >= 0 && slot < keyButtons.size()) {
            keyButtons.get(slot).setMessage(Text.literal("..."));
        }
    }

    private void stopListening(boolean updateButton) {
        if (updateButton && listeningSlot >= 0 && listeningSlot < keyButtons.size()) {
            KeyBinding binding = bindings.get(listeningSlot);
            keyButtons.get(listeningSlot).setMessage(getKeyButtonText(binding));
        }
        listeningSlot = -1;
    }

    private static Text getKeyButtonText(KeyBinding binding) {
        return binding.isUnbound()
                ? Text.literal("Set key")
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

    private void saveAndClose() {
        for (int i = 0; i < messageFields.size() && i < workingCopy.size(); i++) {
            workingCopy.get(i).message = messageFields.get(i).getText();
        }
        ModConfig.setChatShortcuts(workingCopy);
        ModConfig.save();
        close();
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
                    height / 2 - 24,
                    0xFFAAAA00
            );
        }

        int centerX = width / 2;
        int totalRowWidth = MESSAGE_WIDTH + PAD + KEY_BUTTON_WIDTH + PAD + MOVE_BUTTON_WIDTH * 2 + PAD + REMOVE_BUTTON_WIDTH;
        int leftX = centerX - totalRowWidth / 2;
        int y = 36;

        context.drawTextWithShadow(textRenderer, "Command", leftX, y - 12, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, "Key", leftX + MESSAGE_WIDTH + PAD, y - 12, 0xA0A0A0);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
