package com.maday.apex.engine;


import com.maday.apex.TestProvider;
import com.maday.apex.domain.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PersistentMatchingEngineInterruptionTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldTerminateWorkerWhenInterruptedWhileWaitingForCommand() throws Exception {

        var logPath = tempDir.resolve("appex.events");

        var engine = new PersistentMatchingEngine(logPath);

        engine.start();

        var matchingThread = getMatchingThread(engine);

        assertTrue(matchingThread.isAlive());

        matchingThread.interrupt();

        matchingThread.join(2_000);

        assertFalse(matchingThread.isAlive());

        assertThrows(
                IllegalStateException.class,
                () -> engine.submit(TestProvider.order(UUID.randomUUID(), "seller-1", Side.SELL, "100", "5")));

        // Closing must release resources, then report the engine failure.
        assertThrows(
                IllegalStateException.class,
                engine::close
        );

        try (var recoveredEngine =
                     new PersistentMatchingEngine(logPath)) {

            // Successfully acquired ownership and recovered the log.
        }


    }


    @Test
    void deterministicTest() throws Exception {


        var planningReached = new CountDownLatch(1);
        var allowProcessing = new CountDownLatch(1);


        var logPath = tempDir.resolve("appex.events");

        var engine = new PersistentMatchingEngine(logPath, point -> {
            if (point == PersistentMatchingEngine.FailurePoint.AFTER_PLANNING) {
                planningReached.countDown();

                boolean interrupted = false;
                while (true) {
                    try {
                        allowProcessing.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        engine.start();

        var matchingThread = getMatchingThread(engine);

        var order = TestProvider.order(UUID.randomUUID(), "seller-1", Side.SELL, "100", "5");

        // Submit asynchronously from another thread.
        var submitFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return engine.submit(order);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });


        // Wait until the worker reaches AFTER_PLANNING.
        assertTrue(planningReached.await(2, TimeUnit.SECONDS));

        // Interrupt the matching worker.
        matchingThread.interrupt();

        // Allow processing to continue.
        allowProcessing.countDown();

        // The current order should still complete successfully.
        var sequence = submitFuture.get(2, TimeUnit.SECONDS);

        // The worker should terminate at its next queue.take().
        matchingThread.join(2_000);

        assertFalse(matchingThread.isAlive());

        assertThrows(
                IllegalStateException.class,
                () -> engine.submit(TestProvider.order(UUID.randomUUID(), "seller-1", Side.SELL, "100", "5"))
        );
    }

    private Thread getMatchingThread(PersistentMatchingEngine engine) throws Exception {
        var field = PersistentMatchingEngine.class.getDeclaredField("matchingThread");
        field.setAccessible(true);
        return (Thread) field.get(engine);
    }
}
