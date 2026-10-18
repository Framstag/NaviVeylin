# Issue #179 — closure reason and evidence

**Archived change:** `openspec/changes/archive/2026-10-18-fix-issue-filing-state-namespace/`

## Reason

`create.sh --status` can no longer file an item beside the state its labels already carry. The filing path
merges the finding's own `.labels[]`, each `--label` and `--status <value>` into one label array, and it now
refuses that array — before any `gh issue create` — when it names two values of one namespace, with exit 2 and
a reason naming both. The state named only through `--status`, or only through the labels, still files.

**Root cause (one).** The merge at `tools/issues/create.sh:81-92` produced one flat label list and the only
guard over it was the per-value vocabulary check (`issues_check_labels` → `Policy labels`,
`tools/issues/core/Policy.java:163-167`), which asks whether each value exists and is never reserved but never
compares two labels. `update.sh` had already grown the one-value-per-namespace rule for the state namespace as
a *replacement* (`fix-status-label-replacement`, D5/D8); the filing path — where the item is born and there is
nothing to displace — had no equivalent, so it could create an item no state selector reads correctly.

**The single resulting fix.** `tools/issues/create.sh:119-133`: after the vocabulary gate, a prefix scan over
the merged labels refuses with `issues_die` (exit 2) when two labels share the namespace prefix, naming both.
The check is per namespace, not status-only, because the profile's namespace model makes the same rule hold for
every declared namespace and I16 is not status-specific. `tools/issues/fixtures/gh` is deliberately unchanged:
the real `gh` accepts a duplicate-namespace label set (measured during triage), so a stricter stub would assert
a refusal the client does not make.

**The requirement the defect contradicts.** `issue-backlog` → "Labels carry the item's kind, area, state and
provenance", whose text already says "exactly one value per namespace where the item has that fact". The
requirement now states the filing path's share of it, MODIFIED over that one requirement, with the scenario
`create#refusesTwoValuesOfOneNamespace`.

## Evidence

Red on HEAD (`evidence/red-on-head.log`, issue #179's own reproduction, stub `gh` first on `PATH`):

```
$ bash tools/issue-tooling-selftest.sh --case create#refusesTwoValuesOfOneNamespace
  a filing naming two states is a usage error: …/issues/1: expected '2', got '0'
FAIL create#refusesTwoValuesOfOneNamespace

$ bash tools/issues/create.sh --finding "$tmp/f.json" --status open
https://example.invalid/Framstag/NaviVeylin/issues/1    # exit 0
$ cat "$tmp/log"
gh issue create … --label class/bug --label category/ui --label status/fixed-by --label status/open
```

Green (`evidence/verify.md`):

```
$ bash tools/issue-tooling-selftest.sh --case create#refusesTwoValuesOfOneNamespace
PASS create#refusesTwoValuesOfOneNamespace

$ bash tools/issues/create.sh --finding "$tmp/f.json" --status open
create: status/fixed-by and status/open name two values of one namespace — an item carries one value per namespace
exit=2    # no gh issue create was recorded

$ bash tools/issue-tooling-selftest.sh
issue-tooling-selftest: all 77 cases passed (JDK 27, offline, no token)
$ bash tools/sensor-selftest.sh
sensor-selftest: all 43 cases passed
$ bash tools/check-portability-selftest.sh
check-portability-selftest: all 21 cases passed

$ bash tools/mutate.sh --mutations tools/create-mutations.json
mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
```

Falsified by mutation `1.1` (`tools/create-mutations.json`, registered in the "Check issue tooling" CI step).

## The closure run

```
$ tools/issues/update.sh 179 --status fixed-by --comment-file <this file's reason>
```

The comment is posted on the item; the item carries `status/fixed-by` and stays **open**, because
`agent/eligible` is a person's write — every write path refuses the label — and the gate refuses a closure
without it (`close.sh` answers `missing-eligibility-label`, exit 3, writing nothing). The decision to close is
therefore the owner's, and the change is archived either way.
