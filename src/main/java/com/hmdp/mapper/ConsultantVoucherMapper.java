package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConsultantVoucherMapper extends BaseMapper<Voucher> {

    @Select("select * from tb_voucher where shop_id=#{shopId}")
    List<Voucher> findVoucherByShopId(Long shopId);

    @Select("<script>" +
            "SELECT * FROM tb_voucher " +
            "WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "   #{id}" +
            "</foreach>" +
            "</script>")
    List<Voucher> findVoucherByIds(@Param("ids") List<Long> ids);
}
