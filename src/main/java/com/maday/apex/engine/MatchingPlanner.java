package com.maday.apex.engine;

import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;

import java.util.ArrayList;
import java.util.List;

final class MatchingPlanner {

    List<PlannedTrade> plan(LimitOrder incoming, OrderBook book) {
        var trades = new ArrayList<PlannedTrade>();

        var incomingRemaining = incoming.quantity();

        for (var resting : book.matchingCandidates(incoming.side())) {

            if (incomingRemaining.signum() == 0) break;

            var maker = resting.order();

            // Never match different trading instruments.
            if (!maker.symbol().equals(incoming.symbol())) continue;

            // Stop when the best available price no longer crosses.
            if (!crosses(incoming, maker)) break;

            var executionQuantity = incomingRemaining.min(resting.remainingQuantity());

            // The resting order is the maker: execute at its price.
            var executionPrice = maker.price();

            var buyOrderId = incoming.side() == Side.BUY
                    ? incoming.id()
                    : maker.id();

            var sellOrderId = incoming.side() == Side.SELL
                    ? incoming.id()
                    : maker.id();

            trades.add(new PlannedTrade(
                    buyOrderId,
                    sellOrderId,
                    incoming.symbol(),
                    executionPrice,
                    executionQuantity
            ));

            // Local calculation only. No OrderState mutation.
            incomingRemaining = incomingRemaining.subtract(executionQuantity);

        }

        return List.copyOf(trades);

    }

    private boolean crosses(LimitOrder incoming, LimitOrder maker) {
        var priceCompare = incoming.price().compareTo(maker.price());
        if(incoming.side() == Side.BUY)
            return priceCompare >= 0;
        return priceCompare <= 0;
    }
}
