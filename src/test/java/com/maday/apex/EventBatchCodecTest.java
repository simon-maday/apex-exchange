package com.maday.apex;

import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.EventBatch;
import com.maday.apex.domain.event.OrderAccepted;
import com.maday.apex.domain.event.OrderRested;
import com.maday.apex.persistence.EventBatchCodec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventBatchCodecTest {

    @Test
    void shouldEncodeAndDecodeBatch() throws Exception {
        var orderId = UUID.randomUUID();
        var original = new EventBatch(
                UUID.randomUUID(),
                List.of(
                        new OrderAccepted(
                                20,
                                orderId,
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        ),
                        new OrderRested(
                                21,
                                orderId,
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        )
                )
        );

        var encoded = EventBatchCodec.encode(original);
        var decoded = EventBatchCodec.decode(encoded);

        assertEquals(original, decoded);
    }

    @Test
    void shouldRejectInvalidMagicNumber() throws IOException {
        var encoded = EventBatchCodec.encode(createBatch());

        encoded[0] = 0;
        assertThrows(IOException.class, () -> EventBatchCodec.decode(encoded));
    }

    @Test
    void shouldRejectUnsupportedVersion() throws IOException {
        var encoded = EventBatchCodec.encode(createBatch());

        var buffer = ByteBuffer.wrap(encoded);

        buffer.putShort(4, (short) 99);

        assertThrows(
                IOException.class,  () -> EventBatchCodec.decode(encoded)
        );
    }

    @Test
    void shouldRejectTruncatedFrame() throws IOException {
        byte[] encoded = EventBatchCodec.encode(createBatch());

        byte[] truncated = Arrays.copyOf(
                encoded,
                encoded.length - 5
        );

        assertThrows(
                IOException.class,
                () -> EventBatchCodec.decode(truncated)
        );
    }

    @Test
    void shouldRejectUnknownEventType() throws IOException {
        var encoded = EventBatchCodec.encode(createBatch());

        encoded[30] = (byte) 99;

        assertThrows(IOException.class, () -> EventBatchCodec.decode(encoded));
    }

    @Test
    void shouldRejectCorruptedPayload() throws Exception {
        var encoded = EventBatchCodec.encode(createBatch());

        encoded[10] = (byte) (encoded[10] ^ 0x01);

        assertThrows(IOException.class, () -> EventBatchCodec.decode(encoded));

    }

    //@Test
    void shouldRejectInvalidDecimal() throws IOException {
        var encoded = EventBatchCodec.encode(createBatch());

        // Locate the first order's price string.
        // Event starts at byte 30.
        // Event header: 1 + 8 + 8 + 4 + 16 + 4 + 8 + 1 = 50 bytes.
        // Price length is at offset 80.
        int priceLengthOffset = 80;

        ByteBuffer buffer = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN);

        int priceLength = buffer.getInt(priceLengthOffset);

        assertEquals(5, priceLength);

        // Replace "50000" with "XXXXX".
        var invalidPrice = "XXXXX".getBytes(StandardCharsets.UTF_8);

        System.arraycopy(
                invalidPrice, 0,
                encoded, priceLengthOffset + 4,
                invalidPrice.length
        );

        updateChecksum(encoded);

        assertThrows(
                IOException.class,
                () -> EventBatchCodec.decode(encoded)
        );
    }

    private void updateChecksum(byte[] encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN);

        int payloadLength = buffer.getInt(6);

        var crc = new CRC32();
        crc.update(encoded, 10, payloadLength);

        buffer.putInt(
                10 + payloadLength,
                (int) crc.getValue()
        );
    }

    private EventBatch createBatch() {
        UUID orderId = UUID.randomUUID();

        return new EventBatch(
                UUID.randomUUID(),
                List.of(
                        new OrderAccepted(
                                20,
                                orderId,
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        ),
                        new OrderRested(
                                21,
                                orderId,
                                new BigDecimal("2.00"),
                                Instant.parse("2026-11-03T10:00:00Z")
                        )
                )
        );
    }
}
