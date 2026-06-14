--[[
  DungeonProfileServer — authoritative profile, equip/hotbar/chest, weapon↔hotbar[1] mirror.
  Place as Script under ServerScriptService named DungeonProfileServer.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local DataStoreService = game:GetService("DataStoreService")
local Types = require(ReplicatedStorage:WaitForChild("DungeonProfileTypes"))
local ItemDefinitions = require(ReplicatedStorage:WaitForChild("ItemDefinitions"))

local pushEvent = ReplicatedStorage:WaitForChild("DungeonProfilePush")
local rfSync = ReplicatedStorage:WaitForChild("DungeonProfileRequestSync")
local rfInventoryAct = ReplicatedStorage:WaitForChild("DungeonInventoryAct")
local evEquip = ReplicatedStorage:WaitForChild("DungeonEquipItem")
local evUnequip = ReplicatedStorage:WaitForChild("DungeonUnequipItem")

local STORE_NAME = "DungeonBloxProfileV1"
local store = nil
do
	local ok, s = pcall(function()
		return DataStoreService:GetDataStore(STORE_NAME)
	end)
	if ok then
		store = s
	end
end

local profiles = {}

local function sumArmorFromEquipped(profile)
	local n = 0
	local inv = profile.inventory
	local eq = profile.equipped
	if type(inv) ~= "table" or type(eq) ~= "table" then
		return 0
	end
	for _, uuid in pairs(eq) do
		if type(uuid) == "string" and uuid ~= "" then
			local it = inv[uuid]
			if it and type(it.itemId) == "string" then
				local def = ItemDefinitions.Get(it.itemId)
				if def and type(def.Armor) == "number" then
					n = n + def.Armor
				end
			end
		end
	end
	return n
end

local function buildDerived(profile)
	local sc = profile.stats and profile.stats.combat or {}
	local rt = profile.runtime or {}
	local gearArmor = sumArmorFromEquipped(profile)
	local maxHp = math.floor((sc.maxHp or 100) + 0)
	local hp = math.floor(rt.currentHp or maxHp)
	hp = math.clamp(hp, 0, maxHp)
	return {
		combat = {
			hp = hp,
			maxHp = maxHp,
			armor = gearArmor,
			hpRegen = sc.hpRegen or 0.5,
			energyRegen = sc.energyRegen or 8,
		},
		mining = {
			miningLevel = (profile.stats and profile.stats.mining and (profile.stats.mining.miningLevel or profile.stats.mining.level))
				or 1,
		},
		fishing = {
			fishingLevel = (profile.stats and profile.stats.fishing and (profile.stats.fishing.fishingLevel or profile.stats.fishing.level))
				or 1,
		},
	}
end

local function push(player, profile)
	local snap = { profile = profile, derived = buildDerived(profile) }
	pushEvent:FireClient(player, snap)
end

local function saveProfile(player, profile)
	if not store then
		return
	end
	local key = tostring(player.UserId)
	local ok, err = pcall(function()
		store:SetAsync(key, profile)
	end)
	if not ok then
		warn("[DungeonProfileServer] Save failed", err)
	end
end

local function clearUuidFromHotbar(profile, uuid)
	local hb = profile.hotbar
	if type(hb) ~= "table" or type(uuid) ~= "string" then
		return
	end
	for i = 1, 9 do
		if hb[i] == uuid then
			hb[i] = nil
		end
	end
end

local function syncWeaponHotbar(profile)
	local hb = profile.hotbar
	if type(hb) ~= "table" then
		return
	end
	local w = profile.equipped and profile.equipped.Weapon
	if type(w) == "string" and w ~= "" and profile.inventory[w] then
		hb[1] = w
	else
		hb[1] = nil
	end
end

local function migrateHotbarWeaponSlot(profile)
	syncWeaponHotbar(profile)
	local hb = profile.hotbar
	local ew = profile.equipped and profile.equipped.Weapon
	if type(hb[1]) ~= "string" or hb[1] == "" then
		return
	end
	if type(ew) == "string" and ew ~= "" then
		return
	end
	local u1 = hb[1]
	local it = profile.inventory[u1]
	if not it or it.type ~= "Weapon" then
		return
	end
	for j = 2, 9 do
		if hb[j] == nil or hb[j] == "" then
			hb[j] = u1
			hb[1] = nil
			return
		end
	end
	hb[1] = nil
end

local function loadOrCreateProfile(player)
	local key = tostring(player.UserId)
	local base = Types.DefaultProfile()
	if store then
		local ok, data = pcall(function()
			return store:GetAsync(key)
		end)
		if ok and type(data) == "table" then
			for k, v in pairs(data) do
				base[k] = v
			end
		end
	end
	Types.Reconcile(base)
	migrateHotbarWeaponSlot(base)
	syncWeaponHotbar(base)
	return base
end

local function uuidInEquipped(profile, uuid)
	for _, u in pairs(profile.equipped or {}) do
		if u == uuid then
			return true
		end
	end
	return false
end

local function resolveEquipSlot(item)
	if not item then
		return nil
	end
	local slot = Types.GetAllowedEquipSlot(item)
	if slot == "Armor" then
		local def = ItemDefinitions.Get(item.itemId)
		return def and def.Slot or "Chest"
	end
	return slot
end

local function equipUuid(profile, uuid)
	local inv = profile.inventory
	if type(uuid) ~= "string" or uuid == "" or type(inv) ~= "table" then
		return false, "bad_uuid"
	end
	local item = inv[uuid]
	if not item then
		return false, "not_in_inventory"
	end
	if item.broken then
		return false, "item_broken"
	end
	local slot = resolveEquipSlot(item)
	if not slot then
		return false, "not_equippable"
	end
	profile.equipped[slot] = uuid
	clearUuidFromHotbar(profile, uuid)
	syncWeaponHotbar(profile)
	return true, nil
end

local function unequipSlot(profile, slot)
	local u = profile.equipped[slot]
	if type(u) ~= "string" or u == "" then
		return false, "empty"
	end
	if not profile.inventory[u] then
		profile.equipped[slot] = nil
		syncWeaponHotbar(profile)
		return true, nil
	end
	profile.equipped[slot] = nil
	syncWeaponHotbar(profile)
	return true, nil
end

local function hotbarEligible(item)
	if not item or type(item.itemId) ~= "string" then
		return false
	end
	if ItemDefinitions.IsHotbarEquippable(item.itemId) then
		return true
	end
	if item.type == "Weapon" or item.type == "Consumable" then
		return true
	end
	if item.type == "Material" then
		if item.equipSlot == "Pickaxe" or item.equipSlot == "FishingSpear" then
			return true
		end
		if type(item.toolPrefabName) == "string" and item.toolPrefabName ~= "" then
			return true
		end
	end
	return false
end

local function handleSetHotbar(player, profile, slot, uuid)
	if slot < 1 or slot > 9 then
		return false, "bad_slot"
	end
	if slot == 1 then
		return false, "reserved_slot"
	end
	if uuid == nil then
		profile.hotbar[slot] = nil
		syncWeaponHotbar(profile)
		return true, nil
	end
	if type(uuid) ~= "string" or uuid == "" then
		return false, "bad_uuid"
	end
	local item = profile.inventory[uuid]
	if not item then
		return false, "not_in_inventory"
	end
	if uuidInEquipped(profile, uuid) then
		return false, "equipped"
	end
	if not hotbarEligible(item) then
		return false, "not_hotbar_eligible"
	end
	clearUuidFromHotbar(profile, uuid)
	profile.hotbar[slot] = uuid
	syncWeaponHotbar(profile)
	return true, nil
end

local function firstEmptyHotbarFrom2(profile)
	for i = 2, 9 do
		if profile.hotbar[i] == nil or profile.hotbar[i] == "" then
			return i
		end
	end
	return nil
end

local function handleApplyEnchantScroll(profile, scrollUuid, targetUuid)
	local scroll = profile.inventory[scrollUuid]
	local target = profile.inventory[targetUuid]
	if not scroll or not target then
		return false, "missing_item"
	end
	if not ItemDefinitions.IsEnchantScroll(scroll.itemId) then
		return false, "not_scroll"
	end
	local st = ItemDefinitions.GetScrollTarget(scroll.itemId)
	if st == "Weapon" and target.type ~= "Weapon" then
		return false, "wrong_target"
	end
	if st == "Armor" and target.type ~= "Armor" then
		return false, "wrong_target"
	end
	target.enchantLevel = math.min(50, (target.enchantLevel or 0) + 1)
	if scroll.count and scroll.count > 1 then
		scroll.count = scroll.count - 1
	else
		profile.inventory[scrollUuid] = nil
	end
	return true, nil
end

local function handleChestDeposit(profile, uuid, slot)
	if slot < 1 or slot > Types.CHEST_SLOT_COUNT then
		return false, "bad_slot"
	end
	local cu = profile.chestSlots[slot]
	if type(cu) == "string" and cu ~= "" then
		return false, "occupied"
	end
	if uuidInEquipped(profile, uuid) then
		return false, "equipped"
	end
	for i = 1, 9 do
		if profile.hotbar[i] == uuid then
			return false, "in_hotbar"
		end
	end
	local it = profile.inventory[uuid]
	if not it then
		return false, "not_in_inventory"
	end
	profile.chestInventory[uuid] = it
	profile.inventory[uuid] = nil
	profile.chestSlots[slot] = uuid
	return true, nil
end

local function handleChestWithdraw(profile, slot)
	local uuid = profile.chestSlots[slot]
	if type(uuid) ~= "string" or uuid == "" then
		return false, "empty"
	end
	local it = profile.chestInventory[uuid]
	if not it then
		profile.chestSlots[slot] = nil
		return false, "missing"
	end
	profile.inventory[uuid] = it
	profile.chestInventory[uuid] = nil
	profile.chestSlots[slot] = nil
	return true, nil
end

local function onInventoryAct(player, act)
	local profile = profiles[player]
	if not profile or type(act) ~= "table" then
		return false, "no_profile"
	end
	local kind = act.kind
	if kind == "SetHotbar" then
		return handleSetHotbar(player, profile, tonumber(act.slot) or 0, act.uuid)
	elseif kind == "AssignFirstEmptyHotbar" then
		local uuid = act.uuid
		if type(uuid) ~= "string" or uuid == "" then
			return false, "bad_uuid"
		end
		local item = profile.inventory[uuid]
		if not item or uuidInEquipped(profile, uuid) or not hotbarEligible(item) then
			return false, "cannot_assign"
		end
		local idx = firstEmptyHotbarFrom2(profile)
		if not idx then
			return false, "hotbar_full"
		end
		return handleSetHotbar(player, profile, idx, uuid)
	elseif kind == "ApplyEnchantScroll" then
		local ok, err = handleApplyEnchantScroll(profile, act.scrollUuid, act.targetUuid)
		return ok, err
	elseif kind == "ChestDeposit" then
		return handleChestDeposit(profile, act.uuid, tonumber(act.slot) or 0)
	elseif kind == "ChestWithdraw" then
		return handleChestWithdraw(profile, tonumber(act.slot) or 0)
	end
	return false, "unknown_act"
end

rfInventoryAct.OnServerInvoke = function(player, act)
	local profile = profiles[player]
	if not profile then
		return false, "no_profile"
	end
	local ok, err = onInventoryAct(player, act)
	if ok then
		push(player, profile)
		saveProfile(player, profile)
	end
	return ok, err
end

rfSync.OnServerInvoke = function(player)
	local profile = profiles[player]
	if not profile then
		local blank = Types.Reconcile(Types.DefaultProfile())
		return { profile = blank, derived = buildDerived(blank) }
	end
	return { profile = profile, derived = buildDerived(profile) }
end

if evEquip:IsA("RemoteEvent") then
	evEquip.OnServerEvent:Connect(function(player, itemUuid)
		local pr = profiles[player]
		if not pr or type(itemUuid) ~= "string" then
			return
		end
		local ok = equipUuid(pr, itemUuid)
		if ok then
			push(player, pr)
			saveProfile(player, pr)
		end
	end)
elseif evEquip:IsA("RemoteFunction") then
	evEquip.OnServerInvoke = function(player, itemUuid)
		local pr = profiles[player]
		if not pr or type(itemUuid) ~= "string" then
			return false
		end
		local ok = equipUuid(pr, itemUuid)
		if ok then
			push(player, pr)
			saveProfile(player, pr)
		end
		return ok
	end
end

if evUnequip:IsA("RemoteEvent") then
	evUnequip.OnServerEvent:Connect(function(player, slot)
		local pr = profiles[player]
		if not pr or type(slot) ~= "string" then
			return
		end
		unequipSlot(pr, slot)
		push(player, pr)
		saveProfile(player, pr)
	end)
elseif evUnequip:IsA("RemoteFunction") then
	evUnequip.OnServerInvoke = function(player, slot)
		local pr = profiles[player]
		if not pr or type(slot) ~= "string" then
			return false
		end
		unequipSlot(pr, slot)
		push(player, pr)
		saveProfile(player, pr)
		return true
	end
end

Players.PlayerAdded:Connect(function(player)
	local profile = loadOrCreateProfile(player)
	profiles[player] = profile
	push(player, profile)
end)

Players.PlayerRemoving:Connect(function(player)
	local p = profiles[player]
	if p then
		saveProfile(player, p)
		profiles[player] = nil
	end
end)

for _, plr in ipairs(Players:GetPlayers()) do
	local profile = loadOrCreateProfile(plr)
	profiles[plr] = profile
	push(plr, profile)
end

print("[DungeonProfileServer] ready")
