package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.ConsultantShopMapper;
import com.hmdp.mapper.ConsultantVoucherMapper;
import com.hmdp.mapper.ConsultantVoucherOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConsultantVoucherService {

    @Autowired
    private ConsultantVoucherMapper voucherMapper;

    @Autowired
    private ConsultantVoucherOrderMapper voucherOrderMapper;

    @Autowired
    private ConsultantShopMapper shopMapper;

    public List<Voucher> findVoucherByShopName(String shopName) {
        Shop shop = shopMapper.findShop(shopName);
        if (shop == null) {
            return new ArrayList<>();
        }
        return voucherMapper.findVoucherByShopId(shop.getId());
    }

    public List<Voucher> findVoucherByUserId(Long userId) {
        List<Long> voucherIds = voucherOrderMapper.findByUserId(userId);
        if (voucherIds == null || voucherIds.isEmpty()) {
            return new ArrayList<>();
        }
        return voucherMapper.findVoucherByIds(voucherIds);
    }

    public List<Voucher> findVoucherByUserPhone(String phone) {
        List<Long> voucherIds = voucherOrderMapper.findByPhone(phone);
        if (voucherIds == null || voucherIds.isEmpty()) {
            return new ArrayList<>();
        }
        return voucherMapper.findVoucherByIds(voucherIds);
    }
}
