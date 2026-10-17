# Proposal

## Why

The repository's screenshot workflow rests on a premise that is now false in both halves.

**It says `ctx_read` cannot deliver pixels.** Verified false on 2026-10-16: `ctx_read` on
`app/src/main/cpp/libosmscout/Android/OsmScoutViewer/res/drawable/ic_launcher.png` and on
`.pi/logs/carnav1/A-browse.png` each returned a real image block under lean-ctx 3.11.4, and the
model described both correctly (the launcher icon; the AAOS browse screen with its status bar,
the blue "+" plate and the 61°/68° bottom rail). The instruction that "image files go through
`view_image`" — `guidelines/Build.md` §1 ("Screenshot reading"), `.pi/skills/pixel-check/SKILL.md`
(the "sanctioned exception" rule), and the `tools/measure-highlight.py` docstring — is obsolete.

**It never says that pixels in a tool result are unsafe.** After those two image-bearing tool
results the session failed: the owner reports a pi error and, from then on, the same error on
every further prompt, with the session unusable and the error text not captured. The capability is
fine; the *transcript shape* is the defect.

```
capability            image reaches the model        -> PROVED (twice, 2026-10-16)
transcript stability  tool result carries an image   -> session dead, error repeats
                      user message carries an image  -> documented, provider-exercised
                                                        (`pi --print "@path"`, RPC `images:[]`)
                      pixels never in the transcript  -> always safe (script measurement, OCR)
```

**Root cause, and its evidence.** The reachable defect is that an image content block carried by a
*tool result* in the main session's transcript is not a survivable shape for this harness/provider
path. The evidence is the repro command below plus the two recorded deliveries in lean-ctx's
session store — **not** a failing test: no host-side case can assert an agent transcript, so there
is no XML tally and no red-on-HEAD test, and this change is therefore **not fix-loop eligible**
(`openspec/config.yaml` — the loop requires a root cause proven by a test that is red on HEAD).
The error text was lost and the session that produced it was unusable afterwards, while a later drafting session (a different process) still read the same PNGs — so the failing *path* is shaped but not isolated; the first task captures it.

