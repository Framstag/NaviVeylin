# Apply evidence — fix-screenshot-evidence-transcript-safety

Applied 2026-10-17 by an agent session in `/home/tim/projects/NaviVeylin`.
Harness: pi 1.3.0 (`/home/tim/.local/share/mise/installs/pi/1.3.0`), lean-ctx 3.11.4 (official).
Capture used for both shapes: `.pi/logs/carnav1/A-browse.png` (AAOS browse screen, from the car-observation run).

## 1. The two transcript shapes

| shape | what was done | outcome |
|---|---|---|
| **A** — image in a tool result | not runnable from an agent session: the failing path needs this session's `ctx_read` (a `delegate` child gets no `ctx_*` tools) and the throwaway half needs a `pi` process the agent may not start | **witness stands** (2026-10-16), no new run; the un-isolated path is `TODO.md` §203 with the operator repro protocol |
| **A′** — image in a tool result, in a *child* | `subagent` (`delegate`) told to `read` the PNG, report the tool result verbatim, describe the screen, then confirm the session still worked | tool result `Read image file [image/png]` with a **real image content block**, no error; the child described pixel-only details (blue "+" plate, "Driver" chip, climate-rail readings) and reported its turn **completed normally** |
| **B** — the chosen lane | same `subagent` call, answer captured | text only, exit status 0, nothing but text entered this session's transcript |

