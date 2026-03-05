package com.jelly.farmhelperv2.config;

/**
 * Crop / macro type selectable in the config GUI.
 * Only S_SHAPE_VERTICAL is implemented for now.
 */
public enum CropMacroType {
    S_SHAPE_VERTICAL("S-Shape Vertical (Nether Wart, etc.)");

    private final String displayName;

    CropMacroType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
