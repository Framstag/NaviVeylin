#!/usr/bin/env bash
# metadata-mutations.sh — the falsification of the metadata pass (`issue-metadata-loop`, task 8).
#
# One mutation per invariant, applied to the tree, and the *named* rule must notice it; then the mutation is
# restored and the same probe must go quiet again. Both halves matter: a probe that stays loud after the
# restore would mean it asserts something else than the mutation, and a mutation that cannot be noticed means
# the invariant is prose rather than a check.
#
# A mutation that does not change its file is a failure here, not a silent pass (`loop-mutations.sh`'s own
# lesson: a mutation that did not apply reads exactly like a mutation that was noticed by nothing), so every
# patch is compared against a backup before the probe runs, and the tree is restored by a trap whatever
# happens.
#
# The probes are not copies of the invariants: they are the real cases — `tools/issue-tooling-selftest.sh
# --case <name>`, the core's `Cases`, and the two check scripts — driven against a fixture tracker built by
# the case helpers.
#
# Usage: bash tools/issues/metadata-mutations.sh [--list] [--only <id>]
# Exit:  0 every mutation was noticed and every probe went quiet again, 1 otherwise, 2 unusable environment.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
engine="$root/tools/issues"

only=""
list_only=0
case "${1:-}" in
  --list) list_only=1 ;;
  --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; only="${2:-}" ;;
  "") ;;
  -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
  *) echo "metadata-mutations: unknown argument $1" >&2; exit 2 ;;
esac

command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1 || {
  echo "metadata-mutations: java and javac are required (the core is Java)" >&2
  exit 2
}

