package com.zad.minimarket.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderExecutionProjection {
    Long getOrderId();
    String getAccountId();
    String getSymbol();
    String getSide();
    BigDecimal getQuantity();
    String getStatus();
    LocalDateTime getCreatedAt();

    Long getExecutionId();
    BigDecimal getPrice();
    LocalDateTime getExecutedAt();
}
