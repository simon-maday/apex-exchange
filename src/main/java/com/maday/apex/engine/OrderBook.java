package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;

import java.math.BigDecimal;
import java.util.*;

public class OrderBook {

    private static final Comparator<BookOrder> BEST_BID =
            Comparator.comparing((BookOrder o) -> o.order().price())
                    .reversed()
                    .thenComparing(BookOrder::sequence);

    private static final Comparator<BookOrder> BEST_ASK =
            Comparator.comparing((BookOrder o) -> o.order().price())
                    .thenComparing(BookOrder::sequence);

    private final PriorityQueue<BookOrder> bids = new PriorityQueue<>(BEST_BID);
    private final PriorityQueue<BookOrder> asks = new PriorityQueue<>(BEST_ASK);

    private final Map<UUID, OrderState> orders = new HashMap<>();

    private final Map<UUID, BookOrder> restingOrders = new HashMap<>();

    void registerAccepted(LimitOrder order) {
        var id = order.id();

        if (orders.containsKey(id))
            throw new IllegalStateException(
                    "Order already exists: " + id
            );

        orders.put(id, new OrderState(order));
    }

    void rest(UUID orderId, BigDecimal remainingQuantity, long sequence) {

        var state = requireOrder(orderId);

        if (state.isResting())
            throw new IllegalStateException(
                    "Order is already resting: " + orderId
            );

        if (remainingQuantity.compareTo(state.remainingQuantity()) != 0) {
            throw new IllegalStateException(
                    "Resting quantity does not match order state"
            );
        }

        if (remainingQuantity.signum() <= 0) {
            throw new IllegalStateException(
                    "Cannot rest an empty order"
            );
        }

        state.markResting();

        var bookOrder = new BookOrder(state, sequence);

        if (state.order().side() == Side.BUY)
            bids.add(bookOrder);
        else
            asks.add(bookOrder);

        restingOrders.put(orderId, bookOrder);

    }

    void applyExecution(UUID orderId, BigDecimal quantity) {
        var state = requireOrder(orderId);

        state.applyExecution(quantity);

        if (state.isFilled() && state.isResting())
            removeFromBook(orderId);
    }

    private void removeFromBook(UUID orderId) {
        var bookOrder = restingOrders.remove(orderId);

        if (bookOrder == null) {
            throw new IllegalStateException(
                    "Order is not in the resting book: " + orderId
            );
        }

        if (bookOrder.order().side() == Side.BUY) {
            bids.remove(bookOrder);
        } else {
            asks.remove(bookOrder);
        }

        bookOrder.state().markNotResting();
    }

    List<BookOrder> matchingCandidates(Side incomingSide) {
        var candidates = incomingSide == Side.BUY ? new PriorityQueue<>(asks) : new PriorityQueue<>(bids);

        var result = new ArrayList<BookOrder>();

        while (!candidates.isEmpty())
            result.add(candidates.poll());

        return result;
    }


    OrderState requireOrder(UUID orderId) {
        var state = orders.get(orderId);

        if (state == null)
            throw new IllegalStateException("Unknown order: " + orderId);

        return state;
    }

    void validateExecution(UUID orderId, BigDecimal quantity) {
        requireOrder(orderId).validateExecution(quantity);
    }

    public synchronized int openOrderCount() {
        return bids.size() + asks.size();
    }

    public int restingOrderCount() {
        return restingOrders.size();
    }

}