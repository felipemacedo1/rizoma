import csv
import tempfile
import unittest
from pathlib import Path

from smart_import_engine.engine import ImportEngine
from smart_import_engine.schema import ImportSchema


class EngineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.schema = ImportSchema.from_dict(
            {
                "fields": {
                    "customer_name": {"aliases": ["nome"], "required": True},
                    "email": {"aliases": ["e-mail"], "required": True},
                }
            }
        )

    def test_plan_detects_semicolon_csv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.csv"
            source.write_text("Nome;E-mail\nAna;ana@example.com\n", encoding="utf-8")
            plan = ImportEngine(self.schema).plan(source)
            self.assertEqual(plan.row_count, 1)
            self.assertEqual(plan.metadata["delimiter"], ";")
            self.assertFalse(plan.unresolved_required)

    def test_run_writes_normalized_csv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.csv"
            output = Path(directory) / "output.csv"
            source.write_text("Nome,E-mail\n Ana , ana@example.com \n", encoding="utf-8")
            result = ImportEngine(self.schema).run(source, output)
            with output.open(encoding="utf-8") as handle:
                rows = list(csv.DictReader(handle))
            self.assertEqual(result.rows_written, 1)
            self.assertEqual(rows, [{"customer_name": "Ana", "email": "ana@example.com"}])

    def test_run_stops_before_write_when_required_field_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.csv"
            output = Path(directory) / "output.csv"
            source.write_text("Nome\nAna\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "email"):
                ImportEngine(self.schema).run(source, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
