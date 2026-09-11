from __future__ import annotations

import csv
from pathlib import Path
from typing import Iterable


ENCODINGS = ("utf-8-sig", "utf-8", "latin-1")


def _read_text(path: Path) -> tuple[str, str]:
    raw = path.read_bytes()
    for encoding in ENCODINGS:
        try:
            return raw.decode(encoding), encoding
        except UnicodeDecodeError:
            continue
    raise ValueError(f"unsupported text encoding: {path}")


def read_csv(path: str | Path) -> tuple[list[str], list[dict[str, str]], dict[str, str]]:
    source = Path(path)
    text, encoding = _read_text(source)
    if not text.strip():
        raise ValueError("input CSV is empty")
    try:
        dialect = csv.Sniffer().sniff(text[:8192], delimiters=",;\t|")
        delimiter = dialect.delimiter
    except csv.Error:
        delimiter = ","

    reader = csv.DictReader(text.splitlines(), delimiter=delimiter)
    if not reader.fieldnames:
        raise ValueError("input CSV has no header")
    headers = [header.strip() for header in reader.fieldnames]
    rows = [
        {str(key).strip(): (value or "").strip() for key, value in row.items() if key is not None}
        for row in reader
    ]
    return headers, rows, {"encoding": encoding, "delimiter": delimiter}


def write_csv(path: str | Path, fieldnames: list[str], rows: Iterable[dict[str, str]]) -> None:
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
