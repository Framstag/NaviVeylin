## Root cause

`shift 2` with one word left in argv fails, leaves `$#` at 1 and the parse loop re-enters with the same `$1`
for ever (`tools/loop/run.sh:44`, base commit `a04ef9bf`), so the value never reaches the validation after
the loop and the refusal is silent — exit 124 under `timeout 5`, empty stdout and stderr. The same arm shape
is in 152 places across 35 engines: the defect is the *shape*, not the file.

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

The two shapes are one rule and the sweep case asserts both, so neither can drift: every arm that ends in
`shift 2 ;;` must also carry `need_value` or `needs a value`.

## Blast radius

- 152 flag arms in 35 engines change behaviour only on the input that used to hang: a missing value, or a
  value that is itself a flag. No flag of this tooling takes a value beginning with `--`, checked by
  `grep -rE '\-\-[a-z-]+ +--[a-z]'` over the case files (the only matches are consecutive boolean flags).
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
  (`tools/issue-tooling-selftest.sh`): red on the base commit (four FAILs recorded in
  `evidence/red-on-HEAD.log`), green after the change (`evidence/green-after-fix.log`).
- The affected suites: `tools/loop-selftest.sh` 111 of 112 cases pass; the one failure,
  `worktree#keepsByDefaultAndRemovesOnRequest`, is an artefact of running the suite from inside the loop's own
  worktree — the fixture's repository path then contains `worktrees/loop`, which the case greps for in
  `git worktree list`; the same case passes in the project's tree at the same commit.
- No compiled file is touched, so the project's Gradle gate exercises nothing this change touches
  (`guidelines/Build.md` §2, the scope block). It is still run once for the run's own evidence, and its tally is
  quoted as what it is.
