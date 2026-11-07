# Design — two questions that read the same today

## Root cause

`tools/loop-selftest.sh` answers a file set with one outcome beside "ran these cases": a refusal, exit 2, whose
message is `the file set names no case of the N declared — a run that executes nothing would report success, so
this is a refusal` (`tools/loop-selftest.sh:652`). That message is written for the case it was designed against —
a caller who mistyped a path — and it is correct there.

A second caller arrived with the same shape and a different meaning: a CI step that is handed a real delta. Its
question is "does this delta owe any case?", and the answer "no" is ordinary — measured on this tree, `app/`
code and most capability specs declare no case at all, because a case declares the engine scripts it can be
reached through, and application code is reached through none of them.

The two questions cannot be told apart from the answer, because they share one status and one sentence. A caller
that wants to narrow has no way to distinguish "nothing owed" from "you made a typo", so it must either refuse
both or swallow both — and swallowing both is how a narrowed suite would quietly stop covering its engine.

## The fix

An explicit opt-in, in the shape this repository already uses where a caller must say which question it is asking
(`tools/issues/create.sh --allow-duplicate`, which records its override):

- `--allow-empty` makes an empty intersection a **statement**, not a refusal:
  `LOOP-SELFTEST: no case declares the delta` plus the file set it read and the declared set it selected against,
  nothing run, exit 0.
- Without it, the refusal is untouched, byte for byte. A typo is still a refusal, because a caller who has not
  declared that it expects emptiness cannot be told by the driver that emptiness is what it found.

The flag is deliberately about the *intersection*, not about validity: a file set whose paths the tree does not
hold is a separate question the driver does not ask today (the paths come from `git diff`), and this change keeps
it that way rather than growing a second notion of a valid path.

## Why the workflow passes a base rather than a path list

The delta is computed by git in the workflow, because the workflow is the only place that knows which event it is
answering:

```
--from-git ${{ github.event.pull_request.base.sha || github.event.before }}
```

- a pull request: its base commit, so the delta is the branch;
- a push: `github.event.before`, the previous head of the branch, so the delta is what landed;
- a push whose `before` is the all-zeros SHA — the first push to a branch, or a force-push whose base git does not
  know: the whole set, because no base exists to narrow against.

A core file (`tools/loop/lib.sh`, `loop-cases.sh`, `loop.conf`, the driver, the profile) selects every case
whatever the delta, so the engine's own changes are still paid for in full.

## Why not the alternatives

- **Make emptiness exit 0 always.** A caller that narrows by a mistyped path would get a green suite that ran
  nothing, and the message would be a warning nobody reads. This is the failure the refusal exists to prevent.
- **A third exit status (e.g. 3).** Works, and costs the caller a `case` statement over a status it must know; the
  flag costs the caller the same statement over the same knowledge, and says at the invocation what the caller
  expects. A status is also harder to keep distinct: `2` and `3` are both "not a pass" to every `set -e` caller.
- **Narrow in the gate only and leave CI alone.** The gate is not run per push; the 17 minutes are per push.
- **Narrow by "the change's declared cases" (`tools/declared-cases.sh`).** That set is the cases of the change
  being applied, which at CI time is often no change at all — it answers a different question, and using it here
  would silence the suite on a delta that no change describes.

## Blast radius

- One existing case's semantics are untouched: `driver#aFileSetThatNamesNoCaseIsRefused` runs without the flag and
  must keep exiting 2.
- One new case: `driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt` — the same file set, once with and once without
  the flag, so the pair is the contract.
- The mutation set gains one record beside 53.8 (`aFileSetThatNamesNoCaseIsRefused`): with `--allow-empty` the
  intersection must be stated rather than swallowed.
- A step that skips now prints a line saying which delta was covered by nothing. That line is the evidence a
  reviewer reads when the suite did not run, and its absence is a bug: the step must never be silent about it.

## One record beyond the plan, and why it is not scope creep

The plan above assumed the pipeline names the command the suite map names. It does not: the leg split
(`a32b1dfa`, on the base) made the step run `tools/loop-selftest.sh --group "$g"` inside a loop, so
`gate#theMapCoversEveryPipelineStep` was **already red on the base** for a reason that has nothing to do with
this change — and a step that now carries a run-time substitution of its own (`${{ steps.delta.outputs.args }}`)
cannot be pinned at all until both sides are compared on the part they share. The pin therefore drops the
expressions and the two run-time narrowings (`--group`, `--from-git`) from both sides before it compares
(`evidence/pin-on-the-base.txt`), and record `80.2` mutates that normalisation away. Leaving the pin red would
mean archiving a change that cannot run its own suite green; repairing it is the smallest way this change keeps
its own gate meaningful. The open change `loop-suite-legs-across-runners` carries the same repair as its own
task 4.1, so the two lines will conflict and whoever lands second takes the other's copy.

## Open decisions

none
