package io.github.felipemacedo1.rizoma.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic Space-Saving top-K tracker that retains at most K raw keys internally. */
final class BoundedFrequencyTracker {
    private final int capacity;
    private final Map<String, Counter> counters = new HashMap<>();

    BoundedFrequencyTracker(int capacity) { this.capacity = capacity; }

    void add(String value) {
        Counter existing = counters.get(value);
        if (existing != null) {
            existing.count++;
            return;
        }
        if (counters.size() < capacity) {
            counters.put(value, new Counter(value, 1, 0));
            return;
        }
        Counter minimum = counters.values().stream().min(Comparator
                .comparingLong((Counter item) -> item.count)
                .thenComparing(item -> item.value)).orElseThrow();
        counters.remove(minimum.value);
        counters.put(value, new Counter(value, minimum.count + 1, minimum.count));
    }

    List<Entry> entries() {
        var result = new ArrayList<Entry>();
        counters.values().stream().sorted(Comparator
                .comparingLong((Counter item) -> item.count).reversed()
                .thenComparing(item -> item.value))
                .forEach(item -> result.add(new Entry(item.value, item.count, item.error)));
        return List.copyOf(result);
    }

    record Entry(String value, long count, long error) {}

    private static final class Counter {
        private final String value;
        private long count;
        private final long error;
        private Counter(String value, long count, long error) {
            this.value = value;
            this.count = count;
            this.error = error;
        }
    }
}
