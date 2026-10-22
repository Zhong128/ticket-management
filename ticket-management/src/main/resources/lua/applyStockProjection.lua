-- 展示库存投影；KEYS: 库存、版本；ARGV: 版本、库存。只接受不旧于当前的版本。
-- 版本按十进制字符串比较，避免Lua浮点数丢失大整数精度。返回1更新，0旧版本。
local old = redis.call('GET', KEYS[2])
local version = ARGV[1]
if old and (#old > #version or (#old == #version and old > version)) then
    return 0
end
redis.call('SET', KEYS[1], ARGV[2])
redis.call('SET', KEYS[2], version)
return 1
