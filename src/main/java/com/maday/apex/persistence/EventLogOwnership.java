package com.maday.apex.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class EventLogOwnership implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private boolean closed;

    private EventLogOwnership(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static EventLogOwnership acquire(Path path) throws IOException {
        Objects.requireNonNull(path);

        var absolutePath = path.toAbsolutePath().normalize();

        var lockPath = absolutePath.resolveSibling(absolutePath.getFileName() + ".lock");

        var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try {
            FileLock lock;

            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw new IOException("Event log is already owned: " + absolutePath, e);
            }

            if (lock == null)
                throw new IOException("Event log is already owned: " + absolutePath);

            return new EventLogOwnership(channel, lock);

        } catch (IOException | RuntimeException e) {

            try {
                channel.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }

            throw e;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed)
            return;

        closed = true;

        IOException failure = null;

        try {
            lock.release();
        } catch (IOException e) {
            failure = e;
        }

        try {
            channel.close();
        } catch (IOException e) {
            if (failure == null)
                failure = e;
            else
                failure.addSuppressed(failure);
        }

        if (failure != null)
            throw failure;

    }
}
