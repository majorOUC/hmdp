package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class ChatResponse {
    private String reply;
    private List<ShopInfo> shops;
    private List<VoucherInfo> vouchers;
    
    public ChatResponse() {
    }
    
    public ChatResponse(String reply, List<ShopInfo> shops, List<VoucherInfo> vouchers) {
        this.reply = reply;
        this.shops = shops;
        this.vouchers = vouchers;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ShopInfo {
        private Long id;
        private String name;
        private String address;
        private Double x;
        private Double y;
        private Long avgPrice;
        private Double distance;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VoucherInfo {
        private Long id;
        private String title;
        private String subTitle;
        private Long payValue;
        private Long actualValue;
        private Long shopId;
        private String shopName;
    }
}
