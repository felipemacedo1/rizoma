#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
api_classes="$project_dir/rizoma-adoption-tests/target/classes"
cli_jar="$project_dir/rizoma-cli/target/rizoma-cli-0.6.0-SNAPSHOT-all.jar"

if [ ! -d "$api_classes" ] || [ ! -f "$cli_jar" ]; then
  printf '%s\n' "compiled classes not found; run ./mvnw verify first" >&2
  exit 2
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/rizoma-api-overhead.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT HUP INT TERM
csv="$work_dir/customers.csv"
metrics="$work_dir/process-time.txt"
gc_log="$work_dir/gc.log"

printf '%s\n' 'Codigo Cliente;E-mail;Nascimento' > "$csv"
awk 'BEGIN { for (i=1; i<=100000; i++) printf "%010d;cliente%07d@example.test;2020-01-%02d\n", i, i, 1 + (i % 28) }' >> "$csv"

/usr/bin/time -v -o "$metrics" java -Xms32m -Xmx256m \
  "-Xlog:gc+heap=debug:file=$gc_log:uptime,level,tags" \
  -cp "$api_classes:$cli_jar" io.github.felipemacedo1.rizoma.examples.ApiOverheadProbe "$csv"

printf 'csv_bytes=%s heap_limit=-Xmx256m\n' "$(wc -c < "$csv" | tr -d ' ')"
grep -E 'Maximum resident set size|Elapsed \(wall clock\) time|Exit status' "$metrics"
observed_heap=$(sed -n 's/.* used \([0-9][0-9]*\)K.*/\1/p' "$gc_log" | sort -n | tail -1)
printf 'observed_gc_heap_before_kib=%s\n' "${observed_heap:-unavailable}"
printf '%s\n' 'timings are end-to-end smoke observations, not JMH or latency gates'
