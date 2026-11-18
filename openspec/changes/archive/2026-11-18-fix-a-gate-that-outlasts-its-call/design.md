# Design — the child is told the wall, and how to get past it

## The defect, from the evidence

The prompt hands the gate as one line (`tools/loop/prompts/worker-gate.md:103` on the base,
`{{GATE_CMD}}`), and nothing in either skeleton names how long a tool call lives. The child therefore does the only
thing it is told: it runs the gate inline. When the gate's suites outlast the call, the harness ends the call and the
child's session ends with no `LOOP-GATE:` line — and the run's evidence rule (`agent-issue-loop`, "The gate's evidence is
the tally this item's gate wrote") then reads the item's tree as having no gate execution at all.

The three alternatives the evidence eliminates:

1. **Raise the ceiling a tool call may reach.** Impossible as a project fix: the ceiling belongs to the harness that
   runs the child, and this project cannot see it (`tools/loop/loop.conf:431`-`:450` — `LOOP_CALL_CEILING_SECONDS` exists
   because a project file *cannot* read the harness's own value; it is named by a caller, and only for the process the
   caller started). Any ceiling below the longest gate leaves the defect: the run that filed this issue had 2 694 s and
   3 025 s gates against a 1 500 s ceiling. A ceiling above the longest gate is the other defect — the liveness guard
   exists because a hung child holds the host (`guidelines/BuildGate.md` §2b).
2. **Run the gate in the run's own process instead of the child's.** Not this defect's fix: the tally, the archived
   change and the evidence are the phase that stands before the gate (issue #837), the deferred shape's gate child is
   the one that must quote the verdict, and moving the gate across the process boundary is a redesign of phase E. It
   would also change what a run pays for an item, which no measurement here licenses.
3. **Teach the child to start the gate detached and poll it** (chosen). Costs no engine code, uses two primitives the
   child's own shell already allows (`nohup … &`, `sleep`, `tail -n` — a wait and a range read, where `grep`/`cat`/`head`
   over the tree are refused, issue #687), and the guard against the one abuse it invites — starting a second gate — is
   the engine's own lock refusal, exit `4`, `LOOP-GATE-REFUSED: another gate of this tree holds …`
   (`tools/loop/gate.sh:491`), which measures nothing and is not a red gate.

## The renderer defect the first gate found

The first full gate over this change went red with four failures, three of them in cases that render `worker.md`
(`run#aCorrectiveRoundIsHandedTheCorrections`, `run#childPastItsContextBoundIsCut`,
`run#aWorkerAtItsCheckpointIsContinuedBeforeTheBound`), and the assertion named the cause: `every placeholder in the
round-2 worker's prompt is substituted: '{{' should not appear in …`, with the rendered text carrying
`cd … {{GATE_STEP}}{{GATE_STEP}} nohup … 2>{{GATE_STEP}}1 {{GATE_STEP}}` where the section's `&&` and `2>&1` were.

`loop_render` (`tools/loop/lib.sh:967`) substitutes with `${tpl//\{\{$key\}\}/$value}`, and bash 5.2's
`patsub_replacement` makes `&` in the **replacement** mean the matched text; this host's bash 5.3.20 has it on by default.
So a value carrying an ampersand is re-read as a copy of the placeholder — not only the new section, but every substituted
value: the gate command, a work package path, a reviewer's finding.

Alternatives: (a) keep every prompt ampersand-free — impossible for this fix, because a background start *is* `&`, and
`2>&1` cannot be spelled without one; (b) escape `&` in the value before substituting — wrong on a shell whose
`patsub_replacement` is off, where the escape reaches the prompt as a literal backslash, and this engine runs on whatever
bash the host has; (c) turn the option off around the substitution and restore it after (chosen) — the option is the
shell's setting and not this function's, so it is read, cleared for the loop and put back, and older bash versions are
untouched because the guard is behind `shopt -q`.

## What the prompt says, and why each sentence is there

- **The wall, with the two numbers it is measured against** — the harness's tool call (900 s and 1 500 s measured here)
  and the profile's own clocks (`LOOP_GATE_STEP_MAX_SECONDS`, `LOOP_ITEM_GATE_SECONDS`), which are longer. A child that
  is told only "run it once" cannot tell that the shape is wrong for its own gate.
- **The detached start, with the log in `{{EVIDENCE_DIR}}`** — the evidence directory is the one directory a child may
  still write after the gate, so the log is a copy the tail can keep; a log in the change root would be a file the
  round's diff reads as the change's own work.
- **The poll** — one call per poll, `sleep` then `tail -n`, and why that shape is allowed: a wait and a range read of
  a file under the item's own directory, which is not a search of the project's tree (issue #687's refusal is about
  that tree; the reviewer of round 1 corrected this paragraph's first draft, which claimed the refusal covered this
  log).
- **The two lines that end the gate** — `LOOP-GATE:` (the verdict the report quotes) and `LOOP-GATE-REFUSED:`. Without
  them a polled child has no way to tell a finished gate from a quiet one.
- **The resume** — a cut call is the call ending, not the gate: the process it started keeps running (this loop's own
  record, `tools/loop/loop.conf:435` — an orphaned suite held its host for 1 383 s after the child was gone), so poll
  again. A second gate is the one thing barred, and the refusal it meets is named so the child reads it as a wait
  rather than a red gate to repair.

Both skeletons carry it — `worker-gate.md` for the deferred shape and `gate-own.md` for the one-child shape — because
`worker.md`'s `{{GATE_STEP}}` hands a child its gate from one or the other, and a rule in one of them is a rule half the
children never see.

## Risks

- **A child that ends its session before the polled gate ends.** Then the item records no gate result — the same
  outcome as today, not worse; and the prompt's closing clause ("do not end your session before that log carries the
  tally") is the instruction that avoids it. `LOOP_GATE_RUNS_MAX` and the run's own tally read are the backstop.
- **A detached gate outliving the child that started it.** Its writes are the tally and the child's event stream, both
  inside the change root or the item directory, so no write of it can land in the project's own tree and trip the
  placed-write reading. A gate still running when the run reads the tally reads as a missing tally, which is the
  defect's own outcome again.
- **A poll that costs turns.** One call per poll at the loop's own per-turn price (`tools/loop/loop.conf:125`,
  `:308`); a 3 000 s gate is about five polls of 600 s, against a gate that today writes no tally at all.

## Verification

No compiled file changes, so the build is not owed (`guidelines/Build.md` §2, the scope block): the structural checks
are `bash -n` over the edited test file, `openspec validate fix-a-gate-that-outlasts-its-call --strict`, and
`tools/check-portability.sh` — which scans the prompt skeletons as **templates**, so a hard-coded project value in the
new text fails there.

Case: `prompt#theGatePromptNamesTheWallAndTheWayThrough` — red on the base (`evidence/red-on-HEAD.log`), green on the
change (`evidence/focused-case.log`), and the `prompt` group green beside it (`evidence/prompt-group.log`), because the
same skeletons are read by `prompt#thePhasePromptsCarryTheirOwnRoute`,
`prompt#thePhasePromptNamesWhatItsShellRefuses` and `gate#theGateCompletesTheChangeBeforeTheGate`.

Case: `prompt#aValueArrivesAsTheTextItWasHanded` — red on the base (`evidence/red-on-HEAD-render.log`), green with the
guard (`evidence/focused-cases.log`), and the three run cases the first gate reded are green on this tree
(`evidence/gate-round-1-attribution.log`).

Revert-check, both invariants: remove `nohup` from `gate-own.md`'s start and the first case fails naming it; remove the
`shopt` guard and the second fails with the mangled value. Both restored byte-identical (sha256 equal), forced green
(`evidence/revert-check-prompt.log`, `evidence/revert-check-render.log`).

On-device verification: none — this is the loop engine, and its harness is the shell suite.
