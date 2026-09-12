package io.github.rizoma.cli;

import io.github.rizoma.core.InMemoryMappingKnowledgeBase;
import io.github.rizoma.core.MappingFeedback;
import io.github.rizoma.core.MappingKnowledgeBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, append-only JSON Lines knowledge adapter for the CLI. */
public final class JsonLinesMappingKnowledgeBase implements MappingKnowledgeBase {
    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;
    public static final int DEFAULT_MAX_EVENTS = 100_000;
    public static final int DEFAULT_MAX_LINE_BYTES = 16 * 1024;
    private final Path path;
    private final long maxBytes;
    private final int maxEvents;
    private final int maxLineBytes;

    public JsonLinesMappingKnowledgeBase(Path path) {
        this(path, DEFAULT_MAX_BYTES, DEFAULT_MAX_EVENTS, DEFAULT_MAX_LINE_BYTES);
    }

    public JsonLinesMappingKnowledgeBase(Path path, long maxBytes, int maxEvents, int maxLineBytes) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (maxBytes <= 0 || maxEvents <= 0 || maxLineBytes <= 0 || maxLineBytes > maxBytes)
            throw new IllegalArgumentException("invalid knowledge file limits");
        this.maxBytes = maxBytes;
        this.maxEvents = maxEvents;
        this.maxLineBytes = maxLineBytes;
    }

    @Override public KnowledgeSnapshot snapshot() {
        InMemoryMappingKnowledgeBase memory = new InMemoryMappingKnowledgeBase(maxEvents);
        readEvents().values().forEach(memory::record);
        return memory.snapshot();
    }

    @Override public synchronized void record(MappingFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        byte[] serialized;
        try {
            serialized = (JsonSupport.MAPPER.writeValueAsString(feedback) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("feedback cannot be serialized safely", exception);
        }
        if (serialized.length > maxLineBytes) throw new IllegalArgumentException("feedback exceeds line size limit");
        ensureSafeFile();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ,
                StandardOpenOption.WRITE); var ignored = channel.lock()) {
            Map<String, MappingFeedback> events = readEvents(channel);
            MappingFeedback existing = events.get(feedback.feedbackId());
            if (existing != null) {
                if (existing.equals(feedback)) return;
                throw new IllegalArgumentException("feedbackId already identifies a different event");
            }
            if (events.size() >= maxEvents) throw new IllegalStateException("knowledge event limit reached");
            long current = channel.size();
            if (current + serialized.length > maxBytes) throw new IllegalStateException("knowledge file size limit reached");
            channel.position(current);
            ByteBuffer buffer = ByteBuffer.wrap(serialized);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (IOException exception) {
            throw new IllegalStateException("knowledge file write failed safely", exception);
        }
    }

    private Map<String, MappingFeedback> readEvents() {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Map.of();
        validateExistingPath();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
                var ignored = channel.lock(0, Long.MAX_VALUE, true)) {
            return readEvents(channel);
        } catch (IOException exception) {
            throw new IllegalStateException("knowledge file read failed safely", exception);
        }
    }

    private Map<String, MappingFeedback> readEvents(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size > maxBytes) throw new IllegalStateException("knowledge file size limit exceeded");
        if (size > 0) {
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.position(size - 1);
            while (last.hasRemaining() && channel.read(last) >= 0) { /* one bounded byte */ }
            if (last.position() != 1 || last.array()[0] != '\n')
                throw corrupt(1, "truncated final JSON line", null);
        }
        channel.position(0);
        var events = new LinkedHashMap<String, MappingFeedback>();
        // The surrounding FileChannel owns the lifecycle; this reader must not close it.
        BufferedReader reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8));
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.getBytes(StandardCharsets.UTF_8).length + 1 > maxLineBytes)
                throw corrupt(lineNumber, "line size limit exceeded", null);
            if (line.isBlank()) throw corrupt(lineNumber, "blank JSON line", null);
            MappingFeedback feedback;
            try { feedback = JsonSupport.MAPPER.readValue(line, MappingFeedback.class); }
            catch (IOException | RuntimeException exception) {
                throw corrupt(lineNumber, "invalid feedback JSON", exception);
            }
            MappingFeedback previous = events.putIfAbsent(feedback.feedbackId(), feedback);
            if (previous != null && !previous.equals(feedback))
                throw corrupt(lineNumber, "conflicting duplicate feedbackId", null);
            if (events.size() > maxEvents) throw new IllegalStateException("knowledge event limit exceeded");
        }
        return Map.copyOf(events);
    }

    private void ensureSafeFile() {
        Path parent = path.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                validateExistingPath();
                return;
            }
            try {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException exception) {
                Files.createFile(path);
            }
        } catch (java.nio.file.FileAlreadyExistsException race) {
            validateExistingPath();
        } catch (IOException exception) {
            throw new IllegalStateException("knowledge file cannot be created safely", exception);
        }
    }

    private void validateExistingPath() {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("knowledge path must be a regular non-symlink file");
    }

    private static IllegalArgumentException corrupt(int line, String reason, Throwable cause) {
        return new IllegalArgumentException("knowledge file is corrupt at line " + line + ": " + reason, cause);
    }
}
