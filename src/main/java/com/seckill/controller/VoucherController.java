package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillVoucher;
import com.seckill.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final SeckillVoucherMapper voucherMapper;

    @GetMapping("/list")
    public Result<List<SeckillVoucher>> list() {
        return Result.ok(voucherMapper.selectList(null));
    }
}
