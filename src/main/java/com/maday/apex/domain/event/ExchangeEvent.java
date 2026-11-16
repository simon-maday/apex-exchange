package com.maday.apex.domain.event;

import java.time.Instant;

public sealed interface ExchangeEvent permits OrderAccepted, OrderRested, TradeExecuted, OrderCancelled {
    long sequence();

    Instant occurredAt();
}
