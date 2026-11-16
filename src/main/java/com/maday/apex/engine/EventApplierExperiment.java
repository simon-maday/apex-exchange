package com.maday.apex.engine;

import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.ExchangeEvent;
import com.maday.apex.domain.event.OrderAccepted;
import com.maday.apex.domain.event.OrderRested;
import com.maday.apex.domain.event.TradeExecuted;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

public class EventApplierExperiment {
    static void main() {
        var orderBook = new OrderBook();
        var tradeHistory = new TradeHistory();

        var eventApplier = new EventApplier(orderBook, tradeHistory);

        var events = new ArrayList<ExchangeEvent>();

        var buyOrderId = UUID.randomUUID();
        events.add(new OrderAccepted(
                100,
                buyOrderId,
                "alice",
                "BTC/USDT",
                Side.BUY,
                new BigDecimal("50000"),
                new BigDecimal("2.0"),
                Instant.now()));

        events.add(new OrderRested(101, buyOrderId, new BigDecimal("2.0"), Instant.now()));


        var sellOrderId = UUID.randomUUID();
        events.add(new OrderAccepted(
                102,
                sellOrderId,
                "josh",
                "BTC/USDT",
                Side.SELL,
                new BigDecimal("49000"),
                new BigDecimal("0.5"),
                Instant.now()));

        events.add(new TradeExecuted(
                103,
                UUID.randomUUID(),
                buyOrderId,
                sellOrderId,
                "BTC/USDT",
                new BigDecimal("50000"),
                new BigDecimal("0.5"),
                Instant.now()
        ));

        for (var event : events) {
            eventApplier.apply(event);
        }

        IO.println(orderBook.restingOrderCount());
        IO.println(tradeHistory.size());

        tradeHistory.all().forEach(event -> {IO.println(event.toString());});
    }
}
