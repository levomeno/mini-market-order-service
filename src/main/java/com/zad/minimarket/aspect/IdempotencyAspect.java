package com.zad.minimarket.aspect;

import com.zad.minimarket.annotation.Idempotent;
import com.zad.minimarket.dto.OrderResponse;
import com.zad.minimarket.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;

    public IdempotencyAspect(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String idempotencyKey = request.getHeader(idempotent.keyHeader());

        if (idempotencyKey != null && !idempotencyKey.isEmpty() && idempotencyService.isKeyProcessed(idempotencyKey)) {
            OrderResponse cachedResponse = idempotencyService.getProcessedOrderResponse(idempotencyKey);
            return ResponseEntity.ok(cachedResponse);
        }

        Object result = joinPoint.proceed();

        if (idempotencyKey != null && !idempotencyKey.isEmpty() && result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() instanceof OrderResponse) {
                idempotencyService.saveOrderResponse(idempotencyKey, (OrderResponse) responseEntity.getBody());
            }
        }

        return result;
    }
}
