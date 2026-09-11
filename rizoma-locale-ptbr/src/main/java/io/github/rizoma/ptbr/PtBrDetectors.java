package io.github.rizoma.ptbr;

import io.github.rizoma.core.SemanticDetector;
import java.util.List;

/** Factory for the semantic detectors implemented in the 0.1a pt-BR module. */
public final class PtBrDetectors {
    private PtBrDetectors() {}
    public static List<SemanticDetector> defaults() {
        return List.of(new CpfDetector(), new BrazilianPhoneDetector());
    }
}
