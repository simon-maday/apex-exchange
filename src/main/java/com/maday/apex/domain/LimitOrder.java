package com.maday.apex.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LimitOrder(
        UUID id,
        String userId,
        String symbol,
        Side side,
        BigDecimal price,
        BigDecimal quantity,
        Instant createdAt
) {
    public LimitOrder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(side);
        Objects.requireNonNull(price);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(createdAt);

        if (price.signum() <= 0 || quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price and quantity must be positive");
        }
    }
}
