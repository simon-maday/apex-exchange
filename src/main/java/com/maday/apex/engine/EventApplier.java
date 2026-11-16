package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;
import com.maday.apex.domain.Trade;
import com.maday.apex.domain.event.*;

import java.util.Objects;

final class EventApplier {

    private final OrderBook orderBook;
    private final TradeHistory tradeHistory;

    EventApplier(OrderBook orderBook, TradeHistory tradeHistory) {
        this.orderBook = Objects.requireNonNull(orderBook);
        this.tradeHistory = Objects.requireNonNull(tradeHistory);
    }

    void apply(ExchangeEvent event) {
        switch (event) {
            case OrderAccepted accepted -> orderBook.registerAccepted(toLimitOrder(accepted));
            case OrderRested rested -> orderBook.rest(rested.orderId(), rested.remainingQuantity(), rested.sequence());
            case TradeExecuted executed -> applyTrade(executed);
            case OrderCancelled cancelled ->  throw new UnsupportedOperationException(
                    "OrderCancelled will be implemented in the cancellation lab"
            );
        }
    }

    private void applyTrade(TradeExecuted event) {
        var buy = orderBook.requireOrder(event.buyOrderId());
        var sell = orderBook.requireOrder(event.sellOrderId());

        // Validate the relationship between the orders and the trade.
        if (buy.order().side() != Side.BUY ||
                sell.order().side() != Side.SELL) {
            throw new IllegalStateException(
                    "Trade references orders with incorrect sides"
            );
        }

        if (!buy.order().symbol().equals(event.symbol()) ||
                !sell.order().symbol().equals(event.symbol())) {
            throw new IllegalStateException(
                    "Trade symbol does not match both orders"
            );
        }

        // Validate both quantities before changing either order.
        orderBook.validateExecution(event.buyOrderId(), event.quantity());
        orderBook.validateExecution(event.sellOrderId(), event.quantity());

        // Apply the execution to both orders.
        orderBook.applyExecution(event.buyOrderId(), event.quantity());
        orderBook.applyExecution(event.sellOrderId(), event.quantity());

        // Reconstruct and record the historical trade.
        tradeHistory.add(toTrade(event));
    }


    private LimitOrder toLimitOrder(OrderAccepted event) {
        return new LimitOrder(
                event.orderId(),
                event.userId(),
                event.symbol(),
                event.side(),
                event.price(),
                event.quantity(),
                event.occurredAt()
        );
    }

    private Trade toTrade(TradeExecuted event) {
        return new Trade(
                event.tradeId(),
                event.buyOrderId(),
                event.sellOrderId(),
                event.symbol(),
                event.price(),
                event.quantity(),
                event.occurredAt()
        );
    }
}
