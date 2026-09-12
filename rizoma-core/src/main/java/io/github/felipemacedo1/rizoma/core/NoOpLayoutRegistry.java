package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Optional;

/** Backward-compatible empty layout registry. */
public final class NoOpLayoutRegistry implements LayoutRegistry {
    public static final NoOpLayoutRegistry INSTANCE = new NoOpLayoutRegistry();
    private NoOpLayoutRegistry() {}
    @Override public List<LayoutTemplate> findCandidates(LayoutQuery query) { return List.of(); }
    @Override public void register(LayoutTemplate template) {
        throw new UnsupportedOperationException("NoOp layout registry does not persist templates");
    }
    @Override public Optional<LayoutTemplate> get(String templateId, String templateVersion) {
        return Optional.empty();
    }
}
