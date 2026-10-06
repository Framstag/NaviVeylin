#!/usr/bin/env bash
# Gate timing report — change `speed-up-build-test-gate`, spec `build-test-gate`
# ("Every gate run records its phase timings").
#
# Usage:
#   bash tools/gate-timing-report.sh [record.json] [results-root]
# Defaults: build/gate-timings.json and .
#
# Reads the record written by tools/gate-timings.init.gradle.kts (per-task duration and
# outcome) plus the per-module JUnit XML (suite wall time, class count, test count,
# failures), and prints the phase breakdown the gate procedure quotes.
#
# Exit codes: 0 = a record with executed tasks was reported; 1 = missing or empty record,
# or no executed task in it — a record with no executed task is evidence of nothing.
set -uo pipefail

RECORD="${1:-build/gate-timings.json}"
RESULTS_ROOT="${2:-.}"

fail() { echo "gate-timing-report: $*" >&2; exit 1; }

[ -f "$RECORD" ] || fail "no timing record at '$RECORD' — run the gate with -I tools/gate-timings.init.gradle.kts"
command -v jq >/dev/null 2>&1 || fail "jq not found on PATH"

jq -e . "$RECORD" >/dev/null 2>&1 || fail "cannot parse '$RECORD' as JSON"
executed=$(jq -r '[.tasks[] | select(.outcome == "executed")] | length' "$RECORD")
[ "$executed" -eq 0 ] && fail "no executed task in '$RECORD' — the run proves nothing, so there is nothing to report"

fmt_ms() { awk -v ms="$1" 'BEGIN { if (ms >= 60000) printf "%dm%02ds", int(ms/60000), int((ms%60000)/1000); else printf "%.1fs", ms/1000 }'; }
fmt_s()  { awk -v s="$1"  'BEGIN { if (s >= 60) printf "%dm%02ds", int(s/60), int(s)%60; else printf "%.1fs", s }'; }

total_ms=$(jq -r '.durationMs' "$RECORD")
task_failures=$(jq -r '.taskFailures // 0' "$RECORD")
if [ "$task_failures" -eq 0 ]; then verdict="no failed task"; else verdict="$task_failures task(s) failed"; fi

# A run's own verdict is the log's, not this record's: quote the log's BUILD SUCCESSFUL/FAILED
# next to it. The record can only report the tasks it saw fail.
echo "Gate timing record: $RECORD"
echo "Run:     $(jq -r '.startedAt' "$RECORD") -> $(jq -r '.finishedAt' "$RECORD")   wall $(fmt_ms "$total_ms")   verdict: $verdict"
echo "Requested: $(jq -r '(.invocation // []) | join(" ")' "$RECORD")"
echo

echo "Tasks by outcome (record-level; Gradle's own 'actionable tasks' line counts lifecycle tasks differently)"
jq -r '.tasks | group_by(.outcome) | map({outcome: .[0].outcome, count: length}) | sort_by(-.count)[] | "  \(.count)\t\(.outcome)"' "$RECORD"

sum_ms=$(jq -r '[.tasks[] | select(.outcome == "executed") | .durationMs] | add // 0' "$RECORD")
overhead=$(( total_ms - sum_ms ))
echo "  executed task durations sum: $(fmt_ms "$sum_ms") of $(fmt_ms "$total_ms") wall"
if [ "$overhead" -lt 0 ]; then
    echo "  unaccounted: tasks ran concurrently, their durations sum to more than the wall time"
    overhead=0
fi
echo

classify() { # emits "<durationMs>\t<bucket>" per executed task
    jq -r '.tasks[] | select(.outcome == "executed") | "\(.durationMs)\t\(.path)"' "$RECORD" \
        | while IFS=$'\t' read -r ms path; do
            task="${path##*:}"
            case "$path" in
                :buildSrc:*|*:buildSrc:*)
                    # Gradle's own build for buildSrc: its tasks share the operation stream but are
                    # not part of the gate's task graph, so they must not dilute the phase sums.
                    printf '%s\tgradle own build (buildSrc)\n' "$ms" ;;
                *)
                    case "$task" in
                        test*) printf '%s\ttests\n' "$ms" ;;
                        *) case "$path" in
                               *ompile*|*Kotlin*|*kotlin*|*avac*|*dex*|*Dex*|*ackage*|*erge*|*esources*|*ssets*|*trip*|*CMake*|*cmake*|*ninja*|*Ksp*|*kapt*|*Bundle*|*undle*|*Lint*|*lint*|*Sbom*|*licen*|*Licen*)
                                   printf '%s\tcompile & package\n' "$ms" ;;
                               *) printf '%s\tother executed\n' "$ms" ;;
                           esac ;;
                    esac ;;
            esac
          done
}

