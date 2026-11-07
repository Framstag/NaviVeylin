# Proposal

## Why

`tools/issues/fixtures/gh` answers every `gh issue edit` by writing the invocation to the record log and
exiting 0: the arm at `tools/issues/fixtures/gh:272-276` calls `check_close_reason` for the `close` sub-command
and nothing at all for `edit`, and `record_write` inspects no flag. The real CLI refuses that shape —
`field to edit flag required when not running interactively` — and that refusal is the whole of what issue
#165 recorded: a bare `gh issue edit <n>` ran, `gh` printed its usage, and the call still reported
`updated: true`. The tool side is guarded today (`edit_needed`, `tools/issues/update.sh:192-196`, the write at
`:208-214`), so nothing is red.

**Root cause**: the suite's only `gh` cannot express the refusal, because the `edit` arm of its
`create|edit|close|comment|delete|reopen` branch validates nothing — so "an under-specified `gh issue edit` is
refused by the CLI" is a fact no case can observe, and a path added later that assembles an empty edit (a new
`close.sh`/`prioritise.sh` shape) stays green here until somebody runs the real CLI by hand: exactly how #165
reached the tracker with every suite green.

Evidence, all taken in this change root at base `1abb81f`:

- **Observed, on HEAD**: a bare `gh issue edit 21` with `GH_STUB_RECORD` set exits `0` and is recorded as
  `gh issue edit 21` (red case `surface#theStubRefusesAnEditWithNoField`, log in
  `evidence/red-on-head.log`).
- **The tool-side guard that makes it invisible**: `tools/issues/update.sh:192-196` computes `edit_needed`
  and only then issues the call, so no existing case reaches the edit arm without a flag.
- **The precedent in the same file**: `check_close_reason`, `tools/issues/fixtures/gh:92-105`, refuses a
  `gh issue close --reason` value the real CLI rejects — the same fidelity argument, for the closure reason
  (issue #195).

The single fix follows from the root cause: the stub's `edit` arm refuses an invocation that carries none of
`--add-label`, `--remove-label`, `--body-file`, with a non-zero exit and a message naming them, and one case
drives the stub directly to assert the refusal. Every existing legit write carries one of those flags
(`update.sh:177-189`, `close.sh:175`, `reopen.sh:111-113`, `prioritise.sh:332`), so nothing else changes.

## What Changes

- `tools/issues/fixtures/gh` gains `check_edit_field`, beside `check_close_reason`, called from the `edit` arm
  before `record_write`. It exits 1 and prints a `stub gh:` line naming the three flags when none is present,
  so the refusal is visible and the write is not recorded.
- `tools/issue-tooling-cases.sh` gains `surface#theStubRefusesAnEditWithNoField`: a bare `gh issue edit 21`
  exits non-zero, the message names the flags, nothing reaches the record log, and a legal
  `gh issue edit 21 --add-label class/bug` is still recorded unchanged.
- No production code, no engine, no engine behaviour: the fixture only. The refusal cannot change what any
  suite records for a legitimate write, because every caller in the tree carries a flag (measured below).

**Surface** (`grep -c 'issue edit' tools/issue-tooling-cases.sh tools/loop-cases.sh
tools/resolve-stale-cases.sh tools/sensor-cases.sh` → 32, 5, 9, 1 = **47 assertions** over the edit arm, in the
four suites that drive this stub): 1 case added, 0 cases rewritten, 1 fixture edited, 0 scenario fixtures
realigned. The stub is shared by five drivers — `tools/issue-tooling-selftest.sh`, `tools/loop-selftest.sh`,
`tools/resolve-stale-selftest.sh`, `tools/sensor-selftest.sh`, `tools/check-portability-selftest.sh`
(`grep -rln 'ISSUES_FIXTURES\|fixtures/gh' tools/*.sh` → 7 files, of which these five set the stub on `PATH`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `issue-backlog`: one requirement is added — the suite's stub `gh` refuses what the real CLI refuses on the
  `edit` shape it recorded unexamined. It is ADDED rather than MODIFIED because no existing requirement in
  `issue-backlog` speaks about the test double; the requirement the *defect* contradicts (`Closure is the only
  terminal state…` and the state-transition pair) is already satisfied on the tool side and is not rewritten.

## Impact

- `tools/issues/fixtures/gh` — the refusal (one function, one call line, the header's write list).
- `tools/issue-tooling-cases.sh` — the one new case.
- Existing writers keep working unchanged: `tools/issues/update.sh:177-189`, `tools/issues/close.sh:175`,
  `tools/issues/reopen.sh:111-113`, `tools/issues/prioritise.sh:332`, `tools/loop/labels.sh:133-135` each carry
  `--add-label`, `--remove-label` or `--body-file`.
- Not breaking a data format: no persisted schema, no migration, no dependency. Rollback is deleting
  `check_edit_field` and its call line, plus the case and the delta.
- No native, JNI, Android or submodule surface; tracker tooling and its offline suite only.
