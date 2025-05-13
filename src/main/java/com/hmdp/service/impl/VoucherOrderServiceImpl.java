package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> {
//
//    @Resource
//    private ISeckillVoucherService seckillVoucherService;
//
//    @Resource
//    private RedisIdWorker redisIdWorker;
//
//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Resource
//    private RedissonClient redissonClient;
//
//    @Resource
//    private ExecutorService executorService;
//
//    private static DefaultRedisScript<Long> SECKILL_SCRIPT;
//
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
//
//    final String QUEUE_NAME = "stream.orders";
//
//    @PostConstruct
//    private void init() {
//        executorService.submit(()->{
//           while (true) {
//               try {
//                   // 获取队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.order >
//                   List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                           Consumer.from("g1", "c1"),
//                           StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                           StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
//                   );
//                   // 判断是否获取成功
//                   if (list == null || list.isEmpty()) {
//                       continue;
//                   }
//                   // 获取消息中的订单
//                   MapRecord<String, Object, Object> entries = list.get(0);
//
//                   Map<Object, Object> value = entries.getValue();
//                   VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//
//                   // 创建订单
//                   handleVoucherOrder(voucherOrder);
//
//                   // 手动确认消息
//                   stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, "g1", entries.getId());
//               } catch (Exception e) {
//                   log.error("处理消息队列中的消息时发生错误！" + e);
//
//                   handlePendingList();
//               }
//           }
//        });
//    }
//
//    private void handlePendingList() {
//        while (true) {
//            try {
//                // 获取队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1  STREAMS streams.order 0
//                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                        Consumer.from("g1", "c1"),
//                        StreamReadOptions.empty().count(1),
//                        StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
//                );
//                // 判断是否获取成功
//                if (list == null || list.isEmpty()) {
//                    // 失败，结束循环
//                    break;
//                }
//                // 获取消息中的订单
//                MapRecord<String, Object, Object> entries = list.get(0);
//
//                Map<Object, Object> value = entries.getValue();
//                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//
//                // 创建订单
//                handleVoucherOrder(voucherOrder);
//
//                // 手动确认消息
//                stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, "g1", entries.getId());
//            } catch (Exception e) {
//                log.error("处理PendingList中的消息时发生错误！" + e);
//                try {
//                    Thread.sleep(200);
//                } catch (InterruptedException ex) {
//                    log.error("休眠发生错误");
//                }
//            }
//        }
//    }
//
//    private void handleVoucherOrder(VoucherOrder take) {
//        RLock rLock = redissonClient.getLock(SECKILL_LOCK_KEY + take.getUserId());
//
//        try {
//            boolean isLock = rLock.tryLock();
//            if (!isLock) {
//                // 获取锁失败
//                log.error("不允许重复下单");
//            }
//             iVoucherOrderService.createVoucherOrder(take);
//        } finally {
//            rLock.unlock();
//        }
//    }
//
//    private IVoucherOrderService iVoucherOrderService;
//
//    // 使用lua脚本
//    public Result seckillVoucher(Long voucherId) {
//        // 生成订单ID
//        Long orderId = redisIdWorker.nextId(ORDER_PREFIX_KEY);
//
//        Long executed = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                UserHolder.getUser().getId().toString(),
//                orderId.toString()
//        );
//
//        int r = executed.intValue();
//
//        if (r != 0) {
//            // 失败则总数减一
//            redisIdWorker.decrementId(orderId);
//            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
//        }
//
//        // 获取代理对象
//        iVoucherOrderService = (IVoucherOrderService)AopContext.currentProxy();
//
//        return Result.ok(orderId);
//    }
//
//    @Transactional
//    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        Long count = query()
//                .eq("user_id", voucherOrder.getUserId())
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .count();
//
//        if (count > 0) {
//            log.error("已经获取过了！");
//        }
//
//        // 扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherOrder.getVoucherId())
//                .gt("stock", 0)
//                .update();
//
//        if (!success) {
//            log.error("优惠券已经被抢光了");
//        }
//
//        // 保存订单
//        save(voucherOrder);
//    }

//    @Override
//    public Result seckillVoucher2(Long voucherId) {
//        // 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 秒杀活动是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀活动未开始！");
//        }
//
//        // 秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀活动已经结束！");
//        }
//
//        // 库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("优惠券已经被抢光了~");
//        }
//        Long id = UserHolder.getUser().getId();
//
////        synchronized (id.toString().intern()) { // 防止每次都是新的引用，去常量池找同一个值
////            IVoucherOrderService iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
////            return iVoucherOrderService.createVoucherOrder(id, voucherId);
////        }
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, SECKILL_LOCK_KEY + id);
//
//        RLock rLock = redissonClient.getLock(SECKILL_LOCK_KEY + id);
//
//        boolean isLock = rLock.tryLock();
//        if (!isLock) {
//            // 获取锁失败
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            return iVoucherOrderService.createVoucherOrder(voucherId);
//        } finally {
//            rLock.unlock();
//        }
//    }
}
