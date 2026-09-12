#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/rizoma-cli/target/rizoma-cli-0.4.0-SNAPSHOT-all.jar"

if [ ! -f "$jar" ]; then
  printf '%s\n' "CLI jar not found; run ./mvnw package first" >&2
  exit 2
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/rizoma-volume.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT HUP INT TERM
csv="$work_dir/million.csv"
report="$work_dir/report.json"
plan="$work_dir/mapping.json"
dry_report="$work_dir/dry-run.json"
schema="$work_dir/schema.json"
analyze_metrics="$work_dir/analyze-time.txt"
analyze_gc="$work_dir/analyze-gc.log"
dry_metrics="$work_dir/dry-time.txt"
dry_gc="$work_dir/dry-gc.log"

printf '%s\n' 'Código Cliente;E-mail;Nascimento' > "$csv"
awk 'BEGIN { for (i=1; i<=1000000; i++) printf "%010d;cliente%07d@example.test;2020-01-%02d\n", i, i, 1 + (i % 28) }' >> "$csv"

printf '%s\n' '{"formatVersion":"1.0","schemaId":"volume","schemaVersion":"1","locale":"en-US","fields":[' \
  '{"id":"customer.code","displayName":"Código Cliente","physicalType":"TEXT","semanticTypes":[],"required":true},' \
  '{"id":"customer.email","displayName":"E-mail","physicalType":"TEXT","semanticTypes":["core:email"],"required":true},' \
  '{"id":"customer.birthDate","displayName":"Nascimento","physicalType":"DATE","semanticTypes":["core:date"],"required":true}' \
  ']}' > "$schema"

java -version 2> "$work_dir/java-version.txt"
start=$(date +%s)
/usr/bin/time -v -o "$analyze_metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$analyze_gc:uptime,level,tags" \
  -jar "$jar" analyze "$csv" --schema "$schema" --out "$report" --delimiter semicolon --header first
analyze_end=$(date +%s)

java -Xms32m -Xmx256m -jar "$jar" plan "$report" --schema "$schema" --out "$plan" \
  --map c0=customer.code --map c1=customer.email --map c2=customer.birthDate

dry_start=$(date +%s)
/usr/bin/time -v -o "$dry_metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$dry_gc:uptime,level,tags" \
  -jar "$jar" dry-run "$csv" --schema "$schema" --mapping "$plan" --out "$dry_report" \
  --delimiter semicolon --header first --max-errors 100 --max-issue-samples 10
dry_end=$(date +%s)

python3 - "$report" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    report = json.load(stream)
assert report["rowsProcessed"] == 1_000_000, report["rowsProcessed"]
assert len(report["profiles"]) == 3
assert report["candidatesByColumn"]
print("rowsProcessed=1000000 profiles=3 result=produced")
PY

python3 - "$dry_report" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    report = json.load(stream)
assert report["rowsProcessed"] == 1_000_000, report["rowsProcessed"]
assert report["rowsValid"] == 1_000_000, report["rowsValid"]
assert report["rowsInvalid"] == 0, report["rowsInvalid"]
assert report["transformationsApplied"] == 1_000_000, report["transformationsApplied"]
assert report["validationsExecuted"] == 4_000_000, report["validationsExecuted"]
print("dryRunRowsProcessed=1000000 valid=1000000 result=produced")
PY

printf 'analyze_duration_seconds=%s\n' "$((analyze_end-start))"
printf 'dry_run_duration_seconds=%s\n' "$((dry_end-dry_start))"
printf 'csv_bytes=%s\n' "$(wc -c < "$csv" | tr -d ' ')"
sed -n '1,3p' "$work_dir/java-version.txt"
printf '%s\n' 'analyze process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$analyze_metrics"
analyze_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$analyze_gc" | sort -n | tail -1)
printf 'analyze_observed_gc_heap_before_kib=%s\n' "${analyze_heap:-unavailable}"
printf '%s\n' 'dry-run process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$dry_metrics"
dry_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$dry_gc" | sort -n | tail -1)
printf 'dry_run_observed_gc_heap_before_kib=%s\n' "${dry_heap:-unavailable}"
printf '%s\n' 'heap_limit=-Xmx256m; GC value is the largest observed pre-collection heap, not an exact peak; RSS is total process memory'
