#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/rizoma-cli/target/rizoma-cli-0.3.0-SNAPSHOT-all.jar"
output_dir="$project_dir/target/quickstart"

if [ ! -f "$jar" ]; then
  printf '%s\n' "CLI jar not found; run ./mvnw package first" >&2
  exit 2
fi
mkdir -p "$output_dir"

java -jar "$jar" analyze "$project_dir/examples/clientes.csv" \
  --schema "$project_dir/examples/customer.schema.json" \
  --out "$output_dir/report.json"
java -jar "$jar" explain "$output_dir/report.json" --column "CPF Cliente"

java -jar "$jar" analyze "$project_dir/examples/dry-run-customers.csv" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --out "$output_dir/dry-analysis.json" --delimiter semicolon --header first
java -jar "$jar" plan "$output_dir/dry-analysis.json" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --out "$output_dir/mapping.json" \
  --map c0=customer.document --map c1=customer.email \
  --map c2=customer.phone --map c3=customer.postalCode \
  --map c4=customer.birthDate --map c5=customer.amount \
  --map c6=customer.active --map c7=customer.code
java -jar "$jar" dry-run "$project_dir/examples/dry-run-customers.csv" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --mapping "$output_dir/mapping.json" --out "$output_dir/dry-run.json" \
  --delimiter semicolon --header first

python3 - "$output_dir/report.json" "$output_dir/dry-run.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    analysis = json.load(stream)
with open(sys.argv[2], encoding="utf-8") as stream:
    dry_run = json.load(stream)
assert analysis["rowsProcessed"] == 20
assert dry_run["rowsProcessed"] == 3
assert dry_run["rowsValid"] == 2
assert dry_run["rowsInvalid"] == 1
print("quickstart: analysis=20 rows; dry-run=3 rows (2 valid, 1 invalid)")
PY
