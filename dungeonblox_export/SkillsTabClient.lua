-- SkillsTabClient
-- Tab toggles the Character menu: server-derived stats + bag inventory + 9-slot hotbar,
-- plus the existing Combat/Mining/Fishing XP rows (client-side SkillXPShared).
-- Click the dimmed backdrop or press Escape to close.

local Players = game:GetService("Players")
local UserInputService = game:GetService("UserInputService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")

local MenuMouse = require(ReplicatedStorage:WaitForChild("CursorUtils"))
local playerScripts = script.Parent
local DungeonMenuNet = require(playerScripts:WaitForChild("DungeonMenuNet"))
local DungeonMenuUI = require(playerScripts:WaitForChild("DungeonMenuUI"))
local InventoryDragController = require(playerScripts:WaitForChild("InventoryDragController"))
local ItemDefinitions = require(ReplicatedStorage:WaitForChild("ItemDefinitions"))

pcall(function()
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
end)

local player = Players.LocalPlayer
local playerGui = player:WaitForChild("PlayerGui")

-- Parent runtime GUIs under PlayerGui.GUI when StarterGui contains a GUI folder (see dungeonblox_export/GUI/).
local function getGuiFolder()
	local f = playerGui:FindFirstChild("GUI")
	if f and f:IsA("Folder") then
		return f
	end
	return playerGui
end

local combatTemplateGui = playerGui:WaitForChild("CombatXPUI")
local miningTemplateGui = playerGui:WaitForChild("MiningXPUI")
local fishingTemplateGui = playerGui:WaitForChild("FishingXPUI")

local function getXPForLevel(level)
	local lv = math.max(1, math.floor(tonumber(level) or 1))
	return 5 * (2 ^ (lv - 1))
end

local function updateBar(mainFrame, level, xp)
	local xpNeeded = getXPForLevel(level)
	local progress = math.clamp((xp or 0) / xpNeeded, 0, 1)
	local xpBackground = mainFrame:FindFirstChild("XPBackground")
	local xpFill = xpBackground and xpBackground:FindFirstChild("XPFill")
	local levelLabel = mainFrame:FindFirstChild("LevelLabel")
	local xpLabel = mainFrame:FindFirstChild("XPLabel")
	if xpFill then
		xpFill.Size = UDim2.new(progress, 0, 1, -4)
	end
	if levelLabel then
		levelLabel.Text = "Level " .. tostring(level or 1)
	end
	if xpLabel then
		xpLabel.Text = tostring(xp or 0) .. " / " .. tostring(xpNeeded) .. " XP"
	end
end

local screenGui = Instance.new("ScreenGui")
screenGui.Name = "SkillsPopupUI"
screenGui.ResetOnSpawn = false
screenGui.IgnoreGuiInset = true
screenGui.DisplayOrder = 120
screenGui.Enabled = false
screenGui.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
screenGui.Parent = getGuiFolder()

local dim = Instance.new("TextButton")
dim.Name = "Dim"
dim.Size = UDim2.fromScale(1, 1)
dim.Position = UDim2.new()
dim.BackgroundColor3 = Color3.new(0, 0, 0)
dim.BackgroundTransparency = 0.45
dim.BorderSizePixel = 0
dim.Text = ""
dim.AutoButtonColor = false

dim.ZIndex = 1
dim.Parent = screenGui

local panel = Instance.new("Frame")
panel.Name = "Panel"
panel.AnchorPoint = Vector2.new(0.5, 0.5)
panel.Position = UDim2.new(0.5, 0, 0.45, 0)
panel.Size = UDim2.fromOffset(960, 680)
panel.BackgroundColor3 = Color3.fromRGB(30, 22, 22)
panel.BorderSizePixel = 0
panel.ZIndex = 2
panel.Parent = screenGui

local panelCorner = Instance.new("UICorner")
panelCorner.CornerRadius = UDim.new(0, 12)
panelCorner.Parent = panel

