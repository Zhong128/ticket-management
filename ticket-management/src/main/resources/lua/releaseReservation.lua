-- 按token释放一次；不删除后来申请的用户占位。
-- KEYS与reserveStock相同；ARGV: 1代次 2token。返回1释放，0已释放/旧代次，-1缓存缺失。
local expected = {'hash', 'string', 'hash', 'hash', 'hash', 'zset'}
for i = 1, #KEYS do
    local kind = redis.call('TYPE', KEYS[i]).ok
    if kind ~= 'none' and kind ~= expected[i] then
        return -1
    end
end
if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1] then
    return 0
end
local raw = redis.call('HGET', KEYS[5], ARGV[2])
local stock = tonumber(redis.call('GET', KEYS[2]))
if not raw or not stock then
    return -1
end
local record = cjson.decode(raw)
if record.state == 'RELEASED' then
    return 0
end
local quantity = tonumber(record.quantity)
if not quantity or quantity < 1 or quantity ~= math.floor(quantity) then
    return -1
end

record.state = 'RELEASED'
local encoded = cjson.encode(record)
redis.call('INCRBY', KEYS[2], quantity)
redis.call('HSET', KEYS[5], ARGV[2], encoded)
redis.call('HSET', KEYS[4], record.userId .. ':' .. record.requestKey, encoded)
if redis.call('HGET', KEYS[3], record.userId) == ARGV[2] then
    redis.call('HDEL', KEYS[3], record.userId)
end
redis.call('ZREM', KEYS[6], ARGV[2])
return 1
