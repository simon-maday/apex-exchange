package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class EventBatchFactory {

    EventBatch create(
            LimitOrder incoming,
            List<PlannedTrade> plannedTrades,
            long firstSequence,
            Instant occurredAt
    ) {
        Objects.requireNonNull(incoming);
        Objects.requireNonNull(plannedTrades);
        Objects.requireNonNull(occurredAt);

        validatePlan(incoming, plannedTrades);

        var events = new ArrayList<ExchangeEvent>();

        var sequence = firstSequence;

        events.add(new OrderAccepted(
                sequence++,
                incoming.id(),
                incoming.userId(),
                incoming.symbol(),
                incoming.side(),
                incoming.price(),
                incoming.quantity(),
                occurredAt
        ));

        var incomingRemaining = incoming.quantity();

        for (var planned : plannedTrades) {

            if (planned.quantity().signum() <= 0 || planned.quantity().compareTo(incomingRemaining) > 0)
                throw new IllegalStateException("Invalid matching plan quantity");

            events.add(new TradeExecuted(
                    sequence++,
                    UUID.randomUUID(),
                    planned.buyOrderId(),
                    planned.sellOrderId(),
                    planned.symbol(),
                    planned.price(),
                    planned.quantity(),
                    occurredAt
            ));

            incomingRemaining = incomingRemaining.subtract(planned.quantity());

        }

        // Only the unfilled remainder enters the resting book.
        if (incomingRemaining.signum() > 0) {
            events.add(new OrderRested(
                    sequence++,
                    incoming.id(),
                    incomingRemaining,
                    occurredAt
            ));
        }

        return new EventBatch(UUID.randomUUID(), events);

    }

    private void validatePlan(LimitOrder incoming, List<PlannedTrade> plannedTrades) {

        var totalExecuted = BigDecimal.ZERO;

        for (var trade : plannedTrades) {
            Objects.requireNonNull(trade, "Planned trade cannot be null");

            if (!trade.buyOrderId().equals(incoming.id()) && !trade.sellOrderId().equals(incoming.id()))
                throw new IllegalArgumentException("Trade must reference the incoming order");

            if (trade.buyOrderId().equals(trade.sellOrderId()))
                throw new IllegalArgumentException("Buy and sell order IDs must be different");

            if (!incoming.symbol().equals(trade.symbol()))
                throw new IllegalArgumentException("Trade symbol does not match incoming order");

            if (trade.quantity() == null || trade.quantity().signum() <= 0)
                throw new IllegalArgumentException("Trade quantity must be positive");

            if (trade.price() == null || trade.price().signum() <= 0)
                throw new IllegalArgumentException("Trade price must be positive");

            int comparePrice = trade.price().compareTo(incoming.price());
            var validPrice = incoming.side() == Side.BUY ? comparePrice <= 0 : comparePrice >= 0;

            if (!validPrice)
                throw new IllegalArgumentException("Execution price violates incoming limit price");

            totalExecuted = totalExecuted.add(trade.quantity());

        }

        if (totalExecuted.compareTo(incoming.quantity()) > 0)
            throw new IllegalArgumentException("Total executed quantity exceeds incoming quantity");


    }
}
