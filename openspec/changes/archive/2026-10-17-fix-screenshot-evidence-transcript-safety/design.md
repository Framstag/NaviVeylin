# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- **The harness today.** lean-ctx 3.11.4 sits between the model and the filesystem with
  `"mode": "replace"` (`.pi/extensions/pi-lean-ctx/config.json`) and `shadow_mode: active`
  (`lean-ctx status`), so the native `read`/`grep` tools are denied and only `ctx_*` tools reach the
  model. `ctx_read` on a local PNG now returns a real image content block (verified 2026-10-16 on a
  launcher icon and `.pi/logs/carnav1/A-browse.png`), and the session model described both correctly.
- **The stale premise, with its locations.** "`ctx_read` returns only a placeholder, so image files
  go through `view_image`": `guidelines/Build.md` §1 (both subsections) and §11 step 2;
  `.pi/skills/pixel-check/SKILL.md`; `.pi/skills/device-check/SKILL.md`; `AGENTS.md` (the paragraph under
  "Agent iteration loop"); `tools/measure-highlight.py` (the docstring's *why* paragraph). The line numbers
  in the original draft of this list have moved with the edits — every location is named by section now.
- **What a child session is, and is not.** It is handed the native tools (`read`, `bash`, `edit`, `write`)
  and **not** lean-ctx's `ctx_*` tools (measured 2026-10-17), and native `read` returns image content only
  for a vision-capable model — a child inherits the parent's model, so the lane's capability follows the
  session's model, while its risk does not follow the session's work.
- **The measured failure.** Two image-bearing *tool results* in one session, then a pi error that
  repeated on every further prompt; the session was unusable and the error text was not captured.
  No host-side case can assert an agent transcript, so this change has no test that can go red on
  HEAD (proposal.md — Why).
- **Constraints.** `.pi/*` is gitignored except `.pi/skills/` (`.gitignore` line 21 plus the
  re-admission lines), so a rule that must survive a fresh clone goes into `AGENTS.md` or
  `guidelines/`. `openspec/config.yaml` states the doctrine: durable agent rules there, the *tools*
  a skill uses in the repository. No spec owns this workflow, hence `skip_specs: true`.

## Goals / Non-Goals

**Goals:**

- One documented way to look at a screenshot that cannot poison the session holding the work: a throwaway
  **child session** whose answer is text (`subagent`, D1).
- The general rule this implies, stated where it survives a fresh clone: a tool result must never
  carry an image block, with the 2026-10-16 observation as its measurement.
- Every stale statement of the old premise corrected, including the retired package's pinning and
  filtering instructions (which existed only to suppress that package's bundled TUI host).
- A runnable verification of *both* transcript shapes in throwaway sessions, so the fix rests on a
  repeatable result and not only on a witness report.

**Non-Goals:**

- No Pi extension, no MCP image server, no vendored copy of the removed package, no settings-side
  extension allowlist, **and no second `pi` process started by the agent** (the owner's rule) — the look
  needs no third-party code and no new process the agent has to be trusted with.
- No describe-the-image fallback and no hidden model call: the look's cost stays visible as one
  named, per-invocation CLI call.
- No build gate or repository check for the rule: nothing in this repository can observe a session
  transcript, and a check that greps prose would assert spelling, not behaviour.
- No Kotlin/Java/C++/Gradle/manifest change, and no spec delta — neither variant's behaviour changes.
- No new repository tool for the lane, and therefore no self-test for one; whether the lane deserves
  a `tools/` wrapper with a device-free self-test is a separate change (recorded as a `TODO.md`
  candidate in the tasks, not decided here).

## Decisions

### D1 — Where the look happens: a throwaway child session

**Chosen:** one `subagent` call — a `delegate` child reads the PNG with its native `read` and answers in
words; the image block lands in the child's disposable transcript, and only text reaches the session
holding the work.

The decision rule: the outcome still unresolved in this repository — *does an image in a transcript
survive?* — must be paid for by a transcript that can be thrown away, never by the session holding the work.

Alternatives, and why the evidence puts them out of reach:

- **Image in a tool result of the work session** (`ctx_read`, or any tool returning an image block):
  *eliminated by measurement* — that is exactly the shape that killed the session on 2026-10-16.
- **A one-shot `pi --print "@png …"` process**: *eliminated by the owner's constraint and by the shell
  allowlist.* It was this design's first choice. The owner forbids the agent starting further Pi processes
  ("I do not want the LLM to be able to start further PI instances in general … it should not be the final
  solution") and `lean-ctx` refuses the call independently (`'pi' is not in the shell allowlist`), so it
  survives only as the operator-run repro protocol.
- **Own Pi extension attaching the image as a user message**: *dominated.* It bets the live session on
  the very question that is open; the chosen lane asks the same question in a disposable transcript, and
  the repository's history with third-party harness code (the removed package's bundled TUI host,
  `TODO.md` §149) is the reason not to add an owned one for a single call the harness already offers.
- **Text only — measurement plus OCR, no pixels**: *eliminated by measurement.* The model does see
  pixels, and the questions the look answers (which control has focus, is a label truncated, is the
  list scrolled — `.pi/skills/pixel-check/SKILL.md`) are precisely the ones a detector cannot
  express, so discarding the capability would remove triage without gaining safety.

Threading and lifecycle: the child is a separate harness process owned by the `subagent` tool. It adds
no dispatcher, coroutine, surface or lifecycle to the app, and `guidelines/Design.md` §4 does not apply
to it — the app's main thread, render thread and native boundary are untouched.

**Verification** (both shapes, throwaway sessions, no work in flight): shape A — put an image into a
session through a tool result, prompt again, record the error verbatim; shape B — run the chosen lane on an
existing capture and assert non-empty text naming something only the pixels support (a label, a control's
state), exit 0. If shape A no longer fails on this harness version, that is a result to record, and the rule
is written defensively rather than dropped.

**Measured, 2026-10-17** (`.pi/logs/image-transcript-check/{shape-a,look}.txt`): shape B is **proved** — a
`delegate` child read `.pi/logs/carnav1/A-browse.png` through native `read`, the tool result said `Read image
file [image/png]` with a real image block attached, the child described pixel-only details (the blue "+"
plate, the "Driver" chip, the climate rail readings) and completed its turn, while this session received text
only. Shape A could **not** be re-run from inside an agent session: the failing path needs the work session's
`ctx_read` (a child gets no `ctx_*` tools — recorded in the same evidence file) and the throwaway-session
half needs a `pi` process the agent may not start. So the 2026-10-16 witness stands, the 2026-10-17 child
measurement bounds the claim, and the un-isolated path keeps its own `TODO.md` entry (§203) with the
operator-run repro protocol — the shape is recorded, not asserted.

### D2 — What the look leaves behind

**Chosen:** text. The child's answer pasted verbatim into the change's own capture directory
(`.pi/logs/image-transcript-check/look.txt`), quoted into `tasks.md` as triage; the transcript receives
text only, and nothing else in the session changes.

Alternatives: *image in the tool result* — eliminated as in D1; *image in the work session's user
message* — eliminated: it is the same unresolved survival question, but paid for by the live session;
*no artifact, a spoken impression only* — eliminated by `.pi/skills/pixel-check/SKILL.md` rule 2, which
requires the record to quote what was seen, and by the same skill's rule 4, which forbids a look
standing in for the measurement. The measurement authority is unchanged: `tools/measure-highlight.py`
keeps its bbox/band/margin/verdict and exit code, and the look establishes only the precondition
(rule 3 and the `px=18` counter-example, `TODO.md` §147).

### D3 — Where the rule is recorded

**Chosen:** the rule goes into `AGENTS.md` (the file a fresh clone or another agent reads first, per
the doctrine at `AGENTS.md` lines 189-194) and into `guidelines/Build.md` §1, which owns the harness
prerequisites and the skills table; the workflow that uses it goes into the two skills.

Alternatives: *skill-only* — eliminated: `.pi/skills/` is versioned, but the rule constrains how an
agent may use *tools* at all, next to the lean-ctx paragraph in `AGENTS.md` that already governs that
question, and a rule only a skill knows is not read by a session that never loads that skill;
*`openspec/config.yaml` guidance* — eliminated: it would apply to every change's apply pass
regardless of whether a screenshot is involved, and the config's own rules describe it as apply/archive
guidance, not as harness facts.

### D4 — How the rule is verified, given that no test can assert it

**Chosen:** the runnable proxy of D1's verification — the two-shape experiment in throwaway sessions,
recorded with versions — plus repository-wide greps that the stale premise is gone and that the
retired package's names (`view_image`, `PI_VIEW_IMAGE_BIN`, `pi-libtui`, `/libtui:colors`) survive only
in historical logs and archived changes.

Alternatives: *a unit test* — impossible: the artifact under test is a session transcript outside the
repository; *a `tools/` self-test that runs `pi --print` headless and asserts non-empty text* — a real
option, deferred as a Non-Goal because it would put a harness dependency into the repository's
device-free tooling for one call, and because it would not test the rule, only the lane.

### D5 — Retire the superseded package material rather than preserve it

**Chosen:** delete the pinning/filtering instructions, the `PI_VIEW_IMAGE_BIN` route, the Rust-build
note and the `/libtui:colors` check together with the package they describe; retire `TODO.md` §149
whose subject is that filter's drift.

Alternatives: *keep them as a historical note* — eliminated: instructions for a package nobody
installs are the same class of expired premise this change exists to fix, and the archived change
directory already preserves the history; *keep §149 but reword it* — eliminated: with the package
gone there is no filter to drift, and its residual (no check asserts which extensions a package
loaded) has no subject either, so the entry moves to retired with that reason.

## Risks / Trade-offs

- **[A text-only model gets no pixels in the child either]** → Mitigation: the child inherits the parent's
  model and native `read` returns image content only for a vision-capable model; the docs state that limit
  and its fallback (the measurement script alone, which answers *where* but not *what*) instead of implying
  a look that did not happen.
- **[The child lane has its own recorded failure mode]** (`ki_processing_failures.log`: a
  `pi.ModelRuntime.create` skew on child runs) → Mitigation: the look is one call whose failure is visible
  as a missing answer, and the rule and the docs fall back to the measurement script rather than to an
  impression; the parent session is never at risk, which is the point of the lane.
- **[Each look costs a real model call]** → Mitigation: it is one named invocation with one frame and
  a one-sentence prompt, visible to the operator — the opposite of the removed package's text-only
  fallback, whose spend was invisible and which was among the reasons for removing it.
- **[The two-shape experiment can kill a session again]** → Mitigation: throwaway sessions only
  (`/tmp`, nothing in flight), which is the protocol the rule itself will state; capturing the error
  text is one of the experiment's deliverables, so the risk is the experiment's purpose, not a
  side effect.
- **[The premise can expire again when lean-ctx or pi is upgraded]** → Mitigation: the rule and the
  doc record the observation *with its date and the check* (`ctx_read` on a PNG; then one more
  prompt) rather than only the conclusion, so a future session can re-test it in one call.

## Migration Plan

Documentation and skills only — nothing deploys. Order, per dependency: verify the lane (D1) →
record the rule (D3) → correct the premise and retire the superseded material (D5) → log the failure
and the residual. Rollback is `git checkout` of the affected documents plus the change directory:
the lane installs nothing, writes no configuration outside the repository, and leaves no state in
either flavor of the app.

## Open Questions

- Whether the session model's ability to consume images is a property of the model, of the lean-ctx
  version, or of both — it cannot change the approach: the chosen lane is safe whether or not the
  model sees pixels, because the look happens outside the transcript either way. It can be answered
  by re-running the one-call check after a future upgrade.
