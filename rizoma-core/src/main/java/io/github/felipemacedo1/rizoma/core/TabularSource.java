package io.github.felipemacedo1.rizoma.core;

import java.io.IOException;
import java.io.InputStream;

/** Reopenable, read-only byte source. Implementations must not mutate their origin. */
public interface TabularSource {
    String id();
    String fileName();
    long size() throws IOException;
    InputStream openStream() throws IOException;
    String sha256() throws IOException;
}
