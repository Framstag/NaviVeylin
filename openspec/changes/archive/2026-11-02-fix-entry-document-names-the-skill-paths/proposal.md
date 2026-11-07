# The entry document names the skill paths it lost

## Why

Measured on base `9318dad90896e1e14364f71e5d6ae119def8f330`, the documentation names exactly two skill contracts
while `.pi/skills/` carries 36:

```
$ grep -rho '\.pi/skills/[A-Za-z0-9._-]*' AGENTS.md guidelines/*.md | sort -u
.pi/skills/
.pi/skills/feature-list
.pi/skills/pixel-check
$ ls .pi/skills | wc -l
36
```

`create-issue` and `device-check` were named in the two blocks the diet of 2026-10-31 removed — the nine-skill table
and the iteration loop's pointer bullets — and neither name survives as a *path*: `create-issue` appears twice as a
bare word inside `.pi/skills/*` (`AGENTS.md:54`, `AGENTS.md:149`) and `device-check` appears nowhere at all
(`grep -c 'device-check' AGENTS.md` = 0). The change that removed the blocks recorded the gap itself:
`openspec/changes/archive/2026-10-31-guideline-layers-mechanism/evidence/known-gaps.md:6` (G1) states that the
statements those blocks carried do live in the owning sections, that the *path-shaped* pointer did not survive, and
that "a one-line restore in `AGENTS.md` is strictly subtractive to fix".

**The root cause, in one sentence.** The entry document reaches those two skills only through a directory listing
(`.pi/skills/*`) or not at all, because the pointers that named them by path were removed with the blocks they
lived in, and the check that resolves a documented skill path (`tools/check-doc-routes.sh:18`) resolves the paths a
document still writes — a reference that no longer exists is not a reference, so the removal is silent.

**The red case.** `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`, added to
`tools/check-doc-routes-selftest.sh:745`. It reads this repository's entry document, requires both paths in it, and
then runs `tools/check-doc-routes.sh` over the tree so a name that does not resolve fails the same case. On base
`9318dad9` with only the case added it fails (`evidence/red-on-HEAD.log`: "the entry document names no path for
.pi/skills/create-issue .pi/skills/device-check", XML tests=1 failures=1, 2026-11-02T04:37:21Z, taken by
`evidence/red-on-HEAD.sh`). Falsified once: deleting the added bullet takes it red again and restoring the bullet
takes it green (`evidence/mutation.log`, `evidence/mutation.sh`).

**Owning capability and its home.** `documentation-ownership` — the requirement "A document that names a skill
resolves to a skill that exists" (`openspec/specs/documentation-ownership/spec.md:412`) owns the path form, and the
check it names is `tools/check-doc-routes.sh`. `AGENTS.md:57` routes "how the guidelines themselves are organized"
to that spec rather than to a guideline, which is why the rule is stated there and no file under `guidelines/` is
touched.

**Surface.** The rule this change writes has no other place that asserts it, and the two skills it names are named
nowhere else in the documentation:

```
$ grep -rln 'create-issue\|device-check' AGENTS.md guidelines tools | sort
AGENTS.md
tools/check-doc-routes-selftest.sh
$ grep -c '^echo_case ' tools/check-doc-routes-selftest.sh          # 6
$ git show 9318dad9:tools/check-doc-routes-selftest.sh | grep -c '^echo_case '   # 5
```

surface: 1 case added, 0 rewritten, 0 fixtures realigned — `grep -c '^echo_case ' tools/check-doc-routes-selftest.sh`
against the base's own count, and the skill-path reference count rising from 2 to 4 by the `grep -rho` above.

## What Changes

- **`AGENTS.md`** — one bullet under the iteration loop's contract naming both skills by their paths, and naming the
  case that holds them. No sentence is removed and no claim is restated: the two statements the removed blocks
  carried already live in the owning sections, and this is the pointer only.
- **`tools/check-doc-routes-selftest.sh`** — `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`, the case that
  reads the entry document and the route check together, plus the file's own header sentence that counts the
  `routed-read#` cases.
- **`openspec/specs/documentation-ownership/spec.md`** (delta) — one added requirement, "The entry document names the
  skill a reader needs by its path", and one scenario naming the case above.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-ownership`: one requirement added beside "A document that names a skill resolves to a skill that
  exists" — the entry document names a skill by its path when the job it describes is done by that skill — and one
  scenario naming the case. No shipped requirement's text changes.

## Impact

| path | what changes |
|---|---|
| `AGENTS.md` | one bullet added (5 lines) under "Agent iteration loop (measure first)"; 25 319 -> 25 790 bytes against the 32 000-byte entry-document bound (`tools/sensors/sensors.json:100`) |
| `tools/check-doc-routes-selftest.sh` | one case (`case_routed_read_skill_pointers` and its `echo_case`), the header's case count |
| `openspec/specs/documentation-ownership/spec.md` | one added requirement and its scenario, through the delta and the archive |

No compiled file: the gate this change owes is the shell checks `*.md` and `tools/check-doc-routes*` pull in, and
`./tools/loop/gate.sh` runs exactly those.