local stroke = Instance.new("UIStroke")
stroke.Thickness = 1
stroke.Color = Color3.fromRGB(90, 70, 70)
stroke.Parent = panel

local title = Instance.new("TextLabel")
title.Name = "Title"
title.BackgroundTransparency = 1
title.Size = UDim2.new(1, -24, 0, 36)
title.Position = UDim2.new(0, 12, 0, 8)
title.Font = Enum.Font.GothamBold
title.TextSize = 22
title.TextXAlignment = Enum.TextXAlignment.Left
title.TextColor3 = Color3.new(1, 1, 1)
title.Text = "Character"
title.ZIndex = 3
title.Parent = panel

local combatRow = combatTemplateGui:WaitForChild("MainFrame"):Clone()
combatRow.Name = "CombatRow"
combatRow.Visible = true

local miningRow = miningTemplateGui:WaitForChild("MainFrame"):Clone()
miningRow.Name = "MiningRow"
miningRow.Visible = true

local fishingRow = fishingTemplateGui:WaitForChild("MainFrame"):Clone()
fishingRow.Name = "FishingRow"
fishingRow.Visible = true

local uiRefs = DungeonMenuUI.createLayout(panel, { combatRow, miningRow, fishingRow })

InventoryDragController.install(uiRefs, function()
	return DungeonMenuNet.getLastSnapshot()
end)
InventoryDragController.bindStaticSources(uiRefs)

local pendingScrollUuid = nil

local function getProfileFromSnapshot()
	local s = DungeonMenuNet.getLastSnapshot()
	return s and s.profile
end

local function snapshotItem(profile, uuid)
	if not profile or type(uuid) ~= "string" or uuid == "" then
		return nil
	end
	return profile.inventory and profile.inventory[uuid]
end

local function updateTitleHint()
	if pendingScrollUuid then
		title.Text = "Character — right-click a weapon or armor to apply scroll"
	else
		title.Text = "Character"
	end
end

local function validatePendingScroll()
	if not pendingScrollUuid then
		return
	end
	local p = getProfileFromSnapshot()
	local it = snapshotItem(p, pendingScrollUuid)
	if not it or not ItemDefinitions.IsEnchantScroll(it.itemId) then
		pendingScrollUuid = nil
	end
end

local menuCtx = {
	onBagSecondary = function(uuid)
		local profile = getProfileFromSnapshot()
		local item = snapshotItem(profile, uuid)
		if not item then
			return
		end

		if ItemDefinitions.IsEnchantScroll(item.itemId) then
			if pendingScrollUuid == uuid then
				pendingScrollUuid = nil
			else
				pendingScrollUuid = uuid
			end
			updateTitleHint()
			return
		end

		if pendingScrollUuid then
			if item.type == "Weapon" or item.type == "Armor" then
				local ok, err = DungeonMenuNet.requestInventoryAct({
					kind = "ApplyEnchantScroll",
					scrollUuid = pendingScrollUuid,
					targetUuid = uuid,
				})
				if ok then
					pendingScrollUuid = nil
				elseif err then
					warn("[Character] ApplyEnchantScroll failed:", err)
				end
			end
			updateTitleHint()
			return
		end

		DungeonMenuNet.requestInventoryAct({ kind = "AssignFirstEmptyHotbar", uuid = uuid })
	end,
	onHotbarClear = function(i)
		local profile = getProfileFromSnapshot()
		if not profile then
			return
		end
		local hotbar = profile.hotbar or {}
		local slotUuid = hotbar[i]
		if pendingScrollUuid and type(slotUuid) == "string" and slotUuid ~= "" then
			local hotItem = snapshotItem(profile, slotUuid)
			if hotItem and (hotItem.type == "Weapon" or hotItem.type == "Armor") then
				local ok, err = DungeonMenuNet.requestInventoryAct({
					kind = "ApplyEnchantScroll",
					scrollUuid = pendingScrollUuid,
					targetUuid = slotUuid,
				})
				if ok then
					pendingScrollUuid = nil
				elseif err then
					warn("[Character] ApplyEnchantScroll failed:", err)
				end
				updateTitleHint()
				return
			end
		end
		if i == 1 then
			DungeonMenuNet.requestUnequip("Weapon")
		else
			DungeonMenuNet.requestInventoryAct({ kind = "SetHotbar", slot = i, uuid = nil })
		end
	end,
	onEquippedSecondary = function(slot)
		if pendingScrollUuid then
			local profile = getProfileFromSnapshot()
			if not profile then
				return
			end
			local eq = profile.equipped or {}
			local uuid = eq[slot]
			if type(uuid) ~= "string" or uuid == "" then
				return
			end
			local it = snapshotItem(profile, uuid)
			if not it or (it.type ~= "Weapon" and it.type ~= "Armor") then
				return
			end
			local ok, err = DungeonMenuNet.requestInventoryAct({
				kind = "ApplyEnchantScroll",
				scrollUuid = pendingScrollUuid,
				targetUuid = uuid,
			})
			if ok then
				pendingScrollUuid = nil
			elseif err then
				warn("[Character] ApplyEnchantScroll failed:", err)
			end
			updateTitleHint()
			return
		end
		DungeonMenuNet.requestUnequip(slot)
	end,
}

