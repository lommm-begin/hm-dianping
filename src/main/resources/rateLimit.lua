-- KEYS[1] 存储限流操作key
-- ARGV[1] 限制次数
-- ARGV[2] 窗口大小
-- ARGV[3] 当前时间戳

local currentTime = tonumber(ARGV[3])
local windowSize = tonumber(ARGV[2])
local limit = tonumber(ARGV[1])

-- 移除窗口外的数据
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, currentTime - windowSize)

-- 获取当前窗口内的请求数量
local count = redis.call('ZCARD', KEYS[1])

if count >= limit then
    return 0 -- 达到上限
end

-- 添加当前请求到窗口
redis.call('ZADD', KEYS[1], currentTime, currentTime)
-- 设置过期时间，避免长期占用内存
redis.call('EXPIRE', KEYS[1], windowSize/1000 + 1) -- 多加1秒确保覆盖

return 1 -- 可以继续访问