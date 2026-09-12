package io.github.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BoundedProfilingStructuresTest {
    @Test void hyperLogLogHasDocumentedErrorOrderAndIsDeterministic() {
        var first = new HyperLogLogSketch();
        var second = new HyperLogLogSketch();
        for (int i = 0; i < 100_000; i++) {
            String value = "synthetic-" + i;
            first.add(value);
            second.add(value);
        }
        assertEquals(first.estimate(), second.estimate());
        assertTrue(Math.abs(first.estimate() - 100_000) / 100_000.0 < .10,
                "estimate=" + first.estimate());
        assertEquals(.0325, HyperLogLogSketch.EXPECTED_RELATIVE_ERROR, .0001);
    }

    @Test void spaceSavingNeverExceedsCapacityAndRetainsHeavyHitter() {
        var tracker = new BoundedFrequencyTracker(3);
        for (int i = 0; i < 1_000; i++) {
            tracker.add("hot");
            tracker.add("value-" + i);
        }
        assertEquals(3, tracker.entries().size());
        var hot = tracker.entries().stream().filter(item -> item.value().equals("hot")).findFirst().orElseThrow();
        assertEquals(1_000, hot.count());
        assertEquals(0, hot.error());
    }
}
