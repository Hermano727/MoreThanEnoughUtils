package com.jelly.farmhelperv2.freecam;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;

/**
 * Input that never reads the keyboard: keeps the real player still while freecam uses its own {@link net.minecraft.client.input.KeyboardInput}.
 */
public final class FrozenPlayerInput extends Input {

    public static final FrozenPlayerInput INSTANCE = new FrozenPlayerInput();

    private FrozenPlayerInput() {
    }

    @Override
    public void tick() {
        this.playerInput = new PlayerInput(false, false, false, false, false, false, false);
    }
}
