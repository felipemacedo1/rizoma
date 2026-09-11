package io.github.rizoma.core;

import java.util.Iterator;
import java.util.List;

/** One-pass dataset. Calling {@link #rows()} twice or after close must fail. */
public interface Dataset extends AutoCloseable {
    /** Returns the immutable detected structure. */
    DataReader.SourceStructure structure();
    /** Returns the only row iterator; later calls and calls after close fail. */
    Iterator<Row> rows();
    /** Returns bounded, source-safe warnings accumulated during iteration. */
    List<String> warnings();
    /** Closes source resources; implementations must be idempotent. */
    @Override void close();

    /** Source row with logical record and ending physical line positions. */
    record Row(long recordNumber, long physicalLine, List<String> values) {
        public Row { values = List.copyOf(values); }
    }
}
