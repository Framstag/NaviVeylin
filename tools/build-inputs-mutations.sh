#!/usr/bin/env bash
# The `wire-sensor-inputs` falsification harness (`revert-check`): one mutation per invariant the publisher
# and the fetcher add, each applied to the shipped script, the named case run, the file restored, and the
# case run again. A mutation that leaves its case green is a case that is not evidence, so this script fails
# when that happens.
#
# It mutates the working tree, so it is a **change-time** step and never a CI step: the build pipeline lists
# the mutations (`--list`) and does not run them, exactly as it does for the issue tooling's, the sensor
# tooling's and the loop's mutation harnesses.
#
# Usage: bash tools/build-inputs-mutations.sh --list          # one line per mutation: <task> <case> <what>
#        bash tools/build-inputs-mutations.sh [--only <task>] # run them all (or one), restoring after each
# Exit:  0 every mutation behaved, 1 one did not, 2 usage/environment.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root" || exit 2

only=""
case "${1:-}" in
  --list) mode=list ;;
  --only) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf '%s: %s needs a value\n' "$(basename -- "$0" .sh)" "$1" >&2; exit 2; }; mode=run; only="${2:-}" ;;
  ""|--run) mode=run ;;
  -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
  *) echo "build-inputs-mutations: unknown argument $1" >&2; exit 2 ;;
esac

# <task>@<file>@<sed-expression>@<case>@<what the mutation makes untrue> — `@` separates the fields because
# a sed expression uses `|` as its own delimiter. A case in the sensor tooling's own suite is written
# `sensor:<case>`, because the workflow's shape is asserted there as well (the record fetch is the one thing
# the workflow's own token may be used for).
MUTATIONS=(
  '5.1@tools/build-inputs.sh@s/"$timing_record" "$package_apk"/"$timing_record"/@inputs#manifestIsTheProducerSet@the manifest is the set of paths the producer writes'
  '5.2@tools/build-inputs.sh@s/\[ "$refused" -eq 0 \] || exit 1/true/@inputs#refusesADeclaredInputWithNoProducer@a declared input outside the manifest is refused'
  '5.3@tools/build-inputs.sh@s/tail -n 1)/head -n 1)/@inputs#convertsPerModuleCoverage@the module percentage is the report'"'"'s own counter, not the first one in the document'
  '5.4@tools/build-inputs.sh@s/^    write_result_record$/    :/@inputs#resultRecordShapes@the result record is written for every build, green or red'
  '5.5@tools/fetch-build-inputs.sh@s/select(\.headSha == \$sha)/select(true)/@inputs#refusesAnotherCommitsRecord@the record is the commit'"'"'s own'
  '5.6@tools/fetch-build-inputs.sh@s/^  exit 3$/  exit 0/@inputs#noRecordExitsThree@an absent record is not ready, and not a failed check'
  '5.7@.github/workflows/sensors.yml@s/cron: '"'"'0,30 \* \* \* \*'"'"'/cron: '"'"'0 * * * *'"'"'/@workflow#tickMinutesCoverTheRegistryCrons@the tick'"'"'s minutes cover the registry'"'"'s'
  '5.8@.github/workflows/sensors.yml@s/secrets\.SENSORS_TOKEN/github.token/@sensor:workflow#onlyTwoTriggers@the workflow'"'"'s own token never writes to the tracker'
  '5.9@tools/sensors/conditions/broken-main.sh@s/if \[ "$cleared" -eq 0 \]; then/if false; then/@sensor:sensors#brokenMainGreenReportsClear@a condition row with nothing to report still reports its outcome'
  '5.10@tools/sensors/conditions/pr-conflicts.sh@s/if \[ "$findings" -eq 0 \]; then/if false; then/@sensor:sensors#prConflictsNothingReportsClear@the same duty on the pull-request row'
  '5.11@.github/workflows/sensors.yml@s/if: steps.record.outputs.ready == '\''true'\''/if: true/@sensor:workflow#onlyTwoTriggers@every tracker write waits for the record'
  '5.12@.github/workflows/sensors.yml@s/inputs.apply == true/inputs.apply == '\''true'\''/@sensor:workflow#onlyTwoTriggers@the dispatch apply is compared as the boolean it is declared to be'
  '5.13@tools/fetch-build-inputs.sh@s/^conclusion=.*/conclusion=success/@inputs#reportsTheRunsConclusion@the conclusion the caller gates its rows on is the run'"'"'s own'
  '5.14@tools/build-inputs.sh@s/xml_instruction_percentage "\$jacoco_vendored_xml"/xml_instruction_percentage "$jacoco_xml"/@inputs#splitsTheJavaModulesCoverageByProvenance@the vendored provenance is read from its own report, not from the module'"'"'s own one'
)

if [ "$mode" = "list" ]; then
  for entry in "${MUTATIONS[@]}"; do
    IFS='@' read -r task file expr case_name what <<< "$entry"
    printf '%s %s — %s (%s)\n' "$task" "$case_name" "$what" "$file"
  done
  exit 0
fi

backup_dir=$(mktemp -d) || exit 2
trap 'rm -rf "$backup_dir"' EXIT

pass=0
fail=0
for entry in "${MUTATIONS[@]}"; do
  IFS='@' read -r task file expr case_name what <<< "$entry"
  if [ -n "$only" ] && [ "$only" != "$task" ]; then
    continue
  fi
  backup="$backup_dir/$(printf '%s' "$file" | tr '/' '_')"
  # Which suite owns the case: the build inputs' own, or the sensor tooling's (a `sensor:` prefix).
  suite="bash tools/build-inputs-selftest.sh"
  case "$case_name" in
    sensor:*) suite="bash tools/sensor-selftest.sh"; case_name="${case_name#sensor:}" ;;
  esac
  cp "$file" "$backup"
  if ! sed -i "$expr" "$file"; then
    printf 'MUTATION %s: could not apply the mutation to %s\n' "$task" "$file" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  if cmp -s "$backup" "$file"; then
    printf 'MUTATION %s: the mutation changed nothing — the assert would be vacuous\n' "$task" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  if $suite --case "$case_name" >/dev/null 2>&1; then
    printf 'MUTATION %s: %s stayed green under the mutation (%s)\n' "$task" "$case_name" "$what" >&2
    cp "$backup" "$file"
    fail=$((fail + 1))
    continue
  fi
  cp "$backup" "$file"
  if ! $suite --case "$case_name" >/dev/null 2>&1; then
    printf 'MUTATION %s: %s is red after the restore\n' "$task" "$case_name" >&2
    fail=$((fail + 1))
    continue
  fi
  printf 'MUTATION %s: %s red under the mutation, green after the restore — %s\n' "$task" "$case_name" "$what"
  pass=$((pass + 1))
done

printf '\nbuild-inputs-mutations: %d behaved, %d did not\n' "$pass" "$fail"
[ "$fail" -eq 0 ] || exit 1
exit 0
