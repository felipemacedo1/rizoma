#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar"
output_dir="$project_dir/target/quickstart"
knowledge="$output_dir/knowledge.jsonl"

if [ ! -f "$jar" ]; then
  printf '%s\n' "CLI jar not found; run ./mvnw package first" >&2
  exit 2
fi
mkdir -p "$output_dir"

java -jar "$jar" analyze "$project_dir/examples/clientes.csv" \
  --schema "$project_dir/examples/customer.schema.json" \
  --out "$output_dir/report.json"
java -jar "$jar" explain "$output_dir/report.json" --column "CPF Cliente"
java -jar "$jar" feedback confirm "$output_dir/report.json" \
  --schema "$project_dir/examples/customer.schema.json" \
  --knowledge "$knowledge" --column-id c1 --target customer.document \
  --feedback-id quickstart-document-confirmation --timestamp 2026-01-01T00:00:00Z
java -jar "$jar" analyze "$project_dir/examples/clientes.csv" \
  --schema "$project_dir/examples/customer.schema.json" \
  --knowledge "$knowledge" --out "$output_dir/report-with-history.json"
java -jar "$jar" explain "$output_dir/report-with-history.json" --column-id c1

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
java -jar "$jar" template create "$output_dir/dry-analysis.json" \
  --mapping "$output_dir/mapping.json" --name customer-import \
  --timestamp 2026-09-12T00:00:00Z --out "$output_dir/layout-template.json"
java -jar "$jar" recognize "$project_dir/examples/dry-run-customers-next.csv" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --template "$output_dir/layout-template.json" --out "$output_dir/layout-recognition.json" \
  --plan-out "$output_dir/reused-mapping.json" --delimiter semicolon --header first
java -jar "$jar" dry-run "$project_dir/examples/dry-run-customers.csv" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --mapping "$output_dir/mapping.json" --out "$output_dir/dry-run.json" \
  --delimiter semicolon --header first
java -jar "$jar" dry-run "$project_dir/examples/dry-run-customers-next.csv" \
  --schema "$project_dir/examples/dry-run-customer.schema.json" \
  --mapping "$output_dir/reused-mapping.json" --out "$output_dir/reused-dry-run.json" \
  --delimiter semicolon --header first

python3 - "$output_dir/report.json" "$output_dir/report-with-history.json" \
  "$output_dir/dry-run.json" "$knowledge" "$output_dir/layout-recognition.json" \
  "$output_dir/reused-mapping.json" "$output_dir/reused-dry-run.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    analysis = json.load(stream)
with open(sys.argv[2], encoding="utf-8") as stream:
    history = json.load(stream)
with open(sys.argv[3], encoding="utf-8") as stream:
    dry_run = json.load(stream)
with open(sys.argv[5], encoding="utf-8") as stream:
    recognition = json.load(stream)
with open(sys.argv[6], encoding="utf-8") as stream:
    reused_mapping = json.load(stream)
with open(sys.argv[7], encoding="utf-8") as stream:
    reused_dry_run = json.load(stream)
assert analysis["rowsProcessed"] == 20
assert history["historicalEvidenceByColumn"]["c1"][0]["confirmedCount"] == 1
assert dry_run["rowsProcessed"] == 3
assert dry_run["rowsValid"] == 2
assert dry_run["rowsInvalid"] == 1
assert recognition["route"] == "FAST_REUSE"
assert recognition["candidatePairsEvaluated"] == 0
assert recognition["similarityMetricsExecuted"] == 0
assert recognition["sourceContentFingerprint"] == reused_mapping["sourceFingerprint"]
assert reused_mapping["sourceFingerprint"] != dry_run["sourceFingerprint"]
assert reused_dry_run["rowsProcessed"] == 3
assert reused_dry_run["rowsValid"] == 2
with open(sys.argv[4], encoding="utf-8") as stream:
    persisted = stream.read()
assert "529.982.247-25" not in persisted
print("quickstart: analysis=20; feedback=1; dry-run=3; known layout=FAST_REUSE; reused dry-run=3 rows/2 valid")
PY
