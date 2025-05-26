-- KEYS[1] 存储限流操作key
-- ARGV[1] 限制次数
-- ARGV[2] 结束时间

local nowLimit = redis.call('INCR', KEYS[1]) -- 递增1

if tonumber(nowLimit) == 1  then -- 第一次，设置过期时间
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

if tonumber(nowLimit) > tonumber(ARGV[1]) then
    return 0 -- 达到上限
end

return 1 -- 可以继续访问