#!/usr/bin/env bash
# The same probe while the host is held: 20 spinning shells at nice 0 for the length of the batch, so the
# start-up's dependence on the host is a reading and not a claim.
set -uo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root" || exit 2
hogs=""
trap 'kill $hogs 2>/dev/null' EXIT
for _ in $(seq 20); do ( while :; do :; done ) & hogs="$hogs $!"; done
printf '\n# The same probe while 20 spinning shells at nice 0 hold the host: the start-up moves with the host,\n# and the case'"'"'s window does not.\n'
bash "$root/openspec/changes/fix-loop-gate-case-reads-the-stream/evidence/timing-probe.sh" 3
