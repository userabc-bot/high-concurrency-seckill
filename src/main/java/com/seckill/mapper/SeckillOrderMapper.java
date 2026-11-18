package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    @Select("SELECT COUNT(*) FROM t_seckill_order WHERE voucher_id = #{voucherId}")
    long countByVoucherId(@Param("voucherId") Long voucherId);

    @Select("SELECT COUNT(DISTINCT user_id) FROM t_seckill_order WHERE voucher_id = #{voucherId}")
    long countDistinctUsers(@Param("voucherId") Long voucherId);

    @Delete("DELETE FROM t_seckill_order WHERE voucher_id = #{voucherId}")
    int deleteByVoucherId(@Param("voucherId") Long voucherId);
}
