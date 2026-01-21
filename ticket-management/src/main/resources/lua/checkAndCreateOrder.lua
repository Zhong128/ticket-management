-- resources/lua/decrStock.lua
-- 原子扣减库存的Lua脚本
-- KEYS[1]: 库存key (seckill:stock:{tierId})
-- ARGV[1]: 扣减数量
-- 返回: 扣减后的库存数量，如果库存不足返回-1

local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

-- 获取当前库存
local currentStock = redis.call('GET', stockKey)
if not currentStock then
    return -1  -- 库存不存在
end

currentStock = tonumber(currentStock)

-- 检查库存是否足够
if currentStock < quantity then
    return -1  -- 库存不足
end

-- 扣减库存
redis.call('DECRBY', stockKey, quantity)

-- 返回扣减后的库存
local newStock = currentStock - quantity
return newStock