#!/usr/bin/env bash
# A fixture sensor that cannot determine anything: it fails, which is the third outcome's trigger.
set -uo pipefail
printf 'fixture sensor: cannot reach its input\n' >&2
exit 1
