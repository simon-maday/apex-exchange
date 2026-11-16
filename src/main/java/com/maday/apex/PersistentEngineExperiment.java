import com.maday.apex.domain.LimitOrder;
import com.maday.apex.domain.Side;
import com.maday.apex.engine.PersistentMatchingEngine;

void main() throws Exception {

    Path path = Path.of("apex-events.log");

    try (var engine = new PersistentMatchingEngine(path)) {
        engine.start();

        UUID id = UUID.randomUUID();

        var first = new LimitOrder(
                id, "alice", "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("0.5"),
                Instant.now()
        );

        var retry = new LimitOrder(
                id, "alice", "BTC-USDT",
                Side.BUY,
                new BigDecimal("60000"),
                new BigDecimal("0.5"),
                first.createdAt()
        );

        long firstSequence = engine.submit(first).get();
        long retrySequence = engine.submit(retry).get();

        System.out.println(firstSequence);
        System.out.println(retrySequence);
    }

}

