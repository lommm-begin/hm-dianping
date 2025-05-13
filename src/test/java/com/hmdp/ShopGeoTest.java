package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.constants.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class ShopGeoTest {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    ShopServiceImpl shopService;

    @Test
    public void contextLoads() {

    }

    @Test
    public void test01() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        collect.forEach((k, v) -> {
            String key = SHOP_GEO_KEY + k;
            List<RedisGeoCommands.GeoLocation<String>> geoLocations =
                    v.stream()
                    .map(shop ->
                            new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())))
                    .toList();
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
        });
    }
}
