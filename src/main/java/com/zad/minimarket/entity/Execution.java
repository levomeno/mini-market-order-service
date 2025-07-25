package com.zad.minimarket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "executions")
@Getter
@Setter
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @NotNull
    private Long orderId;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal price;

    @NotNull
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;
}

