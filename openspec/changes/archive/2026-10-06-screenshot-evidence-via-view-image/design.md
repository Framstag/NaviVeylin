# Design

## Context

See `proposal.md` — Why. Current state that constrains the approach:

- `view_image` (from `@luan.sh/pi-view-image`, installed as a user package) is live in this session:
  a call on `.pi/logs/baseline/shot.png` returned an image content block that the model described
  correctly. Its README states a native Rust binary reads a local PNG/JPEG/GIF/WebP and returns it
  "as a Pi image content block, so any vision-capable model can look at a file that is already on
  disk"; on text-only models it substitutes a description from `descriptionModel`.
- The session model accepts image input, proved independently of that package by an HTTP image
  fetched through `fetch_content`.
- `ctx_read` discards images (`[Image: nav-panel.png (1391 KB, image/png)]`, also with `mode=raw`),
  and lean-ctx runs in `mode: "replace"`, so Pi's image-capable built-in `read` is suppressed.
- The evidence practice already in place: `tools/measure-highlight.py` returns a bbox, a band, a
  margin, a `verdict` and exit codes 0/1/2, with a detector self-test
  (`.pi/skills/pixel-check/selftest.sh`) that runs without a device. `guidelines/Build.md` §10 holds
  the on-device recipe; `AGENTS.md`'s iteration loop holds the measure-first rule.
- `.pi/` is gitignored. Project doctrine (AGENTS.md): a rule that must survive a fresh clone belongs
  in `AGENTS.md`, `openspec/config.yaml` or `guidelines/*.md`; the tools a skill uses belong in the
  repository.

## Goals / Non-Goals

Goals: pixels reach the model in one step; measurement stays the verdict; the workflow is enforceable
from documents that survive a fresh clone and another agent; the prerequisite is reproducible.

Non-goals (design-level boundaries): no new app code, no new Gradle module or task, no change to
lean-ctx's mode, no replacement of `measure-highlight.py`, no per-call model pinning. Also excluded:
making the *agent harness* reproduce on CI — the skills are local tooling, so CI is unaffected.

## Decisions

### D1 — Deliver pixels with `view_image`, not by repairing the read path

| alternative | why not |
|---|---|
| **`view_image` (chosen)** | one tool, local files, image block, independent of lean-ctx; already installed and verified |
| lean-ctx `mode: "additive"` | restores built-in `read`, but re-declares ~5 builtins into the tool surface and fights the injected rule that mandates `ctx_*` exclusively |
| own extension `view_image` | duplicates a maintained package; native binary would be ours to build and ship |
| nested `pi -p --model <vlm> @shot.png "…"` | needs `pi` added to the lean-ctx shell allowlist, spawns a second model session per screenshot, and returns text — not pixels — so it cannot feed the model's own reasoning |
| MCP image server | `fetch_content` rejects `file://` and blocks internal addresses (verified: `Blocked internal address for 127.0.0.1`), so a local route would need its own server; a public host means uploading position-bearing imagery |
| public upload + remote URL | rejected: a navigation screenshot can carry the user's position, and the project's regulatory stance treats that as the thing to avoid |

### D2 — Two authorities: vision triages, the script verdicts

```
view_image  ->  what is on screen (semantics, "does this look wrong")
                triage only, non-reproducible, may be wrong
measure-highlight.py -> bbox / band / margin / verdict / exit code
                the evidence recorded in tasks.md; self-tested
```

| alternative | why not |
|---|---|
| **vision triage + script verdict (chosen)** | keeps evidence reproducible and keeps the skill's existing five rules intact |
| vision as the verdict | a VLM's box is fuzzy and non-reproducible; it would replace an exit code with a sentence and break the project's evidence rule |
| script only (status quo) | cannot answer the questions scripts cannot express: which control has focus, is a label truncated, is the expected panel state present |

**Precondition, learned on the device (applying this change, task 4.1).** A positive verdict is only
meaningful when the frame is in the state the detector assumes — a route with an analysed segment on
screen, and a card top inside the canvas. On a phone frame with no route the script printed
`highlight (dark): bbox=[619, 641, 413, 734] px=18` / `canvas=1080x2400 band=[0,2400] margin=126` /
`verdict: inside`, exit 0: 18 pixels of light-blue `P` parking-glyph colour matching the dark casing
`#E0F7FA`, with `--dump` finding no card so the band degraded to the whole canvas. The look caught it —
a crop of that box is ordinary base map (a red primary road, parking glyphs, bicycle icons).

So the load divides differently than "the script is the authority" alone implies:

```
look (view_image)  -> establishes the PRECONDITION and reads what a script cannot ask
                      (is a route on screen, is the expected panel state present)
script             -> measures GIVEN that precondition, reproducibly
sanity check       -> band must end above the canvas bottom (a measured card top) AND the match must
                      be a stroke (hundreds/thousands of px over many rows), not a couple of dozen
                      pixels spread wide
```

A verdict failing those checks is recorded as a **false positive**, never as a finding. The detector's
own gap — refusing instead of reporting `inside` when no plausible highlight exists — is filed as
`TODO.md` §143, outside this change.

### D3 — One skill, not a new skill per question class

The `pixel-check` skill already owns the discipline, the rules and the three-way comparison. Chosen:
insert the look step and correct the premise there.

| alternative | why not |
|---|---|
| **amend `pixel-check` (chosen)** | one place to keep the rule; `/skill:pixel-check` already exists |
| a new `screenshot-look` skill | splits the workflow; an agent could look without measuring, which is the failure mode to prevent |
| guideline text only | the skill is what the model loads at the moment of need; a guideline alone is not reached |

### D4 — No model pinning by default

