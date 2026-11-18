package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.SeckillAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SeckillAdminService adminService;

    @PostMapping("/reset/{voucherId}")
    public Result<Map<String, Object>> reset(@PathVariable Long voucherId,
                                             @RequestParam(defaultValue = "100") int stock) {
        if (stock < 0) {
            return Result.fail(400, "库存不能为负数");
        }
        adminService.reset(voucherId, stock);
        return Result.ok(adminService.stats(voucherId));
    }

    @GetMapping("/stats/{voucherId}")
    public Result<Map<String, Object>> stats(@PathVariable Long voucherId) {
        return Result.ok(adminService.stats(voucherId));
    }
}
