# Tasks

Parent references are to `proposal.md` / `design.md` — the change declares `skip_specs: true`, so no
spec requirement exists to name. Nothing here touches a compiled file, so no task runs Gradle; the
structural checks stand in (`guidelines/Build.md` §2, the scope block), and the exclusion is named in
5.1. Applied 2026-10-17; the lane is a throwaway child session (D1), after the owner's rule against the
agent starting `pi` processes removed the one-shot `pi --print` form this draft first named.

## 1. Prove the two transcript shapes, in throwaway sessions

- [x] 1.1 **Shape A — the failure that was lost.** Outcome recorded in
  `.pi/logs/image-transcript-check/shape-a.txt`: the reproduction could **not** be re-run from an agent
  session on 2026-10-17 — the failing path needs the work session's `ctx_read`, and a `delegate` child gets
  no `ctx_*` tools (probe A1: its surface is `read`, `bash`, `edit`, `write`, `bg_wait`,
  `contact_supervisor`), while the throwaway-session half needs a `pi` process the owner forbids the agent to
  start (`lean-ctx` refuses it too: `'pi' is not in the shell allowlist`). Probe A2 measured the adjacent
  shape: a child's native `read` of the same PNG returned a real image content block and the child completed
  its turn — so the shape is survivable in a disposable transcript and fatal in the one that holds the work.
  The 2026-10-16 witness stands; the un-isolated path is `TODO.md` §203 with the operator repro protocol.
  parent: proposal.md "What Changes" bullet 3, design D1.
- [x] 1.2 **Shape B — run the chosen lane and record its output.** Done 2026-10-17 in a `delegate` child on
  `.pi/logs/carnav1/A-browse.png`; the child's answer is
  `.pi/logs/image-transcript-check/look.txt`. Verification met: the tool result was `Read image file
  [image/png]` with a real image block, the answer is non-empty text naming things only the pixels support
  (the blue "+" plate, the "Driver" chip, the climate-rail readings with their chevron buttons), the child
  reported its turn completed normally, and this session's transcript received text only.  parent:
  proposal.md "What Changes" bullet 7, design D1/D2.
- [x] 1.3 **Record both outcomes with their versions** in
  `openspec/changes/fix-screenshot-evidence-transcript-safety/apply-evidence.md`. Verification: the file
  names both shapes, their outcomes and the versions (pi 1.3.0, lean-ctx 3.11.4), with the timestamps and the
  capture paths.  parent: proposal.md "What Changes" bullets 6-7.

## 2. Record the rule where it survives a fresh clone

- [x] 2.1 **`AGENTS.md`: replace the `view_image` paragraph** with the rule — a tool result must never carry
  an image block into the session holding the work; a screenshot is looked at out of that transcript — plus
  both observations with their dates (2026-10-16 in the work session, 2026-10-17 in a child), the child lane,
  the `ctx_read`-is-image-capable fact and the throwaway-session protocol. Verification: `grep -nE
  'view_image|PI_VIEW_IMAGE_BIN|libtui' AGENTS.md` returns nothing, and the paragraph names both the rule and
  the protocol.  parent: proposal.md "What Changes" bullets 1 and 3, design D3/D5.
