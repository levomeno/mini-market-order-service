package com.zad.minimarket.service;

import com.zad.minimarket.dto.*;
import com.zad.minimarket.entity.Order;
import com.zad.minimarket.entity.OrderStatus;
import com.zad.minimarket.exception.OrderNotFoundException;
import com.zad.minimarket.exception.PriceFeedException;
import com.zad.minimarket.mapper.OrderMapper;
import com.zad.minimarket.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

@Slf4j
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ExecutionService executionService;
    private final PriceFeedService priceFeedService;
    private final RateLimitingService rateLimitingService;
    private final OrderMapper orderMapper;
    private final Counter orderCounter;

    public OrderService(OrderRepository orderRepository,
                        ExecutionService executionService,
                        PriceFeedService priceFeedService,
                        RateLimitingService rateLimitingService,
                        OrderMapper orderMapper,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.executionService = executionService;
        this.priceFeedService = priceFeedService;
        this.rateLimitingService = rateLimitingService;
        this.orderMapper = orderMapper;
        this.orderCounter = Counter.builder("orders.created")
            .description("Total number of orders created")
            .register(meterRegistry);
    }

    /**
     * Create and execute a new order
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for account: {}, symbol: {}, side: {}, quantity: {}",
            request.getAccountId(), request.getSymbol(), request.getSide(), request.getQuantity());

        // Check rate limit
        rateLimitingService.checkRateLimit(request.getAccountId());

        // Create order entity
        Order order = orderMapper.toOrder(request);

        // Save order first to get an ID
        order = orderRepository.save(order);
        log.debug("Order saved with ID: {}", order.getId());

        try {
            // Get current price from price feed
            PriceResponse priceResponse = priceFeedService.getCurrentPrice(request.getSymbol());

            // Create and save execution
            ExecutionResponse executionResponse = executionService.saveExecution(order.getId(),
                priceResponse.getPrice().setScale(6, RoundingMode.HALF_UP));

            // Update order status to EXECUTED
            order.setStatus(OrderStatus.EXECUTED);
            order = orderRepository.save(order);

            log.info("Order {} executed successfully at price: {}",
                order.getId(), priceResponse.getPrice());

            // Increment order counter metric
            orderCounter.increment();

            return orderMapper.toOrderResponse(order, executionResponse);

        } catch (PriceFeedException e) {
            log.error("Failed to get price for symbol {}: {}", request.getSymbol(), e.getMessage());

            // Update order status to failed
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);

            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating order: {}", e.getMessage(), e);

            // Update order status to failed
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);

            throw new RuntimeException("Failed to create order", e);
        }
    }


    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        log.debug("Getting order by ID: {}", id);

        return orderRepository.findOrderWithExecutionNative(id)
            .map(orderMapper::projectionToOrderResponse)
            .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
    }

    /**
     * Get orders for account
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByAccount(String accountId, Pageable pageable) {
        log.debug("Getting paginated orders for account: {}, pageable: {}", accountId, pageable);

        Page<OrderExecutionProjection> projections = orderRepository.findOrderWithExecutionByAccountId(accountId, pageable);
        return projections.map(orderMapper::projectionToOrderResponse);
    }

    /**
     * Get all orders
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        log.debug("Getting all orders with pageable: {}", pageable);

        Page<OrderExecutionProjection> projections = orderRepository.findOrdersWithExecution(pageable);
        return projections.map(orderMapper::projectionToOrderResponse);
    }

    /**
     * Get order count for account
     */
    @Transactional(readOnly = true)
    public long getOrderCountByAccount(String accountId) {
        return orderRepository.countByAccountId(accountId);
    }
}
