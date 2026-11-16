package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;

import java.math.BigDecimal;
import java.util.Objects;

final class OrderState {
    private final LimitOrder order;
    private BigDecimal remainingQuantity;
    private boolean resting;

    OrderState(LimitOrder order) {
        this.order = Objects.requireNonNull(order);
        this.remainingQuantity = order.quantity();
    }

    public LimitOrder order() {
        return order;
    }

    public BigDecimal remainingQuantity() {
        return remainingQuantity;
    }

    public boolean isResting() {
        return resting;
    }

    void markResting() {
        if (remainingQuantity.signum() <= 0) {
            throw new IllegalStateException("Cannot rest a fully filled order");
        }

        if (resting) {
            throw new IllegalStateException("Order is already resting");
        }

        this.resting = true;
    }

    void applyExecution(BigDecimal quantity) {

        validateExecution(quantity);

        remainingQuantity = remainingQuantity.subtract(quantity);
    }

    boolean isFilled() {
        return remainingQuantity.signum() == 0;
    }

    void markNotResting() {
        if (!resting) {
            throw new IllegalStateException("Order is not resting");
        }

        this.resting = false;
    }

    void validateExecution(BigDecimal quantity) {
        Objects.requireNonNull(quantity);

        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Execution quantity must be positive"
            );
        }

        if (quantity.compareTo(remainingQuantity) > 0) {
            throw new IllegalStateException(
                    "Execution exceeds remaining order quantity"
            );
        }
    }
}