**Measured again on 2026-10-17, and the answer sharpens the fix.** A `delegate` child session read the
same capture through its native `read`, *did* receive an image content block, described the frame
correctly and completed its turn — so the shape is survivable in a **disposable** transcript and fatal in
the one that holds the work. Two consequences: the rule is written for the work session's transcript, not
for tool results in general, and the look belongs in a child session, where a loss costs one child. **The
owner's constraint shapes the lane as much as the measurement does**: the agent must not start further `pi`
processes, and `lean-ctx`'s shell allowlist refuses `pi` independently (`[BLOCKED — DO NOT RETRY] 'pi' is
not in the shell allowlist`), so the one-shot `pi --print` form this proposal first chose is **operator-run
only** and is not the documented lane.

```bash
# repro protocol — OPERATOR-RUN in a THROWAWAY session (the agent does not start `pi` processes):
cd /home/tim/projects/NaviVeylin && pi
#   > read .pi/logs/carnav1/A-browse.png with ctx_read    # image block returns, model describes it
#   > then send any further prompt                        # <- error, and it repeats: session dead
```

**The single resulting fix.** Pixels must stay out of the transcript of the session that holds the work:
the "look" step becomes a **throwaway child session** that reads the frame and answers in words, documented
with the rule that a tool result must never carry an image block into that session. The look remains triage;
`tools/measure-highlight.py` keeps the verdict.

**No spec requirement is contradicted.** No capability under `openspec/specs/` owns the agent
harness or its evidence workflow (`pixel-check`, `measure-highlight` and "screenshot" appear in no
spec), and no application behaviour changes: neither the phone variant nor the car variant gains or
loses a behaviour, so no parity requirement applies. The durable home for this rule is
`AGENTS.md` / `guidelines/*.md` (the doctrine the archived change
`2026-10-06-screenshot-evidence-via-view-image` also followed).

## What Changes

- **Correct the false premise** where the repository states it: `guidelines/Build.md` §1 (both
  subsections — "Screenshot reading" and "Agent harness and the TUI package") and §11 step 2,
  `.pi/skills/pixel-check/SKILL.md`, `.pi/skills/device-check/SKILL.md` (its `view_image` failure-mode
  note), and the `tools/measure-highlight.py` docstring. `ctx_read` is stated as image-capable, the
  `view_image` package as retired, and the pinning/filtering constraint that existed only to
  suppress that package's bundled TUI host is deleted with it. §1's two subsections collapse into one
  ("Looking at a screenshot"), which is the lane this change documents.
- **Define the look step as a throwaway child session.** The documented form is one `subagent` call:

  ```text
  subagent({agent: "delegate", cwd: "/home/tim/projects/NaviVeylin",
            task: "read .pi/logs/<check>/shot.png and describe the screen in words — no coordinates,
                   no numbers; write nothing"})
  ```

  whose answer is text and goes into the evidence file; the image block lands in the child's own
disposable transcript. No second Pi process, no third-party package, no settings-side extension
allowlist — the child is the harness-native throwaway. Recorded as such for `pixel-check` /
`device-check` / §11 (the "look first, then measure" step), and named as the shape that keeps the work
session's transcript clean.
- **Add the missing rule, with its failure mode.** "A tool result must never carry an image block into
  the session holding the work; a screenshot is looked at out of that transcript" — in `AGENTS.md` next to
  the iteration-loop evidence rules and in `guidelines/Build.md` §1, with the 2026-10-16 observation (two
  image-bearing tool results, session unusable thereafter, error repeating) as the measurement behind it and
  the 2026-10-17 child-session result as the boundary of the claim.
- **State the protocol that bounds the risk**: any experiment that puts an image into a session runs in a
throwaway session (`/tmp`, no work in flight) and captures the error text; the rule exists because a poisoned
session is lost whole, not one turn. The agent cannot run that protocol itself (the owner's rule on `pi`
processes, plus the shell allowlist); it is an operator recipe, and the child-session lane is what the agent
uses.
- **Retire what this supersedes**: `TODO.md` §149 (the package's extension-filter drift) loses its
  subject — the package is gone, and the drift it guarded against cannot return — and its residual
  (no check asserts which extensions a package loaded) is retired with it, not repaired.
- **Record the failure** in `ki_processing_failures.log`: the two lessons of 2026-10-16 — a
  capability premise can expire under the version you upgrade to (re-test it before designing
  around it), and "the model can consume an image" is a different question from "the transcript
  survives one".
- **Leave one residual as backlog, not as a claim**: the un-isolated error path, with the repro
  protocol and the requirement to capture the message, as a new `TODO.md` entry — because it cannot
  be enforced by a test or a build gate.
- **Verify the lane once, for real**: done 2026-10-17 in a child session on an existing device capture —
`read` returned an image content block, the child's description named pixel-only details (the blue "+"
plate, the "Driver" chip, the climate rail), the turn completed and only text reached this session
(`.pi/logs/image-transcript-check/{shape-a,look}.txt`). The operator-run `pi --print` form stays
unverified-by-the-agent by design.
- **Not breaking for the app, breaking for the old instruction.** The workflow documented today
  (`view_image`, third-party package) is removed; the app, its builds, its tests and both
  distribution flavors are untouched. Additive in dependencies: the one prerequisite is the `pi`
  CLI on `PATH` (and `lean-ctx allow pi` for agent-run use), a machine fact, not a repository fact.
- **Rollback path**: revert the documentation and skill edits. The `pi` CLI lane installs nothing
  and leaves no state; no manifest, Gradle file, native source or resource is touched, so there is
  nothing to roll back beyond text.

## Capabilities

### New Capabilities

None. This change is agent-harness tooling and documentation only: no application behaviour changes,
so no spec is created. `.openspec.yaml` sets `skip_specs: true`, as the predecessor change
`2026-10-06-screenshot-evidence-via-view-image` did for the same reason.

### Modified Capabilities

None. No existing capability's requirements change. Verified: `openspec list --specs` plus a search
of `openspec/specs/` shows no requirement mentioning `pixel-check`, `measure-highlight`, `view_image`
or screenshot evidence; `build-test-gate` and `test-coverage` own the Gradle gate and coverage
measurement, not the agent's visual-evidence workflow.

## Impact

Affected files (documentation, agent skills and one tool docstring; `.pi/` outside `.pi/skills/` is
gitignored, so every rule that must survive a fresh clone lives in `AGENTS.md` or `guidelines/`):

- `guidelines/Build.md` — §1 both harness subsections collapse into one ("Looking at a screenshot": the
  child-session lane, the rule, the retired `view_image` package, `PI_VIEW_IMAGE_BIN`, the pinning/filter
  constraint and the `/libtui:colors` check all deleted); §11 step 2 (the "look, then measure" step reads
  the frame in a throwaway child session); the §10 recipes that name `view_image`
- `guidelines/Build.md` / `AGENTS.md` — the new rule: a tool result must never carry an image block,
  with the 2026-10-16 observation and the throwaway-session protocol
- `AGENTS.md` — the `view_image` paragraph under "Agent iteration loop (measure first)" becomes the
  child-session look lane and the rule, with both measurements (2026-10-16 in the work session,
  2026-10-17 in a child)
- `.pi/skills/pixel-check/SKILL.md` — premise line, the `view_image` triage step, the "sanctioned
  exception to `ctx_*`" rule (its subject disappears), the `unable to locate image at <path>` failure
  mode, and the capture-directory guidance that survives unchanged in intent
- `.pi/skills/device-check/SKILL.md` — its `view_image` failure-mode note
- `tools/measure-highlight.py` — docstring premise line only; detection logic and exit-code contract
  untouched
- `TODO.md` — §149 retired (package extension filter and its residual); one new entry for the
  un-isolated error path with the repro protocol
- `ki_processing_failures.log` — the 2026-10-16 entries (expired premise; capability versus
  transcript stability)
- `README.md` — not affected: it names neither the skills nor the screenshot workflow (checked)

Not affected: no Kotlin/Java/C++ source, no `app` / `auto` / `core` / `osmscout-client-java` /
`buildSrc` change, no manifest, no Gradle configuration, no native or JNI boundary (so no submodule
patch and no bridge-module override), no `openspec/specs/` file, and neither flavor's behaviour.
Scope: agent harness only — neither phone-only nor car-only work; both variants' evidence workflow
is affected identically, and the guidance lives in variant-independent documents.

Guidelines referenced: `guidelines/Build.md` (§1 skills and harness prerequisites, §2 iteration
protocol, §10 on-device evidence, §11 phone-UI measurement). No `UI.md`, `MapRendering.md`,
`Design.md`, `Regulatory.md` or `Logging.md` section is affected: no UI, rendering, logging or
disclosure behaviour changes, and no log line, coordinate or diagnostics rule is touched.
