package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Trade;
import com.maday.apex.domain.event.OrderAccepted;
import com.maday.apex.persistence.EventLog;
import com.maday.apex.persistence.EventLogOwnership;
import com.maday.apex.persistence.EventLogRecovery;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class PersistentMatchingEngine implements AutoCloseable {

    private sealed interface Command permits NewOrder, Stop {
    }

    private record NewOrder(LimitOrder order, CompletableFuture<Long> result) implements Command {
    }

    private enum Stop implements Command {
        INSTANCE;
    }

    private final BlockingQueue<Command> queue = new LinkedBlockingQueue<>(1_000);

    private final OrderBook orderBook = new OrderBook();
    private final TradeHistory tradeHistory = new TradeHistory();
    private final EventApplier eventApplier = new EventApplier(orderBook, tradeHistory);
    private final MatchingPlanner matchingPlanner = new MatchingPlanner();
    private final EventBatchFactory batchFactory = new EventBatchFactory();

    private final EventLog eventLog;

    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    private long nextSequence;

    private Thread matchingThread;

    // Because one thread owns this map, we don't need concurrent map or a lock for it
    private final Map<UUID, OrderAccepted> acceptedOrders = new HashMap<>();

    private final FailureInjector failureInjector;

    private final EventLogOwnership ownership;

    public PersistentMatchingEngine(Path logPath) throws IOException {
        this(logPath, _ -> {});
    }

    public PersistentMatchingEngine(Path logPath, FailureInjector failureInjector) throws IOException {

        this.failureInjector = Objects.requireNonNull(failureInjector);

        var acquired = EventLogOwnership.acquire(logPath);

        EventLog openedLog = null;

        try {
            // Ownership is acquired BEFORE recovery.
            var recoveredNextSequence = recover(logPath);

            // Only open the writer after successful recovery.
            openedLog = new EventLog(logPath);

            this.ownership = acquired;
            this.eventLog = openedLog;
            this.nextSequence = recoveredNextSequence;


        } catch (IOException | RuntimeException e) {

            if (openedLog != null) {
                try {
                    openedLog.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
            }

            try {
                acquired.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }

            throw e;
        }

    }

    private long recover(Path logPath) throws IOException {
        var recovery = EventLogRecovery.recover(logPath, 1);

        for (var batch : recovery.batches()) {

            for (var event : batch.events()) {

                if (event instanceof OrderAccepted accepted) {

                    var previous = acceptedOrders.putIfAbsent(accepted.orderId(), accepted);

                    if (previous != null)
                        throw new IOException("Duplicate accepted order ID in event history: " + accepted.orderId());

                }

                // Rebuild the order book and trade history
                eventApplier.apply(event);

            }
        }

        return recovery.nextSequence();
    }

    public synchronized void start() {
        if (matchingThread != null)
            throw new IllegalStateException("Already started");

        matchingThread = Thread.ofPlatform()
                .name("apex-matching-engine")
                .start(this::processOrders);
    }

    public CompletableFuture<Long> submit(LimitOrder order) throws InterruptedException {

        checkFailure();

        if (matchingThread == null || !matchingThread.isAlive()) {
            throw new IllegalStateException("Matching engine is not running");
        }

        var result = new CompletableFuture<Long>();

        queue.put(new NewOrder(order, result));

        checkFailure();

        return result;
    }

    private void processOrders() {

        try {
            while (true) {
                var command = queue.take();

                if (command == Stop.INSTANCE) break;

                var newOrder = (NewOrder) command;

                try {
                    var order = newOrder.order();

                    validate(order);

                    long sequence = processOrder(order);

                    // 4. Acknowledge successful processing.
                    newOrder.result().complete(sequence);

                } catch (IllegalArgumentException e) {
                    // Normal request rejection.
                    // The engine remains operational.
                    newOrder.result().completeExceptionally(e);
                } catch (EngineFatalException e) {

                    // Persistence or projection failure.
                    newOrder.result().completeExceptionally(e);

                    failure.compareAndSet(null, e);
                    failQueuedCommands(e);

                    return;
                }
            }
        } catch (InterruptedException e) {
            failure.compareAndSet(null, e);
            failQueuedCommands(e);

        }
    }


    private long processOrder(LimitOrder incoming) {

        // 1. Idempotency check
        var previous = acceptedOrders.get(incoming.id());

        if (previous != null) {
            if (sameRequest(previous, incoming))
                return previous.sequence();

            throw new IllegalArgumentException("Order ID already exists with a different request: " + incoming.id());
        }

        // 2. Calculate the matching plan without mutating the book.
        var plannedTrades = matchingPlanner.plan(incoming, orderBook);

        injectFailure(FailurePoint.AFTER_PLANNING);

        // 3. Generate a candidate event batch.
        var batch = batchFactory.create(incoming, plannedTrades, nextSequence, Instant.now());

        var candidateNextSequence = Math.incrementExact(batch.lastSequence());

        // 4. Commit the entire batch to durable storage.
        try {
            eventLog.appendBatch(batch);
        } catch (IOException | RuntimeException e) {
            throw new EngineFatalException("Failed to durably append event batch", e);
        }

        // 5. The batch is now committed.
        nextSequence = candidateNextSequence;

        injectFailure(FailurePoint.AFTER_DURABLE_APPEND);

        // 6. Apply committed events to in-memory projections.
        try {
            for (var event : batch.events()) {
                eventApplier.apply(event);
            }
        } catch (RuntimeException e) {
            throw new EngineFatalException("Failed to apply a durably committed event batch", e);
        }

        injectFailure(FailurePoint.AFTER_EVENT_APPLICATION);

        // 7. Update the idempotency index.
        var accepted = (OrderAccepted) batch.events().getFirst();

        acceptedOrders.put(accepted.orderId(), accepted);

        return accepted.sequence();

    }

    private void failQueuedCommands(Throwable cause) {
        Command command;
        while ((command = queue.poll()) != null) {
            if (command instanceof NewOrder newOrder)
                newOrder.result().completeExceptionally(new IllegalStateException("Matching engine has stopped", cause));
        }

    }

    private boolean sameRequest(
            OrderAccepted existing,
            LimitOrder incoming
    ) {
        return existing.userId().equals(incoming.userId())
                && existing.symbol().equals(incoming.symbol())
                && existing.side() == incoming.side()
                && existing.price().compareTo(incoming.price()) == 0
                && existing.quantity().compareTo(incoming.quantity()) == 0;
    }

    private void validate(LimitOrder order) {
        if (order.quantity().signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (order.price().signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    private void checkFailure() {
        var error = failure.get();

        if (error != null) {
            throw new IllegalStateException("Matching engine has failed", error);
        }
    }

    List<Trade> trades() {
        return tradeHistory.all();
    }

    int restingOrderCount() {
        return orderBook.restingOrderCount();
    }

    BigDecimal remainingQuantity(UUID orderId) {
        return orderBook.requireOrder(orderId).remainingQuantity();
    }

    private void injectFailure(FailurePoint point) {
        try {
            failureInjector.at(point);
        } catch (RuntimeException e) {
            throw new EngineFatalException("Injected failure at " + point, e);
        }
    }

    @Override
    public synchronized void close() throws Exception {

        if (matchingThread != null && matchingThread.isAlive()) {
            queue.put(Stop.INSTANCE);
            matchingThread.join();
        }

        IOException closeFailure = null;

        try {
            eventLog.close();
        } catch (IOException e) {
            closeFailure = e;
        }

        try {
            ownership.close();
        } catch (IOException e) {
            if (closeFailure == null) {
                closeFailure = e;
            } else {
                closeFailure.addSuppressed(e);
            }
        }

        if (closeFailure != null) {
            throw closeFailure;
        }

        checkFailure();
    }

    private static final class EngineFatalException
            extends RuntimeException {

        EngineFatalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public enum FailurePoint {
        AFTER_PLANNING,
        AFTER_DURABLE_APPEND,
        AFTER_EVENT_APPLICATION
    }

    @FunctionalInterface
    public interface FailureInjector {
        void at(FailurePoint point);
    }
}
