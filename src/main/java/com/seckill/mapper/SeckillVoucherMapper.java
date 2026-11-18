package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    @Update("UPDATE t_seckill_voucher SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
    int deductStock(@Param("id") Long id);

    @Update("UPDATE t_seckill_voucher SET stock = #{stock} WHERE id = #{id}")
    int resetStock(@Param("id") Long id, @Param("stock") int stock);
}
