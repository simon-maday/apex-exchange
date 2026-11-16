package com.maday.apex.engine;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedTrade(
        UUID buyOrderId,
        UUID sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity) {
}
