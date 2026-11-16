package com.maday.apex.domain.event;

import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EventBatchTest {
    static void main() {
        EventBatch batch = new EventBatch(
                UUID.randomUUID(),
                List.of(
                        new OrderAccepted(
                                20,
                                UUID.randomUUID(),
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2"),
                                Instant.now()
                        ),
                        new OrderAccepted(
                                21,
                                UUID.randomUUID(),
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2"),
                                Instant.now()
                        ),
                        new OrderAccepted(
                                22,
                                UUID.randomUUID(),
                                "user-1",
                                "BTC-USDT",
                                Side.BUY,
                                new BigDecimal("50000"),
                                new BigDecimal("2"),
                                Instant.now()
                        )
                )
        );
    }
}
