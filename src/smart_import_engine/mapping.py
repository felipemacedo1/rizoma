from __future__ import annotations

import re
import unicodedata
from dataclasses import asdict, dataclass
from difflib import SequenceMatcher

from .schema import ImportSchema, SchemaField


def normalize(value: str) -> str:
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    return " ".join(re.findall(r"[a-z0-9]+", ascii_value.lower()))


@dataclass(frozen=True, slots=True)
class ColumnMapping:
    target: str
    source: str | None
    confidence: float
    reason: str
    required: bool

    def as_dict(self) -> dict[str, str | float | bool | None]:
        return asdict(self)


def _score(source: str, field: SchemaField) -> tuple[float, str]:
    source_norm = normalize(source)
    candidates = ((field.name, "field name"), *((alias, "alias") for alias in field.aliases))
    best_score = 0.0
    best_reason = "no reliable lexical match"

    for candidate, label in candidates:
        candidate_norm = normalize(candidate)
        if source_norm == candidate_norm:
            return 1.0, f"exact {label} match"
        ratio = SequenceMatcher(None, source_norm, candidate_norm).ratio()
        source_tokens = set(source_norm.split())
        candidate_tokens = set(candidate_norm.split())
        overlap = len(source_tokens & candidate_tokens) / max(len(source_tokens | candidate_tokens), 1)
        score = (ratio * 0.75) + (overlap * 0.25)
        if score > best_score:
            best_score = score
            best_reason = f"similar to {label} '{candidate}'"
    return round(best_score, 4), best_reason


def infer_mapping(
    headers: list[str], schema: ImportSchema, threshold: float = 0.72
) -> tuple[ColumnMapping, ...]:
    if not 0 <= threshold <= 1:
        raise ValueError("threshold must be between 0 and 1")

    candidates: list[tuple[float, int, int, str]] = []
    reasons: dict[tuple[int, int], str] = {}
    for field_index, field in enumerate(schema.fields):
        for header_index, header in enumerate(headers):
            score, reason = _score(header, field)
            candidates.append((score, field_index, header_index, header))
            reasons[(field_index, header_index)] = reason

    assigned_fields: dict[int, tuple[int, float, str]] = {}
    used_headers: set[int] = set()
    for score, field_index, header_index, header in sorted(
        candidates, key=lambda item: (-item[0], item[1], item[2])
    ):
        if score < threshold or field_index in assigned_fields or header_index in used_headers:
            continue
        assigned_fields[field_index] = (header_index, score, header)
        used_headers.add(header_index)

    mappings: list[ColumnMapping] = []
    for field_index, field in enumerate(schema.fields):
        assignment = assigned_fields.get(field_index)
        if assignment is None:
            mappings.append(ColumnMapping(field.name, None, 0.0, "unresolved", field.required))
            continue
        header_index, score, header = assignment
        mappings.append(
            ColumnMapping(
                target=field.name,
                source=header,
                confidence=score,
                reason=reasons[(field_index, header_index)],
                required=field.required,
            )
        )
    return tuple(mappings)
