package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ConsultantShopMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConsultantShopService {

    @Autowired
    private ConsultantShopMapper shopMapper;

    public Shop findShop(String shopName) {
        return shopMapper.findShop(shopName);
    }

}
