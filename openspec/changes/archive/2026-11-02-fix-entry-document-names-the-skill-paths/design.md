# Design — one pointer, and the case that keeps it

## The root cause

`tools/check-doc-routes.sh:18` resolves every skill path the documentation writes against the skills under
`.pi/skills/`. That rule catches a pointer to a skill that is gone; it cannot catch a pointer that is gone — an
absent reference and a reference to nothing are the same thing to a grep over the text that is there. The diet of
2026-10-31 removed the blocks that named `.pi/skills/create-issue` and `.pi/skills/device-check` and left the
statements they carried in their owning sections, so nothing was wrong with a guideline and nothing was missing
from the tree except the two paths in the entry document.

## The fix

One bullet in `AGENTS.md` under "Agent iteration loop (measure first)", naming both paths and the case that holds
them, and one case in `tools/check-doc-routes-selftest.sh` that reads the entry document and then runs the route
check over the tree. Two legs, one invariant: the names are present, and the names resolve. The second leg is what
makes the case a test of the documentation instead of a grep with a hard-coded expectation — a path added later that
names no skill fails this case through the same run.

The case's two names are the delta's expectation and are declared in the case, not in the check: the checker stays
fact-free (it derives the document set and the skill set at run time), and the entry document's own content is what
the case asserts. `routed-read#bound`, `#route`, `#figure` and `#range` already read this repository's documents for
the same reason, so the tree-reading family is where it belongs.

## Blast radius

| path | what it can break |
|---|---|
| `AGENTS.md` | the entry-document bound (32 000 bytes, `tools/sensors/sensors.json:100`): 25 319 → 25 790 today, so +471 bytes leaves 6.2 kB of headroom; no route, rule or citation text changes |
| `tools/check-doc-routes-selftest.sh` | every existing case still runs; the new case adds one `PASS` line and reads two files the other tree-reading cases already read. `routes#theRepositorysAgentsFileIsUntouched` still holds, because the case writes nothing |

No compiled file, no Gradle, no device, no network.

## Rollback

Delete the bullet and the `echo_case` line with its `case_routed_read_skill_pointers` function. Nothing else
references either: the delta requirement is the only artifact that names the case, and its removal is the archive's
own reverse.

## Alternatives the evidence eliminated

- **A new check that a skill pointer was not dropped.** There is nothing to compare a document against: the set of
  skills a session needs cannot be derived from the tree, so the check would need a declared list — which is the
  case, with a checker wrapped around it. The case states the expectation once and the second leg reuses the
  existing check; a second checker would duplicate its resolution logic.
- **Making every bare skill name in `AGENTS.md` a path.** `AGENTS.md:54` and `AGENTS.md:149` name eight skills as
  bare words, and `AGENTS.md:224` names four more. A rule that refused a bare name would report thirteen lines in
  three blocks, and the repair would rewrite them — far beyond the one-line pointer the gap itself records, and it
  would break the routing table's prose to buy a form the reader does not need there.
- **Restating the two skills' jobs in `AGENTS.md`.** G1 of the diet's record states that the statements those blocks
  carried survive in the owning sections; a restatement would add a second normative home, which
  "A rule has one normative home" refuses.
