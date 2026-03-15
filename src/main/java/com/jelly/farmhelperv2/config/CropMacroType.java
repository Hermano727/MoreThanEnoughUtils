package com.jelly.farmhelperv2.config;

/**
 * Crop / macro type selectable in the config GUI.
 * Each type has its own block-detection behavior (e.g. vertical = bump side and swap; pumpkin/melon = lane switch with forward/back).
 */
public enum CropMacroType {
    S_SHAPE_VERTICAL("S-Shape Vertical (Nether Wart, etc.)"),
    S_SHAPE_PUMPKIN_MELON("S-Shape Pumpkin/Melon"),
    S_SHAPE_PUMPKIN_MELON_MELONKINGDE("S-Shape Pumpkin/Melon (Melonkingde)");

    private final String displayName;

    CropMacroType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
