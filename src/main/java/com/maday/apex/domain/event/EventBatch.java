package com.maday.apex.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EventBatch(UUID batchId, List<ExchangeEvent> events) {

    public EventBatch {
        Objects.requireNonNull(batchId);
        Objects.requireNonNull(events);

        events = List.copyOf(events);

        if (events.isEmpty()) {
            throw new IllegalArgumentException(
                    "An event batch cannot be empty"
            );
        }

        long expected = events.getFirst().sequence();

        if (expected <= 0) {
            throw new IllegalArgumentException("Event sequence must be positive");
        }

        for (var event : events) {
            Objects.requireNonNull(event);

            if(event.sequence() != expected)
                throw new IllegalArgumentException(
                        "Non-contiguous event sequence. Expected: "
                                + expected + ", actual: " + event.sequence()
                );

            expected = Math.incrementExact(expected);
        }
    }

    public long firstSequence() {
        return events.getFirst().sequence();
    }

    public long lastSequence() {
        return events.getLast().sequence();
    }
}
