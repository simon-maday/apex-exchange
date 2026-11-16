package com.maday.apex.engine;

import com.maday.apex.domain.Trade;

import java.util.*;

final class TradeHistory {
    
    private final List<Trade> trades = new ArrayList<>();
    private final Set<UUID> tradeIds = new HashSet<>();

    void add(Trade trade) {
        if (!tradeIds.add(trade.id())) {
            throw new IllegalStateException(
                    "Duplicate trade ID: " + trade.id()
            );
        }

        trades.add(trade);
    }

    List<Trade> all() {
        return List.copyOf(trades);
    }

    int size() {
        return trades.size();
    }
}
