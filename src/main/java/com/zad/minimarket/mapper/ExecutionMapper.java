package com.zad.minimarket.mapper;

import com.zad.minimarket.dto.ExecutionResponse;
import com.zad.minimarket.entity.Execution;
import org.mapstruct.*;

import java.math.BigDecimal;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ExecutionMapper {

    @Mapping(target = "executedAt", expression = "java(java.time.LocalDateTime.now())")
    Execution toEntity(Long orderId, BigDecimal price);

    /**
     * Convert Execution entity to ExecutionResponse DTO
     */
    ExecutionResponse toExecutionResponse(Execution execution);

}

