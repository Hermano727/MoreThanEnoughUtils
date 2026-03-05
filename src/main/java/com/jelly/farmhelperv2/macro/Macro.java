package com.jelly.farmhelperv2.macro;

import net.minecraft.client.MinecraftClient;

/**
 * Minimal macro interface for the 1.21 Fabric port.
 * This is intentionally tiny so we can bring up an MVP macro
 * without dragging in the full 1.8.9 infrastructure yet.
 */
public interface Macro {

    /**
     * Called once when the macro is enabled via the FarmHelper toggle.
     */
    void onEnable(MinecraftClient client);

    /**
     * Called every client tick while the macro is enabled.
     */
    void onTick(MinecraftClient client);

    /**
     * Called once when the macro is disabled (toggle off / world change).
     * Implementations must release any pressed keys here.
     */
    void onDisable(MinecraftClient client);
}

