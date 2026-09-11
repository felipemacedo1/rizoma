import unittest

from smart_import_engine.mapping import infer_mapping, normalize
from smart_import_engine.schema import ImportSchema


class MappingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.schema = ImportSchema.from_dict(
            {
                "fields": {
                    "customer_name": {"aliases": ["nome do cliente"], "required": True},
                    "email": {"aliases": ["e-mail"], "required": True},
                    "phone": {"aliases": ["telefone", "celular"]},
                }
            }
        )

    def test_normalize_accents_and_punctuation(self) -> None:
        self.assertEqual(normalize("  TELEFONE (Móvel)  "), "telefone movel")

    def test_exact_aliases_win(self) -> None:
        mapping = infer_mapping(["Nome do Cliente", "E-mail", "Celular"], self.schema)
        self.assertEqual([item.source for item in mapping], ["Nome do Cliente", "E-mail", "Celular"])
        self.assertTrue(all(item.confidence == 1 for item in mapping))

    def test_source_column_is_not_reused(self) -> None:
        schema = ImportSchema.from_dict(
            {"fields": {"primary_email": {"aliases": ["email"]}, "billing_email": {"aliases": ["email"]}}}
        )
        mapping = infer_mapping(["email"], schema)
        self.assertEqual(sum(item.source == "email" for item in mapping), 1)

    def test_unresolved_required_is_preserved(self) -> None:
        mapping = infer_mapping(["observacoes"], self.schema)
        unresolved = [item.target for item in mapping if item.required and item.source is None]
        self.assertEqual(unresolved, ["customer_name", "email"])


if __name__ == "__main__":
    unittest.main()
