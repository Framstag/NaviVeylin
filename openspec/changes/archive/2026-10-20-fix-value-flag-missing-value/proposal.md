## Why

A value flag written without its value made the tooling hang instead of failing as usage, and the same
unguarded consumption was in every one of the tooling's parsers.

- **Verified in the tree today** ✗: every value flag in the parse loop of `tools/loop/run.sh` is written as
  `--push) push="${2:-}"; shift 2 ;;` (`tools/loop/run.sh:45` on the base commit
  `a04ef9bf52a220f377d98a99557ad14e0f72d6e4`), with the same line for `--items`, `--minutes`, `--review`,
  `--may-close`, `--max-decisions`, `--max-review-rounds`, `--review-read-only` and `--base`. Reproduced:
  `timeout 5 bash tools/loop/run.sh --items 100 --minutes 480 --push` exits 124 with empty stdout and empty
  stderr — no header, no usage line, no error — while the flag form that carries a value is refused
  correctly (`--push=yes` prints `run: unknown argument --push=yes`).
- **Root cause**: `shift 2` with one word left in argv fails, leaves `$#` at 1, and the `while [ $# -gt 0 ]`
  loop re-enters with the same `$1`, overwriting the variable with its empty default — for ever. The
  validation that would have refused the empty value sits after the loop and is therefore never reached, and
  the `shift` failure is silent in a non-interactive shell. One mechanism, one fix: the value is checked
  before the arm consumes the two words.
- **Scope, verified in the tree today** ✗: the shape is not `run.sh`'s. `grep -rn 'shift 2 ;;' tools
  --include='*.sh'` finds 152 occurrences in 35 engines with no guard — `tools/loop/state.sh` (29),
  `tools/loop/run.sh` (9), `tools/issues/list.sh` (8), `tools/sensors/run.sh`, `tools/issues/update.sh`,
  `tools/issues/prioritise.sh`, `tools/gen-feature-list.sh` (7 each), down to one in
  `tools/check-portability.sh`. Which of them hang depends only on where the bare flag lands in argv; none
  can report the missing value, because none checks for it before shifting.
- **Red test** (base commit, 2026-10-20T07:11Z, `tools/loop-selftest.sh --case …` and
  `tools/issue-tooling-selftest.sh --case …`, logs in `.pi/issue-loop/items/190/red-3.log`):

  | case | on HEAD |
  |---|---|
  | `usage#bareValueFlagAtTheEndOfArgvIsAUsageError` | FAIL — `run.sh --items 100 --minutes 480 --push` exited 124, not 2 |
  | `usage#bareValueFlagDoesNotSwallowTheNextFlag` | FAIL — `run.sh --items 100 --push --dry-run` answered `run: --push must be yes or no` |
  | `usage#noUnguardedValueFlagRemains` | FAIL — 152 value flags still consume their value before checking it |
  | `create#bareValueFlagNeedsAValue` | FAIL — `create.sh --finding --class bug` answered `create: unknown argument bug` |

- **Owning requirement**: `tooling-portability` — the capability that owns the tooling's shared engine
  behaviour ("the whole toolchain runs in the foreign project"): a hang in an engine is a defect every
  project that copies the tooling inherits. Owning guideline sections: `guidelines/Backlog.md` §9 "Checks"
  (the self-tests are the gate for `tools/**`) and §10 "Portability" (engine versus project).
- **Why it matters**: a hung run is indistinguishable from the pre-screen's deliberate silence — `run.sh`
  prints the pre-screen's output only when it finishes (measured 53 s) — so the operator's only signal is a
  run that never reaches its second phase. Measured on 2026-10-20: the bare `--push` invocation was
  interrupted by hand after no output, and the two loop runs beside it were interrupted in the same silent
  window (`tools/loop/run.sh:38-54`, the parse loop).

## What Changes

- One helper, `need_value`, in `tools/issues/lib.sh` — the root of the engine's source graph, reached by
  `tools/issues/**` directly and by `tools/loop/**` and `tools/sensors/**` through their own libraries. A
  value flag whose value is missing, or whose next word is itself a flag, is a usage error naming the tool and
  the flag, before the arm consumes anything.
- Every one of the 152 value-flag arms is guarded: `need_value "$@"` inserted before the arm's own read and
  `shift`, in the 26 engines that source a library, and the same check inline in the 9 standalone engines that
  source none (`tools/mutate.sh`, `tools/gen-feature-list.sh`, `tools/fetch-build-inputs.sh`,
  `tools/build-inputs.sh`, `tools/loop-mutations.sh`, the four `tools/check-*.sh`).
- Three new cases in `tools/loop-selftest.sh` and one in `tools/issue-tooling-selftest.sh`: the bare flag at
  the end of argv, the bare flag in front of another flag, and a sweep over the tree that makes a newly
  written unguarded arm a red case on the next run instead of a hang discovered by an operator.

Additive; no behaviour that worked before changes, except that a value beginning with `--` is now refused —
no flag of this tooling takes one. Rollback: `git revert` restores the previous one-line arms; nothing
persisted depends on the change.

## Impact

- Modified: `tools/issues/lib.sh` (the helper), 26 engines that source it, 9 standalone engines, and the two
  case files/suites (`tools/loop-cases.sh`, `tools/issue-tooling-cases.sh`).
- Affected specs: `tooling-portability` (one ADDED requirement); `agent-issue-loop`, `issue-backlog` and
  `backlog-sensors` gain nothing — the rule is the engines' shared one, stated once.
- Affected guidelines: none contradicts the change; `guidelines/Backlog.md` §9 and §10 are the sections read.
