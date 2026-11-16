package com.maday.apex;

import java.util.List;

public record RecoveryResult(
        List<byte[]> events,
        long validPosition
) {
}
