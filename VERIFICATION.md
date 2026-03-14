# MoreThanEnoughUtils — Config verification checklist

After building the mod, use this checklist to confirm config behavior.

## Build

- From repo root: `.\gradlew --project-dir=MoreThanEnoughUtils build`
- Or from this directory: `.\gradlew.bat build`
- Output: `MoreThanEnoughUtils/build/libs/` (remapped JAR for Fabric 1.21.10).

## Sanity-check: config opens, saves, reloads, option effects

1. **Config opens**
   - **Mod Menu:** Mods → MoreThanEnoughUtils → Configure. The vanilla config screen should open (Crop, Pest Destroyer, Auto Experiments, Click Delay, Done).
   - **Keybind:** Options → Controls → find "Open Config" (default F). In-game, press it; the same config screen should open.

2. **Values change in the UI**
   - Change **Crop type** (button cycles). Label updates.
   - Toggle **Pest Destroyer** and **Auto Enchanting Experiments** (checkboxes).
   - Cycle **Click Delay** (button steps through 50–2000 ms presets). Label updates.

3. **Apply/Done persists to disk**
   - Change any option, click **Done**. Exit the screen.
   - Reopen the config (Mod Menu or keybind). The same values should be shown.
   - Check that `config/farmhelperv2.json` exists and contains the updated fields (e.g. `cropTypeName`, `pestDestroyerEnabled`, `autoExperimentsClickDelayMs`).

4. **Restart reloads**
   - Note current options, then fully exit the game.
   - Restart, load a world, open config. Values should match what you had before exit.

5. **Option effects**
   - **Pest Destroyer:** Enable in config (or via its keybind). Behavior should match (pests broken in range when enabled).
   - **Auto Experiments:** Enable in config. In Chronomatron/Ultrasequencer, auto-click behavior should run at the configured click delay (50–2000 ms).
   - **Click delay:** Clamping is enforced in code (50–2000 ms); the UI only offers presets in that range.