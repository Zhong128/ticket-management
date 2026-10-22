-- 仅开放已完整恢复的代次。KEYS: 元数据、配额；ARGV: 代次。返回1开放，0未就绪。
if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1]
        or redis.call('HGET', KEYS[1], 'expected') ~= redis.call('HGET', KEYS[1], 'loaded')
        or not tonumber(redis.call('GET', KEYS[2])) then
    return 0
end
redis.call('HSET', KEYS[1], 'ready', '1')
return 1
