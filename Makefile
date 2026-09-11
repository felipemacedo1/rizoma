.PHONY: test check demo

test:
	PYTHONPATH=src python -m unittest discover -s tests -v

check: test
	python -m compileall -q src
	git diff --check

demo:
	PYTHONPATH=src python -m smart_import_engine.cli plan examples/customers.csv --schema examples/customer_schema.json
