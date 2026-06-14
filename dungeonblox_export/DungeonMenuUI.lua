--[[
  Builds + redraws the Character menu inventory/equipped UI region.
  SkillsTabClient owns Tab/dim/backdrop; this module owns the dungeon panels only.
]]

local Players           = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Types           = require(ReplicatedStorage:WaitForChild("DungeonProfileTypes"))
local ItemDefinitions = require(ReplicatedStorage:WaitForChild("ItemDefinitions"))
local ItemTooltip     = require(Players.LocalPlayer:WaitForChild("PlayerScripts"):WaitForChild("ItemTooltip"))
local InventoryDragController = require(script.Parent:WaitForChild("InventoryDragController"))

local HOTBAR_SLOTS = 9

local EQUIP_SLOTS = { "Helm", "Chest", "Legs", "Boots", "Weapon", "Shield", "Necklace", "Ring" }
local SLOT_ACCENT = {
    Helm     = Color3.fromRGB(180,160,255),
    Chest    = Color3.fromRGB(255,190,110),
    Legs     = Color3.fromRGB(110,210,180),
    Boots    = Color3.fromRGB(200,160,130),
    Weapon   = Color3.fromRGB(255,120,120),
    Shield   = Color3.fromRGB(120,180,255),
    Necklace = Color3.fromRGB(255,220,80),
    Ring     = Color3.fromRGB(180,255,180),
}

local DungeonMenuUI = {}

local function clearInventory(scroll)
	for _, child in ipairs(scroll:GetChildren()) do
		if not child:IsA("UIGridLayout") and not child:IsA("UIPadding") and not child:IsA("UIListLayout") then
			child:Destroy()
		end
	end
end

