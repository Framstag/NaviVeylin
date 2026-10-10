# Revert-checks — `widen-fix-loop`

Three new invariants, each falsified by one mutation against the case that must fail, then restored. The
discipline is `revert-check` (`guidelines/Build.md` §6); the marker is `REVERT-CHECK MUTATION`, removed on
restore and absent from the tree at the end.

## 5.1 — an improvement is inside the pre-screen's scope

- **invariant**: the pre-screen enumerates `improvement` alongside `bug`; only `feature` is dropped by class.
- **mutation**: restore the class reject — `In scope: `bug` only; an improvement is dropped, with its class as
  the reason.` in `SKILL.md`, step 1 of "Pre-screen the backlog".
- **case that must fail**: `pre-screen admits improvements` (selftest assertion (e)).

```
mutated   FAIL pre-screen admits improvements: expected [1] got [0]
          selftest: FAIL
restored  ok   pre-screen admits improvements
          selftest: PASS
          grep -c 'REVERT-CHECK MUTATION' SKILL.md  ->  1   (invariant 1's own prose naming the marker, not a leftover)
```

Assertion (e) was added by this task, which is why the design's D5 now names five guards rather than four.

## 5.2 — a state file carrying only `bugLimit` still resolves its budget

- **invariant**: every reader tolerates the pre-rename field, so an open run survives the `--items` change.
- **mutation**: delete the `// .bugLimit` fallback in `cmd_remaining` (`scripts/loop-state.sh`).
- **case that must fail**: `legacy bugLimit-only state resolves its budget`.

First round, with the assertion as D5 wrote it, **the mutation aborted the selftest instead of naming the
case** — a bare `$( … )` under `set -e` exits on the failing substitution. The behaviour was captured directly
and the assertion hardened to capture the exit code and the output under `set +e`:

```
direct    LOOP_STATE=<legacy> loop-state.sh remaining
          jq: error (at …/legacy.json:18): null (null) and number (0) cannot be subtracted
          exit=5
mutated   FAIL legacy bugLimit-only state resolves its budget: expected exit 0 got 5
          FAIL legacy bugLimit-only state: [jq: error (at …/legacy.json:18): null (null) and number (0) cannot be subtracted]
          selftest: FAIL
restored  ok   legacy bugLimit-only state resolves its budget (exit 0)
          ok   legacy bugLimit-only state: 3 items left, 60 min left (deadline …)
          selftest: PASS
          grep -c 'REVERT-CHECK MUTATION' scripts/loop-state.sh  ->  0
          grep -c 'itemLimit // .bugLimit' scripts/loop-state.sh ->  5   (the fallback is back)
```

## 5.3 — the copy-out rule is stated once

- **invariant**: `SKILL.md` carries the cited-XML copy-out instruction exactly once (invariant 1).
- **mutation**: append a second copy of that instruction.
- **case that must fail**: `copy-out rule stated once` (selftest assertion (d)).

```
mutated   FAIL copy-out rule stated once: expected [1] got [2]
          selftest: FAIL
restored  ok   copy-out rule stated once
          selftest: PASS
          wc -l SKILL.md -> 377      (the pre-mutation size)
          grep -c 'REVERT-CHECK MUTATION' SKILL.md -> 1   (invariant 1's prose only)
```

## Baseline, before any mutation

The three structure guards, whose subject is the document this change rewrites, are **red on the
un-restructured document** (`0444fc6`) and green after — which is what makes them falsifiable rather than
tautological:

```
                         on 0444fc6      after
no report-template heading is a section   1   ->   0
report template still present             0   ->   1
gate table has seven conditions           6   ->   7
copy-out rule stated once                 1   ->   1   (regression guard; red only under 5.3)
pre-screen admits improvements        (n/a)   ->   1   (added by 5.1; red under 5.1)
```
