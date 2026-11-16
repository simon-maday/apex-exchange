package com.maday.apex.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EventLogProcessCrashTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecoverCommittedBatchAfterAbruptJvmTermination()
            throws Exception {

        var logPath = tempDir.resolve("process-crash.events");

        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        // Surefire's test classpath is preferable when running under Maven.
        var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));

        var process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                EventLogCrashWriter.class.getName(),
                logPath.toString()
        )
                .redirectErrorStream(true)
                .start();

        try {
            var reader = process.inputReader(StandardCharsets.UTF_8);

            // The child has completed appendBatch(), including force(true).
            assertEquals("COMMITTED", reader.readLine(), "Child JVM did not report a successful commit");

            // Kill the child without allowing its shutdown hooks or finally
            // blocks to execute.
            process.destroyForcibly();

            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Child JVM did not terminate");

        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }

        // Recover the file in this JVM.
        var recovery = EventLogRecovery.recover(logPath, 1);

        assertEquals(1, recovery.batches().size());
        assertEquals(2, recovery.nextSequence());
        assertFalse(recovery.tailRepaired());

        var recoveredEvent = recovery.batches()
                .getFirst()
                .events()
                .getFirst();

        assertInstanceOf(
                com.maday.apex.domain.event.OrderAccepted.class,
                recoveredEvent
        );
    }
}