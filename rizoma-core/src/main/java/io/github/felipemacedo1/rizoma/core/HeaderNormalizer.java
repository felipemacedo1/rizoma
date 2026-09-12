package io.github.felipemacedo1.rizoma.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/** Non-destructive, idempotent header normalization. */
public final class HeaderNormalizer {
    private final Map<String, List<String>> expansions;

    /** Creates a normalizer with token-to-token-list abbreviation expansions. */
    public HeaderNormalizer(Map<String, List<String>> expansions) {
        var copy = new LinkedHashMap<String, List<String>>();
        (expansions == null ? Map.<String, List<String>>of() : expansions)
                .forEach((key, value) -> copy.put(key.toLowerCase(Locale.ROOT), List.copyOf(value)));
        this.expansions = Map.copyOf(copy);
    }

    /** Returns comparable derived forms while retaining the original header. */
    public NormalizedHeader normalize(String original) {
        String value = original == null ? "" : Normalizer.normalize(original, Normalizer.Form.NFKC).strip();
        value = value.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])", " ")
                .toLowerCase(Locale.ROOT);
        value = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{Alnum}]+", " ").strip().replaceAll("\\s+", " ");
        var tokens = new ArrayList<String>();
        if (!value.isEmpty()) {
            for (String token : value.split(" ")) tokens.addAll(expansions.getOrDefault(token, List.of(token)));
        }
        return new NormalizedHeader(original == null ? "" : original, String.join(" ", tokens), List.copyOf(tokens),
                String.join("", tokens));
    }

    /** Stable identity of the normalization rules and configured expansions. */
    public String fingerprint() {
        var canonical = new StringBuilder("header-normalizer-1|");
        new TreeMap<>(expansions).forEach((key, value) -> canonical.append(key)
                .append('=').append(value).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Original and derived comparable representations. */
    public record NormalizedHeader(String original, String comparable, List<String> tokens, String compact) {}
}
