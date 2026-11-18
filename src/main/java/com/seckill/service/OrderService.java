package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.seckill.common.SoldOutException;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SeckillVoucherMapper voucherMapper;
    private final SeckillOrderMapper orderMapper;

    @Transactional(rollbackFor = Exception.class)
    public String createOrder(SeckillMessage message) {
        int rows = voucherMapper.deductStock(message.getVoucherId());
        if (rows == 0) {
            throw new SoldOutException("数据库库存不足");
        }
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(IdWorker.getIdStr());
        order.setVoucherId(message.getVoucherId());
        order.setUserId(message.getUserId());
        order.setAmount(message.getAmount());
        order.setStatus(1);
        orderMapper.insert(order);
        return order.getOrderNo();
    }

    public SeckillOrder findOrder(Long voucherId, Long userId) {
        return orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getVoucherId, voucherId)
                .eq(SeckillOrder::getUserId, userId));
    }
}
