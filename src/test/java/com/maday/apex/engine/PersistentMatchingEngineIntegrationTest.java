package com.maday.apex.engine;

import com.maday.apex.TestProvider;
import com.maday.apex.domain.Side;
import com.maday.apex.domain.Trade;
import com.maday.apex.persistence.EventLogRecovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PersistentMatchingEngineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMatchPersistRecoverAndNotDuplicateTrades() throws Exception {

        var logPath = tempDir.resolve("exchange.events");

        var sell = TestProvider.order(
                "00000000-0000-0000-0000-000000000001", "seller-1", Side.SELL, "100", "5"
        );

        var buy = TestProvider.order(
                "00000000-0000-0000-0000-000000000002", "buyer-1", Side.BUY, "105", "3"
        );

        List<Trade> originalTrades;

        // Phase 1: Submit orders and execute a trade.
        try (var engine = new PersistentMatchingEngine(logPath)) {

            engine.start();

            var sellSequence = engine.submit(sell).get(5, TimeUnit.SECONDS);

            var buySequence = engine.submit(buy).get(5, TimeUnit.SECONDS);

            assertEquals(1L, sellSequence);
            assertEquals(3L, buySequence);

            assertEquals(1, engine.trades().size());
            assertEquals(1, engine.restingOrderCount());

            assertEquals(new BigDecimal("2"), engine.remainingQuantity(sell.id()));
            assertEquals(BigDecimal.ZERO, engine.remainingQuantity(buy.id()));

            originalTrades = engine.trades();

        }

        // Phase 2: Reconstruct the engine from the same log
        try (var recovered = new PersistentMatchingEngine(logPath)) {

            recovered.start();

            // The trade must be recovered not re-executed.
            assertEquals(1, recovered.trades().size());

            // The recovered trade must be exactly the original trade.
            assertEquals(originalTrades, recovered.trades());

            // The resting SELL must still have quantity 2.
            assertEquals(1, recovered.restingOrderCount());

            assertEquals(new BigDecimal("2"), recovered.remainingQuantity(sell.id()));

            // The BUY was fully executed and must not be resting.
            assertEquals(BigDecimal.ZERO, recovered.remainingQuantity(buy.id()));

            // Verify sequence continuation after recovery.
            var nextSellId = "00000000-0000-0000-0000-000000000003";


            var nextSell = TestProvider.order(nextSellId, "seller-2", Side.SELL, "110", "1");

            var nextSequence = recovered.submit(nextSell).get(5, TimeUnit.SECONDS);

            assertEquals(5L, nextSequence);

            // No new trade should have appeared.
            assertEquals(1, recovered.trades().size());
            assertEquals(2, recovered.restingOrderCount());
        }

    }

    @Test
    void shouldNotDuplicateOrderWhenClientRetriesAfterRestart() throws Exception {
        var logPath = tempDir.resolve("idempotency.events");

        var sell = TestProvider.order(
                UUID.randomUUID(), "seller-1", Side.SELL, "100", "5"
        );

        var buy = TestProvider.order(
                UUID.randomUUID(), "buyer-1", Side.BUY, "105", "3"
        );

        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
            assertEquals(3L, engine.submit(buy).get(5, TimeUnit.SECONDS));

            // Client retries the exact same request.
            assertEquals(3L, engine.submit(buy).get(5, TimeUnit.SECONDS));

            assertEquals(1, engine.trades().size());
        }

        // Second engine lifecycle: recover from disk.
        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
            assertEquals(3L, engine.submit(buy).get(5, TimeUnit.SECONDS));

            // The retry must still return the original sequence.
            assertEquals(3L, engine.submit(buy).get(5, TimeUnit.SECONDS));

            assertEquals(1, engine.trades().size());

            assertEquals(1, engine.restingOrderCount());

            assertEquals(
                    new BigDecimal("2"),
                    engine.remainingQuantity(sell.id())
            );
        }

        // Inspect the durable log after closing the writer.
        var recovery = EventLogRecovery.recover(logPath, 1);

        assertEquals(2, recovery.batches().size());

        var eventCount = recovery.batches().stream()
                .mapToInt(batch -> batch.events().size())
                .sum();

        assertEquals(4, eventCount);

    }

    @Test
    void shouldRejectSameOrderIdWithDifferentPayload() throws Exception {
        var logPath = tempDir.resolve("exchange.events");

        var sellId = UUID.randomUUID();
        var buyId = UUID.randomUUID();

        var sell = TestProvider.order(
                sellId, "seller-1", Side.SELL, "100", "5"
        );

        var buy = TestProvider.order(
                buyId, "buyer-1", Side.BUY, "105", "3"
        );

        // Create the original durable history.
        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
            assertEquals(3L, engine.submit(buy).get(5, TimeUnit.SECONDS));
        }

        // Recover and try to reuse the BUY ID with a different quantity.
        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            var conflictingBuy = TestProvider.order(
                    buyId, "buyer-1", Side.BUY, "105", "4"
            );

            var exception = assertThrows(ExecutionException.class, () -> engine.submit(conflictingBuy).get(5, TimeUnit.SECONDS));

            assertInstanceOf(IllegalArgumentException.class, exception.getCause());

            // The engine must remain operational.
            assertEquals(1, engine.trades().size());

            var nextOrderId = UUID.randomUUID();

            var nextOrder = TestProvider.order(
                    nextOrderId, "seller-2", Side.SELL, "110", "1"
            );

            // The rejected request must not consume sequence numbers.
            assertEquals(5L, engine.submit(nextOrder).get(5, TimeUnit.SECONDS));

            assertEquals(1, engine.trades().size());
            assertEquals(2, engine.restingOrderCount());
        }
    }

    @Test
    void shouldRecoverCommittedBatchAfterFailureBeforeApplyingEvents() throws Exception {
        var logPath = tempDir.resolve("crash-after-commit.events");

        var sellId = UUID.randomUUID();
        var buyId = UUID.randomUUID();

        var sell = TestProvider.order(sellId, "seller-1", Side.SELL, "100", "5");

        var buy = TestProvider.order(buyId, "buyer-1", Side.BUY, "105", "3");

        // First start: establish a resting SELL order.
        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
        }

        // Second start: deliberately fail after durable append.
        var failingEngine = new PersistentMatchingEngine(logPath, point -> {
            if (point == PersistentMatchingEngine.FailurePoint.AFTER_DURABLE_APPEND)
                throw new RuntimeException("Simulated process failure");
        });

        failingEngine.start();

        var exception = assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> failingEngine.submit(buy).get(5, TimeUnit.SECONDS)
        );

        assertInstanceOf(RuntimeException.class, exception.getCause());

        // The failed engine must not continue processing.
        assertThrows(Exception.class, failingEngine::close);

        // Third start: recover from the durable event log.
        try (var recovered = new PersistentMatchingEngine(logPath)) {
            recovered.start();

            // The trade was committed before the failure.
            assertEquals(1, recovered.trades().size());

            // Recovery must reconstruct the correct remaining quantity.
            assertEquals(new BigDecimal("2"), recovered.remainingQuantity(sellId));

            assertEquals(new BigDecimal("0"), recovered.remainingQuantity(buyId));

            assertEquals(1, recovered.restingOrderCount());

            // Simulate the client retrying after it received no acknowledgement.
            assertEquals(3L, recovered.submit(buy).get(5, TimeUnit.SECONDS));

            // The retry must not execute the trade again.
            assertEquals(1, recovered.trades().size());

        }
    }

    @Test
    void shouldNotPersistOrderWhenFailureOccursAfterPlanning() throws Exception {
        var logPath = tempDir.resolve("crash-after-planning.events");

        var sellId = UUID.randomUUID();
        var buyId = UUID.randomUUID();

        var sell = TestProvider.order(sellId, "seller-1", Side.SELL, "100", "5");

        var buy = TestProvider.order(buyId, "buyer-1", Side.BUY, "105", "3");

        // Establish the resting SELL order.
        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();

            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
        }

        // Fail after planning but before writing the BUY batch.
        var failingEngine = new PersistentMatchingEngine(logPath, point -> {
            if (point == PersistentMatchingEngine.FailurePoint.AFTER_PLANNING)
                throw new RuntimeException("Simulated failure after planning");
        });

        failingEngine.start();

        assertThrows(ExecutionException.class,
                () -> failingEngine.submit(buy).get(5, TimeUnit.SECONDS));

        assertThrows(Exception.class, failingEngine::close);

        // Recover: only the SELL should exist.
        try (var recovered = new PersistentMatchingEngine(logPath)) {
            recovered.start();

            assertEquals(0, recovered.trades().size());
            assertEquals(1, recovered.restingOrderCount());

            assertEquals(new BigDecimal("5"), recovered.remainingQuantity(sellId));

            // The failed BUY was never committed. It can now be submitted.
            assertEquals(3L, recovered.submit(buy).get(5, TimeUnit.SECONDS));

            assertEquals(1, recovered.trades().size());

            assertEquals(new BigDecimal("2"), recovered.remainingQuantity(sellId));
        }
    }

    @Test
    void shouldRecoverAppliedEventsWhenFailureOccursBeforeAcknowledgement() throws Exception {
        var logPath = tempDir.resolve("crash-after-application.events");

        var sellId = UUID.randomUUID();
        var buyId = UUID.randomUUID();

        var sell = TestProvider.order(sellId, "seller-1", Side.SELL, "100", "5");

        var buy = TestProvider.order(buyId, "buyer-1", Side.BUY, "105", "3");

        try (var engine = new PersistentMatchingEngine(logPath)) {
            engine.start();
            assertEquals(1L, engine.submit(sell).get(5, TimeUnit.SECONDS));
        }

        // Fail after all events have been applied.
        var failingEngine = new PersistentMatchingEngine(
                logPath,
                point -> {
                    if (point == PersistentMatchingEngine.FailurePoint.AFTER_EVENT_APPLICATION) {
                        throw new RuntimeException("Simulated acknowledgement failure");
                    }
                }
        );

        failingEngine.start();

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> failingEngine.submit(buy).get(5, TimeUnit.SECONDS)
        );

        assertThrows(Exception.class, failingEngine::close);

        try (var recovered = new PersistentMatchingEngine(logPath)) {
            recovered.start();

            assertEquals(1, recovered.trades().size());
            assertEquals(1, recovered.restingOrderCount());

            assertEquals(new BigDecimal("2"), recovered.remainingQuantity(sellId));

            assertEquals(new BigDecimal("0"), recovered.remainingQuantity(buyId));

            // The client retries because it never received an acknowledgement.
            assertEquals(3L, recovered.submit(buy).get(5, TimeUnit.SECONDS));

            // No duplicate trade.
            assertEquals(1, recovered.trades().size());

            assertEquals(new BigDecimal("2"), recovered.remainingQuantity(sellId));

        }
    }

    @Test
    void shouldPreventSecondEngineFromOpeningSameLog() throws Exception {
        var logPath = tempDir.resolve("apex.events");

        try (var first = new PersistentMatchingEngine(logPath)) {
            assertThrows(IOException.class, () -> new PersistentMatchingEngine(logPath));
        }

        // The first engine has shut down.
        // A new engine should now be able to acquire ownership.
        try (PersistentMatchingEngine second = new PersistentMatchingEngine(logPath)) {

            assertNotNull(second);
        }
    }
}
