package io.github.felipemacedo1.rizoma.api;

import java.util.Map;

/** Optional synchronous observer for safe process lifecycle events. */
@FunctionalInterface
public interface ProcessObserver {
    /** Receives a metadata-only event. Implementations must not expect cell values. */
    void onEvent(ProcessEvent event);

    /** Safe event emitted by the high-level facade. */
    record ProcessEvent(Stage stage, String code, Map<String, String> metadata) {
        public ProcessEvent {
            if (stage == null) throw new IllegalArgumentException("stage must not be null");
            code = code == null ? "" : code;
            metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        }
    }

    /** Coarse lifecycle stage, deliberately independent from a logging framework. */
    enum Stage { STARTED, ROUTE_SELECTED, COMPLETED, FAILED }

    /** Observer that performs no action. */
    static ProcessObserver noop() { return event -> { }; }
}