function DungeonMenuUI.createLayout(panel, skillRows)
	local body = Instance.new("Frame")
	body.Name = "DungeonBody"
	body.BackgroundTransparency = 1
	body.Position = UDim2.new(0, 8, 0, 44)
	body.Size = UDim2.new(1, -16, 1, -130)
	body.ZIndex = 3
	body.Parent = panel

	local INV_H = 222
	local invSection = Instance.new("Frame", body)
	invSection.Name = "InventorySection"
	invSection.BackgroundColor3 = Color3.fromRGB(18, 14, 14)
	invSection.BorderSizePixel = 0
	invSection.Size = UDim2.new(1, 0, 0, INV_H)
	invSection.ZIndex = 3
	Instance.new("UICorner", invSection).CornerRadius = UDim.new(0, 8)

	local scroll = Instance.new("ScrollingFrame", invSection)
	scroll.Name = "InventoryScroll"
	scroll.BackgroundTransparency = 1
	scroll.BorderSizePixel = 0
	scroll.ScrollBarThickness = 6
	scroll.AutomaticCanvasSize = Enum.AutomaticSize.Y
	scroll.CanvasSize = UDim2.new()
	scroll.Size = UDim2.new(1, 0, 1, 0)
	scroll.ZIndex = 4

	local grid = Instance.new("UIGridLayout", scroll)
	grid.CellSize = UDim2.fromOffset(60, 60)
	grid.CellPadding = UDim2.fromOffset(6, 6)
	grid.SortOrder = Enum.SortOrder.LayoutOrder
	grid.FillDirectionMaxCells = 9

	local invPad = Instance.new("UIPadding", scroll)
	invPad.PaddingTop = UDim.new(0, 8)
	invPad.PaddingLeft = UDim.new(0, 10)
	invPad.PaddingRight = UDim.new(0, 10)
	invPad.PaddingBottom = UDim.new(0, 8)

	local CHAR_GAP = 10
	local charSection = Instance.new("Frame", body)
	charSection.Name = "CharacterSection"
	charSection.BackgroundTransparency = 1
	charSection.Position = UDim2.new(0, 0, 0, INV_H + CHAR_GAP)
	charSection.Size = UDim2.new(1, 0, 1, -(INV_H + CHAR_GAP))
	charSection.ZIndex = 3

	local COL_W   = 80
	local MODEL_W = 220
	local GAP     = 10
	local SLOT_SZ = 64
	local SLOT_GAP = 8

	local armorCol = Instance.new("Frame", charSection)
	armorCol.Name = "ArmorColumn"
	armorCol.BackgroundTransparency = 1
	armorCol.Position = UDim2.fromOffset(0, 0)
	armorCol.Size = UDim2.new(0, COL_W, 1, 0)

	local modelX = COL_W + GAP
	local modelFrame = Instance.new("Frame", charSection)
	modelFrame.Name = "PlayerModelArea"
	modelFrame.BackgroundColor3 = Color3.fromRGB(20, 16, 16)
	modelFrame.BorderSizePixel = 0
	modelFrame.Position = UDim2.fromOffset(modelX, 0)
	modelFrame.Size = UDim2.new(0, MODEL_W, 1, 0)
	Instance.new("UICorner", modelFrame).CornerRadius = UDim.new(0, 8)
	local modelLabel = Instance.new("TextLabel", modelFrame)
	modelLabel.BackgroundTransparency = 1
	modelLabel.Size = UDim2.new(1, 0, 1, 0)
	modelLabel.Font = Enum.Font.Gotham
	modelLabel.TextSize = 12
	modelLabel.TextColor3 = Color3.fromRGB(55, 45, 45)
	modelLabel.Text = "Character"
	modelLabel.ZIndex = 4

	local accessX = modelX + MODEL_W + GAP
	local accessCol = Instance.new("Frame", charSection)
	accessCol.Name = "AccessoryColumn"
	accessCol.BackgroundTransparency = 1
	accessCol.Position = UDim2.fromOffset(accessX, 0)
	accessCol.Size = UDim2.new(0, COL_W, 1, 0)

	local statsX = accessX + COL_W + GAP
	local statsPanel = Instance.new("Frame", charSection)
	statsPanel.Name = "StatsPanel"
	statsPanel.BackgroundTransparency = 1
	statsPanel.Position = UDim2.fromOffset(statsX, 0)
	statsPanel.Size = UDim2.new(1, -statsX, 1, 0)

	local statsBox = Instance.new("Frame", statsPanel)
	statsBox.Name = "PlayerStatsBox"
	statsBox.BackgroundColor3 = Color3.fromRGB(22, 16, 16)
	statsBox.BorderSizePixel = 0
	statsBox.Size = UDim2.new(1, 0, 0, 136)
	statsBox.ZIndex = 4
	Instance.new("UICorner", statsBox).CornerRadius = UDim.new(0, 8)
	local sbPad = Instance.new("UIPadding", statsBox)
	sbPad.PaddingLeft = UDim.new(0, 8)
	sbPad.PaddingTop = UDim.new(0, 6)
	sbPad.PaddingRight = UDim.new(0, 6)

	local currency = Instance.new("TextLabel", statsBox)
	currency.Name = "Currency"
	currency.BackgroundTransparency = 1
	currency.Font = Enum.Font.GothamMedium
	currency.TextSize = 13
	currency.TextColor3 = Color3.fromRGB(230, 220, 200)
	currency.TextXAlignment = Enum.TextXAlignment.Left
	currency.Text = "Scrap: 0   Coins: 0"
	currency.Size = UDim2.new(1, 0, 0, 20)
	currency.ZIndex = 5

	local stats = Instance.new("TextLabel", statsBox)
	stats.Name = "DerivedStats"
	stats.BackgroundTransparency = 1
	stats.Font = Enum.Font.Gotham
	stats.TextSize = 13
	stats.TextColor3 = Color3.fromRGB(220, 220, 235)
	stats.TextXAlignment = Enum.TextXAlignment.Left
	stats.TextYAlignment = Enum.TextYAlignment.Top
	stats.TextWrapped = true
	stats.AutomaticSize = Enum.AutomaticSize.Y
	stats.Text = "Syncing..."
	stats.Size = UDim2.new(1, 0, 0, 0)
	stats.Position = UDim2.new(0, 0, 0, 22)
	stats.ZIndex = 5

	local skillsBox = Instance.new("Frame", statsPanel)
	skillsBox.Name = "SkillsBox"
	skillsBox.BackgroundColor3 = Color3.fromRGB(22, 16, 16)
	skillsBox.BorderSizePixel = 0
	skillsBox.Position = UDim2.new(0, 0, 0, 144)
	skillsBox.Size = UDim2.new(1, 0, 1, -144)
	skillsBox.ZIndex = 4
	Instance.new("UICorner", skillsBox).CornerRadius = UDim.new(0, 8)
	local skillsPad = Instance.new("UIPadding", skillsBox)
	skillsPad.PaddingTop = UDim.new(0, 8)
	skillsPad.PaddingLeft = UDim.new(0, 6)
	skillsPad.PaddingRight = UDim.new(0, 6)
	local skillsLayout = Instance.new("UIListLayout", skillsBox)
	skillsLayout.FillDirection = Enum.FillDirection.Vertical
	skillsLayout.HorizontalAlignment = Enum.HorizontalAlignment.Center
	skillsLayout.SortOrder = Enum.SortOrder.LayoutOrder
	skillsLayout.Padding = UDim.new(0, 6)
	for i, row in ipairs(skillRows) do
		row.Parent = skillsBox
		row.LayoutOrder = i
		row.Size = UDim2.new(1, 0, 0, 50)
	end

	local equipButtons = {}
	local function makeSlotBtn(slot, parent, x, y)
		local btn = Instance.new("TextButton")
		btn.Name = "Equip_" .. slot
		btn.Position = UDim2.fromOffset(x, y)
		btn.Size = UDim2.fromOffset(SLOT_SZ, SLOT_SZ)
		btn.BackgroundColor3 = Color3.fromRGB(30, 22, 22)
		btn.BorderSizePixel = 0
		btn.Font = Enum.Font.GothamMedium
		btn.TextSize = 10
		btn.TextWrapped = true
		btn.TextColor3 = Color3.fromRGB(100, 90, 90)
		btn.Text = ""
		btn.ZIndex = 5
		Instance.new("UICorner", btn).CornerRadius = UDim.new(0, 6)
		local sk = Instance.new("UIStroke", btn)
		sk.Thickness = 1
		sk.Color = SLOT_ACCENT[slot] or Color3.fromRGB(60, 45, 45)
		sk.Transparency = 0.5
		local slotLbl = Instance.new("TextLabel", btn)
		slotLbl.Name = "SlotName"
		slotLbl.BackgroundTransparency = 1
		slotLbl.Size = UDim2.new(1, -4, 0, 13)
		slotLbl.Position = UDim2.new(0, 3, 0, 2)
		slotLbl.Font = Enum.Font.GothamBold
		slotLbl.TextSize = 8
		slotLbl.TextColor3 = Color3.fromRGB(70, 60, 60)
		slotLbl.TextXAlignment = Enum.TextXAlignment.Left
		slotLbl.Text = slot:upper()
		slotLbl.ZIndex = 7
		local itemLbl = Instance.new("TextLabel", btn)
		itemLbl.Name = "ItemName"
		itemLbl.BackgroundTransparency = 1
		itemLbl.Size = UDim2.new(1, -4, 1, -16)
		itemLbl.Position = UDim2.new(0, 2, 0, 14)
		itemLbl.Font = Enum.Font.GothamMedium
		itemLbl.TextSize = 10
		itemLbl.TextColor3 = Color3.fromRGB(80, 72, 72)
		itemLbl.TextXAlignment = Enum.TextXAlignment.Center
		itemLbl.TextYAlignment = Enum.TextYAlignment.Center
		itemLbl.TextWrapped = true
		itemLbl.Text = "empty"
		itemLbl.ZIndex = 7
		btn.Parent = parent
		return btn
	end

	local ARMOR_ORDER = { "Helm", "Chest", "Legs", "Boots" }
	for i, slot in ipairs(ARMOR_ORDER) do
		equipButtons[slot] = makeSlotBtn(slot, armorCol, 8, (i-1) * (SLOT_SZ + SLOT_GAP))
	end

	local ACCESS_ORDER = { "Weapon", "Shield", "Necklace", "Ring" }
	for i, slot in ipairs(ACCESS_ORDER) do
		equipButtons[slot] = makeSlotBtn(slot, accessCol, 8, (i-1) * (SLOT_SZ + SLOT_GAP))
	end

	local hotbarBar = Instance.new("Frame")
	hotbarBar.Name = "HotbarRow"
	hotbarBar.BackgroundTransparency = 1
	hotbarBar.AnchorPoint = Vector2.new(0, 1)
	hotbarBar.Position = UDim2.new(0, 10, 1, -8)
	hotbarBar.Size = UDim2.new(1, -20, 0, 84)
	hotbarBar.ZIndex = 3
	hotbarBar.Parent = panel

	local help = Instance.new("TextLabel", hotbarBar)
	help.Name = "HotbarHelp"
	help.BackgroundTransparency = 1
	help.Font = Enum.Font.Gotham
	help.TextSize = 11
	help.TextColor3 = Color3.fromRGB(180, 170, 170)
	help.TextXAlignment = Enum.TextXAlignment.Left
	help.TextWrapped = true
	help.Text = "Drag items to equip, hotbar slots 2–9, or bag to unequip/clear. Slot 1 shows your equipped weapon. Right-click bag: scroll/hotbar assign. Right-click equipped: unequip (or apply selected scroll)."
	help.Size = UDim2.new(1, -8, 0, 24)
	help.ZIndex = 4

	local row = Instance.new("Frame", hotbarBar)
	row.Name = "HotbarButtons"
	row.BackgroundTransparency = 1
	row.Position = UDim2.new(0, 0, 0, 26)
	row.Size = UDim2.new(1, 0, 1, -26)
	row.ZIndex = 4
	local rowLayout = Instance.new("UIListLayout", row)
	rowLayout.FillDirection = Enum.FillDirection.Horizontal
	rowLayout.HorizontalAlignment = Enum.HorizontalAlignment.Left
	rowLayout.VerticalAlignment = Enum.VerticalAlignment.Center
	rowLayout.Padding = UDim.new(0, 6)

	local hotbarButtons = {}
	local hotbarSlotItem = {}
	local ctxHolder = { current = nil }

	local equipSlotState = {}
	for _, slot in ipairs(EQUIP_SLOTS) do
		local btn = equipButtons[slot]
		if btn then
			equipSlotState[slot] = { item = nil, uuid = nil }
			local sl = slot
			btn.MouseEnter:Connect(function()
				local s = equipSlotState[sl]
				if s and s.item then
					ItemTooltip.show(s.item, btn)
				end
			end)
			btn.MouseLeave:Connect(function()
				ItemTooltip.hide()
			end)
			btn.MouseButton2Click:Connect(function()
				ItemTooltip.hide()
				local h = ctxHolder.current
				if h and h.onEquippedSecondary then
					h.onEquippedSecondary(sl)
				end
			end)
		end
	end

	for i = 1, HOTBAR_SLOTS do
		local b = Instance.new("TextButton")
		b.Name = "HB" .. tostring(i)
		b.AutoButtonColor = true
		b.TextWrapped = true
		b.Font = Enum.Font.GothamMedium
		b.TextSize = 11
		b.TextColor3 = Color3.new(1, 1, 1)
		b.BackgroundColor3 = Color3.fromRGB(40, 32, 32)
		b.Size = UDim2.fromOffset(82, 54)
		b.ZIndex = 4
		b.Text = tostring(i) .. "\n(empty)"
		local c = Instance.new("UICorner", b)
		c.CornerRadius = UDim.new(0, 8)
		b.Parent = row
		local si = i
		b.MouseEnter:Connect(function()
			local it = hotbarSlotItem[si]
			if it then
				ItemTooltip.show(it, b)
			end
		end)
		b.MouseLeave:Connect(function()
			ItemTooltip.hide()
		end)
		hotbarButtons[i] = b
		b.MouseButton1Click:Connect(function()
			local h = ctxHolder.current
			if h and h.onHotbarSelect then h.onHotbarSelect(si) end
		end)
		b.MouseButton2Click:Connect(function()
			local h = ctxHolder.current
			if h and h.onHotbarClear then h.onHotbarClear(si) end
		end)
	end

	return {
		statsCurrency   = currency,
		statsDerived    = stats,
		inventoryScroll = scroll,
		hotbarButtons   = hotbarButtons,
		hotbarSlotItem  = hotbarSlotItem,
		ctxHolder       = ctxHolder,
		equipButtons    = equipButtons,
		equipSlotState  = equipSlotState,
	}
