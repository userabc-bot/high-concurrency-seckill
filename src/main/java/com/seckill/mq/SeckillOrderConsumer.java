package com.seckill.mq;

import com.seckill.common.RedisKeys;
import com.seckill.common.SoldOutException;
import com.seckill.config.RabbitConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private static final long RESULT_TTL_HOURS = 24L;

    private final OrderService orderService;
    private final SeckillService seckillService;
    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitConfig.ORDER_QUEUE)
    public void handle(SeckillMessage message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        String resultKey = RedisKeys.result(voucherId, userId);
        try {
            String orderNo = orderService.createOrder(message);
            markSuccess(resultKey, orderNo);
            channel.basicAck(deliveryTag, false);
            log.info("秒杀订单创建成功 voucherId={}, userId={}, orderNo={}", voucherId, userId, orderNo);
        } catch (SoldOutException e) {
            seckillService.restoreStock(voucherId, userId);
            markFailed(resultKey, "库存不足，下单失败");
            channel.basicAck(deliveryTag, false);
        } catch (DuplicateKeyException e) {
            SeckillOrder existing = orderService.findOrder(voucherId, userId);
            if (existing != null) {
                markSuccess(resultKey, existing.getOrderNo());
            } else {
                markFailed(resultKey, "重复抢购");
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("秒杀订单处理失败 voucherId={}, userId={}", voucherId, userId, e);
            seckillService.restoreStock(voucherId, userId);
            markFailed(resultKey, "系统繁忙，下单失败");
            channel.basicAck(deliveryTag, false);
        }
    }

    private void markSuccess(String resultKey, String orderNo) {
        try {
            redisTemplate.opsForValue().set(resultKey, "SUCCESS:" + orderNo, RESULT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("写入订单结果缓存失败 key={}", resultKey, e);
        }
    }

    private void markFailed(String resultKey, String reason) {
        try {
            redisTemplate.opsForValue().set(resultKey, "FAILED:" + reason, RESULT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("写入订单结果缓存失败 key={}", resultKey, e);
        }
    }
}