local open = false
local lastSyncRequestClock = 0
local function maybeRequestSync()
	local now = os.clock()
	if now - lastSyncRequestClock < 0.75 then
		return
	end
	lastSyncRequestClock = now
	DungeonMenuNet.requestSync()
end

local function redrawFromServer()
	local snap = DungeonMenuNet.getLastSnapshot()
	if snap then
		DungeonMenuUI.redraw(uiRefs, snap, menuCtx)
	else
		uiRefs.statsCurrency.Text = "Scrap: --   Coins: --"
		uiRefs.statsDerived.Text =
			"No snapshot yet.\n\nThis usually means the client subscribed after the first server push.\n\nRequesting a resync..."
		maybeRequestSync()
	end
end

local function refresh()
	local snap = DungeonMenuNet.getLastSnapshot()
	local profile = snap and snap.profile
	local stats = profile and profile.stats
	local combat = stats and stats.combat or {}
	local mining = stats and stats.mining or {}
	local fishing = stats and stats.fishing or {}

	updateBar(combatRow, combat.level or 1, combat.xp or 0)
	updateBar(miningRow, mining.level or mining.miningLevel or 1, mining.xp or 0)
	updateBar(fishingRow, fishing.level or fishing.fishingLevel or 1, fishing.xp or 0)
	redrawFromServer()
	validatePendingScroll()
	updateTitleHint()
end

local function setOpen(v)
	if v == open then
		return
	end
	open = v
	screenGui.Enabled = v
	if v then
		MenuMouse.acquire()
		pcall(function()
			game:GetService("StarterGui"):SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, false)
		end)
		if not DungeonMenuNet.getLastSnapshot() then
			maybeRequestSync()
		end
		refresh()
	else
		MenuMouse.release()
		pcall(function()
			game:GetService("StarterGui"):SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, true)
		end)
		pendingScrollUuid = nil
		title.Text = "Character"
	end
end

DungeonMenuNet.setListener(function(_snap)
	if open then
		refresh()
	end
end)
DungeonMenuNet.start()

dim.Activated:Connect(function()
	setOpen(false)
end)

UserInputService.InputBegan:Connect(function(input, gameProcessed)
	if gameProcessed then
		return
	end
	if UserInputService:GetFocusedTextBox() ~= nil then
		return
	end
	if input.KeyCode == Enum.KeyCode.Tab then
		setOpen(not open)
	elseif input.KeyCode == Enum.KeyCode.Escape and open then
		setOpen(false)
	end
end)

task.defer(refresh)

print("[SkillsTabClient] ready")
