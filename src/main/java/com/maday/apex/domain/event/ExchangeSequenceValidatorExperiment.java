package com.maday.apex.domain.event;

import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ExchangeSequenceValidatorExperiment {
    static void main() {

        var validator = new EventSequenceValidator(100);

        var event100 = new OrderAccepted(
                100,
                UUID.randomUUID(),
                "alice",
                "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("1.0"),
                Instant.now()
        );

        var event101 = new OrderAccepted(
                101,
                UUID.randomUUID(),
                "alice",
                "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("1.0"),
                Instant.now()
        );

        var event102 = new OrderAccepted(
                102,
                UUID.randomUUID(),
                "alice",
                "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("1.0"),
                Instant.now()
        );

        validator.validate(event100);
        validator.validate(event101);
        validator.validate(event102);

        IO.println(validator.nextExpectedSequence());
    }
}
