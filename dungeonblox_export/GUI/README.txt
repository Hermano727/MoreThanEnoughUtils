DungeonBlox — StarterGui / GUI folder
=====================================

1) In Roblox Studio Explorer, select StarterGui.

2) Drag GUI.rbxmx from this folder onto StarterGui.
   You should get: StarterGui > GUI   (empty Folder)

3) Drag your hotbar .rbxmx (and any other ScreenGui / Frame assets) onto StarterGui > GUI
   so they live under the GUI folder. Example:
   StarterGui
     GUI
       Hotbar          (your imported asset)
       SomeOtherHud    (optional)

4) At runtime, everything under StarterGui clones into PlayerGui with the same tree:
   PlayerGui
     GUI
       Hotbar
     CombatXPUI        (if you left templates on StarterGui root, they stay here)

5) Repo scripts in dungeonblox_export (SkillsTabClient, DungeonHotbarHud) parent their
   runtime ScreenGuis to PlayerGui.GUI when that Folder exists, otherwise PlayerGui root.

6) If you move template UIs (CombatXPUI, MiningXPUI, FishingXPUI) under GUI as well,
   update SkillsTabClient lines that do playerGui:WaitForChild("CombatXPUI") to:
   playerGui:WaitForChild("GUI"):WaitForChild("CombatXPUI")
   (or use FindFirstChild(name, true) if you prefer a single deep search).
