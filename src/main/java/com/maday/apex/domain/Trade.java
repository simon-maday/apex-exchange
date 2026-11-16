package com.maday.apex.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Immutable record of an executed trade
public record Trade(
        UUID id,
        UUID buyOrderId,
        UUID sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        Instant occurredAt
) {

    public Trade {

        Objects.requireNonNull(id);
        Objects.requireNonNull(buyOrderId);
        Objects.requireNonNull(sellOrderId);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(price);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(occurredAt);

        if (price.signum() <= 0 || quantity.signum() <= 0)
            throw new IllegalArgumentException(
                    "Trade price and quantity must be positive"
            );

    }
}