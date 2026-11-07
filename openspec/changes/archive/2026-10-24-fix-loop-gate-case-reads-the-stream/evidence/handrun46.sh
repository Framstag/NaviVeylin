#!/usr/bin/env bash
# Apply the substitution of record 46.1 to tools/loop/gate.sh by hand, run the case, capture the assertion it
# fires for, restore the file and prove the restore (the shape .pi/skills/revert-check owns).
set -uo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root" || exit 2
cp tools/loop/gate.sh .pi/tmp/gate.sh.before
perl -0777 -i -pe 's{    \$cmd 2>&1 \| tee "\$stream"\n}{    out=\$(\$cmd 2>&1); printf %s "\$out" > "\$stream"\n}' tools/loop/gate.sh
grep -q 'printf %s "\$out" > "\$stream"' tools/loop/gate.sh || { echo "the mutation did not land" >&2; exit 3; }
printf 'MUTATION-LANDED: %s\n' "$(grep -n 'printf %s "\$out" > "\$stream"' tools/loop/gate.sh)"
bash tools/loop-selftest.sh --case 'gate#theSuitesOutputReachesTheConsoleWhileItRuns' 2>&1
printf 'case exit=%s\n' "$?"
cp .pi/tmp/gate.sh.before tools/loop/gate.sh
printf 'RESTORE-DIFF: %s\n' "$(git diff --stat -- tools/loop/gate.sh | tr -d '\n')"
