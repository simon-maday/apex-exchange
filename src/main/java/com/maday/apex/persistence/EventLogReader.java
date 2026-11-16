package com.maday.apex.persistence;


import com.maday.apex.domain.event.EventBatch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class EventLogReader {

    private static final int HEADER_SIZE = 10;
    private static final int CHECKSUM_SIZE = 4;
    private static final int MAX_PAYLOAD_SIZE = 64 * 1024 * 1024;

    private EventLogReader() {}

    public static BatchRecoveryResult readBatches(Path path)
            throws IOException {

        List<EventBatch> batches = new ArrayList<>();
        long validPosition = 0;
        boolean incompleteTail = false;

        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ)) {

            long fileSize = channel.size();

            while (validPosition < fileSize) {

                // Read the fixed-size frame header.
                ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);

                if (!readFully(channel, header, validPosition)) {
                    incompleteTail = true;
                    break;
                }

                header.flip();
                header.order(ByteOrder.BIG_ENDIAN);

                int payloadLength = header.getInt(6);

                if (payloadLength < 0 ||
                        payloadLength > MAX_PAYLOAD_SIZE) {
                    throw new IOException(
                            "Invalid payload length at position "
                                    + validPosition
                    );
                }

                long frameLength =
                        (long) HEADER_SIZE + payloadLength + CHECKSUM_SIZE;

                // The frame has started but has not been fully written.
                if (fileSize - validPosition < frameLength) {
                    incompleteTail = true;
                    break;
                }

                if (frameLength > Integer.MAX_VALUE) {
                    throw new IOException("Frame is too large");
                }

                ByteBuffer frame = ByteBuffer.allocate((int) frameLength);

                if (!readFully(channel, frame, validPosition)) {
                    incompleteTail = true;
                    break;
                }

                EventBatch batch = EventBatchCodec.decode(frame.array());

                batches.add(batch);
                validPosition += frameLength;
            }
        }

        return new BatchRecoveryResult(
                batches,
                validPosition,
                incompleteTail
        );
    }

    private static boolean readFully(
            FileChannel channel,
            ByteBuffer buffer,
            long position
    ) throws IOException {

        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);

            if (read == -1) {
                return false;
            }

            if (read == 0) {
                Thread.onSpinWait();
                continue;
            }

            position += read;
        }

        return true;
    }
}