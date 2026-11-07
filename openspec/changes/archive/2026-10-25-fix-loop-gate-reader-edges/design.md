# Design — closing the gate map's reader path side

## The root cause, and the alternatives it eliminates

The defect is one mechanism: the map's path side is a hand-kept copy of which paths each pipeline step's tools
read, and `Check portability`'s seven reader edges are missing from it, so `gate.sh` answers `nothing owed` for
a path that step's tools open. The owner's recorded answer to issue #289 is option 1; the other three options
are eliminated, not preferred over:

1. **Map only the three config rows to `Check portability`'s commands, leaving the engine globs.** Eliminated
   by measurement: `probe-plan.txt` §2 shows a delta touching `tools/issues/create.sh` and `tools/loop/worktree.sh`
   still owes no command of the step, although both are in the engine set the check scans and one of them is the
   file the pipeline's own `portability#plantedSubmodulePathFails` case plants a leak into. Mapping three of
   seven edges ships a map that reads complete.
2. **Map only the commands that run in the change root, leaving the self-test out.** Eliminated by the map's own
   pinned invariant: `loop.conf` and `gate#aPipelineCheckIsOwedByItsPaths` hold that a step is a unit, and
   mutation `54.4` names a partial step the rejected option 2 of #242. It would also leave
   `tools/check-portability-selftest.sh` — the exact command that reddened `main` on 2026-10-21 — ungated for
   every reader path.
3. **Leave the residual and only correct the prose.** Eliminated by the owner's decision: bound 0 stays open by
   policy, which is the trade the item was filed to decide.
4. **Derive the reader edges mechanically.** Not eliminated as *wrong*, deferred as out of scope: it is a new
   engine feature in `check-portability.sh`/`build-inputs.sh` with its own cases, mutations and a bound on the
   heuristic's false matches, and the item's option 1 needs none of that. The residual a later step's edges
   leave is named in `guidelines/Build.md` §3 either way.

## The decision

Map the whole step for every measured reader edge, and make the one command of that step which cannot run in
the change root runnable there. A path a step's tool reads owes that step's commands; a step is a unit. This is
the only option that closes the measured edges without bending `gate#aPipelineCheckIsOwedByItsPaths` and without
leaving the command that reddened `main` ungated. The review round measured a second step the same way —
`Check issue tooling`, whose case greps `tools/loop/*.sh` and `tools/sensors/*.sh` and reads
`tools/sensors/sensors.json` — and the owner's answer was the same whole-step rule, so its six commands are
mapped too.

## The precondition, asked of git rather than of the path

The suite refuses a `/.pi/` root because a *copy* under machine-local state is not a checkout — but the loop's
change root is `.pi/worktrees/loop`, a linked working tree, which carries the tracked tree and the fixture
exactly as the main checkout does. The shell cannot tell a copy from a checkout by the path, so it asks git:
`git -C "$root" rev-parse --show-prefix` is empty at the top of a working tree and non-empty for a copy nested
inside someone else's tree. This is the same distinction the `tooling-portability` requirement already draws
for a tool invoked in a linked working tree ("that working tree's own profile and repository are used").

- Risk: a worktree is a full checkout, so `portability#passesOnTheRealTree` and `foreign#fixtureIsVersioned`
  run against the worktree's content — measured `all 29 cases passed` in `.pi/worktrees/`, `passesOnTheRealTree`
  and `fixtureIsVersioned` among them (`evidence/worktree-run.txt`). No path of the main checkout is read.
- Risk: the refined precondition widens what is accepted. The refusal case still passes, because a copy at
  `<repo>/.pi/snapshot` has a non-empty `--show-prefix`; mutation `6.5` still breaks it, and `6.6` breaks the
  new allowance.
- Threading/lifecycle: none. The change is shell tooling; no dispatcher, no device, no network, exit codes are
  the only contract.

## Verification

- `bash tools/loop-selftest.sh --case gate#aPathACheckReadsOwesThatStepsCommandSet` — red on HEAD
  (`evidence/red-on-HEAD.log`), green after the map pairs.
- `bash tools/check-portability-selftest.sh` — `all 29 cases passed` in the main tree and in
  `.pi/worktrees/` (`evidence/full-suite.log`, `evidence/worktree-run.txt`).
- `bash tools/loop-mutations.sh --only 54.5` and `--only 54.6`;
  `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 6.6` — each `RED` then `GREEN`.
- `bash tools/loop-selftest.sh --case gate#theMapCoversEveryPipelineStep` and
  `--case gate#aPipelineCheckIsOwedByItsPaths` — no regression in the command side or the step-is-a-unit rule.
- `openspec validate fix-loop-gate-reader-edges --strict`.
