#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar"

if [ ! -f "$jar" ]; then
  printf '%s\n' "CLI jar not found; run ./mvnw package first" >&2
  exit 2
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/rizoma-volume.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT HUP INT TERM
csv="$work_dir/million.csv"
report="$work_dir/report.json"
plan="$work_dir/mapping.json"
template="$work_dir/layout-template.json"
fast_report="$work_dir/fast-recognition.json"
fast_plan="$work_dir/fast-mapping.json"
adaptive_report="$work_dir/adaptive-recognition.json"
adaptive_plan="$work_dir/adaptive-mapping.json"
dry_report="$work_dir/dry-run.json"
schema="$work_dir/schema.json"
analyze_metrics="$work_dir/analyze-time.txt"
analyze_gc="$work_dir/analyze-gc.log"
dry_metrics="$work_dir/dry-time.txt"
dry_gc="$work_dir/dry-gc.log"
fast_metrics="$work_dir/fast-time.txt"
fast_gc="$work_dir/fast-gc.log"
adaptive_metrics="$work_dir/adaptive-time.txt"
adaptive_gc="$work_dir/adaptive-gc.log"

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
java -Xms32m -Xmx256m -jar "$jar" template create "$report" --mapping "$plan" \
  --name volume-layout --timestamp 2026-09-12T00:00:00Z --out "$template"

# Change content without changing structure: the next source must have its own content fingerprint.
python3 - "$csv" <<'PY'
import sys
path = sys.argv[1]
with open(path, "r+b") as stream:
    header = stream.readline()
    position = stream.tell()
    row = stream.readline()
    changed = row.replace(b"cliente0000001", b"synthet0000001", 1)
    assert len(changed) == len(row) and changed != row
    stream.seek(position)
    stream.write(changed)
PY

fast_start=$(date +%s)
/usr/bin/time -v -o "$fast_metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$fast_gc:uptime,level,tags" \
  -jar "$jar" recognize "$csv" --schema "$schema" --template "$template" \
  --out "$fast_report" --plan-out "$fast_plan" --delimiter semicolon --header first
fast_end=$(date +%s)

# Localized header drift of equal byte length exercises bounded adaptive reanalysis.
python3 - "$csv" <<'PY'
import sys
path = sys.argv[1]
with open(path, "r+b") as stream:
    header = stream.readline()
    changed = header.replace("Código Cliente".encode(), "Código Clientx".encode(), 1)
    assert len(changed) == len(header) and changed != header
    stream.seek(0)
    stream.write(changed)
PY

adaptive_start=$(date +%s)
/usr/bin/time -v -o "$adaptive_metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$adaptive_gc:uptime,level,tags" \
  -jar "$jar" recognize "$csv" --schema "$schema" --template "$template" \
  --out "$adaptive_report" --plan-out "$adaptive_plan" --delimiter semicolon --header first
adaptive_end=$(date +%s)

dry_start=$(date +%s)
/usr/bin/time -v -o "$dry_metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$dry_gc:uptime,level,tags" \
  -jar "$jar" dry-run "$csv" --schema "$schema" --mapping "$adaptive_plan" --out "$dry_report" \
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

python3 - "$fast_report" "$fast_plan" "$adaptive_report" "$adaptive_plan" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    fast = json.load(stream)
with open(sys.argv[2], encoding="utf-8") as stream:
    fast_plan = json.load(stream)
with open(sys.argv[3], encoding="utf-8") as stream:
    adaptive = json.load(stream)
with open(sys.argv[4], encoding="utf-8") as stream:
    adaptive_plan = json.load(stream)
assert fast["route"] == "FAST_REUSE", fast["route"]
assert fast["rowsReadForRecognition"] == 64, fast["rowsReadForRecognition"]
assert fast["candidatePairsEvaluated"] == 0
assert fast["similarityMetricsExecuted"] == 0
assert fast["inferenceSkipped"] is True
assert fast_plan["sourceFingerprint"] != ""
assert adaptive["route"] == "ADAPTIVE_REANALYSIS", adaptive["route"]
assert adaptive["rowsReadForRecognition"] == 64, adaptive["rowsReadForRecognition"]
assert adaptive["candidatePairsEvaluated"] == 1
assert adaptive["similarityMetricsExecuted"] > 0
assert adaptive["fullProfilingExecuted"] is False
assert adaptive_plan["sourceFingerprint"] != fast_plan["sourceFingerprint"]
print("layoutRecognition=FAST_REUSE(0 pairs),ADAPTIVE_REANALYSIS(1 pair)")
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
printf 'fast_reuse_duration_seconds=%s\n' "$((fast_end-fast_start))"
printf 'adaptive_duration_seconds=%s\n' "$((adaptive_end-adaptive_start))"
printf 'dry_run_duration_seconds=%s\n' "$((dry_end-dry_start))"
printf 'csv_bytes=%s\n' "$(wc -c < "$csv" | tr -d ' ')"
sed -n '1,3p' "$work_dir/java-version.txt"
printf '%s\n' 'analyze process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$analyze_metrics"
analyze_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$analyze_gc" | sort -n | tail -1)
printf 'analyze_observed_gc_heap_before_kib=%s\n' "${analyze_heap:-unavailable}"
printf '%s\n' 'fast-reuse process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$fast_metrics"
fast_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$fast_gc" | sort -n | tail -1)
printf 'fast_reuse_observed_gc_heap_before_kib=%s\n' "${fast_heap:-unavailable}"
printf '%s\n' 'adaptive process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$adaptive_metrics"
adaptive_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$adaptive_gc" | sort -n | tail -1)
printf 'adaptive_observed_gc_heap_before_kib=%s\n' "${adaptive_heap:-unavailable}"
printf '%s\n' 'dry-run process:'
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$dry_metrics"
dry_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$dry_gc" | sort -n | tail -1)
printf 'dry_run_observed_gc_heap_before_kib=%s\n' "${dry_heap:-unavailable}"
printf '%s\n' 'heap_limit=-Xmx256m; GC value is the largest observed pre-collection heap, not an exact peak; RSS is total process memory'
