#!/usr/bin/env bash
# revert-check.sh — one mutation per clause the probe asserts, each restored byte-identically.
#
#   owning    delete the owning-reference clause (the section and requirement that own both rules)
#   changes   delete the `--changes --strict` form, leaving the per-change form
#   passage   delete the whole strict-rules passage (the item's defect, reproduced with this probe)
#
# Each run prints the timestamp, the probe's own PASS/FAIL lines and its exit code, then restores
# AGENTS.md from a backup and proves the restore with cmp. Exit 0 when every case failed on its
# mutation and every restore was byte-identical, 1 otherwise.
#
# Usage: revert-check.sh [owning|changes|passage]     # no argument runs all three
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$here
while [ ! -f "$root/AGENTS.md" ]; do
  [ "$root" != "/" ] || { echo "revert-check: no AGENTS.md above $here" >&2; exit 2; }
  root=$(dirname "$root")
done
agents="$root/AGENTS.md"
backup="$here/.AGENTS.md.revert-check.bak"
cp "$agents" "$backup" || exit 2
restore() { cp "$backup" "$agents"; }
trap 'restore; rm -f "$backup"' EXIT

mutate() { # $1 mutation name -> writes AGENTS.md in place
  python3 - "$agents" "$1" <<'PY'
import sys

path, which = sys.argv[1], sys.argv[2]
text = open(path, encoding='utf-8').read()

if which == 'owning':
    start = text.index(' Both rules and their rationale are owned by')
    end = text.index('write".', start) + len('write".')
    cut = text[start:end]
    new = text[:start] + text[end:]
elif which == 'changes':
    cut = ' — or `openspec validate --changes --strict` over the open set'
    if cut not in text:
        sys.exit('revert-check: the --changes form is not in AGENTS.md')
    new = text.replace(cut, '')
elif which == 'passage':
    start = text.index('Two more rules are the validator')
    end = text.index('## Build & Test', start)
    cut = text[start:end]
    new = text[:start] + text[end:]
else:
    sys.exit('revert-check: unknown mutation %s' % which)

if new == text:
    sys.exit('revert-check: mutation %s changed nothing' % which)
open(path, 'w', encoding='utf-8').write(new)
print('MUTATION %s removed: %s' % (which, ' '.join(cut.split())[:160]))
PY
}

run() { # $1 mutation, $2 the case that must fail
  local which="$1" case_name="$2" rc
  restore
  cmp -s "$backup" "$agents" || { echo "revert-check: restore before $which is not byte-identical"; return 1; }
  date -Iseconds
  mutate "$which" || return 1
  bash "$here/probe.sh"
  rc=$?
  echo "EXIT=$rc"
  restore
  if cmp -s "$backup" "$agents"; then
    echo "RESTORED byte-identical ($which)"
  else
    echo "RESTORE FAILED ($which)"
    return 1
  fi
  if [ "$rc" -ne 1 ]; then
    echo "UNEXPECTED: probe exited $rc under the $which mutation, expected 1"
    return 1
  fi
  case_name=$case_name
  return 0
}

ok=0
bad=0
for pair in owning:theEntryDocumentStatesTheValidatorStrictRules \
            changes:theEntryDocumentNamesTheStrictValidationCommand \
            passage:both; do
  which=${pair%%:*}
  name=${pair#*:}
  if run "$which" "$name"; then ok=$((ok + 1)); else bad=$((bad + 1)); fi
done

echo "REVERT-CHECK: $ok of $ok mutations failed a case and restored: $bad unexpected"
[ "$bad" -eq 0 ] || exit 1
exit 0
