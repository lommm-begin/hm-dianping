-- 参数列表
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]

-- 数据
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 脚本业务

--  获取库存（处理nil情况）
local stock = redis.call('get', stockKey)
if stock == false then  -- 如果key不存在，redis.call('get')返回false（Lua中即nil）
    -- 库存不存在
    return 1
end
-- 判断库存是否充足
if tonumber(stock) <= 0 then
    -- 库存不足
    return 1
end

-- 判断用户是否下单 SISMEMBER orderKey userId
if redis.call('sismember', orderKey, userId) == 1 then
    -- 存在，返回2
    return 2
end

-- 扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)

-- 下单(保存用户 sadd orderKey userId
redis.call('sadd', orderKey, userId)

-- 发送消息到redis队列中，XADD stream.orders * k1 v1 k2 v2 ......
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0

