package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.constants.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        List<ShopType> sort = list();
        saveShopType(sort);
    }

    private void saveShopType(List<ShopType> sort) {
        if (stringRedisTemplate.hasKey(CACHE_SHOP_TYPE_KEY)) {
            return;
        }
        List<String> list = sort.stream()
                .map(obj -> {
                    String string = "";
                    try {
                        string = objectMapper.writeValueAsString(obj);
                    } catch (JsonProcessingException e) {
                        log.error("序列化首页类型时发生错误: {}", e);
                    }
                    return string;
                })
                .toList();

        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, list);
    }

    @Override
    public Result queryForType() {
        // 查询缓存
        List<String> range = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        if (range != null && !range.isEmpty()) {
            List<ShopType> list = range.stream()
                    .map(s -> {
                        try {
                            return objectMapper.readValue(s, ShopType.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e + "json解析成对象错误");
                        }
                    })
                    .toList();
            // 存在，直接返回
            return Result.ok(list);
        }

        // 不存在，查询数据库
        List<ShopType> sort = query().orderByAsc("sort").list();

        // 不存在，报错
        if (sort == null && sort.isEmpty()) {
            return Result.fail("商品类型查询错误");
        }

        saveShopType(sort);

        return Result.ok(sort);
    }
}
