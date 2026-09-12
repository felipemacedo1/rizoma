#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar="$project_dir/rizoma-cli/target/rizoma-cli-0.2.0-SNAPSHOT-all.jar"
schema="$project_dir/examples/customer.schema.json"

if [ ! -f "$jar" ]; then
  printf '%s\n' "CLI jar not found; run ./mvnw package first" >&2
  exit 2
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/rizoma-volume.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT HUP INT TERM
csv="$work_dir/million.csv"
report="$work_dir/report.json"
metrics="$work_dir/time.txt"
gc_log="$work_dir/gc.log"

printf '%s\n' 'Código Cliente;E-mail;Nascimento' > "$csv"
awk 'BEGIN { for (i=1; i<=1000000; i++) printf "%010d;cliente%07d@example.test;2020-01-%02d\n", i, i, 1 + (i % 28) }' >> "$csv"

java -version 2> "$work_dir/java-version.txt"
start=$(date +%s)
/usr/bin/time -v -o "$metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$gc_log:uptime,level,tags" \
  -jar "$jar" analyze "$csv" --schema "$schema" --out "$report" --delimiter semicolon --header first
end=$(date +%s)

python3 - "$report" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as stream:
    report = json.load(stream)
assert report["rowsProcessed"] == 1_000_000, report["rowsProcessed"]
assert len(report["profiles"]) == 3
assert report["candidatesByColumn"]
print("rowsProcessed=1000000 profiles=3 result=produced")
PY

printf 'duration_seconds=%s\n' "$((end-start))"
printf 'csv_bytes=%s\n' "$(wc -c < "$csv" | tr -d ' ')"
sed -n '1,3p' "$work_dir/java-version.txt"
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$metrics"
heap_observed=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$gc_log" | sort -n | tail -1)
printf 'observed_gc_heap_before_kib=%s\n' "${heap_observed:-unavailable}"
printf '%s\n' 'heap_limit=-Xmx256m; GC value is the largest observed pre-collection heap, not an exact peak; RSS is total process memory'
