package com.hmdp.tools;

import com.hmdp.entity.Shop;
import com.hmdp.service.ConsultantShopService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShopTool {

    @Autowired
    private ConsultantShopService shopService;

    @Tool(description = "根据商家名称查询商家信息")
    public Shop findShop(@ToolParam(description = "商家名称") String shopName) {
        return shopService.findShop(shopName);
    }

}
