package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConsultantVoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Select("select voucher_id from tb_voucher_order where user_id = #{userId}")
    List<Long> findByUserId(Long userId);

    @Select("select voucher_id from tb_voucher_order where phone = #{phone}")
    List<Long> findByPhone(String phone);
}
