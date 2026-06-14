# DungeonBlox inventory export (Studio MCP disconnected)

When Roblox Studio is connected again, copy these files into your place (or merge manually):

| File | Roblox path |
|------|-------------|
| `InventoryDragController.lua` | `StarterPlayer.StarterPlayerScripts.InventoryDragController` (ModuleScript) |
| `DungeonMenuNet.lua` | `StarterPlayer.StarterPlayerScripts.DungeonMenuNet` (ModuleScript) |
| `SkillsTabClient.lua` | `StarterPlayer.StarterPlayerScripts.SkillsTabClient` (LocalScript) |
| `DungeonMenuUI.lua` | `StarterPlayer.StarterPlayerScripts.DungeonMenuUI` (ModuleScript) — regenerate from `gen_dungeon_menu_ui.py` if you edit the generator |
| `DungeonProfileServer.server.lua` | `ServerScriptService.DungeonProfileServer` (Script) |

**One-time in Studio (Edit mode):** if a duplicate `DungeonEquipItem` RemoteFunction exists under `ReplicatedStorage`, delete it so only `DungeonEquipItem` **RemoteEvent** remains (matches `DungeonMenuNet`).

Stop Play before pasting; then save the place.