end

local function bagItemHotbarOk(item)
	if type(item) ~= "table" then
		return false
	end
	if type(item.itemId) == "string" and item.itemId ~= "" then
		return ItemDefinitions.IsHotbarEquippable(item.itemId)
	end
	if item.type == "Weapon" then
		return true
	end
	if item.type == "Consumable" then
		return true
	end
	if item.type == "Armor" then
		return false
	end
	if item.type == "Material" then
		if item.equipSlot == "Pickaxe" or item.equipSlot == "FishingSpear" then
			return true
		end
		if type(item.toolPrefabName) == "string" and item.toolPrefabName ~= "" then
			return true
		end
		return false
	end
	return false
end

local function itemDisplayName(item)
	if type(item) ~= "table" then
		return "Item"
	end
	if item.name and item.name ~= "" then
		return item.name
	end
	if item.itemId and item.itemId ~= "" then
		return item.itemId
	end
	return "Item"
end

local function itemCount(item)
	if type(item) == "table" and type(item.count) == "number" and item.count > 1 then
		return item.count
	end
	return nil
end

local function syncDurabilityLabel(parent, it)
	local old = parent:FindFirstChild("DurLabel")
	if not it or not it.maxDurability then
		if old then
			old:Destroy()
		end
		return
	end
	local dl = old
	if not dl then
		dl = Instance.new("TextLabel", parent)
		dl.Name = "DurLabel"
		dl.Size = UDim2.new(1, -4, 0, 10)
		dl.Position = UDim2.new(0, 2, 1, -11)
		dl.BackgroundTransparency = 1
		dl.Font = Enum.Font.GothamBold
		dl.TextSize = 7
		dl.TextXAlignment = Enum.TextXAlignment.Center
		dl.ZIndex = 8
	end
	local dur = it.durability or 0
	local ratio = it.maxDurability > 0 and dur / it.maxDurability or 0
	dl.Text = it.broken and "BROKEN" or (dur .. "/" .. it.maxDurability)
	dl.TextColor3 = it.broken and Color3.fromRGB(255, 60, 60)
		or (ratio < 0.3 and Color3.fromRGB(255, 80, 80) or (ratio < 0.5 and Color3.fromRGB(255, 200, 50) or Color3.fromRGB(120, 120, 120)))
