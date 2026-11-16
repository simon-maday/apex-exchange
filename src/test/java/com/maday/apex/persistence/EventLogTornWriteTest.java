package com.maday.apex.persistence;

import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.EventBatch;
import com.maday.apex.domain.event.OrderAccepted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventLogTornWriteTest {

    @TempDir
    Path tempDir;


    @Test
    void shouldRepairIncompleteTrailingHeader() throws Exception {
        Path path = tempDir.resolve("incomplete-header.events");

        byte[] first = frame(1);
        byte[] incompleteHeader = {0x41, 0x50, 0x45};

        Files.write(path, concatenate(first, incompleteHeader));

        var result = EventLogRecovery.recover(path, 1);

        assertEquals(1, result.batches().size());
        assertEquals(2, result.nextSequence());
        assertTrue(result.tailRepaired());

        assertEquals(first.length, Files.size(path));
    }

    @Test
    void shouldRepairIncompleteTrailingPayload() throws Exception {
        Path path = tempDir.resolve("incomplete-payload.events");

        byte[] first = frame(1);
        byte[] second = frame(2);

        // Remove the final two bytes from the second frame.
        byte[] incompleteSecond = java.util.Arrays.copyOf(
                second,
                second.length - 2
        );

        Files.write(path, concatenate(first, incompleteSecond));

        var result = EventLogRecovery.recover(path, 1);

        assertEquals(1, result.batches().size());
        assertEquals(2, result.nextSequence());
        assertTrue(result.tailRepaired());

        assertEquals(first.length, Files.size(path));
    }

    @Test
    void shouldRejectCompleteFrameWithInvalidChecksum() throws Exception {
        Path path = tempDir.resolve("invalid-checksum.events");

        byte[] first = frame(1);
        byte[] second = frame(2);

        // Corrupt the final checksum byte.
        second[second.length - 1] ^= 0x01;

        Files.write(path, concatenate(first, second));

        assertThrows(
                IOException.class,
                () -> EventLogRecovery.recover(path, 1)
        );

        // A complete corrupt frame must not be silently truncated.
        assertEquals(
                first.length + second.length,
                Files.size(path)
        );
    }

    @Test
    void shouldRejectCorruptedFirstFrameEvenWhenLaterFrameIsValid()
            throws Exception {

        Path path = tempDir.resolve("corrupted-first-frame.events");

        byte[] first = frame(1);
        byte[] second = frame(2);

        first[first.length - 1] ^= 0x01;

        Files.write(path, concatenate(first, second));

        assertThrows(
                IOException.class,
                () -> EventLogRecovery.recover(path, 1)
        );
    }

    @Test
    void shouldRecoverValidLogWithoutRepairingAnything() throws Exception {
        Path path = tempDir.resolve("valid.events");

        byte[] first = frame(1);
        byte[] second = frame(2);

        Files.write(path, concatenate(first, second));

        var result = EventLogRecovery.recover(path, 1);

        assertEquals(2, result.batches().size());
        assertEquals(3, result.nextSequence());
        assertFalse(result.tailRepaired());

        assertEquals(first.length + second.length, Files.size(path));
    }

    private byte[] frame(long sequence) throws IOException {
        var event = new OrderAccepted(
                sequence,
                UUID.randomUUID(),
                "test-user",
                "BTC-USD",
                Side.SELL,
                new BigDecimal("100"),
                new BigDecimal("5"),
                Instant.parse("2026-11-09T10:00:00Z")
        );

        var batch = new EventBatch(
                UUID.randomUUID(),
                List.of(event)
        );

        return EventBatchCodec.encode(batch);
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];

        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);

        return result;
    }
}