# The revert-checks

The guard is a refusal path, so it owes a mutation: with the refusal removed the case that asserts it must go
red, and the restore must bring it back green (`revert-check` skill, `guidelines/Build.md` §2).

Run from the change root, 2026-10-28:

```
$ bash tools/mutate.sh --mutations tools/status-mutations.json --only 8.1
8.1	update#refusesToDisplaceASettledState	the guard refusing a routine write that would displace a settled state instead of letting every caller through
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/lib.sh
  GREEN(exit 0)  restored, and the case passes again

mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
mutate: the whole set is falsified — every named case failed with its mutation in place and passed again after the restore
```

The mutation is `[ "$named" -eq 1 ] && return 0` → `return 0` in `issues_status_settled_guard`
(`tools/issues/lib.sh`): every caller is let through, which is HEAD's behaviour, and
`update#refusesToDisplaceASettledState` fails on it. The other entries of the set still pass, so the guard did
not disturb the replacement rule it sits beside.

The closure's and the reopen's half of the rule is asserted by `close#namesTheSettledStateItDisplaces` (green in
the suite run below) and by the foreign-project reopen in `tools/portability-cases.sh`, whose item carries
`status/fixed-by` when it is reopened — an unnamed guard there would refuse and that case would fail.

## The suite the guard lives in

```
$ bash tools/issue-tooling-selftest.sh
…
issue-tooling-selftest: all 127 cases passed (JDK 27, offline, no token, HEAD c2969186, tools 1c75ef89e054, 2026-10-28T19:48:33Z)
```
