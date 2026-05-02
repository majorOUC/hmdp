package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsultantShopMapper extends BaseMapper<Shop> {
    @Select("select * from tb_shop where name=#{shopName}")
    Shop findShop(String shopName);
}
