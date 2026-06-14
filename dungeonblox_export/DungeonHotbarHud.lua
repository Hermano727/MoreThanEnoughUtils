--[[
	DungeonHotbarHud — drives the pre-built StarterGui/GUI/Hotbar Frame.

	Slot discovery: each slot is a direct child of Hotbar that contains a
	TextLabel/TextButton whose .Text is "1"–"9".

	Behaviour:
	  • Press 1–9 or click a slot → equip that hotbar entry + highlight selection
	  • White UIStroke on the active slot (Minecraft-style selection)
	  • Small item-name label added below slot number if not already present
	  • Hidden while SkillsPopupUI (character menu) is open
]]

local Players = game:GetService("Players")
local UserInputService = game:GetService("UserInputService")

local player = Players.LocalPlayer
local playerGui = player:WaitForChild("PlayerGui")
local playerScripts = script.Parent
local DungeonMenuNet = require(playerScripts:WaitForChild("DungeonMenuNet"))

local HOTBAR_SLOTS = 9

local KEY_TO_SLOT = {
	[Enum.KeyCode.One]   = 1,
	[Enum.KeyCode.Two]   = 2,
	[Enum.KeyCode.Three] = 3,
	[Enum.KeyCode.Four]  = 4,
	[Enum.KeyCode.Five]  = 5,
	[Enum.KeyCode.Six]   = 6,
	[Enum.KeyCode.Seven] = 7,
	[Enum.KeyCode.Eight] = 8,
	[Enum.KeyCode.Nine]  = 9,
}

local function isCharacterMenuOpen()
	local g = playerGui:FindFirstChild("SkillsPopupUI", true)
	return g and g:IsA("ScreenGui") and g.Enabled
end

local function itemDisplayName(item)
	if type(item) ~= "table" then return "" end
	if type(item.name) == "string" and item.name ~= "" then return item.name end
	if type(item.itemId) == "string" and item.itemId ~= "" then return item.itemId end
	return "Item"
end

local function findToolByUuid(uuid)
	if type(uuid) ~= "string" or uuid == "" then return nil end
	local function scan(container)
		if not container then return nil end
		for _, c in ipairs(container:GetChildren()) do
			if c:IsA("Tool") and c:GetAttribute("DungeonItemUuid") == uuid then
				return c
			end
		end
		return nil
	end
	return scan(player:FindFirstChildOfClass("Backpack")) or scan(player.Character)
end

local function equipToolForUuid(uuid)
	local char = player.Character
	if not char then return end
	local hum = char:FindFirstChildOfClass("Humanoid")
	if not hum then return end
	local tool = findToolByUuid(uuid)
	if tool then hum:EquipTool(tool) end
end

local function equipHotbarSlot(slotIndex)
	if slotIndex < 1 or slotIndex > HOTBAR_SLOTS then return end
	local snap = DungeonMenuNet.getLastSnapshot()
	local profile = snap and snap.profile
	if not profile then return end
	local uuid = (profile.hotbar or {})[slotIndex]
	if type(uuid) == "string" and uuid ~= "" then
		equipToolForUuid(uuid)
	else
		local char = player.Character
		local hum = char and char:FindFirstChildOfClass("Humanoid")
		if hum then hum:UnequipTools() end
	end
end

-- ── Locate the pre-built Hotbar frame ────────────────────────────────────────

local guiFolder = playerGui:WaitForChild("GUI", 30)
if not guiFolder then
	warn("[DungeonHotbarHud] PlayerGui.GUI not found — aborting")
	return
end

local hotbarFrame = guiFolder:WaitForChild("Hotbar", 30)
if not hotbarFrame then
	warn("[DungeonHotbarHud] PlayerGui.GUI.Hotbar not found — aborting")
	return
end

hotbarFrame.Visible = true

-- ── Discover slot frames by their number TextLabel ───────────────────────────

local slotFrames = {}  -- [1..9] = GuiObject (the slot frame)

for _, child in ipairs(hotbarFrame:GetChildren()) do
	if child:IsA("GuiObject") then
		for _, desc in ipairs(child:GetDescendants()) do
			if (desc:IsA("TextLabel") or desc:IsA("TextButton")) then
				local n = tonumber(desc.Text)
				if n and n >= 1 and n <= 9 then
					slotFrames[n] = child
					break
				end
			end
		end
	end
end

-- ── Per-slot: ensure ItemLabel + SelectionStroke ──────────────────────────────

local itemLabels      = {}  -- [1..9]
local selectionStrokes = {} -- [1..9]

