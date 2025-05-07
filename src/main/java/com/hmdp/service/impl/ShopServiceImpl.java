package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,
                id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES);

        //用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 用逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,
//                id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL,
//                TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        //从redis 中查询
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //存在，直接返回
            return null;
        }

        //命中，需要先把json 反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回店铺信息
            return shop;
        }
        //过期，需要缓存重建
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if(lock){
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回商铺信息
        return shop;
    }



    public Shop queryWithMutex(Long id){
        //从redis 中查询
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if(shopJson != null){
            return null;
        }
        //实现缓存重建
        Shop shop = null;
        try {
            //获取互斥锁
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            //判断是否获取锁成功
            if (!lock) {
                //失败，则休眠并重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return queryWithMutex(id);
            }
            //成功，根据id查询数据库
            shop = getById(id);
            //不存在，返回错误
            if (shop == null) {
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        //返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id){
        //从redis 中查询
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空值
        if(shopJson != null){
            return null;
        }

        //不存在，根据id查询数据库
        Shop shop = getById(id);
        //不存在，返回错误
        if (shop == null) {
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 先更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if(x == null || y == null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis，按照距离排序，分页，结果：shopId,distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
//        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
//                .search(key,
//                        GeoReference.fromCoordinate(x, y),
//                        new Distance(5000),
//                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
//                );
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(
                        key,
                        new Circle(x, y, 5000), // 半径5000米
                        RedisGeoCommands.GeoRadiusCommandArgs
                                .newGeoRadiusArgs()
                                .includeDistance() // 包含距离
                                .sortAscending()    // 按照距离升序排序
                                .limit(end)          // 限制返回数量
                );
        //解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            //没有下一页，结束
            return Result.ok(Collections.emptyList());
        }
        //截取从from到end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //根据id查询shop
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + StrUtil.join(",", ids) + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }
}
