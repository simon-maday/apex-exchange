package com.maday.apex.domain.event;

public final class EventSequenceValidator {
    private long expectedSequence;

    public EventSequenceValidator(long firstSequence) {
        if (firstSequence <= 0) {
            throw new IllegalArgumentException(
                    "First sequence must be positive"
            );
        }

        this.expectedSequence = firstSequence;
    }

    public void validate(ExchangeEvent event) {
        if(event.sequence() != expectedSequence) {
            throw new IllegalStateException(
                    "Invalid event sequence. Expected: "
                            + expectedSequence
                            + ", actual: "
                            + event.sequence()
            );
        }

        expectedSequence++;
    }

    public long nextExpectedSequence() {
        return expectedSequence;
    }
}
