package com.maday.apex.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventLogCrossProcessLockTest {

    @TempDir
    Path tempDir;

    @Test
    @Timeout(30)
    void onlyOneJvmCanOwnTheSameEventLog() throws Exception {

        var logPath = tempDir.resolve("apex.events");

        var first = startProcess("hold", logPath);

        try {

            // Wait until JVM-A confirms ownership
            var firstOutput = new BufferedReader(new InputStreamReader(first.getInputStream()));

            assertEquals("LOCKED", firstOutput.readLine());

            // JVM B attempts to acquire the same lock.
            Process second = startProcess("try", logPath);

            assertTrue(second.waitFor(10, TimeUnit.SECONDS));

            var secondOutput = new String(second.getInputStream().readAllBytes());

            assertEquals(2, second.exitValue());
            assertTrue(secondOutput.contains("BUSY"));

        } finally {

            // Simulate an abrupt JVM termination.
            first.destroyForcibly();
            assertTrue(first.waitFor(10, TimeUnit.SECONDS));
        }

        // JVM A is now dead. A new JVM should acquire ownership.
        var third = startProcess("try", logPath);

        assertTrue(third.waitFor(10, TimeUnit.SECONDS));

        var thirdOutput = new String(third.getInputStream().readAllBytes());

        assertEquals(0, third.exitValue());
        assertTrue(thirdOutput.contains("ACQUIRED"));
    }

    private Process startProcess(String mode, Path logPath) throws IOException {

        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(
                javaExecutable,
                "-cp",
                System.getProperty("java.class.path"),
                EventLogLockProcess.class.getName(),
                mode,
                logPath.toString()
        ).redirectErrorStream(true)
                .start();
    }
}
