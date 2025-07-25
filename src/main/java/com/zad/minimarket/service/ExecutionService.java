package com.zad.minimarket.service;

import com.zad.minimarket.dto.ExecutionResponse;
import com.zad.minimarket.entity.Execution;
import com.zad.minimarket.mapper.ExecutionMapper;
import com.zad.minimarket.repository.ExecutionRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;

    private final ExecutionMapper executionMapper;

    public ExecutionResponse saveExecution(Long orderId, BigDecimal price) {
        Execution execution = executionMapper.toEntity(orderId, price);
        return executionMapper.toExecutionResponse(executionRepository.save(execution));
    }
}
