package com.maday.apex;

import com.maday.apex.persistence.EventLog;
import com.maday.apex.persistence.EventLogReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

public class EventLogReaderTest {

    @TempDir
    Path tempDir;

    @Test
    public void shouldPersistAndRecoverBatches() throws Exception {
        var logPath = tempDir.resolve("apex-events.log");

        var first = TestProvider.createBatchStartingAt(1);
        var second = TestProvider.createBatchStartingAt(3);

        try (var log = new EventLog(logPath)) {
            log.appendBatch(first);
            log.appendBatch(second);
        }

        var result = EventLogReader.readBatches(logPath);

        assertEquals(2, result.batches().size());
        assertEquals(first, result.batches().get(0));
        assertEquals(second, result.batches().get(1));

        assertFalse(result.incompleteTail());
        assertEquals(Files.size(logPath), result.validPosition());
    }

    @Test
    void shouldDetectIncompleteTrailingBatch() throws Exception {
        var logPath = tempDir.resolve("apex-events.log");

        var batch =  TestProvider.createBatchStartingAt(1);
        try (var log = new EventLog(logPath)) {
            log.appendBatch(batch);
        }

        var validSize = Files.size(logPath);

        // Simulate a process that stopped during its next write.
        Files.write(
                logPath,
                new byte[]{0x41, 0x50, 0x45},
                StandardOpenOption.APPEND
        );

        var result = EventLogReader.readBatches(logPath);

        assertEquals(1, result.batches().size());
        assertEquals(batch, result.batches().getFirst());

        assertTrue(result.incompleteTail());
        assertEquals(validSize, result.validPosition());
    }

    @Test
    void shouldRejectCorruptedCompleteBatch() throws Exception {
        Path logPath = tempDir.resolve("apex-events.log");

        try (EventLog log = new EventLog(logPath)) {
            log.appendBatch(TestProvider.createBatchStartingAt(1));
        }

        byte[] file = Files.readAllBytes(logPath);

        // Change a payload byte, leaving the checksum unchanged.
        file[10] ^= 0x01;

        Files.write(logPath, file);

        assertThrows(
                IOException.class,
                () -> EventLogReader.readBatches(logPath)
        );
    }

}
