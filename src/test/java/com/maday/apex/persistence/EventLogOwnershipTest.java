package com.maday.apex.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class EventLogOwnershipTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPreventSecondOwnerInSameJvm() throws Exception {

        var logPath = tempDir.resolve("apex.events");

        try (var first = EventLogOwnership.acquire(logPath)) {
            assertThrows(IOException.class, () -> EventLogOwnership.acquire(logPath));
        }

        // First owner has closed. Ownership can be acquired again.
        try (var second = EventLogOwnership.acquire(logPath)) {

            assertNotNull(second);
        }

    }

    @Test
    void shouldAllowRepeatedClose() throws Exception {

        var logPath = tempDir.resolve("apex.events");

        var ownership = EventLogOwnership.acquire(logPath);

        ownership.close();
        ownership.close();

        assertDoesNotThrow(() -> EventLogOwnership.acquire(logPath).close());
    }
}
