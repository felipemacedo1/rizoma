package io.github.felipemacedo1.rizoma.api;

import io.github.felipemacedo1.rizoma.core.EngineLimits;
import io.github.felipemacedo1.rizoma.core.PathTabularSource;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Safe factories for reopenable Rizoma sources. */
public final class Sources {
    private Sources() {}

    /** Wraps a read-only local path without copying its content. */
    public static TabularSource from(Path path) {
        if (path == null) throw new InvalidSourceException("INVALID_SOURCE", "source path must not be null");
        return new PathTabularSource(path);
    }

    /**
     * Copies bytes once to make a reopenable immutable source. The default
     * engine byte limit is applied before the source is created.
     */
    public static TabularSource from(byte[] bytes, String fileName) {
        return from(bytes, fileName, EngineLimits.defaults().maxBytes());
    }

    /** Copies at most {@code maxBytes} into a reopenable immutable source. */
    public static TabularSource from(byte[] bytes, String fileName, long maxBytes) {
        if (bytes == null)
            throw new InvalidSourceException("INVALID_SOURCE", "source bytes must not be null");
        validateLimit(maxBytes);
        if (bytes.length > maxBytes)
            throw new InvalidSourceException("SOURCE_TOO_LARGE", "in-memory source exceeds configured byte limit");
        return new ByteArraySource(fileName, bytes);
    }

    /**
     * Materializes a one-shot stream into bounded memory so analysis can reopen
     * it for fingerprints and multiple passes. The caller retains ownership of
     * the stream and must close it. Prefer {@link #from(Path)} for large files.
     */
    public static TabularSource from(InputStream input, String fileName) {
        return from(input, fileName, EngineLimits.defaults().maxBytes());
    }

    /** Materializes a one-shot stream using an explicit hard byte limit. */
    public static TabularSource from(InputStream input, String fileName, long maxBytes) {
        if (input == null)
            throw new InvalidSourceException("INVALID_SOURCE", "source stream must not be null");
        validateLimit(maxBytes);
        try {
            var output = new ByteArrayOutputStream((int) Math.min(maxBytes, 64 * 1024));
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) continue;
                total += read;
                if (total > maxBytes)
                    throw new InvalidSourceException("SOURCE_TOO_LARGE",
                            "stream source exceeds configured byte limit");
                output.write(buffer, 0, read);
            }
            return new ByteArraySource(fileName, output.toByteArray());
        } catch (InvalidSourceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidSourceException("SOURCE_READ_FAILED", "stream source could not be read safely");
        }
    }

    private static void validateLimit(long maxBytes) {
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 8L)
            throw new InvalidSourceException("INVALID_SOURCE_LIMIT",
                    "maxBytes must fit a bounded in-memory source");
    }

    private static final class ByteArraySource implements TabularSource {
        private final String fileName;
        private final byte[] bytes;
        private final String fingerprint;

        private ByteArraySource(String fileName, byte[] bytes) {
            this.fileName = safeFileName(fileName);
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.fingerprint = Sources.sha256(this.bytes);
        }
        @Override public String id() { return fileName; }
        @Override public String fileName() { return fileName; }
        @Override public long size() { return bytes.length; }
        @Override public InputStream openStream() { return new ByteArrayInputStream(bytes); }
        @Override public String sha256() { return fingerprint; }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new InvalidSourceException("INVALID_SOURCE_NAME", "source file name is invalid");
        return value;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
