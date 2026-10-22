-- 确认可靠受理，不修改库存；释放后到达的确认不能复活预扣。
-- KEYS与reserveStock相同；ARGV: 1代次 2token。返回1确认，0终态/旧代次，-1缺记录。
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
if not raw then
    return -1
end
local record = cjson.decode(raw)
if record.state == 'RELEASED' then
    return 0
end
record.state = 'ACCEPTED'
local encoded = cjson.encode(record)
redis.call('HSET', KEYS[5], ARGV[2], encoded)
redis.call('HSET', KEYS[4], record.userId .. ':' .. record.requestKey, encoded)
redis.call('ZREM', KEYS[6], ARGV[2])
return 1
