from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from .engine import ImportEngine
from .schema import ImportSchema


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="rizoma", description="Plan and run smart CSV imports")
    subparsers = parser.add_subparsers(dest="command", required=True)

    for command in ("plan", "run"):
        child = subparsers.add_parser(command)
        child.add_argument("source", type=Path)
        child.add_argument("--schema", required=True, type=Path)
        child.add_argument("--threshold", type=float, default=0.72)
        if command == "run":
            child.add_argument("--output", required=True, type=Path)
            child.add_argument("--report", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    engine = ImportEngine(ImportSchema.from_json(args.schema), threshold=args.threshold)

    if args.command == "plan":
        payload = engine.plan(args.source).as_dict()
    else:
        result = engine.run(args.source, args.output)
        payload = result.as_dict()
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
