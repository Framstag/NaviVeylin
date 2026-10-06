# Tasks

## 1. Prerequisite — package and native binary

- [x] 1.1 Confirm `@luan.sh/pi-view-image` is a configured user package; verification: `pi list` shows `npm:@luan.sh/pi-view-image` with its path (already true — record the output).
- [x] 1.2 Force the first `view_image` call so the native binary builds, and confirm pixels arrive; verification: `view_image` on an existing PNG (e.g. `.pi/logs/baseline/shot.png`, `detail: high`) returns an image and the description names something only the pixels support (a label, a list order, a UI state) — not an error, not a placeholder.
- [x] 1.3 Record the prerequisite in the tracked docs — install command, Rust toolchain or `PI_VIEW_IMAGE_BIN` for a prebuilt executable, and the verified package version/pi version; verification: the note exists in `AGENTS.md` (skills / iteration-loop area) and `guidelines/Build.md` §1, and the documented install command is the one that produced the working state.

## 2. Correct the false premise

- [x] 2.1 Rewrite the opening premise of `.pi/skills/pixel-check/SKILL.md`: the agent **looks** at the screenshot with `view_image` first (local PNG path), then **measures** it; keep the existing five rules and the three-way comparison; verification: the skill no longer claims an agent cannot see a screenshot, and its first step is a `view_image` call.
- [x] 2.2 Correct the module docstring of `tools/measure-highlight.py` ("Why this exists: an agent cannot look at a screenshot") to the look-then-measure rationale; verification: `git diff tools/measure-highlight.py` touches the docstring only — the detector, `CASINGS` and `MIN_RUN_PX` are unchanged.
- [x] 2.3 Update `AGENTS.md`: iteration-loop rule 1 and the skills line that describes `pixel-check`; verification: the rule reads "look with `view_image`, then measure", and `grep -n 'cannot see a screenshot' AGENTS.md` returns nothing.
- [x] 2.4 Update `guidelines/Build.md` — the "Measure the screenshot instead of describing it" guidance and §10 (on-device evidence); verification: the section states the look-then-measure order and still names `tools/measure-highlight.py` and `.pi/skills/pixel-check/selftest.sh` as the measurement contract.
- [x] 2.5 Verify the false premise is gone repository-wide and the detector contract still holds; verification: `grep -rniE 'cannot see a screenshot|cannot look at a screenshot' .pi/skills tools AGENTS.md guidelines` returns nothing, and `bash .pi/skills/pixel-check/selftest.sh` passes (inside / behind the card / no highlight).

## 3. Enforce the authority split

- [x] 3.1 State in `.pi/skills/pixel-check/SKILL.md` that `view_image` is the sanctioned exception to the lean-ctx rule mandating `ctx_*` tools exclusively, and that a vision impression is triage while only `tools/measure-highlight.py`'s bbox/band/margin/verdict/exit code is evidence; verification: a fresh read of the skill shows both statements, and the exception names the tool exactly (`view_image`).
- [x] 3.2 Put the same rule in `guidelines/Build.md` where measure-first is documented, because `.pi/` is gitignored and cannot carry a rule that must survive a fresh clone; verification: the guideline text states that a screenshot is looked at with `view_image` and measured with the script, and that a vision verdict never replaces the script's.
- [x] 3.3 Revert-check the look step: remove the `view_image` step from the skill, run the named case — a question only pixels can answer (e.g. which control currently has focus, or whether a list is scrolled) — and confirm the workflow fails to answer it; then restore the step and re-run the case green; verification: the failing run and the restored green run are both recorded, with the case named.
- [x] 3.4 State the detector's precondition and sanity check wherever the authority rule lives — a positive verdict needs a measured card top (`band` ending above the canvas bottom) and a stroke-sized match (hundreds/thousands of px over many rows), otherwise it is a colour coincidence to record as a false positive, not a finding; verification: `design.md` D2, `.pi/skills/pixel-check/SKILL.md` and `guidelines/Build.md` all carry it with the measured counter-example (no route on screen, `px=18`, `band=[0,2400]`, `verdict: inside`); revert-check mutation: delete the precondition sentence from the skill — the observable that then disappears is the refusal of a card-less `inside` verdict, stated as such because a documentation rule has no test that can go red.