echo "Phase breakdown (sum of executed task durations)"
classify | awk -F'\t' '{ sum[$2] += $1; cnt[$2]++ } END { for (b in sum) printf "%s\t%s\t%d\n", sum[b], b, cnt[b] }' \
    | sort -rn \
    | while IFS=$'\t' read -r ms bucket count; do printf "  %-22s %-8s  %s tasks\n" "$bucket" "$(fmt_ms "$ms")" "$count"; done
printf "  %-22s %-8s\n" "configuration & overhead" "$(fmt_ms "$overhead")"
echo

suite_summary() { # label  module-dir (may be empty for the root project)  task-name
    local label="$1" module="$2" task="$3" dir
    if [ -z "$module" ]; then dir="$RESULTS_ROOT/build/test-results/$task"; else dir="$RESULTS_ROOT/$module/build/test-results/$task"; fi
    if ! ls "$dir"/TEST-*.xml >/dev/null 2>&1; then
        printf "  %-40s no result XML under %s\n" "$label" "$dir"
        return
    fi
    awk -v label="$label" '
        # mktime has second granularity, so the ISO fractional part is added back by hand:
        # a suite wall time that drops it is off by up to a second.
        function epoch(iso,   d, hms, frac) {
            split(substr(iso, 1, 10), d, "-")
            split(substr(iso, 12, 8), hms, ":")
            frac = (substr(iso, 20, 1) == ".") ? substr(iso, 21, 3) / 1000 : 0
            return mktime(sprintf("%d %d %d %d %d %d", d[1], d[2], d[3], hms[1], hms[2], hms[3])) + frac
        }
        /<testsuite / {
            if (match($0, /timestamp="[^"]+"/))  ts = substr($0, RSTART + 11, RLENGTH - 12)
            if (match($0, / time="[0-9.]+"/))    t  = substr($0, RSTART + 7, RLENGTH - 8) + 0
            if (match($0, / tests="[0-9]+"/))    n  = substr($0, RSTART + 8, RLENGTH - 9) + 0
            if (match($0, /failures="[0-9]+"/))  f  = substr($0, RSTART + 10, RLENGTH - 11) + 0
            if (match($0, /errors="[0-9]+"/))    e  = substr($0, RSTART + 8, RLENGTH - 9) + 0
            start = epoch(ts); finish = start + t
            if (first == 0 || start < first) first = start
            if (finish > last) last = finish
            classes++; tests += n; failures += f; errors += e
        }
        END {
            wall = last - first
            printf "  %-40s %-8s  %d %s  %d tests  %d failures  %d errors\n", label,
                (wall >= 60 ? sprintf("%dm%02ds", int(wall/60), int(wall)%60) : sprintf("%.1fs", wall)),
                classes, (classes == 1 ? "class" : "classes"), tests, failures, errors
        }' "$dir"/TEST-*.xml
}

echo "Suites (wall time from the first and last result XML timestamp)"
jq -r '.tasks[] | select(.outcome == "executed") | .path' "$RECORD" \
    | grep -E '^(:[^:]+)?:test[^:]*$' \
    | while IFS= read -r path; do
          task="${path##*:}"
          module="${path#:}"; module="${module%%:*}"
          [ "$path" = ":$task" ] && module=""
          suite_summary "$path" "$module" "$task"
      done
echo

echo "Slowest executed tasks"
jq -r '[.tasks[] | select(.outcome == "executed")] | sort_by(-.durationMs) | .[:10][] | "\(.durationMs)\t\(.path)"' "$RECORD" \
    | while IFS=$'\t' read -r ms path; do printf "  %-8s %s\n" "$(fmt_ms "$ms")" "$path"; done
