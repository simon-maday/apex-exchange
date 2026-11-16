package com.maday.apex.engine;

import com.maday.apex.TestProvider;
import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.OrderAccepted;
import com.maday.apex.domain.event.OrderRested;
import com.maday.apex.domain.event.TradeExecuted;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventBatchFactoryTest {

    private final EventBatchFactory factory = new EventBatchFactory();

    private static final Instant NOW =
            Instant.parse("2026-11-07T10:00:00Z");

    private static final UUID SELL_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID BUY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void shouldCreateAcceptedAndRestedEventsWhenOrderIsNotMatched() {
        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "100", "2"
        );

        var batch = factory.create(incoming, List.of(), 100, NOW);

        assertEquals(2, batch.events().size());

        assertInstanceOf(OrderAccepted.class, batch.events().get(0));
        assertInstanceOf(OrderRested.class, batch.events().get(1));

        var accepted = (OrderAccepted) batch.events().get(0);

        assertEquals(100, accepted.sequence());
        assertEquals(incoming.id(), accepted.orderId());
        assertEquals(new BigDecimal("2"), accepted.quantity());

        var rested = (OrderRested) batch.events().get(1);

        assertEquals(101, rested.sequence());
        assertEquals(incoming.id(), rested.orderId());
        assertEquals(new BigDecimal("2"), rested.remainingQuantity());

        assertEquals(100, batch.firstSequence());
        assertEquals(101, batch.lastSequence());
    }

    @Test
    void shouldCreateAcceptedAndTradeEventsWhenOrderIsFullyExecuted() {

        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "101", "2"
        );

        var plannedTrade = new PlannedTrade(
                incoming.id(),
                SELL_A,
                "BTC-USDT",
                new BigDecimal("100"),
                new BigDecimal("2")
        );

        var batch = factory.create(
                incoming,
                List.of(plannedTrade),
                200,
                NOW
        );

        assertEquals(2, batch.events().size());

        assertInstanceOf(OrderAccepted.class, batch.events().get(0));
        assertInstanceOf(TradeExecuted.class, batch.events().get(1));

        var trade = (TradeExecuted) batch.events().get(1);

        assertEquals(201, trade.sequence());
        assertEquals(incoming.id(), trade.buyOrderId());
        assertEquals(SELL_A, trade.sellOrderId());
        assertEquals(new BigDecimal("100"), trade.price());
        assertEquals(new BigDecimal("2"), trade.quantity());

        assertEquals(200, batch.firstSequence());
        assertEquals(201, batch.lastSequence());
    }

    @Test
    void shouldCreateAcceptedTradeAndRestedEventsWhenOrderIsPartiallyExecuted() {

        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "101", "5"
        );

        var plannedTrade = new PlannedTrade(
                incoming.id(),
                SELL_A,
                "BTC-USDT",
                new BigDecimal("100"),
                new BigDecimal("2")
        );

        var batch = factory.create(
                incoming,
                List.of(plannedTrade),
                300,
                NOW
        );

        assertEquals(3, batch.events().size());

        assertInstanceOf(OrderAccepted.class, batch.events().get(0));
        assertInstanceOf(TradeExecuted.class, batch.events().get(1));
        assertInstanceOf(OrderRested.class, batch.events().get(2));

        var trade = (TradeExecuted) batch.events().get(1);

        assertEquals(301, trade.sequence());
        assertEquals(new BigDecimal("2"), trade.quantity());

        var rested = (OrderRested) batch.events().get(2);

        assertEquals(302, rested.sequence());
        assertEquals(incoming.id(), rested.orderId());
        assertEquals(new BigDecimal("3"), rested.remainingQuantity());

        assertEquals(300, batch.firstSequence());
        assertEquals(302, batch.lastSequence());
    }

    @Test
    void shouldRejectTradeThatDoesNotReferenceIncomingOrder() {
        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "101", "5"
        );

        var invalidTrade = new PlannedTrade(
                UUID.randomUUID(),
                SELL_A,
                "BTC-USDT",
                new BigDecimal("100"),
                new BigDecimal("2")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(incoming, List.of(invalidTrade), 100, NOW)
        );
    }

    @Test
    void shouldRejectTradeWithDifferentSymbol() {
        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "101", "5"
        );

        var invalidTrade = new PlannedTrade(
                incoming.id(),
                SELL_A,
                "ETH-USDT",
                new BigDecimal("100"),
                new BigDecimal("2")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(incoming, List.of(invalidTrade), 100, NOW)
        );
    }

    @Test
    void shouldRejectExecutionPriceAboveIncomingBuyLimit() {
        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "100", "5"
        );

        var invalidTrade = new PlannedTrade(
                incoming.id(),
                SELL_A,
                "BTC-USDT",
                new BigDecimal("101"),
                new BigDecimal("2")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(incoming, List.of(invalidTrade), 100, NOW)
        );
    }

    @Test
    void shouldRejectTotalExecutionExceedingIncomingQuantity() {
        var incoming = TestProvider.order(
                "00000000-0000-0000-0000-000000000010",
                Side.BUY, "101", "3"
        );

        var first = new PlannedTrade(
                incoming.id(),
                SELL_A,
                "BTC-USDT",
                new BigDecimal("100"),
                new BigDecimal("2")
        );

        var second = new PlannedTrade(
                incoming.id(),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "BTC-USDT",
                new BigDecimal("101"),
                new BigDecimal("2")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        incoming,
                        List.of(first, second),
                        100,
                        NOW
                )
        );
    }

}
