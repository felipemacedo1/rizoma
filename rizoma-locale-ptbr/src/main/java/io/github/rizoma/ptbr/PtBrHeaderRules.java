package io.github.rizoma.ptbr;

import io.github.rizoma.core.HeaderNormalizer;
import java.util.List;
import java.util.Map;

/** Initial, versioned Portuguese abbreviation rules used by header normalization. */
public final class PtBrHeaderRules {
    private PtBrHeaderRules() {}
    public static String version() { return "pt-BR-0.1a"; }
    public static Map<String, List<String>> expansions() {
        return Map.of("dt", List.of("data"), "nasc", List.of("nascimento"),
                "tel", List.of("telefone"), "cel", List.of("celular"),
                "end", List.of("endereco"), "cod", List.of("codigo"),
                "cli", List.of("cliente"));
    }
    public static HeaderNormalizer normalizer() { return new HeaderNormalizer(expansions()); }
}
