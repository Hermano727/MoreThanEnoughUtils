--[[
  Client networking for Dungeon profile snapshots + equip/unequip requests.
  All authoritative state arrives via DungeonProfilePush; never mutate locally except cache.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local push = ReplicatedStorage:WaitForChild("DungeonProfilePush")
local rfEquip = ReplicatedStorage:WaitForChild("DungeonEquipItem")
local rfUnequip = ReplicatedStorage:WaitForChild("DungeonUnequipItem")
local rfSync = ReplicatedStorage:WaitForChild("DungeonProfileRequestSync")
local rfInventoryAct = ReplicatedStorage:WaitForChild("DungeonInventoryAct")

local DungeonMenuNet = {}

local lastSnapshot = nil
local listener = nil

function DungeonMenuNet.setListener(cb)
	listener = cb
end

function DungeonMenuNet.getLastSnapshot()
	return lastSnapshot
end

function DungeonMenuNet.requestSync()
	if not rfSync or not rfSync:IsA("RemoteFunction") then
		return
	end
	local ok, res = pcall(function()
		return rfSync:InvokeServer()
	end)
	if not ok then
		warn("[DungeonMenuNet] requestSync InvokeServer failed:", res)
		return
	end
	if type(res) == "table" and res.profile ~= nil then
		lastSnapshot = res
		if listener then
			listener(res)
		end
	end
end

function DungeonMenuNet.start()
	push.OnClientEvent:Connect(function(payload)
		if type(payload) ~= "table" then
			return
		end
		local snap = payload
		lastSnapshot = snap
		if listener then
			listener(snap)
		end
	end)

	task.defer(function()
		DungeonMenuNet.requestSync()
	end)
end

function DungeonMenuNet.requestEquip(itemUuid)
	if rfEquip and rfEquip:IsA("RemoteEvent") then
		rfEquip:FireServer(itemUuid)
	elseif rfEquip and rfEquip:IsA("RemoteFunction") then
		local ok, err = pcall(function()
			return rfEquip:InvokeServer(itemUuid)
		end)
		if not ok then
			warn("[DungeonMenuNet] requestEquip InvokeServer failed:", err)
		end
	end
end

function DungeonMenuNet.requestUnequip(slot)
	if rfUnequip and rfUnequip:IsA("RemoteEvent") then
		rfUnequip:FireServer(slot)
	elseif rfUnequip and rfUnequip:IsA("RemoteFunction") then
		local ok, err = pcall(function()
			return rfUnequip:InvokeServer(slot)
		end)
		if not ok then
			warn("[DungeonMenuNet] requestUnequip InvokeServer failed:", err)
		end
	end
end

function DungeonMenuNet.requestInventoryAct(act)
	if not rfInventoryAct or not rfInventoryAct:IsA("RemoteFunction") then
		return false, "no_rf"
	end
	local ok, a, b = pcall(function()
		return rfInventoryAct:InvokeServer(act)
	end)
	if not ok then
		warn("[DungeonMenuNet] requestInventoryAct InvokeServer failed:", a)
		return false, "invoke_failed"
	end
	return a, b
end

return DungeonMenuNet
