package com.maday.apex.persistence;

import com.maday.apex.domain.Side;
import com.maday.apex.domain.event.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.CRC32;

public class EventBatchCodec {

    private static final int MAGIC = 0x41504558; // APEX
    public static final short VERSION = 1;

    private EventBatchCodec() {
    }

    public static byte[] encode(EventBatch batch) throws IOException {
        var payloadBytes = new ByteArrayOutputStream();

        try (var out = new DataOutputStream(payloadBytes)) {
            writeUuid(out, batch.batchId());
            out.writeInt(batch.events().size());
            for (var event : batch.events())
                writeEvent(out, event);
        }

        var payload = payloadBytes.toByteArray();

        var crc = new CRC32();
        crc.update(payload);

        var frame = ByteBuffer.allocate(
                Integer.BYTES +
                        Short.BYTES +
                        Integer.BYTES +
                        payload.length +
                        Integer.BYTES
        ).order(ByteOrder.BIG_ENDIAN);

        frame.putInt(MAGIC);
        frame.putShort(VERSION);
        frame.putInt(payload.length);
        frame.put(payload);
        frame.putInt((int) crc.getValue());

        return frame.array();
    }

    public static EventBatch decode(byte[] frame) throws IOException {
        if (frame.length < 14) {
            throw new IOException("Incomplete batch frame");
        }

        var buffer = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);

        var magic = buffer.getInt();

        if (magic != MAGIC) {
            throw new IOException("Invalid event-log magic number");
        }

        var version = buffer.getShort();

        if (version != VERSION) {
            throw new IOException("Unsupported format version: " + version);
        }

        int payloadLength = buffer.getInt();

        if (payloadLength < 0 ||
                payloadLength > 64 * 1024 * 1024) {
            throw new IOException("Invalid payload length");
        }

        if (buffer.remaining() != payloadLength + Integer.BYTES) {
            throw new IOException("Invalid frame length");
        }

        var payload = new byte[payloadLength];
        buffer.get(payload);

        int expectedChecksum = buffer.getInt();
        var crc = new CRC32();
        crc.update(payload);

        if ((int) crc.getValue() != expectedChecksum) {
            throw new IOException("Event batch checksum mismatch");
        }

        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            var batchId = readUuid(in);

            int eventCount = in.readInt();

            if (eventCount <= 0 || eventCount > 1_000_000) {
                throw new IOException("Invalid event count");
            }

            var events = new ArrayList<ExchangeEvent>();

            for (int i = 0; i < eventCount; i++) {
                events.add(readEvent(in));
            }

            if (in.available() != 0) {
                throw new IOException("Unexpected trailing payload data");
            }

            try {
                return new EventBatch(batchId, events);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid event batch", e);
            }
        }


    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static ExchangeEvent readEvent(DataInputStream in) throws IOException {
        var type = in.readUnsignedByte();
        var sequence = in.readLong();
        var occurredAt = readInstant(in);

        return switch (type) {
            case 1 -> new OrderAccepted(
                    sequence,
                    readUuid(in),
                    readString(in),
                    readString(in),
                    readSide(in),
                    readBigDecimal(in),
                    readBigDecimal(in),
                    occurredAt
            );
            case 2 -> new OrderRested(
                    sequence,
                    readUuid(in),
                    readBigDecimal(in),
                    occurredAt
            );
            case 3 -> new TradeExecuted(
                    sequence,
                    readUuid(in),
                    readUuid(in),
                    readUuid(in),
                    readString(in),
                    readBigDecimal(in),
                    readBigDecimal(in),
                    occurredAt
            );
            case 4 -> new OrderCancelled(
                    sequence,
                    readUuid(in),
                    readBigDecimal(in),
                    occurredAt
            );

            default -> throw new IOException(
                    "Unknown event type: " + type
            );
        };
    }

    private static BigDecimal readBigDecimal(DataInputStream in) throws IOException {
        try {
            return new BigDecimal(readString(in));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid decimal value.", e);
        }
    }

    private static Side readSide(DataInputStream in) throws IOException {
        var code = in.readUnsignedByte();

        return switch (code) {
            case 0 -> Side.BUY;
            case 1 -> Side.SELL;
            default -> throw new IOException("Invalid side code: " + code);
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();

        if (length < 0 || length > 1_000_000) {
            throw new IOException("Invalid string length: " + length);
        }

        byte[] bytes = in.readNBytes(length);

        if (bytes.length != length) {
            throw new EOFException("Incomplete string");
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Instant readInstant(DataInputStream in) throws IOException {
        long seconds = in.readLong();
        int nanos = in.readInt();
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (RuntimeException e) {
            throw new IOException("Invalid event timestamp", e);
        }
    }


    private static void writeEvent(DataOutputStream out, ExchangeEvent event) throws IOException {

        if (event instanceof OrderAccepted e) {
            out.writeByte(1);
            out.writeLong(e.sequence());
            writeInstant(out, e.occurredAt());
            writeUuid(out, e.orderId());
            writeString(out, e.userId());
            writeString(out, e.symbol());
            out.writeByte(e.side().ordinal());
            writeDecimal(out, e.price());
            writeDecimal(out, e.quantity());
        } else if (event instanceof OrderRested e) {
            out.writeByte(2);
            out.writeLong(e.sequence());
            writeInstant(out, e.occurredAt());
            writeUuid(out, e.orderId());
            writeDecimal(out, e.remainingQuantity());

        } else if (event instanceof TradeExecuted e) {
            out.writeByte(3);
            out.writeLong(e.sequence());
            writeInstant(out, e.occurredAt());
            writeUuid(out, e.tradeId());
            writeUuid(out, e.buyOrderId());
            writeUuid(out, e.sellOrderId());
            writeString(out, e.symbol());
            writeDecimal(out, e.price());
            writeDecimal(out, e.quantity());

        } else if (event instanceof OrderCancelled e) {
            out.writeByte(4);
            out.writeLong(e.sequence());
            writeInstant(out, e.occurredAt());
            writeUuid(out, e.orderId());
            writeDecimal(out, e.cancelledQuantity());

        } else {
            throw new IOException(
                    "Unsupported event type: " + event.getClass()
            );
        }

    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeDecimal(DataOutputStream out, BigDecimal value) throws IOException {
        writeString(out, value.toPlainString());
    }

    private static void writeInstant(DataOutputStream out, Instant instant) throws IOException {
        out.writeLong(instant.getEpochSecond());
        out.writeInt(instant.getNano());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }
}