for i = 1, HOTBAR_SLOTS do
	local f = slotFrames[i]
	if not f then
		warn(("[DungeonHotbarHud] slot %d frame not found"):format(i))
		continue
	end

	-- item name label
	local il = f:FindFirstChild("ItemLabel")
	if not il then
		il = Instance.new("TextLabel")
		il.Name = "ItemLabel"
		il.AnchorPoint = Vector2.new(0, 1)
		il.Size = UDim2.new(1, 0, 0.38, 0)
		il.Position = UDim2.new(0, 0, 1, 0)
		il.BackgroundTransparency = 1
		il.TextColor3 = Color3.new(1, 1, 1)
		il.TextScaled = true
		il.Font = Enum.Font.GothamMedium
		il.Text = ""
		il.ZIndex = (f.ZIndex or 1) + 1
		il.Parent = f
	end
	itemLabels[i] = il

	-- selection stroke
	local sk = f:FindFirstChild("SelectionStroke")
	if not sk then
		sk = Instance.new("UIStroke")
		sk.Name = "SelectionStroke"
		sk.Thickness = 3
		sk.Color = Color3.fromRGB(255, 255, 255)
		sk.Parent = f
	end
	sk.Enabled = false
	selectionStrokes[i] = sk

	-- click handler
	local idx = i
	if f:IsA("GuiButton") then
		f.MouseButton1Click:Connect(function()
			selectSlot(idx)
		end)
	else
		f.InputBegan:Connect(function(input)
			if input.UserInputType == Enum.UserInputType.MouseButton1
				or input.UserInputType == Enum.UserInputType.Touch then
				selectSlot(idx)
			end
		end)
	end
end

-- ── Selection state ───────────────────────────────────────────────────────────

local selectedSlot = 0

local function updateSelection()
	for i = 1, HOTBAR_SLOTS do
		if selectionStrokes[i] then
			selectionStrokes[i].Enabled = (i == selectedSlot)
		end
	end
end

function selectSlot(i)
	selectedSlot = i
	updateSelection()
	equipHotbarSlot(i)
end

-- ── Refresh item labels from snapshot ────────────────────────────────────────

local function refreshHud(_snap)
	local snap = DungeonMenuNet.getLastSnapshot()
	local profile = snap and snap.profile
	local inv = profile and profile.inventory or {}
	local hb  = profile and profile.hotbar or {}
	for i = 1, HOTBAR_SLOTS do
		local il = itemLabels[i]
		if not il then continue end
		local uuid = hb[i]
		local it = (type(uuid) == "string" and uuid ~= "") and inv[uuid] or nil
		if it then
			local nm = itemDisplayName(it)
			if #nm > 12 then nm = string.sub(nm, 1, 11) .. "…" end
			il.Text = nm
		else
			il.Text = ""
		end
	end
end

DungeonMenuNet.addSnapshotListener(refreshHud)

-- ── Visibility: hide while character menu is open ────────────────────────────

local function setHotbarVisible(v)
	hotbarFrame.Visible = v
end

task.spawn(function()
	local popup
	repeat
		popup = playerGui:FindFirstChild("SkillsPopupUI", true)
		if not popup then task.wait(0.5) end
	until popup
	setHotbarVisible(not popup.Enabled)
	popup:GetPropertyChangedSignal("Enabled"):Connect(function()
		setHotbarVisible(not popup.Enabled)
	end)
end)

-- ── Character lifecycle ───────────────────────────────────────────────────────

player.CharacterAdded:Connect(function()
	task.defer(refreshHud, nil)
	task.delay(0.35, function()
		selectSlot(1)
	end)
end)

-- ── Backpack refresh ──────────────────────────────────────────────────────────

local bpAddedConn, bpRemovedConn

local function hookBackpack()
	if bpAddedConn then bpAddedConn:Disconnect(); bpAddedConn = nil end
	if bpRemovedConn then bpRemovedConn:Disconnect(); bpRemovedConn = nil end
	local bp = player:FindFirstChildOfClass("Backpack") or player:WaitForChild("Backpack", 30)
	if not bp then return end
	bpAddedConn   = bp.ChildAdded:Connect(function() task.defer(refreshHud, nil) end)
	bpRemovedConn = bp.ChildRemoved:Connect(function() task.defer(refreshHud, nil) end)
end

hookBackpack()
player.ChildAdded:Connect(function(ch)
	if ch:IsA("Backpack") then
		hookBackpack()
		task.defer(refreshHud, nil)
	end
end)

-- ── Key bindings 1–9 ─────────────────────────────────────────────────────────

UserInputService.InputBegan:Connect(function(input, gameProcessed)
	if gameProcessed then return end
	if UserInputService:GetFocusedTextBox() ~= nil then return end
	if isCharacterMenuOpen() then return end
	if input.UserInputType ~= Enum.UserInputType.Keyboard then return end
	local slot = KEY_TO_SLOT[input.KeyCode]
	if slot then
		selectSlot(slot)
	end
end)

-- ── Boot ─────────────────────────────────────────────────────────────────────

DungeonMenuNet.start()
task.defer(refreshHud, nil)

print("[DungeonHotbarHud] ready")
