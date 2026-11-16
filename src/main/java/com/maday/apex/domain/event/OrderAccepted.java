package com.maday.apex.domain.event;

import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderAccepted(
        long sequence,
        UUID orderId,
        String userId,
        String symbol,
        Side side,
        BigDecimal price,
        BigDecimal quantity,
        Instant occurredAt
) implements ExchangeEvent {
    public OrderAccepted {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Invalid sequence");
        }

        Objects.requireNonNull(orderId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(side);
        Objects.requireNonNull(price);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(occurredAt);

        if (price.signum() <= 0 || quantity.signum() <= 0)
            throw new IllegalArgumentException(
                    "Trade price and quantity must be positive"
            );
    }
}