package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderStateExperiment {
    static void main() {
        var order = new LimitOrder(
                UUID.randomUUID(),
                "alice",
                "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("2.0"),
                Instant.now()
        );

        var orderState =  new OrderState(order);

        orderState.applyExecution(new BigDecimal("0.5"));
        orderState.applyExecution(new BigDecimal("1.5"));
        IO.println(orderState.isFilled());
        //orderState.applyExecution(new BigDecimal("0.5"));
        orderState.applyExecution(new BigDecimal("-1.5"));
    }
}
