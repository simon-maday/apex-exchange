package com.maday.apex.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TradeExecuted(
        long sequence,
        UUID tradeId,
        UUID buyOrderId,
        UUID sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        Instant occurredAt
) implements ExchangeEvent {
    public TradeExecuted {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Invalid sequence");
        }

        Objects.requireNonNull(tradeId);
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