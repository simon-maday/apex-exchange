package com.maday.apex.persistence;

import com.maday.apex.domain.event.EventBatch;

import java.util.List;

public record BatchRecoveryResult(
        List<EventBatch> batches,
        long validPosition,
        boolean incompleteTail
) {
    public BatchRecoveryResult {
        batches = List.copyOf(batches);
    }
}
