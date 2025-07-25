package com.zad.minimarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class MiniMarketOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMarketOrderServiceApplication.class, args);
    }
}