## 4. On-device verification of the split

- [x] 4.1 On a device or emulator, take the screenshot and the `uiautomator` dump in the same moment, call `view_image` on the screenshot, then run `python3 tools/measure-highlight.py SHOT.png --dump XML --margin 126`; verification: the record holds the model's description *and* the script's `bbox`, `band`, `margin`, `verdict` (exit code), from the same moment.
- [x] 4.2 Exercise the disagreement case — **observed inverted and recorded as such**: the intended shape (script `CLIPPED`/`no highlight` against an impression of a visible segment) needs a drawn highlight, hence a route session; instead the frame had no route and the script claimed `verdict: inside` (`px=18`, `band=[0,2400]`) while the look and a crop of `bbox=[619, 641, 413, 734]` showed only base-map — the verdict is not accepted there, and this case produced the precondition rule instead; verification: look + script output + crop recorded in `.pi/logs/view-image-check/` (22:03) and the rule landed in design D2.
- [x] 4.3 Record what could not be measured, explicitly: no genuine analysed-segment highlight was produced — `emulator-5554` showed a phone map of "Mitte" with no route and no route panel, and driving the app into a route session was not done because a peer session was mid-edit in `MapCanvasViewModel.kt` and building (`:core:testDebugUnitTest`) against the same device and working tree; verification: this statement is the record — no device proof of a genuine highlight is implied anywhere in this change.

## 5. Housekeeping

- [x] 5.1 Log the failed approaches from this investigation in `ki_processing_failures.log` with timestamps: reaching a native tool through `ctx_call` (lean-ctx's gateway answers `Unknown tool` for it), the subagent lane failing with `undefined is not an object (evaluating 'pi.ModelRuntime.create')` with and without a `model:` override, and `fetch_content` refusing `file://` plus blocking loopback addresses; verification: three entries exist and each names the symptom and the mistaken approach.
- [x] 5.2 Confirm the change is tooling-only and no build input moved; verification: `git status --porcelain` shows changes only under `openspec/changes/screenshot-evidence-via-view-image/`, `.pi/`, `tools/`, `AGENTS.md`, `guidelines/`, `README.md` and `ki_processing_failures.log` — no Kotlin, Java, C++, Gradle, manifest or asset file — so `:app:assembleDebug` and the unit-test gate are unaffected and are not re-run for this change.
- [x] 5.3 Update `README.md` only if it names the skills or the screenshot workflow; verification: `grep -niE 'pixel-check|screenshot|measure-highlight' README.md` decides the task — edits made if it matches, an explicit "no mention, unchanged" note if it does not.

## 6. Skill adaptation (added after the device run, same topic)

- [x] 6.1 Fold the verified capture recipe into `.pi/skills/pixel-check/SKILL.md` and `.pi/skills/device-check/SKILL.md` — device-side `$EXTERNAL_STORAGE` expansion, host-side `sed -n '/<?xml/,$p'` header filter, dedicated capture directory — replacing the `adb pull $EXTERNAL_STORAGE/...` recipe that cannot work here; verification: the documented block run verbatim produced a non-empty `ui.xml` (13345 bytes) and a PNG, and `measure-highlight.py` parsed that dump (exit 0) from `.pi/logs/skill-recipe-check/`.
- [x] 6.2 Replace `device-check`'s pitfall that recommends `adb pull /sdcard/ui-x.xml` — the path policy rejects a literal device path in the host command before execution (`Denied by policy: 'external_directory' … (rule '*')`) — and record the ImageMagick fact (`magick` not in the shell allowlist, `convert` is); verification: both skills state the policy and the `magick`/`convert` difference, and neither recommends the blocked recipe.
- [x] 6.3 Confirm the adapted text stays consistent with this change's rule — look first, then measure given the precondition; verification: the skill names `view_image` throughout, the precondition counter-example (`px=18`, `band=[0,2400]`, `verdict: inside`) appears in the skill, `guidelines/Build.md` and design D2, and `bash .pi/skills/pixel-check/selftest.sh` reports `FAIL=0`.

## Workflow follow-up

- Archive the change once the review requirements are satisfied (`openspec archive`, no spec sync needed — this change has `skip_specs: true`).
- Verify the archived change directory and that `openspec list` no longer shows it as in-flight.
