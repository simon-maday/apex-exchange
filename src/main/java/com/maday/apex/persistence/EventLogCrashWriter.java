package com.maday.apex.persistence;

import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.EventBatch;
import com.maday.apex.domain.event.OrderAccepted;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class EventLogCrashWriter {

    static void main(String[] args) throws Exception {
        var path = java.nio.file.Path.of(args[0]);

        var order = new OrderAccepted(
                1,
                UUID.randomUUID(),
                "crash-test-user",
                "BTC-USD",
                Side.SELL,
                new BigDecimal("100"),
                new BigDecimal("5"),
                Instant.now()
        );

        var batch = new EventBatch(
                UUID.randomUUID(),
                List.of(order)
        );

        try (var log = new EventLog(path)) {
            log.appendBatch(batch);

            // Signal the parent only after appendBatch has returned.
            System.out.println("COMMITTED");
            System.out.flush();

            // Keep the JVM alive until the parent forcibly terminates it.
            new CountDownLatch(1).await();
        }
    }
}