package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchingPlannerTest {

    @Test
    void shouldMatchMultipleSellOrdersInPriceTimePriority() {
        var book = new OrderBook();
        var planner = new MatchingPlanner();

        // Arrange: resting SELL orders
        LimitOrder sellA = order(
                "00000000-0000-0000-0000-000000000001",
                Side.SELL, "100", "2.0"
        );

        LimitOrder sellB = order(
                "00000000-0000-0000-0000-000000000002",
                Side.SELL, "101", "3.0"
        );

        LimitOrder sellC = order(
                "00000000-0000-0000-0000-000000000003",
                Side.SELL, "101", "1.5"
        );

        // Register and rest orders in price-time order
        book.registerAccepted(sellA);
        book.rest(sellA.id(), sellA.quantity(), 1);

        book.registerAccepted(sellB);
        book.rest(sellB.id(), sellB.quantity(), 2);

        book.registerAccepted(sellC);
        book.rest(sellC.id(), sellC.quantity(), 3);

        // Incoming BUY order
        var incomingBuy = order(
                "00000000-0000-0000-0000-000000000004",
                Side.BUY, "101", "4.0"
        );

        var trades = planner.plan(incomingBuy, book);

        assertEquals(2, trades.size());

        // First match: best price, SELL A
        assertEquals(sellA.id(), trades.getFirst().sellOrderId());
        assertEquals(new BigDecimal("100"), trades.get(0).price());
        assertEquals(new BigDecimal("2.0"), trades.get(0).quantity());

        // Second match: SELL B before SELL C (same price, earlier sequence)
        assertEquals(sellB.id(), trades.get(1).sellOrderId());
        assertEquals(new BigDecimal("101"), trades.get(1).price());
        assertEquals(new BigDecimal("2.0"), trades.get(1).quantity());

        // SELL C must remain untouched.
        assertEquals(new BigDecimal("1.5"),
                book.requireOrder(sellC.id()).remainingQuantity());
    }

    @Test
    void   shouldMatchMultipleBuyOrdersInPriceTimePriority() {
        var book = new OrderBook();
        var planner = new MatchingPlanner();

        LimitOrder buyA = order(
                "00000000-0000-0000-0000-000000000001",
                Side.BUY, "100", "2.0"
        );

        LimitOrder buyB = order(
                "00000000-0000-0000-0000-000000000002",
                Side.BUY, "101", "3.0"
        );

        LimitOrder buyC = order(
                "00000000-0000-0000-0000-000000000003",
                Side.BUY, "101", "1.5"
        );

        book.registerAccepted(buyA);
        book.rest(buyA.id(), buyA.quantity(), 1);

        book.registerAccepted(buyB);
        book.rest(buyB.id(), buyB.quantity(), 2);

        book.registerAccepted(buyC);
        book.rest(buyC.id(), buyC.quantity(), 3);

        // Incoming SELL order
        var incomingSell = order(
                "00000000-0000-0000-0000-000000000004",
                Side.SELL, "101", "4.0"
        );

        var trades = planner.plan(incomingSell, book);

        assertEquals(2, trades.size());

        // First match: best price, BUY B (same price, earlier sequence)
        assertEquals(buyB.id(), trades.getFirst().buyOrderId());
        assertEquals(new BigDecimal("101"), trades.getFirst().price());
        assertEquals(new BigDecimal("3.0"), trades.getFirst().quantity());

        assertEquals(buyC.id(), trades.get(1).buyOrderId());
        assertEquals(new BigDecimal("101"), trades.get(1).price());
        assertEquals(new BigDecimal("1.0"), trades.get(1).quantity());

    }

    private LimitOrder order(
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
}
