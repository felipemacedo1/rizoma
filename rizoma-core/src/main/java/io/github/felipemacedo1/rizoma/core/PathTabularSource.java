package io.github.felipemacedo1.rizoma.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Read-only local file source with content identity. */
public final class PathTabularSource implements TabularSource {
    private final Path path;

    public PathTabularSource(Path path) { this.path = Objects.requireNonNull(path).toAbsolutePath().normalize(); }
    /** Returns the normalized local path for adapters that support safe random access. */
    public Path path() { return path; }
    @Override public String id() { return path.getFileName().toString(); }
    @Override public String fileName() { return path.getFileName().toString(); }
    @Override public long size() throws IOException { return Files.size(path); }
    @Override public InputStream openStream() throws IOException { return Files.newInputStream(path); }

    @Override public String sha256() throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = openStream()) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
