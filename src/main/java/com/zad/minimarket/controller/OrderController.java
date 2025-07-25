package com.zad.minimarket.controller;

import com.zad.minimarket.annotation.Idempotent;
import com.zad.minimarket.dto.CreateOrderRequest;
import com.zad.minimarket.dto.OrderResponse;
import com.zad.minimarket.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order management API")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @PostMapping
    @Idempotent
    @Operation(
        summary = "Create a new order",
        description = "Creates a new market order (BUY or SELL), fetches current price, and executes the order"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Order created and executed successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data"
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error or price feed unavailable"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded"
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Service unavailable"
        )
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        
        logger.info("Received create order request: {}", request);
        
        try {
            OrderResponse response = orderService.createOrder(request);
            logger.info("Order created successfully with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage());
            throw e;
        }
    }
    
    @GetMapping("/{id}")
    @Operation(
        summary = "Get order by ID",
        description = "Retrieves a specific order by its ID including execution details"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Order found",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Order not found"
        )
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID", required = true)
            @PathVariable Long id) {
        
        logger.debug("Getting order by ID: {}", id);
        
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(
        summary = "Get orders",
        description = "Retrieves orders for a specific account or all orders (if no accountId provided)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Orders retrieved successfully",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters"
        )
    })
    public ResponseEntity<Page<OrderResponse>> getOrders(
        @Parameter(description = "Account ID to filter orders")
        @RequestParam(required = false) String accountId,

        @ParameterObject Pageable pageable) {

        logger.debug("Getting orders for accountId: {}, pageable: {}", accountId, pageable);

        Page<OrderResponse> orderPage;

        if (accountId != null && !accountId.trim().isEmpty()) {
            orderPage = orderService.getOrdersByAccount(accountId, pageable);
        } else {
            orderPage = orderService.getAllOrders(pageable);
        }

        return ResponseEntity.ok(orderPage);
    }

}

