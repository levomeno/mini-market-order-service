package com.zad.minimarket.repository;

import com.zad.minimarket.dto.OrderExecutionProjection;
import com.zad.minimarket.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(value = """
        SELECT 
            o.id as orderId,
            o.account_id as accountId,
            o.symbol as symbol,
            o.side as side,
            o.quantity as quantity,
            o.status as status,
            o.created_at as createdAt,
            e.id as executionId,
            e.price as price,
            e.executed_at as executedAt
        FROM orders o 
        LEFT JOIN executions e ON o.id = e.order_id 
        WHERE o.id = :orderId
        """, nativeQuery = true)
    Optional<OrderExecutionProjection> findOrderWithExecutionNative(@Param("orderId") Long orderId);

    @Query(value = """
        SELECT 
            o.id as orderId,
            o.account_id as accountId,
            o.symbol as symbol,
            o.side as side,
            o.quantity as quantity,
            o.status as status,
            o.created_at as createdAt,
            e.id as executionId,
            e.price as price,
            e.executed_at as executedAt
        FROM orders o 
        LEFT JOIN executions e ON o.id = e.order_id 
        WHERE o.account_id = :accountId
        """, nativeQuery = true)
    Page<OrderExecutionProjection> findOrderWithExecutionByAccountId(
        @Param("accountId") String accountId, Pageable pageable);

    @Query(value = """
    SELECT 
        o.id as orderId,
        o.account_id as accountId,
        o.symbol as symbol,
        o.side as side,
        o.quantity as quantity,
        o.status as status,
        o.created_at as createdAt,
        e.id as executionId,
        e.price as price,
        e.executed_at as executedAt
    FROM orders o 
    LEFT JOIN executions e ON o.id = e.order_id 
    """, nativeQuery = true)
    Page<OrderExecutionProjection> findOrdersWithExecution(Pageable pageable);


    /**
     * Find all orders for a specific account ID, ordered by creation date descending
     */
    List<Order> findByAccountIdOrderByCreatedAtDesc(String accountId);

    /**
     * Find all orders for a specific account ID with pagination
     */
    Page<Order> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);


    /**
     * Find orders by account ID and symbol
     */
    List<Order> findByAccountIdAndSymbolOrderByCreatedAtDesc(String accountId, String symbol);

    /**
     * Count orders by account ID
     */
    long countByAccountId(String accountId);

    /**
     * Check if order exists by ID and account ID
     */
    boolean existsByIdAndAccountId(Long id, String accountId);
}

