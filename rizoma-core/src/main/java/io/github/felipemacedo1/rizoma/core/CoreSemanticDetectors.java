package io.github.felipemacedo1.rizoma.core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.regex.Pattern;

/** Built-in locale-neutral semantic detectors used by 0.1a. */
public final class CoreSemanticDetectors {
    private CoreSemanticDetectors() {}

    public static SemanticDetector email() {
        Pattern shape = Pattern.compile("^[^\\s@]+@[^\\s@]+$");
        Pattern valid = Pattern.compile("^[^\\s@]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        return counter(new SemanticType("core:email"), shape, valid, true, "email syntax");
    }

    public static SemanticDetector date() { return new DateDetector(); }

    private static SemanticDetector counter(SemanticType type, Pattern shape, Pattern valid,
                                            boolean strong, String explanation) {
        return new SemanticDetector() {
            @Override public SemanticType type() { return type; }
            @Override public ValueEvidence inspect(String raw) {
                if (raw == null || raw.isBlank()) return ValueEvidence.unavailable();
                String value = raw.strip();
                return new ValueEvidence(true, shape.matcher(value).matches(),
                        valid.matcher(value).matches(), false);
            }
            @Override public Accumulator newAccumulator() {
                return new Accumulator() {
                    long observed, shaped, accepted;
                    @Override public void accept(String raw) {
                        if (raw == null || raw.isBlank()) return;
                        observed++;
                        String value = raw.strip();
                        if (shape.matcher(value).matches()) shaped++;
                        if (valid.matcher(value).matches()) accepted++;
                    }
                    @Override public SemanticEvidence finish(int minimum) {
                        return evidence(type, observed, shaped, accepted, 0, minimum, strong, explanation);
                    }
                };
            }
        };
    }

    private static final class DateDetector implements SemanticDetector {
        private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
        private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);
        @Override public SemanticType type() { return new SemanticType("core:date"); }
        @Override public ValueEvidence inspect(String raw) {
            if (raw == null || raw.isBlank()) return ValueEvidence.unavailable();
            String value = raw.strip();
            boolean shaped = value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}");
            boolean iso = parses(value, ISO), dmy = parses(value, DMY), mdy = parses(value, MDY);
            return new ValueEvidence(true, shaped, iso || dmy || mdy,
                    dmy && mdy && !value.substring(0, 2).equals(value.substring(3, 5)));
        }
        @Override public Accumulator newAccumulator() {
            return new Accumulator() {
                long observed, shaped, valid, ambiguous;
                @Override public void accept(String raw) {
                    if (raw == null || raw.isBlank()) return;
                    observed++; String value = raw.strip();
                    if (value.matches("\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}")) shaped++;
                    boolean iso = parses(value, ISO), dmy = parses(value, DMY), mdy = parses(value, MDY);
                    if (iso || dmy || mdy) valid++;
                    if (dmy && mdy && !value.substring(0, 2).equals(value.substring(3, 5))) ambiguous++;
                }
                @Override public SemanticEvidence finish(int minimum) {
                    return evidence(type(), observed, shaped, valid, ambiguous, minimum, false,
                            ambiguous > 0 ? "valid dates include day/month ambiguity" : "recognized date formats");
                }
            };
        }
        private static boolean parses(String value, DateTimeFormatter formatter) {
            try { LocalDate.parse(value, formatter); return true; }
            catch (DateTimeParseException ignored) { return false; }
        }
    }

    static SemanticDetector.SemanticEvidence evidence(SemanticType type, long observed, long shape,
            long valid, long ambiguous, int minimum, boolean strong, String explanation) {
        double shapeScore = observed == 0 ? 0 : (double) shape / observed;
        double validScore = observed == 0 ? 0 : (double) valid / observed;
        return new SemanticDetector.SemanticEvidence(type, observed, shape, valid, ambiguous,
                shapeScore, validScore, SemanticDetector.reliability(observed, ambiguous, minimum),
                strong && validScore >= .8
                        && SemanticDetector.reliability(observed, ambiguous, minimum) >= .5,
                explanation);
    }

    public static List<SemanticDetector> defaults() { return List.of(email(), date()); }
}
