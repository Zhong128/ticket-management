-- 退役旧代次数据，不给活跃配额或占位设置TTL。
-- KEYS: meta,退役游标,每个旧代次的quota/users/requests/tokens/pending。
-- ARGV: 当前代次,原游标,目标游标。返回1完成，0状态已变化，-1参数错误。
-- 先核对当前READY代次和游标，最多清理100代；重复清理不会影响新代次。
local epoch = tonumber(ARGV[1])
local before = tonumber(ARGV[2])
local through = tonumber(ARGV[3])
if not epoch or not before or not through or before < 0 or through <= before
        or epoch % 1 ~= 0 or before % 1 ~= 0 or through % 1 ~= 0
        or through >= epoch or through - before > 100
        or #KEYS ~= 2 + (through - before) * 5 then
    return -1
end
if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1]
        or redis.call('HGET', KEYS[1], 'ready') ~= '1'
        or tonumber(redis.call('GET', KEYS[2]) or '0') ~= before then
    return 0
end
local prefix = string.sub(KEYS[1], 1, -5)
if string.sub(KEYS[1], -4) ~= 'meta' or KEYS[2] ~= prefix .. 'retired-through' then
    return -1
end
local kinds = {'quota:', 'users:', 'requests:', 'tokens:', 'pending:'}
for old = before + 1, through do
    for index, kind in ipairs(kinds) do
        local position = 2 + (old - before - 1) * 5 + index
        if KEYS[position] ~= prefix .. kind .. tostring(old) then
            return -1
        end
    end
end
redis.call('UNLINK', unpack(KEYS, 3))
redis.call('SET', KEYS[2], ARGV[3])
return 1
