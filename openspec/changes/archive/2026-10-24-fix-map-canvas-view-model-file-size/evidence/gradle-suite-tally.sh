#!/usr/bin/env bash
# The gate's Gradle suite, counted. `gate.sh` records the run and its wall clock but no case count —
# Gradle prints no JUnit summary line for the whole suite — so this probe reads the run's own result
# XMLs, one row per module and variant, and sums their `tests=`/`failures=`/`errors=` attributes.
#
# Usage: gradle-suite-tally.sh [<tree>]
set -uo pipefail
cd "${1:-.}" || exit 2
echo "# the gate's Gradle suite, from the result XMLs it wrote ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
total=0; fails=0; errs=0; files=0
for d in */build/test-results/test*; do
  n=$(ls "$d"/*.xml 2>/dev/null | wc -l)
  [ "$n" -eq 0 ] && continue
  line=$(awk 'BEGIN{t=0;f=0;e=0} /<testsuite /{for(i=1;i<=NF;i++){if($i~/^tests=/){gsub(/[^0-9]/,"",$i);t+=$i} if($i~/^failures=/){gsub(/[^0-9]/,"",$i);f+=$i} if($i~/^errors=/){gsub(/[^0-9]/,"",$i);e+=$i}}} END{print t, f, e}' "$d"/*.xml)
  set -- $line
  printf '%-52s %4d xml %6d cases %4d failures %4d errors\n' "$d" "$n" "$1" "$2" "$3"
  total=$((total + $1)); fails=$((fails + $2)); errs=$((errs + $3)); files=$((files + n))
done
printf '# total: %d cases in %d XMLs, %d failures, %d errors\n' "$total" "$files" "$fails" "$errs"
[ "$fails" -eq 0 ] && [ "$errs" -eq 0 ]
