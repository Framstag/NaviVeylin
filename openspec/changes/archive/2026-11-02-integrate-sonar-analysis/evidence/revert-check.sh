#!/usr/bin/env bash
# The revert-check of change integrate-sonar-analysis (issue #741, tasks 8.1–8.4): one mutation per invariant,
# the named case must go red, the file is restored, the case must go green again. Writes its transcript to
# `revert-check.log` beside this script.
#
#   8.1  delete the scanner's floor row from tools/check-action-runtimes.sh  → tools/check-action-runtimes.sh exits 1
#   8.2  add a pull_request trigger to .github/workflows/sonar.yml           → tools/check-sonar-workflow.sh exits 1
#   8.3  remove app/src/main/cpp/** from the exclusions                     → tools/check-sonar-workflow.sh exits 1
#   8.4  set sonar.qualitygate.wait=true in the property file               → tools/check-sonar-workflow.sh exits 1
#
# Usage: bash openspec/changes/<change>/evidence/revert-check.sh
set -uo pipefail
root="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$root" || exit 1
ev="$root/openspec/changes/integrate-sonar-analysis/evidence"
runtimes="tools/check-action-runtimes.sh"
sonar="tools/check-sonar-workflow.sh"
wf=".github/workflows/sonar.yml"
props="sonar-project.properties"
backup=/tmp/nv-sonar-revert-backup
rm -rf "$backup"; mkdir -p "$backup"
cp "$runtimes" "$wf" "$props" "$backup/" || exit 1
restore() { cp "$backup/$(basename "$1")" "$1"; }
run() { # run <label> <expected exit> <command…>
  local label="$1" want="$2" out rc=0
  shift 2
  out=$("$@" 2>&1) || rc=$?
  printf '$ %s\n%s\nexit=%s (expected %s)\n\n' "$*" "$out" "$rc" "$want"
  [ "$rc" = "$want" ]
}

{
  echo "# revert-check for integrate-sonar-analysis ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo
  echo "## 8.1 a scanner pin with no recorded Node runtime is reported"
  sed -i '/^SonarSource\/sonarqube-scan-action /d' "$runtimes"
  run "mutated" 1 bash "$runtimes"
  restore "$runtimes"
  run "restored" 0 bash "$runtimes"

  echo "## 8.2 a pull_request trigger is reported"
  sed -i 's/^on:$/on:\n  pull_request:\n    branches: [main]/' "$wf"
  run "mutated" 1 bash "$sonar"
  restore "$wf"
  run "restored" 0 bash "$sonar"

  echo "## 8.3 a dropped native-tree exclusion is reported"
  sed -i 's|^sonar\.exclusions=app/src/main/cpp/\*\*,|sonar.exclusions=|' "$props"
  run "mutated" 1 bash "$sonar"
  restore "$props"
  run "restored" 0 bash "$sonar"

  echo "## 8.4 a gate wait is reported"
  sed -i 's/^sonar\.sourceEncoding=UTF-8$/sonar.sourceEncoding=UTF-8\nsonar.qualitygate.wait=true/' "$props"
  run "mutated" 1 bash "$sonar"
  restore "$props"
  run "restored" 0 bash "$sonar"
} > "$ev/revert-check.log" 2>&1

rm -rf "$backup"
cat "$ev/revert-check.log"
