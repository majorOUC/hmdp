package com.hmdp.tools;

import com.hmdp.entity.Voucher;
import com.hmdp.service.ConsultantVoucherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VoucherTool {

    @Autowired
    private ConsultantVoucherService voucherService;

    @Tool(description = "根据商家名称查询商家的优惠券信息")
    public List<Voucher> findVoucherByShopName(@ToolParam(description = "商家名称") String shopName) {
        return voucherService.findVoucherByShopName(shopName);
    }

    @Tool(description = "根据用户手机号查询用户拥有的优惠券信息")
    public List<Voucher> findVoucherByUserPhone(@ToolParam(description = "用户手机号") String userPhone) {
        return voucherService.findVoucherByUserPhone(userPhone);
    }

}