tmp=$(mktemp -d "${TMPDIR:-/tmp}/metadata-mutations.XXXXXX") || {
  echo "metadata-mutations: cannot create a scratch directory" >&2
  exit 2
}
mkdir -p "$tmp/classes"
javac --release 17 -d "$tmp/classes" "$engine"/core/*.java || {
  echo "metadata-mutations: the Java core does not compile" >&2
  exit 1
}

# the same environment the tooling's own self-test provides, so the probes are the real entry points
export ISSUES_CORE_CLASSES="$tmp/classes"
export ISSUES_REPO_ROOT="$root"
export ISSUES_ENGINE="$engine"
export ISSUES_FIXTURES="$root/tools/issues/fixtures"
export ISSUES_TMP="$tmp"
export GH_STUB_DIR="$ISSUES_FIXTURES"
export PATH="$ISSUES_FIXTURES:$PATH"
# shellcheck source=tools/issue-tooling-cases.sh
source "$root/tools/issue-tooling-cases.sh"

MUTATIONS=(
  'basis-drops-labels'
  'basis-hashes-the-raw-body'
  'basis-drops-the-anchor-flag'
  'basis-carries-the-run-timestamp'
  'band-edge-leaves-a-hole'
  'classify-writes-the-opt-in'
  'the-writer-skips-the-marker'
  'settled-record-not-checked'
)

mutation_invariant() {
  case "$1" in
    'basis-drops-labels') echo "the basis must move when a kind label changes (task 8.1, D3)" ;;
    'basis-hashes-the-raw-body') echo "a reflowed body must not move the basis (task 8.2, D3)" ;;
    'basis-drops-the-anchor-flag') echo "the basis must move when the cited code goes away (task 8.3, D3)" ;;
    'basis-carries-the-run-timestamp') echo "a repeated run with nothing changed must score nothing (task 8.4, D3, D9)" ;;
    'band-edge-leaves-a-hole') echo "every score the model can produce falls in exactly one band (task 8.5, D2)" ;;
    'classify-writes-the-opt-in') echo "the pass cannot write the automation opt-in (task 8.6, D7)" ;;
    'the-writer-skips-the-marker') echo "no band label is written or removed without the body that leaves the pair intact (task 8.7, D1; the write gate of #671)" ;;
    'settled-record-not-checked') echo "an item whose body keeps a settled state must carry a settled label, or the displaced state is invisible (#683)" ;;
    *) echo "" ;;
  esac
}

if [ "$list_only" -eq 1 ]; then
  for id in "${MUTATIONS[@]}"; do
    printf '%-32s %s\n' "$id" "$(mutation_invariant "$id")"
  done
  exit 0
fi

backups=()
restore_all() {
  local pair file backup
  for pair in "${backups[@]:-}"; do
    [ -n "$pair" ] || continue
    file="${pair%%|*}"
    backup="${pair#*|}"
    cp "$backup" "$file"
  done
}
trap 'restore_all; rm -rf "$tmp"' EXIT

# patch <file> <how> <expression> → apply, back the file up, and refuse to continue when the file did not
# change. `how` is `sed` (an expression) or `awk` (a program whose first `print` emits the replacement line
# ahead of the matching one, so a multi-line insert needs no shell-for-sed escaping).
patch() {
  local file="$1" how="$2" expression="$3" backup
  backup="$tmp/$(printf '%s' "$file" | tr '/.' '__').backup"
  [ -f "$backup" ] || cp "$file" "$backup"
  backups+=("$file|$backup")
  case "$how" in
    sed) sed -i "$expression" "$file" || { echo "  the sed patch failed for $file" >&2; return 1; } ;;
    awk) awk "$expression" "$file" > "$tmp/mutated.$$" || { echo "  the awk patch failed for $file" >&2; return 1; }
         mv "$tmp/mutated.$$" "$file" ;;
    *) echo "  unknown patch kind $how" >&2; return 1 ;;
  esac
  if cmp -s "$file" "$backup"; then
    echo "  the mutation did not apply to $file — a mutation that changes nothing is not evidence" >&2
    return 1
  fi
  return 0
}

# A probe returns 0 when the defect is visible, non-zero when the rule is quiet. Its diagnostics are
# suppressed for the post-restore probe, which is *expected* to be quiet — a message there would read like a
# second failure.
PROBE_QUIET=0

report_probe() {
  [ "$PROBE_QUIET" = 1 ] || printf '%s\n' "$1" >&2
}

probe_case() { # <case-name> → 0 when the case fails (the defect was noticed)
  local name="$1" out status
  out=$(bash "$root/tools/issue-tooling-selftest.sh" --case "$name" 2>&1)
  status=$?
  [ "$status" -ne 0 ] || {
    report_probe "  $name passed: $(printf '%s' "$out" | tail -2 | tr '\n' ' ')"
    return 1
  }
  return 0
}

probe_core_case() { # <Case#name>
  local name="$1" out
  out=$(java -cp "$ISSUES_CORE_CLASSES" Cases "$name" 2>&1)
  [ $? -ne 0 ] || {
    report_probe "  $name passed: $(printf '%s' "$out" | tail -2 | tr '\n' ' ')"
    return 1
  }
  return 0
}

# a fixture tracker in a scratch directory, and the recorded gh calls
fixture_dir() {
  local dir
  dir=$(mktemp -d "$tmp/fixture.XXXXXX")
  metadata_issues "${1:-1}" > "$dir/issues.json"
  printf '%s\n' "$dir"
}

probe_opt_in_write() { # 0 when the check catches a run whose log carries the opt-in
  local dir log out status kind area
  dir=$(fixture_dir 1)
  log="$dir/writes.log"; : > "$log"
  # the vocabulary values come from the profile, never from this file: the engine names no profile value
  # (`lib#noProjectValueInTheEngine`), and a harness that hard-coded one would be that defect itself
  kind=$(core Profile "$(profile_path)" namespaces.class | head -1)
  area=$(core Profile "$(profile_path)" namespaces.category | head -1)
  jq '.[0].labels |= map(select(.name | startswith("class/") | not))' "$dir/issues.json" \
    > "$dir/i.json" && mv "$dir/i.json" "$dir/issues.json"
  jq -n --arg k "$kind" --arg a "$area" \
    '[{issue: 1, class: $k, category: $a, rationale: "fixable"}]' > "$dir/proposals.json"
  GH_STUB_DIR="$dir" GH_STUB_RECORD="$dir/calls.log" bash "$engine/classify.sh" \
    --proposals "$dir/proposals.json" --write --write-log "$log" --profile "$(profile_path)" >/dev/null 2>&1 ||
    { report_probe "  the classification run itself failed"; return 1; }
  out=$(bash "$engine/check-metadata.sh" --write-log "$log" 2>&1)
  status=$?
  [ "$status" -ne 0 ] || { report_probe "  the check accepted a write log carrying the opt-in"; return 1; }
  printf '%s' "$out" | grep -q 'wrote-the-opt-in' || {
    report_probe "  the check failed for another reason: $out"
    return 1
  }
  return 0
}

apply_mutation() {
  case "$1" in
    'basis-drops-labels')
      patch "$engine/lib.sh" sed 's@^  label_key=.*@  label_key=""@' ;;
    'basis-hashes-the-raw-body')
      patch "$engine/lib.sh" sed 's@^  body_key=$(issues_body_hash "$body")@  body_key=$(printf "%s" "$body" | issues_sha256)@' ;;
    'basis-drops-the-anchor-flag')
      patch "$engine/lib.sh" sed 's@^  anchor_key=$(issues_anchor_flag "$body")@  anchor_key=""@' ;;
    'basis-carries-the-run-timestamp')
      patch "$engine/prioritise.sh" sed 's@^    basis=$(issues_basis "$labels" "$body" "$occurrence" "$bucket" "$basis_pattern")@&$(date +%s%N)@' ;;
    'band-edge-leaves-a-hole')
      # the top band starts at 16, so a score of 15 falls in no band at all
      patch "$root/tools/tooling.json" sed 's@"id": "p0", "min": 15@"id": "p0", "min": 16@' ;;
    'classify-writes-the-opt-in')
      # The mutation resolves the reserved label at *runtime*, so the check's file-level grep
      # (`names-the-reserved-label`) cannot see it — the write-log rule is what has to catch it, and that is
      # what the probe asserts. `awk` inserts the two lines because they carry quotes, a redirection and
      # braces that a sed replacement would have to escape twice over.
      patch "$engine/classify.sh" awk '
        /^      written=/ {
          print
          print "      gh issue edit \"$number\" --add-label \"$(issues_core Profile \"$profile\" reservedLabels | head -1)\" >/dev/null 2>&1 || true"
          print "      issues_write_log \"$write_log\" \"classify.sh\" \"$number\" --add-label \"$(issues_core Profile \"$profile\" reservedLabels | head -1)\""
          next
        }
        { print }' ;;
    'the-writer-skips-the-marker')
      # The pair is written by one home (`lib.sh`, "the band pair"), so the mutation disables it there: with no
      # band namespace resolved, neither the writer nor the gate sees a band label, and the pass's write leaves
      # the label half standing on its own. Before #671 the drift came out of the pass itself for the same
      # reason; now the write path refuses it unless this gate is gone, which is what the probe measures.
      patch "$engine/lib.sh" sed 's@^  namespace=\$(issues_core Bands meta.*@  namespace=""@' ;;
    'settled-record-not-checked')
      # The settled pair is read in one place, so the mutation disables the reading there: the record a tombstone
      # keeps no longer reaches the invariant, and the item whose state a park displaced is reported by nothing.
      patch "$engine/check-metadata.sh" sed 's@^  if issues_records_settled_state "\$body" "\$settled_record"; then@  if false; then@' ;;
    *) return 1 ;;
  esac
}

probe_mutation() {
  case "$1" in
    'basis-drops-labels') probe_case 'basis#changesWhenAClassLabelChanges' ;;
    'basis-hashes-the-raw-body') probe_case 'basis#stableAcrossWhitespaceOnlyBodyEdit' ;;
    'basis-drops-the-anchor-flag') probe_case 'basis#changesWhenTheCitedCodeGoesAway' ;;
    'basis-carries-the-run-timestamp') probe_case 'prioritise#secondRunScoresNothing' ;;
    'band-edge-leaves-a-hole') probe_core_case 'Bands#coversTheWholeRange' ;;
    'classify-writes-the-opt-in') probe_opt_in_write ;;
    'the-writer-skips-the-marker') probe_case 'update#refusesABandLabelWithoutItsMarker' ;;
    'settled-record-not-checked') probe_case 'check#aDisplacedSettledStateIsReported' ;;
    *) return 1 ;;
  esac
}

failed=0
checked=0
for id in "${MUTATIONS[@]}"; do
  if [ -n "$only" ] && [ "$only" != "$id" ]; then
    continue
  fi
  checked=$((checked + 1))
  echo "── $id — $(mutation_invariant "$id")"
  if ! apply_mutation "$id"; then
    echo "  FAIL: the mutation could not be applied"
    failed=$((failed + 1))
    restore_all
    backups=()
    continue
  fi
  noticed=0
  PROBE_QUIET=0 probe_mutation "$id" && noticed=1
  restore_all
  backups=()
  if [ "$noticed" -ne 1 ]; then
    echo "  FAIL: nothing noticed the mutation"
    failed=$((failed + 1))
    continue
  fi
  echo "  the rule fired with the mutation in place"
  if PROBE_QUIET=1 probe_mutation "$id"; then
    echo "  FAIL: the probe still fires after the restore — it asserts something else"
    failed=$((failed + 1))
    continue
  fi
  echo "  and it is quiet again after the restore"
done

if [ "$checked" -eq 0 ]; then
  echo "metadata-mutations: no such mutation: $only" >&2
  exit 2
fi
if [ "$failed" -ne 0 ]; then
  echo "metadata-mutations: $failed of $checked mutations were not falsified"
  exit 1
fi
echo "metadata-mutations: $checked of $checked mutations noticed, and every probe quiet again"
exit 0
