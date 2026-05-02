package com.hmdp.tools;

import com.hmdp.pojo.Reservation;
import com.hmdp.service.ConsultantShopService;
import com.hmdp.service.ReservationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationTool {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ConsultantShopService shopService;

    @Tool(description = "预约到店消费服务")
    public void addReservation(
            @ToolParam(description = "用户姓名") String name,
            @ToolParam(description = "用户手机号") String phone,
            @ToolParam(description = "预约到店消费时间，格式为：yyyy-MM-dd'T'HH:mm") String communicationTime,
            @ToolParam(description = "预约指定的商家") String shopName
    ) {
        Reservation reservation = new Reservation(null, name, phone, LocalDateTime.parse(communicationTime), shopName);
        reservationService.insert(reservation);
    }

    @Tool(description = "根据用户手机号查询预约单")
    public List<Reservation> findReservation(@ToolParam(description = "用户手机号") String phone) {
        return reservationService.findByPhone(phone);
    }

}
