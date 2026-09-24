package org.minecraftsmp.dynamicshop.metrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransactionMetricsTest {
    @Test public void sustainedLoadKeepsOnlyThirtyBucketsAndCorrectTotals() throws Exception {
        AtomicLong clock = new AtomicLong();
        TransactionMetrics metrics = new TransactionMetrics(() -> true, clock::get);
        var field = TransactionMetrics.class.getDeclaredField("buckets");
        field.setAccessible(true);
        for (int minute = 0; minute < 1000; minute++) {
            clock.set(minute * 60_000L);
            for (int trade = 0; trade < 1000; trade++) {
                metrics.record(TransactionMetrics.Kind.SERVER_BUY, true, true);
            }
            assertTrue(((java.util.Deque<?>) field.get(metrics)).size() <= 30);
            int count = Math.min(minute + 1, 30) * 1000;
            assertEquals(new TransactionMetrics.Snapshot(count, count, 0, 0, count, count), metrics.snapshot());
        }
    }
    @Test public void countsRecordsByKindWithoutCountingPlayerShopsAsDynamic() {
        TransactionMetrics metrics = new TransactionMetrics(() -> true, () -> 0);
        metrics.record(TransactionMetrics.Kind.SERVER_BUY, true, true);
        metrics.record(TransactionMetrics.Kind.SERVER_SELL, true, false);
        metrics.record(TransactionMetrics.Kind.PLAYER_SHOP, true, true);
        assertEquals(new TransactionMetrics.Snapshot(3, 1, 1, 1, 2, 1), metrics.snapshot());
        assertEquals(metrics.snapshot(), metrics.snapshot()); // Sampling never drains another chart's data.
    }

    @Test public void expiresQuietServersAndKeepsTheLastThirtyMinuteBuckets() {
        AtomicLong clock = new AtomicLong();
        TransactionMetrics metrics = new TransactionMetrics(() -> true, clock::get);
        metrics.record(TransactionMetrics.Kind.SERVER_BUY, false, false);
        clock.set(60_000);
        metrics.record(TransactionMetrics.Kind.SERVER_SELL, true, true);
        clock.set(30 * 60_000);
        assertEquals(new TransactionMetrics.Snapshot(1, 0, 1, 0, 1, 1), metrics.snapshot());
        clock.set(31 * 60_000);
        assertEquals(0, metrics.snapshot().total());
    }

    @Test public void optOutDiscardsCountsAndDoesNotBackfillWhenEnabledAgain() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        TransactionMetrics metrics = new TransactionMetrics(enabled::get, () -> 0);
        metrics.record(TransactionMetrics.Kind.SERVER_BUY, true, true);
        enabled.set(false);
        assertEquals(0, metrics.snapshot().total());
        metrics.record(TransactionMetrics.Kind.SERVER_SELL, true, true);
        enabled.set(true);
        assertEquals(0, metrics.snapshot().total());
        metrics.record(TransactionMetrics.Kind.PLAYER_SHOP, false, false);
        assertEquals(1, metrics.snapshot().total());
    }

    @Test public void volumeBandsIncludeIdleServersAndBoundaryValues() {
        for (int count : new int[] {0, 1, 9, 10, 99, 100, 999, 1000}) {
            String expected = count == 0 ? "0" : count < 10 ? "1-9" : count < 100 ? "10-99" : count < 1000 ? "100-999" : "1000+";
            assertEquals(expected, new TransactionMetrics.Snapshot(count, 0, 0, 0, 0, 0).volumeBand());
        }
    }

    @Test public void clockRollbackClearsFutureCounts() {
        AtomicLong clock = new AtomicLong(600_000);
        TransactionMetrics metrics = new TransactionMetrics(() -> true, clock::get);
        metrics.record(TransactionMetrics.Kind.SERVER_BUY, false, false);
        clock.set(0);
        assertEquals(0, metrics.snapshot().total());
    }
}
