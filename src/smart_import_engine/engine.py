from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from .csv_io import read_csv, write_csv
from .mapping import ColumnMapping, infer_mapping
from .schema import ImportSchema


@dataclass(frozen=True, slots=True)
class ImportPlan:
    source: str
    row_count: int
    metadata: dict[str, str]
    mappings: tuple[ColumnMapping, ...]

    @property
    def unresolved_required(self) -> tuple[str, ...]:
        return tuple(item.target for item in self.mappings if item.required and item.source is None)

    def as_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "row_count": self.row_count,
            "metadata": self.metadata,
            "ready": not self.unresolved_required,
            "unresolved_required": list(self.unresolved_required),
            "mappings": [item.as_dict() for item in self.mappings],
        }


@dataclass(frozen=True, slots=True)
class ImportResult:
    output: str
    rows_written: int
    plan: ImportPlan

    def as_dict(self) -> dict[str, Any]:
        return {"output": self.output, "rows_written": self.rows_written, "plan": self.plan.as_dict()}


class ImportEngine:
    def __init__(self, schema: ImportSchema, threshold: float = 0.72) -> None:
        self.schema = schema
        self.threshold = threshold

    def plan(self, source: str | Path) -> ImportPlan:
        headers, rows, metadata = read_csv(source)
        mappings = infer_mapping(headers, self.schema, self.threshold)
        return ImportPlan(str(source), len(rows), metadata, mappings)

    def run(self, source: str | Path, output: str | Path) -> ImportResult:
        headers, rows, metadata = read_csv(source)
        mappings = infer_mapping(headers, self.schema, self.threshold)
        plan = ImportPlan(str(source), len(rows), metadata, mappings)
        if plan.unresolved_required:
            missing = ", ".join(plan.unresolved_required)
            raise ValueError(f"required fields could not be mapped: {missing}")

        normalized_rows = [
            {mapping.target: row.get(mapping.source, "") if mapping.source else "" for mapping in mappings}
            for row in rows
        ]
        write_csv(output, [field.name for field in self.schema.fields], normalized_rows)
        return ImportResult(str(output), len(normalized_rows), plan)
