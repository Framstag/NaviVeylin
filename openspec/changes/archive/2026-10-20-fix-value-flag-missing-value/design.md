## Root cause

`shift 2` with one word left in argv fails, leaves `$#` at 1 and the parse loop re-enters with the same `$1`
for ever (`tools/loop/run.sh:44`, base commit `a04ef9bf`), so the value never reaches the validation after
the loop and the refusal is silent — exit 124 under `timeout 5`, empty stdout and stderr. The same arm shape,
or a variant of it that reads `${2:-}` without a `shift` at all, is in 162 places across 45 engines: the defect
is the *shape*, not the file.

## The single fix

One check before the arm consumes anything, stated once per source graph:

- `need_value FLAG [VALUE…]` in `tools/issues/lib.sh` — the root of the engine's source graph, since
  `tools/loop/lib.sh` and `tools/sensors/lib.sh` both source it. Called as `need_value "$@"` at the head of
  the arm; it names `$0`'s own tool and the flag, exits 2, and shifts nothing, so the arm keeps its own
  `shift`.
- The nine engines that source no library (`tools/mutate.sh`, `tools/gen-feature-list.sh`,
  `tools/fetch-build-inputs.sh`, `tools/build-inputs.sh`, `tools/loop-mutations.sh` and the four
  `tools/check-*.sh`) carry the same check as one inline expression — they exist to be runnable on their own,
  and making them depend on the 500-line issue engine would invert that.
- The ten single-shot harnesses (seven self-tests, three mutation harnesses) carry the same inline expression
  in their `--case`/`--only` arm. Their variant reads `${2:-}` with no `shift`, so nothing hangs; the defect
  there is the silence — `--case` with nothing after it ran every case, `--only` with nothing after it ran the
  whole mutation set, which rewrites and restores the tree. The review of 2026-10-20 found them because the
  first sweep grepped only `shift 2 ;;` and excluded every `-selftest.sh` (review finding B1).

The three shapes are one rule, and the sweep case reads arm lines (`:--flag) …`) for either `${2:-}` or
`shift 2 ;;`, scans the harnesses, and excludes only the `*-cases.sh` files. A new arm written either way is
red on the next run.

## Blast radius

- 162 flag arms in 45 engines change behaviour only on the input that used to hang or silently run everything:
  a missing value, or a value that is itself a flag. No flag of this tooling accepts `--flag=value`, so a
  free-text value whose first word begins with `--` is now *unreachable*, not merely checked: the refusal is
  the only outcome. The five prose flags (`--evidence`, `--reason`, `--note`, `--finding`, `--title`) are the
  ones a person could hit; every call in the tree passes a value that starts with a word, checked by
  `grep -rE '\-\-[a-z-]+ +--[a-z]'` over the case files (the only matches are consecutive boolean flags). A
  later need for such a value adds `--flag=value` support to the parser rather than weakening the guard.
- `$?` after the guard is unchanged; no script uses `set -e`, and `||` inside the guard is safe under
  `set -uo pipefail`.
- The parse loop's other arms (booleans, `-h|--help`, the `*)` catch-all) are untouched.
- Rollback: revert the commit; nothing persisted depends on the guards.

## Alternatives the evidence eliminated

- **A `shift` wrapper that fails loudly** (`shift() { builtin shift "$@" || die …; }`): the message cannot name
  the flag, the override silently changes a builtin for every sourced script, and the failure would fire in
  the ten places that shift two *function arguments* rather than two argv words (`tools/loop/run.sh:549`,
  `tools/loop-cases.sh:372`, …). Eliminated by the tree itself.
- **Validating after the loop** — already there, and unreachable: the loop never exits. The measurement
  (`timeout 5 … → 124, empty output`) is what proves the position, so the check must be before the `shift`.
- **One guard inside each script's `while` head** (e.g. a `need 2` at the top of the loop body): it cannot
  tell a value flag from a boolean one, so `--dry-run` would start demanding a value.
- **Letting `set -e`/`set -u` stop it**: the engines deliberately run `set -uo pipefail` without `-e`, and
  `set -e` would abort on every non-zero status a probe is meant to observe.

## Verification

- `usage#bareValueFlagAtTheEndOfArgvIsAUsageError`, `usage#bareValueFlagDoesNotSwallowTheNextFlag`,
  `usage#noUnguardedValueFlagRemains` (`tools/loop-selftest.sh`) and `create#bareValueFlagNeedsAValue`
  (`tools/issue-tooling-selftest.sh`): red on the base commit, three of them in `evidence/red-on-HEAD.log`
  (2026-10-20T07:11:51Z) and the sweep in `evidence/red-on-HEAD-first-run.log` (154, superseded) and
  `evidence/red-on-HEAD-sweep.log` (152). Green after the change (`evidence/green-after-fix.log`).
- The two cases the review demanded — `usage#bareValueFlagInASingleShotHarnessIsAUsageError` and the widened
  `usage#noUnguardedValueFlagRemains` — are red with the ten guards removed and green with them restored, in
  `evidence/red-round2-single-shot-and-sweep.log` (2026-10-20T07:38Z, the script that mutates also restores
  and verifies the restore). The same log holds the defect measured directly, without the case's 20 s bound:
  `bash tools/sensor-selftest.sh --case` with its guard removed printed `all 43 cases passed` and exited 0.
  Green after the corrections: `evidence/green-round2.log` (five cases, and each of the ten harnesses in its
  normal `--list` mode).
- The affected suites: `tools/loop-selftest.sh` 112 of 113 cases pass in the change's worktree; the one
  failure, `worktree#keepsByDefaultAndRemovesOnRequest`, is an artefact of the suite's root path containing
  `worktrees/loop` — the fixture repository then carries that string and the case greps for it in
  `git worktree list`. The case passes from the project's tree with the scratch root moved to `/tmp`
  (`evidence/project-tree-worktree-case.log`, engine checksums identical; nothing written into the project's
  tree). The case is path-sensitive for any suite run inside the loop's own worktree, which predates this
  change and is not repaired by it.
- No compiled file is touched, so the project's Gradle gate exercises nothing this change touches
  (`guidelines/Build.md` §2, the scope block). It is still run once for the run's own evidence, and its tally is
  quoted as what it is.
