package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.SeckillResultVO;
import com.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/{voucherId}")
    public Result<?> seckill(@RequestHeader("X-User-Id") Long userId,
                             @PathVariable Long voucherId) {
        if (userId <= 0) {
            return Result.fail(400, "用户身份非法");
        }
        return seckillService.seckill(userId, voucherId);
    }

    @GetMapping("/result/{voucherId}")
    public Result<SeckillResultVO> result(@RequestHeader("X-User-Id") Long userId,
                                          @PathVariable Long voucherId) {
        if (userId <= 0) {
            return Result.fail(400, "用户身份非法");
        }
        return Result.ok(seckillService.getResult(userId, voucherId));
    }
}
