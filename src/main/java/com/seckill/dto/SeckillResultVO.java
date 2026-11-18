package com.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeckillResultVO {

    public static final int QUEUING = 0;
    public static final int SUCCESS = 1;
    public static final int FAILED = 2;
    public static final int SOLD_OUT = 3;
    public static final int NOT_JOINED = 4;

    private int status;
    private String statusDesc;
    private String orderNo;

    public static SeckillResultVO of(int status, String statusDesc, String orderNo) {
        return new SeckillResultVO(status, statusDesc, orderNo);
    }
}
