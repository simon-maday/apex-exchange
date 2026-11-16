package com.maday.apex;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadInterruptionTest {

    @Test
    void shouldDemonstrateSwallowedInterruption() throws Exception {

        var queue = new LinkedBlockingQueue<String>();
        var started = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();

        var worker = new Thread(() -> {

            started.countDown();

            while (true) {
                try {
                    var command = queue.take();
                    if ("STOP".equals(command)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    // BUG: interruption is swallowed.
                }
            }
        });

        worker.start();

        try {

            assertTrue(started.await(5, TimeUnit.SECONDS));

            worker.interrupt();

            Thread.sleep(100);

            assertTrue(interrupted.get());

            // The worker is still alive!
            assertTrue(worker.isAlive());


        } finally {
            // Clean up the test without leaving a thread running.
            queue.offer("STOP");
            worker.join(5000);
        }

        assertFalse(worker.isAlive());

    }
}