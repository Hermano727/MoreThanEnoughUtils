package com.jelly.farmhelperv2.config;

/**
 * Crop / macro type selectable in the config GUI.
 * Each type has its own block-detection behavior (e.g. vertical = bump side and swap; pumpkin/melon = lane switch with forward/back).
 */
public enum CropMacroType {
    S_SHAPE_VERTICAL("S-Shape Vertical (Nether Wart, etc.)"),
    S_SHAPE_PUMPKIN_MELON("S-Shape Pumpkin/Melon"),
    S_SHAPE_PUMPKIN_MELON_MELONKINGDE("S-Shape Pumpkin/Melon (Melonkingde)"),
    S_SHAPE_SUGARCANE_SUNFLOWER_MOONFLOWER("S-Shape Sugarcane/Sunflower/Moonflower"),
    S_SHAPE_MUSHROOM("S-Shape Mushroom"),
    S_SHAPE_MUSHROOM_ROTATE("S-Shape Mushroom (Rotate)");

    private final String displayName;

    CropMacroType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
