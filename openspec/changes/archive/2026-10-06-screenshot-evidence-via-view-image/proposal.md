# Proposal

## Why

Agents in this repository could not see screenshots at all, so every visual claim was settled by
low-level tooling — `tools/measure-highlight.py` colour keys, `tesseract` OCR, ImageMagick pixel
counts (`.pi/logs/analyze.py`). That premise is written into the tooling itself: `pixel-check/SKILL.md`
opens with "An agent cannot see a screenshot" and `tools/measure-highlight.py`'s docstring starts
"Why this exists: an agent cannot look at a screenshot".

The premise is now false. The session model accepts image input (verified: an image block fetched
over HTTP was described correctly), `ctx_read` is what discards pixels — it returns
`[Image: nav-panel.png (1391 KB, image/png)]`, even in `mode=raw`, and lean-ctx runs in
`mode: "replace"` (`.pi/extensions/pi-lean-ctx/config.json`), so Pi's image-capable built-in `read`
is suppressed. The `@luan.sh/pi-view-image` package (installed and verified: `pi list` lists it,
and a `view_image` call on `.pi/logs/baseline/shot.png` returned the image, which the model
described — menu panel, map labels, status bars) delivers a local PNG as an image content block
independent of lean-ctx.

What is missing is not a capability but the *workflow*: the skills and guidelines still instruct the
agent to reason about pixels through scripts, and nothing states that looking is now the first step
and measurement the authoritative second one.

## What Changes

- **Correct the false premise** in `.pi/skills/pixel-check/SKILL.md`, `tools/measure-highlight.py`
  (docstring), `AGENTS.md` (iteration loop rule 1, skills table) and `guidelines/Build.md`
  (the "Measure the screenshot instead of describing it" guidance and §10): the agent **looks** at
  the screenshot with `view_image` first, then **measures** it.
- **Keep measurement authoritative.** Additive, not a replacement: `tools/measure-highlight.py` stays
  the only source of a pixel verdict (bbox, band, margin, exit code, ImageMagick self-test). A vision
  model's impression is triage and narration; it never becomes the verdict or the evidence recorded in
  `tasks.md`.
- **Name `view_image` as the sanctioned exception** to the injected lean-ctx rule that mandates
  `ctx_*` tools exclusively, in the skill that needs it — otherwise the model never reaches for it.
- **Document the prerequisite**: `@luan.sh/pi-view-image` as a user package, the Rust toolchain (or
  `PI_VIEW_IMAGE_BIN` for a prebuilt binary), the install command, and the failure mode
  (`unable to locate image at <path>` → check the file exists before calling).
- **Stable capture path**: capture into a dedicated directory instead of a shared scratch one — a peer
  session deleted `.tmp-verify/` mid-investigation, which broke a verified call.

Not in scope: replacing the measurement script, installing a different package, changing lean-ctx's
`mode`, app code, or any phone / Android Auto behaviour.

## Capabilities

### New Capabilities

None. This change touches agent harness tooling and documentation only; it changes no application
behaviour, so no spec is created or modified. `.openspec.yaml` sets `skip_specs: true`, per the
project's doctrine that durable agent rules live in `AGENTS.md`, `openspec/config.yaml` or
`guidelines/*.md`, while the tools a skill uses live in the repository.

### Modified Capabilities

None. No existing capability's requirements change. Verified by search: no file under
`openspec/specs/` mentions `pixel-check`, `measure-highlight`, `screenshot` or `measure first`
(`build-test-gate` and `test-coverage` describe the Gradle gate and coverage measurement, not the
agent's visual-evidence workflow).

## Impact

Affected files (agent tooling and docs; `.pi/` is gitignored, so every rule that must survive a
fresh clone or another agent goes into `AGENTS.md` or `guidelines/`):

- `.pi/skills/pixel-check/SKILL.md` — premise line, `view_image` step, rules, failure mode, capture dir
- `.pi/skills/pixel-check/selftest.sh` — unchanged (the detector's self-test stays the contract)
- `tools/measure-highlight.py` — docstring premise only; detection logic untouched
- `AGENTS.md` — iteration-loop rule 1, the skills line under "Agent iteration loop (measure first)"
- `guidelines/Build.md` — the screenshot-measurement guidance and §10 (on-device evidence)
- `README.md` — only if it names the skills or the screenshot workflow
- `ki_processing_failures.log` — the failed approaches from this investigation (`ctx_call` reaching a
  native tool, the subagent lane's `pi.ModelRuntime.create` failure)

Guidelines referenced: `guidelines/Build.md` (§2 gate, §10 on-device evidence), `guidelines/UI.md`
(gates), `guidelines/Design.md` (verification expectations). No UI, rendering, native/JNI or manifest
change — so no submodule patch, no bridge-module override, and no `MapRendering.md` impact.

Scope: agent-side only. Neither the phone nor the Android Auto variant gains or loses behaviour, so no
parity requirement applies.

Additive, not breaking. Rollback: revert the documentation and skill edits; the installed package can
stay (it is inert without the skill pointing at it), or be removed with `pi remove`.
