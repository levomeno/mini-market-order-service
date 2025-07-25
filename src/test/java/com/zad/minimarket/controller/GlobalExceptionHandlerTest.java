package com.zad.minimarket.controller;

import com.zad.minimarket.dto.CreateOrderRequest;
import com.zad.minimarket.entity.OrderSide;
import com.zad.minimarket.exception.OrderNotFoundException;
import com.zad.minimarket.exception.PriceFeedException;
import com.zad.minimarket.exception.RateLimitExceededException;
import com.zad.minimarket.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_Return404_When_OrderNotFoundExceptionThrown() throws Exception {
        Mockito.when(orderService.getOrderById(anyLong()))
            .thenThrow(new OrderNotFoundException("Order with ID 1 not found"));

        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Order Not Found"))
            .andExpect(jsonPath("$.message").value("Order with ID 1 not found"));
    }

    @Test
    void should_Return429_When_RateLimitExceededExceptionThrown() throws Exception {
        Mockito.when(orderService.getAllOrders(any()))
            .thenThrow(new RateLimitExceededException("Too many requests"));

        mockMvc.perform(get("/orders"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.error").value("Rate Limit Exceeded"))
            .andExpect(jsonPath("$.message").value("Too many requests"));
    }

    @Test
    void should_Return422_When_PriceFeedExceptionThrown() throws Exception {
        Mockito.when(orderService.createOrder(any()))
            .thenThrow(new PriceFeedException("Invalid symbol"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setSymbol("XYZ");
        request.setQuantity(BigDecimal.TEN);
        request.setSide(OrderSide.BUY);
        request.setAccountId("test-acc");

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.error").value("Price Feed Error"))
            .andExpect(jsonPath("$.message").value("Invalid symbol"));
    }

    @Test
    void should_Return400_When_ValidationFails() throws Exception {
        // Missing required symbol field
        String invalidJson = """
            {
              "symbol": "",
              "quantity": 5,
              "side": "BUY",
              "accountId": "acc123"
            }
        """;

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Validation Failed"))
            .andExpect(jsonPath("$.fieldErrors.symbol").exists());
    }

    @Test
    void should_Return400_When_IllegalArgumentExceptionThrown() throws Exception {
        Mockito.when(orderService.getOrdersByAccount(any(), any()))
            .thenThrow(new IllegalArgumentException("Invalid account ID"));

        mockMvc.perform(get("/orders?accountId=acc123"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Invalid Argument"))
            .andExpect(jsonPath("$.message").value("Invalid account ID"));
    }

    @Test
    void should_Return500_When_GenericExceptionThrown() throws Exception {
        Mockito.when(orderService.getAllOrders(any()))
            .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(get("/orders"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
