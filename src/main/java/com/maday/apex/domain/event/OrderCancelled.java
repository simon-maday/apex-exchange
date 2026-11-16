package com.maday.apex.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderCancelled(
        long sequence,
        UUID orderId,
        BigDecimal cancelledQuantity,
        Instant occurredAt
) implements ExchangeEvent {
    public OrderCancelled {

        if (sequence <= 0) {
            throw new IllegalArgumentException("Invalid sequence");
        }

        Objects.requireNonNull(orderId);
        Objects.requireNonNull(cancelledQuantity);
        Objects.requireNonNull(occurredAt);

        if (cancelledQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Cancelled quantity must be positive");
        }
    }
}
