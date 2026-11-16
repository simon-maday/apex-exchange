package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;

import java.math.BigDecimal;

// Mutable execution state, private to the order book.
record BookOrder(OrderState state, long sequence) {

    LimitOrder order() {
        return state.order();
    }

    BigDecimal remainingQuantity() {
        return state.remainingQuantity();
    }

}