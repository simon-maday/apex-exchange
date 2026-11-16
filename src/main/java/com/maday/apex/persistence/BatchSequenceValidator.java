package com.maday.apex.persistence;

import com.maday.apex.domain.event.EventBatch;

public class BatchSequenceValidator {
    private long expectedSequence;

    public BatchSequenceValidator(long firstSequence) {
        if (firstSequence <= 0) {
            throw new IllegalArgumentException("firstSequence must be positive");
        }
        this.expectedSequence = firstSequence;
    }

    public void validate(EventBatch batch) {
        for (var event : batch.events()) {
            if (event.sequence() != this.expectedSequence) {
                throw new IllegalStateException("Invalid event sequence. Expected: "
                        + expectedSequence
                        + ", actual: " + event.sequence());
            }

            expectedSequence = Math.incrementExact(expectedSequence);
        }
    }

    public long nextExpectedSequence() {
        return this.expectedSequence;
    }

}
