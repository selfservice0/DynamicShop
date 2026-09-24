package org.minecraftsmp.dynamicshop.metrics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Anonymous, bounded minute buckets. No transaction objects or identifiers are retained. */
public final class TransactionMetrics {
    public enum Kind { SERVER_BUY, SERVER_SELL, PLAYER_SHOP }
    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private final Deque<Bucket> buckets = new ArrayDeque<>();

    private static final class Bucket {
        final long minute;
        int total, buys, sells, playerShops, dynamic, inflationEnabled;
        Bucket(long minute) { this.minute = minute; }
    }

    public record Snapshot(int total, int buys, int sells, int playerShops,
                           int dynamic, int inflationEnabled) {
        public String volumeBand() {
            if (total == 0) return "0";
            if (total < 10) return "1-9";
            if (total < 100) return "10-99";
            if (total < 1000) return "100-999";
            return "1000+";
        }
    }

    public TransactionMetrics(BooleanSupplier enabled) { this(enabled, System::currentTimeMillis); }
    TransactionMetrics(BooleanSupplier enabled, LongSupplier clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    public synchronized void record(Kind kind, boolean dynamic, boolean inflationEnabled) {
        if (!enabled.getAsBoolean()) { buckets.clear(); return; }
        long minute = clock.getAsLong() / 60_000;
        expire(minute);
        if (buckets.isEmpty() || buckets.getLast().minute != minute) buckets.addLast(new Bucket(minute));
        Bucket bucket = buckets.getLast();
        bucket.total = add(bucket.total, 1);
        switch (kind) {
            case SERVER_BUY -> bucket.buys = add(bucket.buys, 1);
            case SERVER_SELL -> bucket.sells = add(bucket.sells, 1);
            case PLAYER_SHOP -> bucket.playerShops = add(bucket.playerShops, 1);
        }
        if (kind != Kind.PLAYER_SHOP && dynamic) {
            bucket.dynamic = add(bucket.dynamic, 1);
            if (inflationEnabled) bucket.inflationEnabled = add(bucket.inflationEnabled, 1);
        }
    }

    public synchronized Snapshot snapshot() {
        if (!enabled.getAsBoolean()) buckets.clear();
        expire(clock.getAsLong() / 60_000);
        int total = 0, buys = 0, sells = 0, playerShops = 0, dynamic = 0, inflation = 0;
        for (Bucket b : buckets) {
            total = add(total, b.total); buys = add(buys, b.buys); sells = add(sells, b.sells);
            playerShops = add(playerShops, b.playerShops); dynamic = add(dynamic, b.dynamic);
            inflation = add(inflation, b.inflationEnabled);
        }
        return new Snapshot(total, buys, sells, playerShops, dynamic, inflation);
    }

    public synchronized void clear() { buckets.clear(); }

    private void expire(long minute) {
        // Reset on a clock rollback rather than retaining future buckets indefinitely.
        if (!buckets.isEmpty() && buckets.getLast().minute > minute) buckets.clear();
        while (!buckets.isEmpty() && buckets.getFirst().minute <= minute - 30) buckets.removeFirst();
    }

    private static int add(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) a + b); }
}
