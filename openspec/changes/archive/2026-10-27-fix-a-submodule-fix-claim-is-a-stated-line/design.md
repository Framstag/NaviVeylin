# Design — fix-a-submodule-fix-claim-is-a-stated-line

## The root cause

`tools/loop/prescreen.sh:145-154` answers "does this item's fix have to land in the submodule" by searching the
item's text for the submodule's **path** (`:150`, substring) and its **basename** (`:153`, word). A path is what a
body quotes when it reports where dirt was seen, and a directory name is ordinary vocabulary in an item about the
loop's own working tree, so the sensor fires on evidence. Its verdict (`:391-395`) is a park: `needs-decision` plus
`status/on-hold`, which takes the item out of every later run's queue.

## The single fix

Replace the search with a line-prefix match on the author's marker:

```sh
states_the_fix_lands_in_the_submodule() { # <title> <body> → 0 when the author states the claim on a line of its own
  local marker='The fix lands in the submodule'
  [ -n "$submodule_path" ] || return 1
  printf '%s\n%s\n' "$1" "$2" | grep -q "^$marker"
}
```

`submodule_path` is still read: a project with no submodule owes no such refusal, and the reason the verdict writes
still names the path — the fix itself moves, the verdict does not.

The anchor is the whole rule. `^` makes the phrase match only where the author put it on a line of its own, so the
three quotations a body legitimately carries — inline, backticked, as an example — stay `eligible`. Relaxing it to a
substring (`grep -qF`) is mutation `69.1`, and it is what turns the quoting case red.

## Alternatives the evidence eliminated

- **The `**Where the fix lands**` field recent bodies carry** (the item's `Option 2`). Eliminated by the recorded
  decision and by the tree: #314's own body has no such field, and neither do the older entries, so the rule would be
  silent exactly where it is needed. Its failure mode is the defect being fixed — a field that names the path while
  denying the fix is parked again.
- **Searching only the section that states the work** (a `Candidate repairs` half). Eliminated by the same evidence:
  the park happened in bodies whose *only* submodule mention is a quoted stop reason, so a narrower search still
  reads vocabulary rather than the claim, and "which section states the work" is not a rule a script can apply.
- **Keeping the path search and adding the marker as an escape** — refuse on the path unless the marker denies it.
  Eliminated because it inverts the recorded decision (the marker decides), and because a denial marker in an
  evidence-quoting body is another convention to learn on top of the one the decision chose.

## Blast radius

- `tools/loop/prescreen.sh` — one function replaced, one call site renamed, comments stating the new signal.
- `tools/loop/fixtures/scenarios/submodule/issues.json` — the only fixture the rule reads; two items realigned onto
  the marker, two added for the quoting half.
- `tools/loop-cases.sh` — the flipped case's assertions and queue count, and the new quoting case.
- `tools/loop-mutations.sh` — record `69.1`; `--check` applies it (the gate owes that for a `tools/loop/**` path).
- `.pi/skills/create-issue/SKILL.md` — the marker documented beside the body fields.
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement with two scenarios, each naming one case.

No compiled file, no Android module, no log line: the change is engine text plus tests, so the structural checks of
the files it edits stand in for a build.

## Rollback

Revert this change. A genuine submodule item that omits the marker is then queued instead of parked — one worker
phase before the worker's gate 3 asks the same question — which is the cost the recorded decision accepted, and
strictly cheaper than a parked item nobody repairs.