The session model sees images, so nothing needs pinning. Keep `descriptionModel` documented as the
fallback for a text-only session, and record why not a router.

| alternative | why not |
|---|---|
| **session model + documented `descriptionModel` fallback (chosen)** | zero configuration; the fallback exists in the package for the text-only case |
| virtual model router (`registerVirtualModel`) | routes every request, so a switch costs a prompt-cache miss; needs extension code in gitignored `.pi/` |
| subagent with `model:` override | unavailable here — both probes failed with `undefined is not an object (evaluating 'pi.ModelRuntime.create')`, with and without the override (pi 1.0.4) |

### D5 — Keep lean-ctx in `replace` mode

`view_image` is its own tool, so the mode is irrelevant to pixel delivery. Chosen: no change to
`.pi/extensions/pi-lean-ctx/config.json`; instead the skill names `view_image` as the sanctioned
exception to the `ctx_*` mandate.

| alternative | why not |
|---|---|
| **leave `replace`, document the exception (chosen)** | smallest change; the token cost of additive mode is not paid for text work |
| switch to `additive` | pays tool-surface tokens on every request and invites `read`/`bash` use where `ctx_*` is wanted |

### D6 — Stable capture directory and an existence check

`.tmp-verify/` was deleted by a peer session mid-investigation, which turned a working call into
`unable to locate image at /…/nav-panel.png / Caused by: No such file or directory`. Chosen: a
dedicated capture directory plus a check-before-call step in the skill; the existing rule that the
screenshot and the `uiautomator` dump must be the same moment stands.

## Risks / Trade-offs

- **[Rust toolchain required for the native binary]** → document both paths: the toolchain, or
  `PI_VIEW_IMAGE_BIN` pointing at a prebuilt executable; the first call builds under Pi's agent
  directory and reports it. Verify once on this machine and record the outcome.
- **[Third-party package executing a native binary]** → review before trust, pin the version, and keep
  the workflow usable without it: the script path stays valid, so a missing package degrades to the
  previous behaviour instead of blocking a finding.
- **[VLM hallucination becomes "evidence"]** → D2's rule is written into the skill and into
  `guidelines/Build.md`: only the script's numbers and exit code are evidence.
- **[Image token cost in a long session]** → capture at `detail: high` for layout questions and crop
  with ImageMagick for text questions; do not keep screenshots in a long-running investigation unless
  the finding needs them.
- **[`.pi/` gitignored → the skill edit does not reach CI or a fresh clone]** → the *rule* also lands
  in `AGENTS.md` and `guidelines/Build.md`; the package prerequisite and install command are recorded
  in those tracked documents.
- **[The package may need a newer pi than 1.0.4]** → same class as the `pi.ModelRuntime.create`
  subagent failure. Mitigation: record the working version in the prerequisite note and keep the
  failure mode (an unknown tool / no pixels) in the skill, so the fallback is obvious.
- **[Measurement script edited during a premise fix]** → the change touches the docstring only; the
  detector and its self-test are the contract and are not modified.

## Threading / lifecycle

No new component, no dispatcher and no state: `view_image` is a model-invoked tool whose native work
runs inside pi's tool execution, off the app's UI thread and outside the app process entirely. No
lifecycle handling applies, and nothing in this change touches `guidelines/Design.md` §4 concerns.

## Verification

- Look step: call `view_image` on an existing screenshot and record that an image arrived plus a short
  description that only the pixels can support (e.g. a label or a list order). Done: `.pi/logs/baseline/shot.png`
  — menu list partially scrolled (scrollbar thumb mid-height), no focused control.
- Measure step: on a device or emulator, capture screenshot + `uiautomator` dump in the same moment
  and record `tools/measure-highlight.py`'s `bbox`, `band`, `margin` and `verdict`. Done: `.pi/logs/view-image-check/`
  (22:03, `emulator-5554`) — `band=[0,2400]` and `px=18` for `verdict: inside`, a false positive.
- Authority split: run one scenario where the impression and the script disagree (the highlight
  hidden or the card raised) and record that the script's verdict, not the description, is the
  outcome — the same shape as the recorded `inside=true` vs `CLIPPED` disagreement. Observed inverted
  instead: the script claimed `inside` where the look and a crop showed no highlight at all (no route on
  screen), which produced the precondition rule above instead of a demonstration of the original claim.
  A genuine highlight could not be produced without driving the phone app into a route session on an
  emulator a peer session was using — recorded as such rather than implied.
- Revert-check: remove the look step from the skill, run the named case (a question only pixels can
  answer, e.g. which control is focused), confirm the workflow fails, restore, and re-run green.
- No build or unit-test impact: no Kotlin, Java, C++ or Gradle file changes, so `assembleDebug` and
  the unit-test gate are unaffected; `bash .pi/skills/pixel-check/selftest.sh` still passes as the
  detector's contract.

## Migration Plan

1. Record the prerequisite and the verified package/version in the tracked docs.
2. Correct the premise and add the look step in the skill and the two guidelines.
3. Verify the look/measure split on device, then the disagreement case, then the revert-check.
4. Rollback: revert the documentation and skill edits; remove the package with `pi remove` if wanted.
   Nothing in the app, the build or CI depends on any of it.

## Open Questions

- Whether to file the `ctx_read` image-passthrough gap upstream (a read tool returning a placeholder
  for an image file is wrong for any vision model) — deferrable; it changes neither the workflow nor
  the tasks, because `view_image` already carries the load.
- Whether a future crop-first helper belongs in `tools/` (an ImageMagick wrapper that crops to a
  region of interest before capture of text-heavy UI) — deferrable; today's rule is "crop by hand when
  the question is about text".
