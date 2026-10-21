# Design — fix-ambiguous-section-reference

## Root cause

`tools/check-doc-routes.sh` implements the "a bare number its document uses twice is ambiguous" refusal over
one text region: the `## Documentation Map` section of `AGENTS.md` (`:66` extracted the region, `:95` piped
only that region through the reference parser, `:130`/`:134`/`:138` stamped every error `file=AGENTS.md`).
A guideline body is where a citation is actually written, and it was never read — so `guidelines/Design.md:503`
carries a bare `§14` of `guidelines/MapRendering.md`, whose two `## 14.` headings (`:612` rotation handoff,
`:689` AA renderer) make that reference unresolvable, and no check refuses the next one.

One mechanism, one fix: the extraction is factored into `extract_refs` and run over the map region **and**
every `guidelines/*.md` body, with fenced code blocks skipped and every error naming the file it was written
in. The one reference the widened check then flags is repaired to carry its heading text.

## The single fix

`tools/check-doc-routes.sh`:

- `extract_refs <file> <report-name> <fences>` — the existing `sed` sentinel + `awk` parser, moved into a
  function, emitting a fourth tab-separated field: the file to report the error against. With `fences=1` a
  line inside a ```` ``` ```` block is skipped, so a quoted reference stays an example.
- the refs file is built from `$tmp/region` (reported as `AGENTS.md`, unchanged behaviour) and then from each
  `$gdir/$f.md` (reported as `guidelines/<file>.md`).
- the resolve step prints `file=%s` from that fourth field instead of the literal `AGENTS.md`.

`guidelines/Design.md:503`: `§14` → `§14 "Android Auto renderer"`, the form `AGENTS.md:28` already uses.

`guidelines/Backlog.md` §14: the bullet that documents the check names the body-reference rule beside the
unrouted rule.

`tools/check-doc-routes-selftest.sh`: five fixture cases plus the header's case list.

## Alternatives the evidence eliminates

1. **Renumber the second `## 14.`** (to `§21`, the next free number) and update the live citations. This is
   what the issue text offers first, and the measurement rules it out: 20 of the 22 `MapRendering.md §14`
   references sit in `openspec/changes/archive/`, and they are already split over the duplicated number —
   `archive/2026-10-09-fix-follow-rotation-crash/design.md:13` means the rotation handoff,
   `archive/2026-10-05-fix-car-render-loop-restart/proposal.md:38` means the AA renderer. Renumbering either
   silently repoints the other group at the wrong section, and the archive is the record of what was decided
   at the time, not a document to rewrite. It would also reverse a decision the shipped spec already
   records — scenario "A heading number is ambiguous or absent" of `documentation-ownership` says an
   ambiguous number is resolved *by its heading text* — and the routing table already relies on it.
2. **Extend the check to every `.md` in the repository.** Measured: 65 references across `guidelines/`,
   `openspec/specs/`, `.pi/skills/` and `README.md`, of which exactly the same one is ambiguous. The extra
   readers buy nothing measurable and cost an exemption rule for `openspec/changes/**` (history and open
   artifacts) whose boundary would then be the thing to police.
3. **Extend it to a document's bare `§N` self-references.** 233 of those exist, and the ones that matter name
   `TODO.md`'s retired numbering — a dangling document, not an ambiguous number, and a different item. In the
   document that carries the duplicate, `guidelines/MapRendering.md`, none of its 18 self-references is `§14`,
   so this half of the surface has no defect to fix here.

No viable option remains: (1) is a silent repoint of recorded history plus a reversal of a shipped decision,
(2) and (3) widen the change without removing a single unresolvable reference.

## Blast radius

Four files, two mechanisms:

- the check's scope (one function, one loop, three `printf` arguments);
- one citation in `guidelines/Design.md` and one bullet in `guidelines/Backlog.md`;
- the check's cases.

Not touched: any Kotlin, Java, C++ or JNI source, any manifest, resource, Gradle or CMake file, any
archived change, any open change artifact, and `AGENTS.md` (its `§14 "Android Auto renderer"` row already
resolved and still does). The rule that a *document* name exists stays the routing table's: a guideline body
naming a `.md` that is not a guideline (`TODO.md`, a spec path) is prose and is not resolved, which is also
what keeps this change from failing on the retired backlog's citations. `tools/check-portability.sh` reads the same configuration as before; the check
still derives its documents from `guidelines/*.md` and names no project value, so it stays portable.

Runtime: the check now reads 21 more small text files (the guideline bodies) — it ran on the repository in
under a second, and it still touches no network, no device and no build.

## Rollback

Revert the commit. `tools/check-doc-routes.sh` reads the map region alone, the three red cases fail exactly as
recorded in `evidence/red/selftest.txt`, and `guidelines/Design.md:503` returns to its bare `§14`. Nothing else
consumes `extract_refs`, and no data or state is migrated.

## Verification

- Red on HEAD (`git show 44b673f:tools/check-doc-routes.sh` + this change's case file):
  `check-doc-routes-selftest: 12 passed, 3 failed`, `evidence/red/selftest.txt`.
- Green after the fix: `15 passed, 0 failed`, and `check-doc-routes: all 120 section references resolve`,
  `evidence/green/selftest.txt` (2026-10-21T12:05:23Z).
- Two revert-checks, one mutation each, both restored (identical file, `diff`): disabling the ambiguity
  refusal (`count[key] > 1` → `count[key] > 99`) fails the two ambiguity cases (13 passed, 2 failed);
  disabling the missing-section branch (`if (!(key in headings))` → `if (0)`) fails the new body-dangling case
  and no other (14 passed, 1 failed) — the counter-example that the body scan is what makes that case
  load-bearing. `evidence/green/mutation-*.txt`.
- The fenced-block and change-artifact cases are green on HEAD for the wrong reason and green after the fix
  for the right one; they are recorded as counter-cases, not red cases, in the proposal.
- No device measurement applies: nothing here renders, and no compiled file is touched — the check's own
  self-test is the gate (`guidelines/Build.md` §2, the scope block).
- **Exclusion.** `bash tools/loop-selftest.sh` reports `1 of 131 cases failed`
  (`worktree#keepsByDefaultAndRemovesOnRequest`, `git still lists the worktree that was removed`) when it is
  run *inside the run's own linked worktree*, and the same case passes on a plain copy of this tree
  (`/tmp/nv98/curtree`) and on a pristine `44b673f` tree (`/tmp/nv98/headtree`). This change touches no file
  under `tools/loop/**`, and the case's fixtures are copied from `tools/tooling.json` and
  `tools/pi-child-events.sh`, neither of which it changes — so the failure is environment-dependent and
  pre-existing, recorded in `evidence/green/loop-selftest-environment.txt` rather than repaired here.
