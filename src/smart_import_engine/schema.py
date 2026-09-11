from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True, slots=True)
class SchemaField:
    name: str
    aliases: tuple[str, ...] = ()
    required: bool = False


@dataclass(frozen=True, slots=True)
class ImportSchema:
    fields: tuple[SchemaField, ...]

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> ImportSchema:
        raw_fields = data.get("fields")
        if not isinstance(raw_fields, dict) or not raw_fields:
            raise ValueError("schema must contain a non-empty 'fields' object")

        fields: list[SchemaField] = []
        for name, options in raw_fields.items():
            if not isinstance(name, str) or not name.strip():
                raise ValueError("field names must be non-empty strings")
            if options is None:
                options = {}
            if not isinstance(options, dict):
                raise ValueError(f"field '{name}' options must be an object")
            aliases = options.get("aliases", [])
            if not isinstance(aliases, list) or not all(isinstance(item, str) for item in aliases):
                raise ValueError(f"field '{name}' aliases must be a list of strings")
            fields.append(
                SchemaField(
                    name=name.strip(),
                    aliases=tuple(alias.strip() for alias in aliases if alias.strip()),
                    required=bool(options.get("required", False)),
                )
            )
        return cls(fields=tuple(fields))

    @classmethod
    def from_json(cls, path: str | Path) -> ImportSchema:
        with Path(path).open(encoding="utf-8") as handle:
            return cls.from_dict(json.load(handle))
