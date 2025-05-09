-- 参数说明
-- KEYS: 无
-- ARGV[1]: voucherId (优惠券ID)
-- ARGV[2]: userId (用户ID)
-- ARGV[3]: currentTime (当前时间戳，秒)

-- Redis Key
local voucherKey = 'seckill:stock:' .. ARGV[1]  -- 优惠券Hash键

-- 检查key是否存在
if redis.call('EXISTS', voucherKey) == 0 then
    return 404 -- 优惠券不存在
end

-- 1. 获取优惠券信息
local stock = tonumber(redis.call('HGET', voucherKey, 'stock')) or 0
local status = tonumber(redis.call('HGET', voucherKey, 'status')) or 0
local beginTime = tonumber(redis.call('HGET', voucherKey, 'beginTime')) or 0
local endTime = tonumber(redis.call('HGET', voucherKey, 'endTime')) or 0


-- 2. 状态校验（数据库字段状态码：1=上架, 2=下架, 3=过期）
-- 不等于
if status ~= 1 then
    return status + 2  -- 3=下架, 4=过期
end

-- 3. 时间校验
local current = tonumber(ARGV[3])
if current < beginTime then return 5 end  -- 未开始
if current > endTime then return 6 end      -- 已结束

-- 4. 库存校验
if stock <= 0 then return 1 end  -- 库存不足

-- 5. 重复下单校验
local orderKey = 'seckill:order:' .. ARGV[1]
if redis.call('SISMEMBER', orderKey, ARGV[2]) == 1 then
    return 2  -- 重复购买
end

-- 6. 执行秒杀
redis.call('HINCRBY', voucherKey, 'stock', -1)  -- 扣减库存
redis.call('SADD', orderKey, ARGV[2])          -- 记录订单
redis.call('EXPIRE', orderKey, endTime)        -- 设置过期时间为优惠券结束时间
return 0  -- 成功