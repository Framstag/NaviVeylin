# The revert-checks

The guard is a refusal path, so it owes a mutation: with the refusal removed the case that asserts it must go
red, and the restore must bring it back green (`revert-check` skill, `guidelines/Build.md` §2). The set is
`tools/status-mutations.json` — six records, all of them state writes, so the whole set is run rather than the
one label this change added (a subset run cannot say the rest still hold).

## The whole set, run from the change root

```
$ bash tools/mutate.sh --mutations tools/status-mutations.json
5.1	update#statusReplacesTheOldStatus	the displaced state labels being read from the item, so the add and the removals ride one edit
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.2	update#statusReplacementIsIdempotent	the difference-only rule — a state write does not add the label the item already carries
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.3	update#statusRejectsTwoStatesInOneCall	a call naming two states being refused instead of silently resolved
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
5.4	update#statusReportNamesImpliedRemoval	the report carrying the displaced labels the caller never named
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
7.1	update#stateNamedAsALabelReplacesTheOldState	a state named as the namespaced label being the same act as one named through the state flag
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/update.sh
  GREEN(exit 0)  restored, and the case passes again
8.1	update#refusesToDisplaceASettledState	the guard refusing a routine write that would displace a settled state instead of letting every caller through
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/lib.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 6 mutation(s) run, 6 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

Run at 2026-10-29T06:23:07Z, finished 2026-10-29T06:23:45Z, on the final tree of this change (after the rebase
onto `main`); `git status --short` afterwards listed only this change's own evidence files.
Every entry of the set went red and came back green, so the guard did not disturb the replacement rules it
sits beside — that is a run this record carries, not an inference from a subset.

## The refusal's own message (8.1)

`tools/mutate.sh` reports the exit status of the case it reddens and keeps that case's own output out of its
transcript, so the message is quoted from the same mutation applied by hand — the same one line, so this is one
record and not a second mutation:

```
$ cp tools/issues/lib.sh lib.sh.before-8.1
$ diff lib.sh.before-8.1 tools/issues/lib.sh
377c377
<   [ "$named" -eq 1 ] && return 0
---
>   return 0
--- mutated 2026-10-28T20:04:44Z ---
$ bash tools/issue-tooling-selftest.sh --case 'update#refusesToDisplaceASettledState'
  a hold over a settled state is refused: stub gh: recorded `gh issue edit`
update: #11 removed status/retired — a settled state, displaced by --displace-settled: expected '3', got '0'
FAIL update#refusesToDisplaceASettledState
```

The message is the right one: the case asserts the refusal (`expected '3'`) and the write that should have been
refused went through and said what it took with it (`#11 removed status/retired … displaced by
--displace-settled`). Not a compile error, not an unrelated premise.

```
--- restore 2026-10-28T20:04:46Z ---
$ cp lib.sh.before-8.1 tools/issues/lib.sh && diff -q lib.sh.before-8.1 tools/issues/lib.sh
restored byte-identical
$ rm lib.sh.before-8.1 && git status --short
                     (no production file listed — only this change's own artifacts)
--- green 2026-10-28T20:04:46Z ---
$ bash tools/issue-tooling-selftest.sh --case 'update#refusesToDisplaceASettledState'
PASS update#refusesToDisplaceASettledState
```

The mutation is `[ "$named" -eq 1 ] && return 0` → `return 0` in `issues_status_settled_guard`
(`tools/issues/lib.sh:377`): every caller is let through, which is HEAD's behaviour. No
`REVERT-CHECK MUTATION` marker was written, so nothing of it can survive in the tree: the restore is the
original file byte for byte (`diff -q` clean), the scratch copy was removed, and the case's `PASS` above is that
file's.

The reopen's half of the rule is asserted in the gate's own portability self-test: the foreign-project reopen
in `tools/portability-cases.sh` opens an item carrying `status/fixed-by`, and an unnamed guard there would
refuse it. That case also asserts the report the reopen owes (#684, round 2): the `removed` list in its `--json`
document and, on a second closed fixture, the `removed status/fixed-by` clause on the line a person reads —
`tools/check-portability-selftest.sh` is green in `gate.md`.

## The suite the guard lives in

```
$ bash tools/issue-tooling-selftest.sh
…
issue-tooling-selftest: all 128 cases passed (JDK 27, offline, no token, HEAD 73223aaa, tools e3b0c44298fc, 2026-10-29T06:20:02Z)
```

The 128th case is #680's (`surface#theStubRefusesAnEditWithNoField`), which the rebase brought in; the tree this
change's own 127-case run covered had 127. The gate's own re-run on the final tree (`gate.md`) is the later
green for the same 128 cases.
