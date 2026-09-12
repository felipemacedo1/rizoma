package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Map;

/** Format adapter capable of detecting and opening one tabular source. */
public interface DataReader {
    /** Returns whether this adapter can read the source metadata. */
    boolean supports(TabularSource source);
    /** Detects structure using bounded, reopenable reads without consuming the later dataset. */
    SourceStructure detect(TabularSource source, AnalysisOptions options, EngineLimits limits);
    /** Opens a fresh one-pass dataset for the previously detected structure. */
    Dataset open(TabularSource source, SourceStructure structure,
                 AnalysisOptions options, EngineLimits limits);

    /** Detected structure and safe warnings. */
    record SourceStructure(String format, String charset, String delimiter,
                           boolean headerPresent, List<SourceColumn> columns,
                           List<String> warnings, Map<String, String> attributes) {
        public SourceStructure {
            columns = List.copyOf(columns);
            warnings = List.copyOf(warnings);
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        }

        /** Backward-compatible constructor for structures without format-specific attributes. */
        public SourceStructure(String format, String charset, String delimiter,
                               boolean headerPresent, List<SourceColumn> columns,
                               List<String> warnings) {
            this(format, charset, delimiter, headerPresent, columns, warnings, Map.of());
        }
    }

    /** Position-based column identity remains stable when headers repeat. */
    record SourceColumn(String id, int position, String header, String normalizedHeader) {}
}
