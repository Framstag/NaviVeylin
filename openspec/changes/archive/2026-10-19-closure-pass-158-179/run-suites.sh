#!/usr/bin/env bash
# Closure pass over issues #158-#179 — run the owning suites of the changes that fixed them.
# Writes one log beside this script's evidence directory. Not a project script: kept out of tools/.
set -uo pipefail
cd /home/tim/projects/NaviVeylin || exit 1
E=openspec/changes/archive/2026-10-19-closure-pass-158-179/evidence
{
  echo "## bash tools/sensor-selftest.sh"
  timeout 2400 bash tools/sensor-selftest.sh 2>&1 | tail -3
  echo "exit=${PIPESTATUS[0]}"
  echo
  echo "## bash tools/loop-selftest.sh"
  timeout 3000 bash tools/loop-selftest.sh 2>&1 | tail -3
  echo "exit=${PIPESTATUS[0]}"
  echo
  echo "## bash tools/loop-mutations-selftest.sh"
  timeout 1200 bash tools/loop-mutations-selftest.sh 2>&1 | tail -3
  echo "exit=${PIPESTATUS[0]}"
  echo "DONE"
} > "$E/suites-2.log" 2>&1
