package com.maday.apex.persistence;

import com.maday.apex.domain.event.EventBatch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;

public final class EventLog implements AutoCloseable {

    private final FileChannel channel;
    private boolean closed;
    private boolean failed;

    public EventLog(Path path) throws IOException {
        this.channel = FileChannel.open(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
    }

    public synchronized void appendBatch(EventBatch batch) throws IOException {
        if (closed)
            throw new IllegalStateException("Event log has been closed");

        if (failed)
            throw new IllegalStateException("Event log is in a failed state; recovery is required");

        try {
            var frame = EventBatchCodec.encode(batch);

            var buffer = ByteBuffer.wrap(frame);

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            channel.force(true);

        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }

    }

    @Override
    public synchronized void close() throws IOException {
       if(!closed) {
           closed = true;
           channel.close();
       }
    }
}
