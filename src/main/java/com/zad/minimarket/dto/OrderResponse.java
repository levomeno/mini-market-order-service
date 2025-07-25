package com.zad.minimarket.dto;

import com.zad.minimarket.entity.OrderSide;
import com.zad.minimarket.entity.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class OrderResponse implements Serializable {

    private Long id;
    private String accountId;
    private String symbol;
    private OrderSide side;
    private BigDecimal quantity;
    private OrderStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private ExecutionResponse execution;
}

