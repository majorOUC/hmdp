package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

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
    @Override
    public Result queryTypeLits() {
        //查询商铺
        String shopTypeList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE);
        //获取商铺列表
        if(StringUtils.isNotBlank(shopTypeList)){
            List<ShopType> list = JSONUtil.toList(shopTypeList, ShopType.class);
            return Result.ok(list);
        }
        //如果是空的直接查找数据库
        List<ShopType> shopTList = query().orderByAsc("sort").list();
        //如果数据不存在需要返回错误结果
        if(shopTList==null){
            return Result.fail("商家列表不存在!");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE, JSONUtil.toJsonStr(shopTList),  RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTList);
    }

}