end

function DungeonMenuUI.redraw(refs, snapshot, ctx)
	local profile = snapshot.profile
	local derived = snapshot.derived
	if type(profile) ~= "table" or type(derived) ~= "table" then
		return
	end

	local inv = profile.inventory or {}

	if refs.ctxHolder then
		refs.ctxHolder.current = ctx
	end
	refs.statsCurrency.Text = string.format(
		"Scrap: %d   Coins: %d   Raid: %s",
		profile.currencies.Scrap,
		profile.currencies.Coins,
		profile.flags.inRaid and "Yes" or "No"
	)

	local dc = derived.combat
	refs.statsDerived.Text = string.format(
		"Combat\n  HP: %d / %d\n  Armor: %d\n  HP regen: %.1f/s\n  Energy regen: %.1f/s\nMining level: %d\nFishing level: %d",
		math.floor(dc.hp),
		math.floor(dc.maxHp),
		math.floor(dc.armor),
		dc.hpRegen,
		dc.energyRegen,
		derived.mining.miningLevel,
		derived.fishing.fishingLevel
	)

	local equipped = profile.equipped or {}
	if refs.equipButtons and refs.equipSlotState then
		for _, slot in ipairs(EQUIP_SLOTS) do
			local btn = refs.equipButtons[slot]
			if not btn then
				continue
			end
			local st = refs.equipSlotState[slot]
			local uuid = equipped[slot]
			local it = (type(uuid) == "string" and uuid ~= "") and inv[uuid] or nil
			local itemLbl = btn:FindFirstChild("ItemName")
			if st then
				st.item = it
				st.uuid = it and uuid or nil
			end
			if it then
				local nm = itemDisplayName(it)
				local rCol = Types.GetRarityColor(it.rarity)
				if itemLbl then
					itemLbl.Text = nm
					itemLbl.TextColor3 = rCol
				end
				syncDurabilityLabel(btn, it)
			else
				if itemLbl then
					itemLbl.Text = "empty"
					itemLbl.TextColor3 = Color3.fromRGB(70, 60, 60)
				end
				syncDurabilityLabel(btn, nil)
			end
		end
	end

	clearInventory(refs.inventoryScroll)

	if type(inv) ~= "table" then
		inv = {}
	end
	local hotbar = profile.hotbar
	if type(hotbar) ~= "table" then
		hotbar = {}
	end

	local inHotbar = {}
	for i = 1, HOTBAR_SLOTS do
		local uuid = hotbar[i]
		if type(uuid) == "string" and uuid ~= "" then
			inHotbar[uuid] = true
		end
	end

	local inEquipped = {}
	for _, uuid in pairs(profile.equipped or {}) do
		if type(uuid) == "string" and uuid ~= "" then
			inEquipped[uuid] = true
		end
	end

	local BAG_SLOTS = 27
	local bagItems = {}
	if type(inv) == "table" then
		for uuid, item in pairs(inv) do
			if type(item)=="table" and type(uuid)=="string" and not inHotbar[uuid] and not inEquipped[uuid] then
				table.insert(bagItems, { uuid=uuid, item=item })
			end
		end
		table.sort(bagItems, function(a,b) return a.uuid < b.uuid end)
	end

	do
		for slotIdx = 1, BAG_SLOTS do
			local entry = bagItems[slotIdx]
			local item  = entry and entry.item
			local uuid  = entry and entry.uuid
			local hbOk  = item and bagItemHotbarOk(item)
			local rCol  = item and Types.GetRarityColor(item.rarity) or Color3.fromRGB(35,28,28)
			local dimBg = item and rCol:Lerp(Color3.fromRGB(18,14,14), 0.78) or Color3.fromRGB(22,17,17)
			local nm  = item and itemDisplayName(item) or ""
			local cnt = item and itemCount(item)

			local btn = Instance.new("TextButton")
			btn.Name = uuid and ("Item_"..uuid) or ("Slot_"..slotIdx)
			btn.AutoButtonColor = false
			btn.Text = ""
			btn.BackgroundColor3 = Color3.fromRGB(24, 18, 18)
			btn.BorderSizePixel = 0
			btn.Size = UDim2.fromOffset(60, 60)
			btn.LayoutOrder = slotIdx
			btn.ZIndex = 5
			Instance.new("UICorner", btn).CornerRadius = UDim.new(0, 5)
			local sk = Instance.new("UIStroke", btn)
			sk.Thickness = 1.5
			sk.Color = item and (hbOk and rCol or Color3.fromRGB(50,40,40)) or Color3.fromRGB(38,30,30)
			sk.Transparency = item and (hbOk and 0.4 or 0.7) or 0.35

			local iconImg = item and item.itemId and ItemDefinitions.GetIcon(item.itemId) or ""
			local hasIcon = iconImg ~= ""
			local icon = Instance.new("ImageLabel", btn)
			icon.Name = "Icon"
			icon.Size = UDim2.new(1, -8, 1, -20)
			icon.Position = UDim2.new(0, 4, 0, 4)
			icon.BackgroundColor3 = hasIcon and Color3.fromRGB(0,0,0) or dimBg
			icon.BackgroundTransparency = hasIcon and 1 or (item and (hbOk and 0 or 0.5) or 0.75)
			icon.BorderSizePixel = 0
			icon.Image = iconImg
			icon.ImageTransparency = (item and hasIcon) and 0 or 1
			icon.ScaleType = Enum.ScaleType.Fit
			icon.ZIndex = 6
			Instance.new("UICorner", icon).CornerRadius = UDim.new(0, 4)

			if cnt then
				local badge = Instance.new("TextLabel", btn)
				badge.Size = UDim2.fromOffset(22, 13)
				badge.Position = UDim2.new(1, -24, 0, 2)
				badge.BackgroundTransparency = 1
				badge.Text = "x"..tostring(cnt)
				badge.Font = Enum.Font.GothamBold
				badge.TextSize = 9
				badge.TextColor3 = Color3.fromRGB(230, 220, 200)
				badge.ZIndex = 7
			end

			local lbl = Instance.new("TextLabel", btn)
			lbl.Size = UDim2.new(1, -4, 0, 14)
			lbl.Position = UDim2.new(0, 2, 1, -15)
			lbl.BackgroundTransparency = 1
			lbl.Font = Enum.Font.Gotham
			lbl.TextSize = 8
			lbl.TextColor3 = item and (hbOk and Color3.fromRGB(220,212,195) or Color3.fromRGB(110,100,90)) or Color3.fromRGB(45,36,36)
			lbl.TextXAlignment = Enum.TextXAlignment.Center
			lbl.TextTruncate = Enum.TextTruncate.AtEnd
			lbl.Text = nm
			lbl.ZIndex = 7

			if item and item.maxDurability then
				syncDurabilityLabel(btn, item)
			else
				syncDurabilityLabel(btn, nil)
			end

			if item and uuid then
				local ci, cb = item, btn
				btn.MouseButton2Click:Connect(function()
					if ctx and ctx.onBagSecondary then ctx.onBagSecondary(uuid) end
				end)
				btn.MouseEnter:Connect(function() ItemTooltip.show(ci, cb) end)
				btn.MouseLeave:Connect(function() ItemTooltip.hide() end)
				InventoryDragController.hookSource("bag", btn, { uuid = uuid })
			end
			btn.Parent = refs.inventoryScroll
		end
	end

	local hb = refs.hotbarButtons
	local hsi = refs.hotbarSlotItem
	if type(hb) == "table" then
		for i = 1, HOTBAR_SLOTS do
			local b = hb[i]
			if b then
				local uuid = hotbar[i]
				local it = (type(uuid) == "string" and uuid ~= "") and inv[uuid] or nil
				if hsi then
					hsi[i] = it
				end
				local slotStroke = b:FindFirstChild("SlotRoleStroke")
				if i == 1 then
					if not slotStroke then
						slotStroke = Instance.new("UIStroke", b)
						slotStroke.Name = "SlotRoleStroke"
						slotStroke.Thickness = 2
					end
					slotStroke.Color = Color3.fromRGB(220, 180, 90)
					slotStroke.Enabled = true
				elseif slotStroke then
					slotStroke.Enabled = false
				end
				if it then
					local nm = itemDisplayName(it)
					local cnt = itemCount(it)
					if i == 1 then
						if cnt then
							b.Text = string.format("1 EQ\n%s (x%d)", nm, cnt)
						else
							b.Text = "1 EQ\n" .. nm
						end
					else
						if cnt then
							b.Text = string.format("%d\n%s (x%d)", i, nm, cnt)
						else
							b.Text = string.format("%d\n%s", i, nm)
						end
					end
					b.TextColor3 = Types.GetRarityColor(it.rarity)
					if it.maxDurability then
						local dl = b:FindFirstChild("DurLabel")
						if not dl then
							dl = Instance.new("TextLabel", b)
							dl.Name = "DurLabel"
							dl.Size = UDim2.new(1, 0, 0, 11)
							dl.Position = UDim2.new(0, 0, 1, -12)
							dl.BackgroundTransparency = 1
							dl.Font = Enum.Font.GothamBold
							dl.TextSize = 7
							dl.TextXAlignment = Enum.TextXAlignment.Center
							dl.ZIndex = 6
						end
						local dur = it.durability or 0
						local ratio = it.maxDurability > 0 and dur / it.maxDurability or 0
						dl.Text = it.broken and "BROKEN" or (dur .. "/" .. it.maxDurability)
						dl.TextColor3 = it.broken and Color3.fromRGB(255, 60, 60)
							or (ratio < 0.3 and Color3.fromRGB(255, 80, 80) or (ratio < 0.5 and Color3.fromRGB(255, 200, 50) or Color3.fromRGB(120, 120, 120)))
						b.BackgroundColor3 = (it.broken or ratio < 0.3) and Color3.fromRGB(40, 18, 18)
							or (ratio < 0.5 and Color3.fromRGB(38, 30, 14) or Color3.fromRGB(40, 32, 32))
					end
				else
					b.Text = string.format("%d\n(empty)", i)
					b.TextColor3 = Color3.new(1, 1, 1)
					b.BackgroundColor3 = Color3.fromRGB(40, 32, 32)
					local dlOld = b:FindFirstChild("DurLabel")
					if dlOld then
						dlOld:Destroy()
					end
				end
			end
		end
	end
end

return DungeonMenuUI
