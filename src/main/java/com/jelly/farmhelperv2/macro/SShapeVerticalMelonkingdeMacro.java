package com.jelly.farmhelperv2.macro;

import com.jelly.farmhelperv2.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Variant of the vertical S-shape macro for Melonkingde layouts.
 * Looks slightly upwards instead of straight ahead.
 * Uses end-of-lane detection (front walkable → walk forward 0.5s → swap)
 * instead of side-block hit.
 */
public class SShapeVerticalMelonkingdeMacro extends SShapeVerticalCropMacro implements Macro {

    @Override
    protected boolean useEndOfLaneDetection() {
        return true;
    }

    @Override
    public void onEnable(MinecraftClient client) {
        super.onEnable(client);

        if (client.player != null) {
            // Look upwards similar to 1.8.9 S_PUMPKIN_MELON_MELONGKINGDE (-59.2 to -58.2).
            float pitch = -59.2f;
            client.player.setPitch(pitch);

            GameOptions options = client.options;
            // Ensure attack key stays held when adjusting pitch.
            options.attackKey.setPressed(true);
        }

        if (client.player != null && com.jelly.farmhelperv2.config.ModConfig.isVerboseLogging()) {
            client.player.sendMessage(
                    ChatUtils.success("S-Shape Pumpkin/Melon (Melonkingde) ENABLED"),
                    false);
        }
    }
}