- [x] 2.2 **`guidelines/Build.md` §1: the two harness subsections collapse into one** ("Looking at a
  screenshot") — the `pi install` line, `PI_VIEW_IMAGE_BIN`, the Rust build note, the version pinning, the
  `"extensions": [...]` filter and the `/libtui:colors` check are deleted; the subsection states the child
  lane, the rule, the fact that a child is not a `ctx_*` runtime, and the text-only-model limit. §1's skills
  table and its `.pi/skills` tracking rule above them are intact. Verification: `grep -nE
  'pi-view-image|PI_VIEW_IMAGE_BIN|pi-libtui|libtui:colors' guidelines/Build.md` returns nothing, and §1 still
  names `tools/measure-highlight.py` and the skills table.  parent: proposal.md "What Changes" bullets 1, 3
  and 5, design D3/D5.
- [x] 2.3 **`guidelines/Build.md` §11 step 2 (look-then-measure)**: only the look sentence changed — the
  measurement authority (bbox/band/margin/verdict and exit code) and the precondition text including the
  `px=18` / `band=[0,2400]` counter-example are untouched. Verification: `git diff guidelines/Build.md` shows
  the look sentence and nothing else in §11, and §11 still names `tools/measure-highlight.py` and
  `tools/measure-highlight-selftest.sh`.  parent: proposal.md "What Changes" bullet 2, design D2.
- [x] 2.4 **Revert-check of the rule's observable.** No green/red pair exists here: the mutation ("point the
  documented look step back at a tool-result read in the work session") cannot be observed without risking
  the session holding the work, and no host-side test can assert an agent transcript. Recorded as a
  documentation rule with no host-side test — the wording the archived change
  `2026-10-06-screenshot-evidence-via-view-image` used for its own docs-only guard. What *was* measured is the
  positive half (1.2) and the 2026-10-16 witness (1.1).  parent: proposal.md "What Changes" bullet 3,
  design D4.

## 3. Correct the skills and the tool docstring

- [x] 3.1 **`.pi/skills/pixel-check/SKILL.md`**: the premise, the frontmatter description, the diagram, the
  first authority bullet, the "sanctioned exception" block, the capture-directory note and rule 4 now name the
  child-session look and the rule; rules 1-5, the capture-directory guidance, the detector precondition and the
  three-way comparison are kept. Verification: `grep -n 'view_image' .pi/skills/pixel-check/SKILL.md` returns
  nothing and the skill's first step is the `subagent` call.  parent: proposal.md "What Changes" bullets 1-2,
  design D2/D3.
- [x] 3.2 **`.pi/skills/device-check/SKILL.md`**: step 5 names the look lane ("look in a throwaway child
  session, verdict from `tools/measure-highlight.py`") and the capture-directory pitfall no longer names the
  removed tool, while the dedicated-capture-dir guidance and the "delete your own captures" rule are kept.
  Verification: the skill names the look lane and not `view_image`, and still routes a pixel finding to
  `pixel-check` instead of a description.  parent: proposal.md "What Changes" bullet 1.
- [x] 3.3 **`tools/measure-highlight.py` docstring**: the look is the throwaway child session
  (`guidelines/Build.md` §1); the detector, `CASINGS`, `MIN_RUN_PX` and the exit-code contract are untouched.
  Verification: `git diff tools/measure-highlight.py` touches the docstring only and
  `bash tools/measure-highlight-selftest.sh` passes (green tally recorded in `apply-evidence.md`). Exclusion
  named: no Kotlin/JVM/Robolectric test is affected by this change, and the older
  `.pi/skills/pixel-check/selftest.sh` cover needs ImageMagick (`convert` is allowlisted, `magick` is not), so
  it was not run.  parent: proposal.md "What Changes" bullet 1.

## 4. Retire the superseded material, log the failure, bound the residual

- [x] 4.1 **`TODO.md` §149 retired**: its status line reads retired with the reason (its subject is the removed
  package's extension filter; with the package gone there is no filter to drift and its "no check asserts which
  extensions loaded" residual has no subject either) and the body carries the retirement note. Verification:
  §149 keeps its number and its status reads retired with that reason. The historical body keeps its package
  and `/libtui` names on purpose — ids are never renumbered and the record is not rewritten — so the name grep
  still matches §149 and §147's historical measurement line (2179).  parent: proposal.md
  "What Changes" bullet 5, design D5.
- [x] 4.2 **New `TODO.md` §203** for the residual that cannot be enforced: an image carried by a tool result
  can kill the work session; the error text was not captured on 2026-10-16; the operator repro protocol; the
  2026-10-17 child-session measurement; and the requirement to capture the message if it recurs. Verification:
  the entry carries `**id:** 203 · **category:** build-and-harness · **class:** improvement · **status:** open`
  on the line under its heading, plus the repro command and the protocol.  parent: proposal.md "What Changes"
  bullet 6.
- [x] 4.3 **`ki_processing_failures.log`: two entries dated 2026-10-16** — (a) an expired capability premise
  (the "`ctx_read` shows only a placeholder" line was true of an older lean-ctx and false of 3.11.4, so a
  version upgrade silently invalidated the design built on it); (b) capability versus transcript stability
  ("the model can consume an image" and "the transcript survives one" are different questions with different
  fixes). Verification: both entries exist with a timestamp, the mistaken approach, the correct model of the
  problem and the lesson.  parent: proposal.md "What Changes" bullet 6.
- [x] 4.4 **`README.md` decided by grep, not by habit.** Verification:
  `grep -niE 'pixel-check|measure-highlight|view_image|screenshot' README.md` matches nothing — recorded "no
  mention, unchanged".  parent: proposal.md — Impact.

## 5. Integration check — no build input moved, premise gone repository-wide

- [x] 5.1 **Structural check in place of a build** (this delta touches no compiled file): the paths this change
  touched are `.pi/skills/**`, `.pi/logs/image-transcript-check/**` (untracked capture), `guidelines/Build.md`,
  `AGENTS.md`, `TODO.md`, `tools/measure-highlight.py`, `ki_processing_failures.log` and
  `openspec/changes/fix-screenshot-evidence-transcript-safety/**` — no Kotlin, Java, C++, Gradle, manifest or
  asset file. Recorded in `apply-evidence.md` together with the explicit exclusion "no Gradle build and no
  unit-test gate is run for this change" (`guidelines/Build.md` §2, scope block). The overall
  `git status --porcelain` is **not** expected to be limited to those paths: this working tree carries
  concurrent peer workstreams (`fix-compose-sandbox-idle` and others), so the check is scoped by path, and
  that is stated rather than hidden.
- [x] 5.2 **Premise gone repository-wide, and the measurement contract still holds.** Verification: the
  repository-wide grep for the retired names returns nothing in `.pi/skills/`, `tools/`, `AGENTS.md`,
  `guidelines/` and `README.md`. The remaining hits are **history**, and they are named rather than hidden:
  `ki_processing_failures.log` (the failure record this change adds), `openspec/changes/archive/**` (the
  predecessor change, where the removed package's instructions now live) and `TODO.md` §149 (the retired
  entry's own body) plus §147's measurement line, which records what the then-existing tool saw. One **live**
  artifact outside the checked set still asserts the retired premise —
  `openspec/changes/search-result-row-content/tasks.md`, a task note of another in-flight change — and is named
  here rather than corrected, so the two apply passes do not collide.
  `bash tools/measure-highlight-selftest.sh` reports its green tally (7 passed, 0 failed).
- [x] 5.3 **Both edited skills still parse.** The first lines of both edited skills are printed into
  `apply-evidence.md`, each still carrying `name:` and `description:` with a description that changed only in
  wording.

## Workflow follow-up

- Independent read-only review done 2026-10-17 before archive (repo doctrine): verdict **OK with notes**, six
  findings, all resolved in the same pass (legend for the in-place retirement, cluster index, proposal wording,
  the stale capture-dir name, two attribution notes) — recorded in `apply-evidence.md` §4.
- Archive with `openspec archive fix-screenshot-evidence-transcript-safety` once the review requirements are
  satisfied — no spec sync is needed or possible (`skip_specs: true`).
- Verify the archived change directory and that `openspec list` no longer shows the change in flight.
- Candidate follow-up, deliberately not this change: a `tools/` wrapper for the look lane with a device-free
  self-test, per the doctrine that the tools a skill uses live in the repository. A wrapper cannot own the
  child session, so the candidate is now "a self-test for the measurement contract only"; not decided here.
