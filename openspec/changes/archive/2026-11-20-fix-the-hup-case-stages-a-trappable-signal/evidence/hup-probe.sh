#!/usr/bin/env bash
# Probe: can this shell (and its children) trap HUP, and is HUP ignored on entry?
while IFS= read -r line; do
  case "$line" in SigIgn*|SigCgt*|SigBlk*) printf '%s\n' "$line" ;; esac
done < /proc/self/status
trap 'printf "TRAPPED\n"' HUP
kill -HUP $$
sleep 0.3
printf 'DONE\n'
