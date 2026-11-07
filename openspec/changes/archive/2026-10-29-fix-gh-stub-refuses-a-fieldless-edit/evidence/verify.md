# Verification — `fix-gh-stub-refuses-a-fieldless-edit`

Change root: `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, base `1abb81f`.

## The red case, on HEAD

```
$ bash tools/issue-tooling-selftest.sh --case surface#theStubRefusesAnEditWithNoField
  the stub recorded a fieldless stub gh: recorded `gh issue edit`, which the real CLI refuses: stub gh: recorded `gh issue edit`
FAIL surface#theStubRefusesAnEditWithNoField
```

`evidence/red-on-head.log`, taken 2026-10-29T05:23:24Z. The driver's `--case` path writes no JUnit XML
(`tools/issue-tooling-selftest.sh:76-90` prints `PASS`/`FAIL` and exits), so the red record is this log and the
suite's XML below is the post-fix one.

## The fix, green

```
$ bash tools/issue-tooling-selftest.sh --case surface#theStubRefusesAnEditWithNoField
PASS surface#theStubRefusesAnEditWithNoField
```

Focused cases over the writers that reach the same arm, all `PASS`:

- `update#statusReplacesTheOldStatus` — one `gh issue edit` carrying the state.
- `update#commentOnlyReportsNoStatusWrite` — the #165 tool-side anchor (`tools/issue-tooling-cases.sh:1742`).
- `close#writesTheSettledStatus`
- `duplicates#youngerDuplicateIsClosedWithAPointer`
- `surface#reservationIsNotBypassedByTheOtherLabelFlags`

```
$ bash tools/resolve-stale-selftest.sh
resolve-stale-selftest: all 36 cases passed (JDK 27, offline, no token, no Gradle)
```

`resolve-stale` is the other suite whose writer passes `--body-file` (`tools/issues/resolve-stale.sh:190-222`),
so it is the case that would break if the flag set were read wrongly.

## The gate — run once

```
$ ./tools/loop/gate.sh
```

Its summary line is `evidence/gate.log`, verbatim. It is the aggregate of the seven steps below;
`tools/loop/gate.sh` writes one XML per step and no aggregate XML (`tools/loop/gate.sh:296-312`), so each
step's own tally is the carrier and the aggregate is not retained as one:

| step | tally (`evidence/`) |
|---|---|
| `tools/issue-tooling-selftest.sh` | `tests="126" failures="0" errors="0"` |
| `tools/issues/check-metadata.sh` | `failures="0"` |
| `tools/issues/metadata-mutations.sh --list` | `failures="0"` |
| `tools/check-portability.sh` | `failures="0"` |
| `tools/check-portability-selftest.sh` | `failures="0"` |
| `tools/mutate-selftest.sh` | `failures="0"` |
| `tools/mutate.sh --mutations tools/portability-mutations.json --list` | `failures="0"` |

The seven steps are exactly what `tools/issues/*` owes in `tools/loop/loop.conf`'s `LOOP_GATE_SUITES`.

Every step's XML is under `evidence/`, copied at the moment the gate wrote it.

## Structural check of the edited file (no build)

This change compiles nothing — the fixture is a shell script and the case is a shell function:

```
$ bash -n tools/issues/fixtures/gh && bash -n tools/issue-tooling-cases.sh && echo syntax-ok
syntax-ok
```

## Why the other writers cannot be affected

47 assertions name `issue edit` across the four case files that drive this stub
(`grep -c 'issue edit' tools/issue-tooling-cases.sh tools/loop-cases.sh tools/resolve-stale-cases.sh
tools/sensor-cases.sh` → 32, 5, 9, 1), and every one of them drives a writer that passes `--add-label`,
`--remove-label` or `--body-file`: `tools/issues/update.sh:177-189` (guarded by `edit_needed`, `:192-196`),
`tools/issues/close.sh:175`, `tools/issues/reopen.sh:111-113`, `tools/issues/prioritise.sh:332`,
`tools/loop/labels.sh:133-135`.
