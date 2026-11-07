#!/usr/bin/env bash
# look.sh --path probes: a scope that climbs out of the tree, a scope that climbs out and back in, and the two plain
# shapes. Run from the project root.
cd "$(dirname "$0")/../../../.." || exit 2
ev=openspec/changes/fix-a-child-has-a-working-search/evidence
{
  echo '$ bash tools/look.sh "^## " --path ../outside'
  bash tools/look.sh '^## ' --path ../outside 2>&1; echo "exit=$?"
  echo '$ bash tools/look.sh "^## " --path guidelines/../outside'
  bash tools/look.sh '^## ' --path guidelines/../outside 2>&1; echo "exit=$?"
  echo '$ bash tools/look.sh "^## " --path guidelines/../guidelines/Build.md | head -1'
  bash tools/look.sh '^## ' --path guidelines/../guidelines/Build.md 2>&1 | head -1; echo "exit=$?"
  echo '$ bash tools/look.sh "^## " --path guidelines/Build.md | head -1'
  bash tools/look.sh '^## ' --path guidelines/Build.md 2>&1 | head -1
  echo "at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$ev/look-path-probe.log" 2>&1
cat "$ev/look-path-probe.log"
