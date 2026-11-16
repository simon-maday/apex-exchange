package com.maday.apex.persistence;

import com.maday.apex.domain.event.EventBatch;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

public class EventLogRecovery {
    private EventLogRecovery() {}

    public static RecoveryResult recover(Path path, long firstSequence) throws IOException {

        Objects.requireNonNull(path, "path cannot be null");

        if (firstSequence <= 0) {
            throw new IllegalArgumentException(
                    "First sequence must be positive"
            );
        }

        // An empty event log is a valid starting point.
        if (!Files.exists(path)) {
            return new RecoveryResult(
                    List.of(),
                    0,
                    firstSequence,
                    false
            );
        }

        // Step 1 : Read complete, physically valid batches
        var physical = EventLogReader.readBatches(path);

        // Step 2: Validate the entire logical history
        var validator = new BatchSequenceValidator(firstSequence);
        try {
            for (var batch: physical.batches())
                validator.validate(batch);
        } catch (RuntimeException e) {
            throw new IOException("Event log contains an invalid event sequence", e);
        }

        // Step 3: Only after validation, repair an incomplete tail
        if(physical.incompleteTail()) {
            try(var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.truncate(physical.validPosition());
                channel.force(true);
            }
        }

        return new RecoveryResult(physical.batches(),physical.validPosition(), validator.nextExpectedSequence(), physical.incompleteTail());
    }

    public record RecoveryResult(List<EventBatch> batches, long validPosition, long nextSequence,boolean tailRepaired) {
        public RecoveryResult {
            batches = List.copyOf(batches);
        }
    }
}