Evidence files (untracked capture dir): `.pi/logs/image-transcript-check/shape-a.txt` (both probes, verbatim
tool results, the child's own words about its tool surface) and `.pi/logs/image-transcript-check/look.txt` (the
child's answer, `look.txt` in the form D2 names).

Both halves matter: the shape is **survivable in a disposable transcript and fatal in the one that holds the
work**. The rule this change documents is therefore scoped to the work session, and the lane is the child.
`design.md` D1 records the lane change (the draft's `pi --print` form was eliminated by the owner's constraint
— "I do not want the LLM to be able to start further PI instances in general … it should not be the final
solution" — and by lean-ctx's shell allowlist: `[BLOCKED — DO NOT RETRY] 'pi' is not in the shell allowlist`).

## 2. Repository edits (tracked)

```
 .pi/skills/device-check/SKILL.md |  7 +--
 .pi/skills/pixel-check/SKILL.md  | 52 ++++++++++++----------
 AGENTS.md                        | 18 +++++---
 TODO.md                          | 96 +++++++++++++++++++++++++++++++++++++++-
 guidelines/Build.md              | 90 +++++++++++++++++++++++--------------
 tools/measure-highlight.py       |  5 ++-
 6 files changed, 200 insertions(+), 68 deletions(-)
```

Untracked by repository rule (`.gitignore`): `ki_processing_failures.log` (line 79), `.pi/logs/**` (line 21),
`openspec/changes/*/` (line 75 — in-flight changes are not tracked; the archive step is what lands them).

- `AGENTS.md` — the screenshot paragraph: both measurements, the child lane, the rule, the text-only-model limit.
- `guidelines/Build.md` §1 — the two harness subsections collapse into one ("Looking at a screenshot"): the
  child lane, the rule, "a child is not a `ctx_*` runtime", the retired third-party package without its
  names (they live on in `TODO.md` §149's history and the archived predecessor).
- `guidelines/Build.md` §11 step 2 — the look sentence only; the diff of that hunk is exactly
  `-  Read the frame with view_image …` / `+  Have a throwaway child session read the frame (§1, "Looking at a
  screenshot") …`, and the measurement authority text below it is untouched.
- `.pi/skills/pixel-check/SKILL.md` — premise, frontmatter description, diagram, first authority bullet, the
  former "sanctioned exception" block (now the rule), capture dir `.pi/logs/pixel-check/`, rule 4.
- `.pi/skills/device-check/SKILL.md` — step 5 (look in a child, verdict from the script) and the
  capture-directory pitfall.
- `TODO.md` — §149 retired (status line + retirement note, id kept), new §203 for the un-isolated error path.
- `tools/measure-highlight.py` — docstring paragraph only (3 insertions / 2 deletions).

## 3. Checks

- **5.1 structural check in place of a build** — this delta touches no compiled file, so **no Gradle build and no
  unit-test gate was run** (`guidelines/Build.md` §2, scope block). The touched tracked paths are the six above;
  no Kotlin, Java, C++, Gradle, manifest or asset file is among them.
  `git status --porcelain` overall is **not** limited to those paths: this working tree carries concurrent peer
  workstreams (`fix-compose-sandbox-idle` and others: ~71 dirty files, e.g. `guidelines/Build.md` §6 and many
  `app/src/test/**` cases). The check is therefore scoped by path, and the scoping is stated rather than hidden.
- **5.2 premise gone repository-wide** —
  `grep -rniE 'view_image|PI_VIEW_IMAGE_BIN|pi-view-image|pi-libtui|/libtui:colors' .pi/skills tools AGENTS.md guidelines README.md`
  → **no matches**. Remaining hits repository-wide are history only: `ki_processing_failures.log` (the two
  entries this change adds), `openspec/changes/archive/2026-10-06-screenshot-evidence-via-view-image/**`,
  `TODO.md` §149's retired body and §147's measurement line (2179, what the then-existing tool saw). **One live
  artifact outside the checked set still asserts the retired premise** and is named rather than corrected here,
  because it belongs to another in-flight change: `openspec/changes/search-result-row-content/tasks.md`
  (a task note saying the `pixel-check` verdict needs the removed package "and this harness does not have that
  package"). It routes no image through a tool result, so it is a stale sentence, not a stale instruction —
  correcting it belongs to that change's own apply pass.
  `bash tools/measure-highlight-selftest.sh` → **`measure-highlight selftest: 7 passed, 0 failed`**.
  The older `.pi/skills/pixel-check/selftest.sh` cover needs ImageMagick and was not run (`magick` is not in the
  shell allowlist; `convert` is) — named exclusion, not an omission.
- **5.3 both edited skills still parse** — `sed -n '1,5p'`:
  - `pixel-check`: `name: pixel-check` / `description: Verify a *visual* claim (something is clipped,
    off-centre, missing, the wrong colour) by looking at a device screenshot in a throwaway child session and
    then measuring it with tools/measure-highlight.py …` — wording only.
  - `device-check`: `name: device-check` / `description:` **unchanged** (its routing to `pixel-check` was
    already the right one).
- **4.4 README by grep** — `grep -niE 'pixel-check|measure-highlight|view_image|screenshot' README.md` → no
  matches: "no mention, unchanged".
- **2.4 revert-check** — no green/red pair: the mutation cannot be observed without risking the session that
  holds the work, and no host-side test can assert an agent transcript. Recorded as a documentation rule with
  no host-side test (the predecessor change's own wording for its docs-only guard).
- `openspec status --change fix-screenshot-evidence-transcript-safety` → `Progress: 3/3 artifacts complete
  (1 skipped)`, all planning artifacts complete.

## 4. Independent review (2026-10-17, read-only `reviewer` subagent)

Verdict **OK with notes** — the core (premise correction, child-session lane, the rule in its three homes, the
skill and docstring edits, `TODO.md` §203) reviewed as correct and evidenced, no live guidance routes an image
through a tool result, the measurement contract is untouched, and the selftest re-ran green in the reviewer's own
run. Six findings, all resolved in this pass:

1. **P1 — the in-place retirement contradicted `TODO.md`'s own legend** (which knew only “retired by
   absorption”). Fixed: the legend now admits the second form explicitly (`retired — <date>: <reason>`, id,
   heading and body kept), which is the form §149 uses and the only occurrence of it in the file.
2. **P2 — the cluster index** still listed §149 and did not list §203. Fixed in the `build-and-harness` /
   improvement row. (The index was already stale for §201/§202 — a pre-existing pattern, not repaired here.)
3. **P2 — a live in-flight change artifact** kept the retired premise
   (`openspec/changes/search-result-row-content/tasks.md`). Named in §3 of this file; not corrected, to avoid
   editing another session's in-flight artifact.
4. **P2 — attribution**: `git diff AGENTS.md` also carries a peer hunk (a Documentation-Map row pointing at
   `Backlog.md`) from a concurrent workstream, and `guidelines/Build.md` carries the peer §6 Compose-sandbox hunk.
   Both are peer work, named here so the diff is not misread as this change's.
5. **P2 — the proposal contradicted itself** about whether the session that did the fatal reads survived.
   Fixed: the wording now says that session was unusable afterwards and a later drafting session continued.
6. **P2 — a stale capture-directory name** (`view-image-check`) survived in one live example. Fixed: the skill
   names `.pi/logs/pixel-check/`; `TODO.md` §147 keeps the historical path as history.

## 6. Post-archive incident — two edits destroyed by a parallel landing, then re-applied

After the archive step, the parallel change in this working tree was committed and pushed (commit `34182ef`, "fix:
repair the Compose sandbox per test"). The next `git status` showed `guidelines/Build.md` and `TODO.md` clean — and
both files were back at their **pre-edit** content: `git show HEAD:guidelines/Build.md | grep -c 'Looking at a
screenshot'` = 0, `git show HEAD:TODO.md | grep -cE '^## 203\.'` = 0. Four other edited files
(`.pi/skills/pixel-check/SKILL.md`, `.pi/skills/device-check/SKILL.md`, `AGENTS.md`,
`tools/measure-highlight.py`) survived, staged and intact — and they are exactly the paths the peer's commit did
not touch. The peer's own hunks are in HEAD (§6 Compose-sandbox rule in `guidelines/Build.md`, the §195 residue
block in `TODO.md`), so their work is safe; what was lost is this change's two shared-file edits, both staged at
the time.

**Cause**: one working tree, two builders — `AGENTS.md`'s own rule (`TODO.md` §40 item 4). **Staging did not
protect the edits**: the index entry and the worktree copy both went, consistent with a path-scoped restore of the
two files the peer's commit also modified.

**Repair (this file's §2 and §4 were re-run after it)**: both files re-applied from this change's own artifacts and
re-staged; verified by content, not by `git status` — `grep -n 'Looking at a screenshot' guidelines/Build.md`,
`grep -cE '^## 203\.' TODO.md` (1), `grep -c 'retired — 2026-10-17' TODO.md` (1), the cluster index row now
listing §203 and not §149, `grep -rniE 'view_image|…' .pi/skills tools AGENTS.md guidelines README.md` → no
matches. Recorded as the third entry of 2026-10-17 in `ki_processing_failures.log`.

## 7. Limits of this evidence

- A′/B were measured once, on one capture and one harness version; a second look is one call away and is the
  cheap re-test after any pi or lean-ctx upgrade.
- The failing shape A itself is still not isolated (no error text), which is exactly why the rule is written
  defensively and `TODO.md` §203 keeps the repro protocol.
- The child lane's own recorded failure mode (`pi.ModelRuntime.create` skew, `ki_processing_failures.log`)
  applies to the lane as well; a missing answer falls back to the measurement script, never to an impression.
