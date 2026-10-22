-- 向未开放代次分批恢复有效申请；批次号去重，重试不能重复计数。
-- KEYS与reserveStock相同；ARGV: 代次,配额,预期条数,批次号,记录JSON,是否初始化。
-- 返回1写入，0重复批次，-1代次/参数不合法。初始化只能由持有数据库暂停屏障的重建者执行。
local stock = tonumber(ARGV[2])
local expected = tonumber(ARGV[3])
local records = cjson.decode(ARGV[5])
if not stock or stock < 0 or not expected or expected < 0 then
    return -1
end
for _, record in ipairs(records) do
    if not record.token or not record.userId or not record.requestKey or not tonumber(record.quantity) then
        return -1
    end
end
if ARGV[6] == '1' then
    local previousEpoch = tonumber(redis.call('HGET', KEYS[1], 'epoch'))
    if previousEpoch and previousEpoch >= tonumber(ARGV[1]) then
        return -1
    end
    redis.call('DEL', unpack(KEYS))
    redis.call('HSET', KEYS[1], 'epoch', ARGV[1], 'ready', '0', 'expected', ARGV[3], 'loaded', '0')
    redis.call('SET', KEYS[2], ARGV[2])
end
if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1] or redis.call('HGET', KEYS[1], 'ready') ~= '0' then
    return -1
end
local batchField = 'batch:' .. ARGV[4]
if redis.call('HEXISTS', KEYS[1], batchField) == 1 then
    return 0
end
for _, record in ipairs(records) do
    record.state = 'ACCEPTED'
    local encoded = cjson.encode(record)
    redis.call('HSET', KEYS[3], tostring(record.userId), record.token)
    redis.call('HSET', KEYS[4], tostring(record.userId) .. ':' .. record.requestKey, encoded)
    redis.call('HSET', KEYS[5], record.token, encoded)
end
redis.call('HSET', KEYS[1], batchField, '1')
redis.call('HINCRBY', KEYS[1], 'loaded', #records)
return 1
