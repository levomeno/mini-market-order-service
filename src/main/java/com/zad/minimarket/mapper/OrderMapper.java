package com.zad.minimarket.mapper;

import com.zad.minimarket.dto.CreateOrderRequest;
import com.zad.minimarket.dto.ExecutionResponse;
import com.zad.minimarket.dto.OrderExecutionProjection;
import com.zad.minimarket.dto.OrderResponse;
import com.zad.minimarket.entity.Order;
import com.zad.minimarket.entity.OrderSide;
import com.zad.minimarket.entity.OrderStatus;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    uses = {ExecutionMapper.class},
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OrderMapper {

    @Mapping(source = "orderId", target = "id")
    @Mapping(source = "side", target = "side", qualifiedByName = "stringToOrderSide")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToOrderStatus")
    @Mapping(source = "projection", target = "execution", qualifiedByName = "projectionToExecution")
    OrderResponse projectionToOrderResponse(OrderExecutionProjection projection);

    // Custom mapping methods
    @Named("stringToOrderSide")
    default OrderSide stringToOrderSide(String side) {
        return side != null ? OrderSide.valueOf(side) : null;
    }

    @Named("stringToOrderStatus")
    default OrderStatus stringToOrderStatus(String status) {
        return status != null ? OrderStatus.valueOf(status) : null;
    }

    @Named("projectionToExecution")
    default ExecutionResponse projectionToExecution(OrderExecutionProjection projection) {
        if (projection.getExecutionId() == null) {
            return null;
        }

        ExecutionResponse execution = new ExecutionResponse();
        execution.setId(projection.getExecutionId());
        execution.setOrderId(projection.getOrderId());
        execution.setPrice(projection.getPrice());
        execution.setExecutedAt(projection.getExecutedAt());

        return execution;
    }

    /**
     * Convert Order entity to OrderResponse DTO
     */
    @Mapping(target = "execution", source = "execution")
    @Mapping(target = "id", source = "order.id")
    OrderResponse toOrderResponse(Order order, ExecutionResponse execution);

    @Mapping(target = "id", source = "order.id")
    OrderResponse toOrderResponse(Order order);

    /**
     * Convert CreateOrderRequest DTO to Order entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    Order toOrder(CreateOrderRequest request);
}

