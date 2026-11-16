package com.maday.apex.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadInterruptionTest {

    @Test
    void shouldClearInterruptStatusWhenInterruptedExceptionIsThrown() throws Exception {

        var started = new CountDownLatch(1);

        var exceptionCaught = new AtomicBoolean();
        var interruptStatusInsideCatch = new AtomicBoolean();

        var queue = new LinkedBlockingQueue<>();

        var worker = new Thread(() -> {

            started.countDown();

            try {
                queue.take();
            } catch (InterruptedException e) {
                exceptionCaught.set(true);
                interruptStatusInsideCatch.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();

        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));

        worker.interrupt();

        worker.join();

        assertFalse(worker.isAlive());
        assertTrue(exceptionCaught.get());

        // InterruptedException cleared the interrupt status.
        assertFalse(interruptStatusInsideCatch.get());
    }
}
