# Design — fix-guardrail-entries-carry-their-tags

## Root cause

§40's discharge paragraph promises that "the docs tag the rule with its item number (`§40.N`) so the mapping
stays auditable", and three entries were carried into their owning section with the rule but without the tag
(46, 55), or with the worked example but without the rule (38) — so a reader holding the record cannot reach
the section, and a reader holding the section cannot tell which record entry it answers.

## The single fix

Three line-level edits in two guideline files, each at the section that owns the concern:

1. `guidelines/Build.md:158-160`, §2a "Agent iteration protocol", the one-builder bullet: append `(§40.46)` to
   "run `find <module> -newermt '-10 minutes'` beside the wrapper probe, and if a peer is mid-edit, quote the
   evidence already collected and stop". The rule is already written there in the entry's own terms; only the
   tag is missing.
2. `guidelines/Build.md` §4d "Attributing a red run": add one bullet stating the entry's rule — a flake of
   unknown origin gets an entry with its rerun evidence, never a silent retry — and tag it `(§40.38)`. The
   bullet names the tree's own fact ("this tree keeps no flake list", `:591`) as the reason a retry that leaves
   no record loses the only evidence, and points at §6d for the worked example (the canary that always passed
   alone, whose awaited state came from `Dispatchers.Default`). The rule sits in §4d, not §6d: §6d owns why
   that flake happened, §4d owns how a red run is attributed and what is recorded about it.
3. `guidelines/MapRendering.md:899`, §15a "Stylesheet load failure …": append `(§40.55)` to the verification
   bullet ("a stylesheet defect is NOT caught by building — the app must be run once with `adb logcat -s
   NaviVeylin | grep -i "style error"`"), which states both clauses of the entry.

Each tag is written in the form the guidelines already use for the other 54 entries, so a reader greps `§40.38`
and lands on the rule.

## Blast radius

- `guidelines/Build.md` (two tags, one new bullet) and `guidelines/MapRendering.md` (one tag). No section
  number, heading, route row or spec text changes, so `AGENTS.md` and `tools/check-doc-routes.sh` need no edit.
- No compiled path, no test source: no Gradle task is owed, and the gate's pathspec is markdown only.
- The change's own artifacts plus one evidence probe (`evidence/red-probe.sh`), text only — no build, no
  device, no network.

## Rollback

Delete the three additions (or `git revert` the change's commit). No text depends on them: the rules remain
readable where they were, only the record tag disappears again.

## Alternatives eliminated by the evidence

- **Tag entries 5, 8 and 37 with `§40.N`.** Eliminated by the record's own text: those three entries name a
  section of the retired prose backlog instead of an artifact (`tracked: §17`, `§6`, `§14, §15`), so a `§40.N`
  tag would claim a home the entry never had. Two of the three rules are stated beside the tagged ones
  (`Build.md:1033-1039` states entry 8's batching rule and entry 5's cache-hit rule); leaving them is what the
  record asks.
- **Tag `guidelines/UI.md:1078` for entry 55.** Eliminated: that paragraph states the lowercase-hex rule in the
  route-colour context and still says a rejected stylesheet "crashes the renderer", which §15a's contract
  contradicts (a rejected stylesheet never becomes the active style, the previous one stays, and the failure
  is reported, `:878-886`). A tag there would map the entry to a sentence the tree no longer supports; the
  stylesheet section `AGENTS.md` routes a stylesheet change to is §15a. Correcting the UI.md sentence is a
  separate claim and is left out of this change.
- **State entry 38's rule inside §6d beside the worked example.** Eliminated: §6d owns the cause (the shared
  `Dispatchers.Default` pool), §4d owns attribution and the record a red run leaves behind, which is what the
  entry states.

## Why no capability delta

`documentation-ownership` already requires the tag (*A recorded guardrail is stated in the section that owns
it*, `openspec/specs/documentation-ownership/spec.md:390-406`). This change makes three shipped entries obey
it; it specifies nothing new, so `.openspec.yaml` carries `skip_specs: true`.
