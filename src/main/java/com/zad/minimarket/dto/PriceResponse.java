package com.zad.minimarket.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceResponse implements Serializable {

    private String symbol;

    private BigDecimal price;
}

