# fix-loop-adopt-verification-leak — design

## The decision this change does not have to make

The root-cause evidence is a two-line report from `tools/check-portability.sh`: the value `verification`
is read from `tools/tooling.json` `namespaces.category` and named in an engine. There is no open question —
the engine names a project value and must stop — so the alternatives below are the ones the evidence
eliminated, not a menu.

## Alternatives the evidence eliminated

1. **Move the value into the profile/config, as #162 did for the loop's other values.** Impossible here:
   #162 moved *values the engine was deliberately working with* (the submodule path, the class/status
   vocabulary) into `loop.conf` and the profile. This occurrence is a local variable's name — a word of
   English that happens to equal a category value. There is nothing to configure; the config already holds
   the value, and reading it back into the engine would name it harder, not softer.
2. **Add `verification` to the check's exclusions or allowlist.** Rejected on measurement: the check's
   excluded set is the source adapter, test fixtures and the project's documents
   (`check-portability.sh`, printed at the end of every run), and its forbidding set is derived from the
   profile at run time (design D1 of `tooling-portability`). An allowlist entry would make the check lie
   about its own scope — the next genuine leak of the word `verification` would pass — which is the failure
   mode the value-set reading exists to prevent.
3. **Widen the check's "word" heuristic so a `$var` reference is not a named value.** Rejected as a larger
   change with a worse failure mode: the heuristic already distinguishes identifiers, sentences and paths
   from names, and narrowing it by context would let a real leak through in exchange for tolerating a
   variable name. The engine, not the check, is what broke the rule.
4. **Suppress the gate for this change.** Rejected: the gate is a step of the build workflow, and a
   suppression is indistinguishable from a leak to every later reader and to the scheduled sensor ticks.

**Chosen**: rename the local variable. It is the smallest edit that satisfies the requirement, it changes
no value, output or state, and the check itself is the falsifier — the same command that reported two leaks
reports none.

## Files and mechanisms

| file | change |
|---|---|
| `tools/loop/worktree.sh` | `cmd_adopt_item`: `local … verification …` → `evidence`; `[ -n "$verification" ]` → `[ -n "$evidence" ]`; the `printf 'adopted: … evidence %s\n'` argument follows |

Nothing else: the function name `carried_verification`, the refusal message "carries no green verification
record" and the header prose stay, because the check does not read identifiers or sentences — verified by
the green run rather than assumed.

## Risk

Low and bounded: a rename inside one shell function, with the printed line's field label (`evidence`)
already matching the new name. The regression the change repairs was caused by *adding* an engine to the
loop, so the risk of this change is that the loop's adopt path stops printing or stops refusing — both are
covered by the loop suite's adopt cases (`run#mergesAFinishedAttemptAndClosesTheItem`,
`run#doesNotMergeAnAttemptWithoutAGreenVerification`), of which the first is re-run here.

## Threading, lifecycle, platform

None. Shell tooling, no compiled file, no Android surface, no threading or lifecycle concern.

## Verification

| what | how | evidence |
|---|---|---|
| the defect, on HEAD | `bash tools/check-portability.sh` → `2 leak(s)`, exit 1 | `evidence/red-on-head.log` |
| the repair | the same command → `check-portability: OK — … 0 value(s)`, exit 0 | `evidence/green-on-fix.log` |
| no case regressed | `bash tools/check-portability-selftest.sh --case portability#passesOnTheRealTree` | `evidence/scoped-cases.log` |
| the adopt path still works | `bash tools/loop-selftest.sh --case run#mergesAFinishedAttemptAndClosesTheItem` | `evidence/scoped-cases.log` |

**Named as owed, not skipped silently**: the full `tools/loop-selftest.sh` suite and the loop mutation set
(`tools/loop-mutations.sh`) — the change touches no invariant and no case's target line, and the two scoped
runs above are the ones that move with it. The next full gate run is what closes that gap.
