-- 原子预扣并建立用户占位；重复请求返回原 token，不重复扣减。
-- KEYS: 1元数据 2配额 3用户占位Hash 4请求Hash 5令牌Hash 6待确认ZSet。
-- ARGV: 1代次 2用户ID 3请求键 4数量 5token 6创建毫秒 7截止毫秒。
-- 返回: {状态,token,创建毫秒,截止毫秒}；状态见 Java RedisReservationResult。
local expected = {'hash', 'string', 'hash', 'hash', 'hash', 'zset'}
for i = 1, #KEYS do
    local kind = redis.call('TYPE', KEYS[i]).ok
    if kind ~= 'none' and kind ~= expected[i] then
        return {'INVALID', '', '', ''}
    end
end
local quantity = tonumber(ARGV[4])
local now = tonumber(ARGV[6])
local expiry = tonumber(ARGV[7])
if not quantity or quantity < 1 or quantity > 5 or quantity ~= math.floor(quantity)
        or not now or not expiry or expiry <= now or ARGV[2] == '' or ARGV[3] == '' or ARGV[5] == '' then
    return {'INVALID', '', '', ''}
end

-- 未完成重建的代次不能售票，也不能由旧线程继续预扣。
if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1]
        or redis.call('HGET', KEYS[1], 'ready') ~= '1' then
    return {'NOT_READY', '', '', ''}
end
local requestField = ARGV[2] .. ':' .. ARGV[3]
local previous = redis.call('HGET', KEYS[4], requestField)
if previous then
    local old = cjson.decode(previous)
    if tonumber(old.quantity) ~= quantity then
        return {'MISMATCH', '', '', ''}
    end
    return {'EXISTING', old.token, tostring(old.createdAt), tostring(old.expiresAt)}
end
if redis.call('HEXISTS', KEYS[3], ARGV[2]) == 1 then
    return {'DUPLICATE', '', '', ''}
end
local stock = tonumber(redis.call('GET', KEYS[2]))
if not stock or stock < 0 or stock ~= math.floor(stock) then
    return {'NOT_READY', '', '', ''}
end
if stock < quantity then
    return {'SOLD_OUT', '', '', ''}
end
if redis.call('HEXISTS', KEYS[5], ARGV[5]) == 1 then
    return {'INVALID', '', '', ''}
end

-- 所有校验完成后再写入。状态保留到业务终态，不依靠 TTL 解除限购。
local record = cjson.encode({token=ARGV[5], userId=ARGV[2], requestKey=ARGV[3], quantity=quantity,
    createdAt=now, expiresAt=expiry, state='PROVISIONAL'})
redis.call('DECRBY', KEYS[2], quantity)
redis.call('HSET', KEYS[3], ARGV[2], ARGV[5])
redis.call('HSET', KEYS[4], requestField, record)
redis.call('HSET', KEYS[5], ARGV[5], record)
redis.call('ZADD', KEYS[6], now, ARGV[5])
return {'OK', ARGV[5], ARGV[6], ARGV[7]}
