package com.maday.apex.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderRested(
        long sequence,
        UUID orderId,
        BigDecimal remainingQuantity,
        Instant occurredAt
) implements ExchangeEvent {
    public OrderRested {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Invalid sequence");
        }

        Objects.requireNonNull(orderId);
        Objects.requireNonNull(remainingQuantity);
        Objects.requireNonNull(occurredAt);

        if(remainingQuantity.signum() <= 0) throw new IllegalArgumentException("Remaining quantity must be positive");
    }
}
