package com.maday.apex.domain.event;

import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EventModelExperiment {
    static void main() {
        var now = Instant.now();

        var accepted = new OrderAccepted(
                100,
                UUID.randomUUID(),
                "alice",
                "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("1.0"),
                now
        );

        var rested = new OrderRested(
                101,
                accepted.orderId(),
                new BigDecimal("1.0"),
                now
        );

        var events = List.of(accepted, rested);

        for (ExchangeEvent event : events) {
            System.out.println(
                    event.sequence() + " -> "
                            + event.getClass().getSimpleName()
            );
        }

    }
}
