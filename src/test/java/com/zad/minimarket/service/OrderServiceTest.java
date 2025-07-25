package com.zad.minimarket.service;

import com.zad.minimarket.dto.*;
import com.zad.minimarket.entity.Order;
import com.zad.minimarket.entity.OrderSide;
import com.zad.minimarket.entity.OrderStatus;
import com.zad.minimarket.exception.OrderNotFoundException;
import com.zad.minimarket.exception.PriceFeedException;
import com.zad.minimarket.mapper.OrderMapper;
import com.zad.minimarket.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExecutionService executionService;

    @Mock
    private PriceFeedService priceFeedService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter orderCounter;

    private OrderService orderService;

    private CreateOrderRequest createOrderRequest;
    private Order order;
    private Order savedOrder;
    private OrderResponse orderResponse;
    private PriceResponse priceResponse;
    private ExecutionResponse executionResponse;

    @BeforeEach
    void setUp() {
        // Create a spy of OrderService to avoid Counter.builder() issues
        orderService = spy(new OrderService(
            orderRepository,
            executionService,
            priceFeedService,
            rateLimitingService,
            orderMapper,
            meterRegistry
        ));

        // Use reflection to set the orderCounter field
        try {
            Field counterField = OrderService.class.getDeclaredField("orderCounter");
            counterField.setAccessible(true);
            counterField.set(orderService, orderCounter);
        } catch (Exception e) {
            // If reflection fails, the test will still work, just without counter verification
        }

        // Setup request
        createOrderRequest = new CreateOrderRequest();
        createOrderRequest.setAccountId("acc-123");
        createOrderRequest.setSymbol("AAPL");
        createOrderRequest.setSide(OrderSide.BUY);
        createOrderRequest.setQuantity(BigDecimal.valueOf(10));

        // Setup initial order (before save)
        order = new Order();
        order.setAccountId("acc-123");
        order.setSymbol("AAPL");
        order.setSide(OrderSide.BUY);
        order.setQuantity(BigDecimal.valueOf(10));
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());

        // Setup saved order (after save - with ID)
        savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setAccountId("acc-123");
        savedOrder.setSymbol("AAPL");
        savedOrder.setSide(OrderSide.BUY);
        savedOrder.setQuantity(BigDecimal.valueOf(10));
        savedOrder.setStatus(OrderStatus.EXECUTED);
        savedOrder.setCreatedAt(LocalDateTime.now());

        // Setup price response
        priceResponse = new PriceResponse();
        priceResponse.setPrice(BigDecimal.valueOf(210.55));
        priceResponse.setSymbol("AAPL");

        // Setup execution response
        executionResponse = new ExecutionResponse();
        executionResponse.setId(1L);
        executionResponse.setOrderId(1L);
        executionResponse.setPrice(BigDecimal.valueOf(210.55));
        executionResponse.setExecutedAt(LocalDateTime.now());

        // Setup order response
        orderResponse = new OrderResponse();
        orderResponse.setId(1L);
        orderResponse.setAccountId("acc-123");
        orderResponse.setSymbol("AAPL");
        orderResponse.setSide(OrderSide.BUY);
        orderResponse.setQuantity(BigDecimal.valueOf(10));
        orderResponse.setStatus(OrderStatus.EXECUTED);
        orderResponse.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void should_CreateOrder() {
        // Given
        String symbol = "AAPL";
        String accountId = "acc-123";
        when(orderMapper.toOrder(createOrderRequest)).thenReturn(order);
        when(priceFeedService.getCurrentPrice(symbol)).thenReturn(priceResponse);

        // First save returns order with ID, second save returns executed order
        when(orderRepository.save(any(Order.class)))
            .thenReturn(savedOrder)
            .thenReturn(savedOrder);

        when(executionService.saveExecution(1L, BigDecimal.valueOf(210.55)))
            .thenReturn(executionResponse);

        when(orderMapper.toOrderResponse(savedOrder, executionResponse))
            .thenReturn(orderResponse);

        // When
        OrderResponse result = orderService.createOrder(createOrderRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(result.getSymbol()).isEqualTo(symbol);
        assertThat(result.getAccountId()).isEqualTo(accountId);

        // Verify all interactions
        verify(rateLimitingService).checkRateLimit(accountId);
        verify(orderMapper).toOrder(createOrderRequest);
        verify(priceFeedService).getCurrentPrice(symbol);
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(executionService).saveExecution(1L, BigDecimal.valueOf(210.55));
        verify(orderMapper).toOrderResponse(savedOrder, executionResponse);
        verify(orderCounter).increment();
    }

    @Test
    void should_ThrowRuntimeException_When_RateLimitExceeded() {
        // Given
        doThrow(new RuntimeException("Rate limit exceeded"))
            .when(rateLimitingService).checkRateLimit(anyString());

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(createOrderRequest))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Rate limit exceeded");

        verify(rateLimitingService).checkRateLimit("acc-123");
        verify(orderMapper, never()).toOrder(any());
        verify(priceFeedService, never()).getCurrentPrice(anyString());
        verify(orderRepository, never()).save(any());
        verify(executionService, never()).saveExecution(anyLong(), any());
        verify(orderCounter, never()).increment();
    }

    @Test
    void should_ThrowPriceFeedException_When_PriceFeedUnavailable() {
        // Given
        when(orderMapper.toOrder(createOrderRequest)).thenReturn(order);
        when(priceFeedService.getCurrentPrice("AAPL"))
            .thenThrow(new PriceFeedException("Price feed unavailable"));

        // Mock the first save to return savedOrder (with ID), second save for status update
        when(orderRepository.save(any(Order.class)))
            .thenReturn(savedOrder)
            .thenReturn(savedOrder);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(createOrderRequest))
            .isInstanceOf(PriceFeedException.class)
            .hasMessage("Price feed unavailable");

        verify(rateLimitingService).checkRateLimit("acc-123");
        verify(orderMapper).toOrder(createOrderRequest);
        verify(priceFeedService).getCurrentPrice("AAPL");
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(executionService, never()).saveExecution(anyLong(), any());
        verify(orderCounter, never()).increment();
    }

    @Test
    void should_ThrowRuntimeException_When_ExecutionServiceFails() {
        // Given
        String symbol = "AAPL";
        when(orderMapper.toOrder(createOrderRequest)).thenReturn(order);
        when(priceFeedService.getCurrentPrice(symbol)).thenReturn(priceResponse);
        when(orderRepository.save(any(Order.class)))
            .thenReturn(savedOrder); // First save succeeds
        when(executionService.saveExecution(1L, BigDecimal.valueOf(210.55)))
            .thenThrow(new RuntimeException("Execution failed"));

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(createOrderRequest))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to create order");

        verify(rateLimitingService).checkRateLimit("acc-123");
        verify(orderMapper).toOrder(createOrderRequest);
        verify(priceFeedService).getCurrentPrice(symbol);
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(executionService).saveExecution(1L, BigDecimal.valueOf(210.55));
        verify(orderCounter, never()).increment();
    }

    @Test
    void should_ReturnOrderResponse_When_OrderFoundById() {
        // Given
        OrderExecutionProjection projection = mock(OrderExecutionProjection.class);
        when(orderRepository.findOrderWithExecutionNative(1L))
            .thenReturn(Optional.of(projection));
        when(orderMapper.projectionToOrderResponse(projection))
            .thenReturn(orderResponse);

        // When
        OrderResponse result = orderService.getOrderById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSymbol()).isEqualTo("AAPL");

        verify(orderRepository).findOrderWithExecutionNative(1L);
        verify(orderMapper).projectionToOrderResponse(projection);
    }

    @Test
    void should_ThrowOrderNotFoundException_When_OrderNotFound() {
        // Given
        when(orderRepository.findOrderWithExecutionNative(1L))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> orderService.getOrderById(1L))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessage("Order not found with ID: 1");

        verify(orderRepository).findOrderWithExecutionNative(1L);
        verify(orderMapper, never()).projectionToOrderResponse(any());
    }

    @Test
    void should_ReturnPaginatedOrderResponses_When_ValidAccountId() {

        String accountId = "acc-123";
        Pageable pageable = PageRequest.of(0, 2);

        OrderExecutionProjection projection1 = Mockito.mock(OrderExecutionProjection.class);
        OrderExecutionProjection projection2 = Mockito.mock(OrderExecutionProjection.class);

        Page<OrderExecutionProjection> projectionPage = new PageImpl<>(
            List.of(projection1, projection2),
            pageable,
            2
        );

        OrderResponse response1 = new OrderResponse();
        OrderResponse response2 = new OrderResponse();

        Mockito.when(orderRepository.findOrderWithExecutionByAccountId(accountId, pageable))
            .thenReturn(projectionPage);
        Mockito.when(orderMapper.projectionToOrderResponse(projection1)).thenReturn(response1);
        Mockito.when(orderMapper.projectionToOrderResponse(projection2)).thenReturn(response2);

        // Act
        Page<OrderResponse> result = orderService.getOrdersByAccount(accountId, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).containsExactly(response1, response2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        Mockito.verify(orderRepository).findOrderWithExecutionByAccountId(accountId, pageable);
        Mockito.verify(orderMapper).projectionToOrderResponse(projection1);
        Mockito.verify(orderMapper).projectionToOrderResponse(projection2);
    }

    @Test
    void should_ReturnPaginatedOrderResponses_When_GettingAllOrders() {
        Pageable pageable = PageRequest.of(0, 2);

        OrderExecutionProjection projection1 = Mockito.mock(OrderExecutionProjection.class);
        OrderExecutionProjection projection2 = Mockito.mock(OrderExecutionProjection.class);

        Page<OrderExecutionProjection> projectionPage = new PageImpl<>(
            List.of(projection1, projection2),
            pageable,
            2
        );

        OrderResponse response1 = new OrderResponse();
        OrderResponse response2 = new OrderResponse();

        Mockito.when(orderRepository.findOrdersWithExecution(pageable))
            .thenReturn(projectionPage);
        Mockito.when(orderMapper.projectionToOrderResponse(projection1)).thenReturn(response1);
        Mockito.when(orderMapper.projectionToOrderResponse(projection2)).thenReturn(response2);

        // Act
        Page<OrderResponse> result = orderService.getAllOrders(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).containsExactly(response1, response2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        Mockito.verify(orderRepository).findOrdersWithExecution(pageable);
        Mockito.verify(orderMapper).projectionToOrderResponse(projection1);
        Mockito.verify(orderMapper).projectionToOrderResponse(projection2);
    }

    @Test
    void should_ReturnOrderCount_When_AccountIdProvided() {
        // Given
        String accountId = "acc-123";
        when(orderRepository.countByAccountId(accountId)).thenReturn(5L);

        // When
        long result = orderService.getOrderCountByAccount(accountId);

        // Then
        assertThat(result).isEqualTo(5L);
        verify(orderRepository).countByAccountId(accountId);
    }
}