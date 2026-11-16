package com.maday.apex;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.EventBatch;
import com.maday.apex.domain.event.OrderAccepted;
import com.maday.apex.domain.event.OrderRested;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TestProvider {
    public static EventBatch createBatchStartingAt(long sequence) {
        var orderId = UUID.randomUUID();

        return new EventBatch(
                UUID.randomUUID(),
                List.of(
                        new OrderAccepted(
                                sequence,
                                orderId,
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        ),
                        new OrderRested(
                                sequence + 1,
                                orderId,
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        )
                )
        );
    }

    public static LimitOrder order(
            String id,
            Side side,
            String price,
            String quantity
    ) {
        return new LimitOrder(
                UUID.fromString(id),
                "user-" + id,
                "BTC-USDT",
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                Instant.now()
        );
    }

    public static LimitOrder order(
            String id,
            String userId,
            Side side,
            String price,
            String quantity
    ) {
        return new LimitOrder(
                UUID.fromString(id),
                userId,
                "BTC-USDT",
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                Instant.now()
        );
    }

    public static LimitOrder order(
            UUID id,
            String userId,
            Side side,
            String price,
            String quantity
    ) {
        return new LimitOrder(
                id,
                userId,
                "BTC-USDT",
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                Instant.now()
        );
    }
}
