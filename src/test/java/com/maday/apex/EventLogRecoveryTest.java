package com.maday.apex;

import com.maday.apex.persistence.BatchSequenceValidator;
import com.maday.apex.persistence.EventLog;
import com.maday.apex.persistence.EventLogReader;
import com.maday.apex.persistence.EventLogRecovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

public class EventLogRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    public void shouldRejectMissingSequenceAcrossBatches() {
        var validator = new BatchSequenceValidator(1);

        var first = TestProvider.createBatchStartingAt(1);
        var second = TestProvider.createBatchStartingAt(4);

        validator.validate(first);

        assertThrows(IllegalStateException.class, () -> validator.validate(second));
    }

    @Test
    void shouldRejectDuplicateSequenceAcrossBatches() {
        BatchSequenceValidator validator =
                new BatchSequenceValidator(1);

        var first = TestProvider.createBatchStartingAt(1);
        var second = TestProvider.createBatchStartingAt(2);

        validator.validate(first);

        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(second)
        );
    }

    @Test
    void shouldRepairIncompleteTailAndPreserveValidBatches() throws Exception {
        var logPath = tempDir.resolve("apex-events.log");

        var first = TestProvider.createBatchStartingAt(1);
        var second = TestProvider.createBatchStartingAt(3);

        try (var log = new EventLog(logPath)) {
            log.appendBatch(first);
            log.appendBatch(second);
        }

        var validSize = Files.size(logPath);

        // Simulate a crash halfway through another frame header.
        Files.write(logPath, new byte[] {0x41, 0x50, 0x45}, StandardOpenOption.APPEND);

        assertTrue(Files.size(logPath) > validSize);

        var result = EventLogRecovery.recover(logPath, 1);

        assertEquals(2, result.batches().size());
        assertEquals(5, result.nextSequence());
        assertTrue(result.tailRepaired());

        // Verify that the incomplete bytes were physically removed.
        assertEquals(validSize, Files.size(logPath));

        // Verify that the repaired file is readable again.
        var reread = EventLogReader.readBatches(logPath);

        assertEquals(2, reread.batches().size());
        assertFalse(reread.incompleteTail());

    }

    @Test
    void shouldNotModifyFileWhenSequenceHistoryIsInvalid()
            throws Exception {

        Path logPath = tempDir.resolve("apex-events.log");

        try (EventLog log = new EventLog(logPath)) {
            log.appendBatch(TestProvider.createBatchStartingAt(1));
            log.appendBatch(TestProvider.createBatchStartingAt(4));
        }

        long originalSize = Files.size(logPath);

        assertThrows(
                IOException.class,
                () -> EventLogRecovery.recover(logPath, 1)
        );

        assertEquals(originalSize, Files.size(logPath));
    }
}
