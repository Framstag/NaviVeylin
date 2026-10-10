# Tasks

Delta under implementation: three new capabilities — `spec-feature-index` (6 requirements, 19 scenarios),
`feature-list-generation` (8 requirements, 24 scenarios), `release-notes-generation` (10 requirements,
21 scenarios): 24 requirements, 64 scenarios. The scenario → case mapping is enumerated in §5.0, where every
scenario is named and §5.1 is the fixture case that machine-checks it.

This change adds no runtime code: it introduces `tools/gen-feature-list.sh` plus its self-test, two data files,
prompt files, and two generated Markdown documents. It compiles no Kotlin and changes no test, so no Gradle gate
is owed; §7.4 proves that mechanically rather than asserting it. §7.6 runs the project's gate once anyway,
because `openspec/config.yaml`'s apply guidance asks for it and because a prediction that the build is
unaffected is worth falsifying once.

## 1. Classification data — the gate everything else reads

- [x] 1.1 Author `tools/feature-list/areas.json`: the area catalogue, one entry per feature area with `id`,
      `heading`, `order`, and `carOnly` set on exactly the one car-only area. Seed the set from the id survey in
      `design.md` (Context): routing, search, favorites, offline maps, map appearance, map interaction, driving
      and positioning, Android Auto and car, device fit and accessibility, settings data and legal, sharing
      across devices, plus the car-only area. **Spec**: `feature-list-generation` "Car support is a tag, not a
      separate copy of the feature" and "The catalogue names every user-visible area" — the file is where the
      area set is decided. **Verify**: the file parses with `jq`/`yq`-free shell parsing, holds no duplicate area
      id, marks exactly one area `carOnly`, and the tool's `--check-classification` reports the area count.

- [x] 1.2 Author `tools/feature-list/specs.json`: one entry per shipped spec id — 153 today — with `area`,
      `userVisible` and `surfaces` (`phone`, `car`, or both). Seed the 128 unambiguous ids by prefix rule
      (`auto*`/`car-*` → car or both, `build-*`/`ci-*` → internal), then decide the remaining gray zone by reading
      each spec. **Spec**: `spec-feature-index` "An unclassified spec id stops the run" / "A classified spec id
      passes". **Verify**: `--check-classification` exits 0 and reports 153 classified, 0 unclassified, 0 stale;
      the count equals `openspec list --specs --json | jq '.specs|length'`.

- [x] 1.3 Cross-check the gray-zone decisions against the specs and record them: for each id whose
      `userVisible` or `surfaces` was not derivable from its prefix — the ~25 in `design.md` (Context) plus any
      the seeding left ambiguous — quote the purpose line of its spec that decided the value. **Spec**:
      `feature-list-generation` "The catalogue presents only user-visible capabilities" / "An internal capability
      is absent" — a wrong `userVisible: true` publishes plumbing, a wrong `false` hides a feature. **Verify**: the
      quote list, one line per id, is in the change report; every id with a gray-zone classification appears in it.

## 2. The capability index — `tools/gen-feature-list.sh`

- [x] 2.1 Create the tool skeleton: argument parsing, the required `--release <versionName>`, the optional
      `--phrase-cmd <command>`, `--check-classification`, `--root`, a usage error path that writes nothing, and an
      atomic write helper (write a temporary file, then rename). Shell plus `jq` only — no `python3`
      (`openspec/config.yaml`). **Spec**: `release-notes-generation` "The version is supplied, never read from the
      build's version state" / "A run without a version fails", "The run works where the version-state file is
      absent"; `spec-feature-index` "The index runs offline, without a model and without a build". **Verify**: a run
      with no `--release` exits non-zero with a usage message while `git status --porcelain` stays unchanged; a run
      with `--release 2026-10-08-1` on a copy of the tree with `app/release-version.properties` deleted completes.

- [x] 2.2 Implement enumeration, capability-key derivation and spec digests: walk the shipped-spec directory, derive
      `<spec-id>#<requirement name>` per requirement, digest each spec's bytes. **Spec**: `spec-feature-index`
      "A capability key identifies one requirement of one shipped spec" / "Keys are derived for every shipped
      capability", "A key survives other specs changing", "An in-flight change contributes no key". **Verify**: the
      fixture suite's three cases of that requirement pass, and on the real tree the key count equals the sum of
      `openspec list --specs --json | jq '[.specs[].requirementCount]|add'` (929 today).

- [x] 2.3 Implement the delta at requirement-text level: store a digest of each key's requirement text in the
      snapshot and classify each key as added, changed, removed or unchanged by comparing digests, never the spec
      digest. **Spec**: `spec-feature-index` "The delta compares requirement text, not spec bytes" / "A prose-only
      edit is not a change", "A requirement text edit is a change", "An added requirement is an addition".
      **Verify**: the fixture cases pass; on the real tree, editing a sentence in a spec's `## Purpose` reports zero
      changed keys while editing a requirement's text reports exactly that one key.

- [x] 2.4 Implement the classification gate: read both data files, report a shipped spec id named by neither, fail
      on it, report a stale entry without failing, validate `surfaces` against the known set, and report the surface
      per capability. **Spec**: `spec-feature-index` "An unclassified spec id stops the run" / "A new spec id fails
      the run", "A classified spec id passes", "A removed spec id is reported without failing on it", "A surface
      outside the known set fails the run", "A capability's surface is reported". **Verify**: the five fixture cases
      pass; on the real tree, deleting one line from `specs.yaml` fails naming that id and writes no document.

- [x] 2.5 Implement snapshot writing and baseline selection: write
      `tools/feature-list/snapshots/<version>.json` holding each key's text digest, area, user-visibility and
      surfaces; select as baseline the lexicographically greatest snapshot version below the requested one; report
      when none exists. **Spec**: `spec-feature-index` "A snapshot records the state a version was generated from" /
      "The baseline is the most recent earlier snapshot", "Versions order as the release format orders them",
      "No earlier snapshot exists", "A snapshot carries what a later run needs to compare". **Verify**: fixture
      snapshots `…-1`, `…-2`, `…-10` select `…-10` for a run at `…-09-1`; a later run decides every changed key
      reading only the snapshot and the specs.

- [x] 2.6 Implement the run report: specs read, keys in the index, keys added/changed removed/unchanged, dirty areas,
      unclassified and stale ids. **Spec**: `spec-feature-index` "A run reports what it read and what moved" /
      "A no-op run is visibly a no-op", "A one-spec change is visibly one change". **Verify**: two consecutive runs
      report zero added, changed and removed keys and zero dirty areas; after editing one requirement's text, the
      report names one changed key and its area.

- [x] 2.7 Implement the model-free degraded mode and check the bound: when no `--phrase-cmd` is configured, assemble
      mechanically from the classified capability data instead of failing; assert the index path reaches no network,
      runs no Gradle task and invokes no phrasing command. **Spec**: `spec-feature-index` "The index runs offline,
      without a model and without a build" / "The index runs with no model available", "The index runs over the whole
      shipped set". **Verify**: with no phrasing command configured the run completes and reports keys and delta; the
      wrapped `time` for the full 153-spec set is under the 30 s the spec requires; the invocation log is empty.

- [x] 2.8 **Revert-check** for the classification gate: mutate by deleting one spec id's line from
      `tools/feature-list/specs.yaml`; the run MUST exit non-zero naming that id and MUST leave both generated
      documents unchanged; restore the line; re-run and it MUST exit 0. One mutation only. **Spec**:
      `spec-feature-index` "An unclassified spec id stops the run" / "A new spec id fails the run". **Verify**: both
      exit codes quoted in the change report, failure first; the file digests of the two documents before and after
      the failing run are identical. This check reads text files only, so "forced green" is the re-run of the command
      — no build cache is involved.
- [x] 2.9 **Revert-check** for baseline selection: mutate the selector to take the newest snapshot instead of the
      greatest version below the requested one; the `…-09-1` case MUST then select `…-10` rather than `…-2`, failing
      the ordering case; restore; re-run green. One mutation only. **Spec**: `spec-feature-index` "A snapshot records
      the state a version was generated from" / "Versions order as the release format orders them". **Verify**: the
      failing run's reported baseline and both exit codes in the change report.
- [x] 2.10 **Revert-check** for delta granularity: mutate the delta to compare spec digests instead of requirement
      text; the prose-only case MUST then report every key of the edited spec as changed; restore; re-run green.
      One mutation only. **Spec**: `spec-feature-index` "The delta compares requirement text, not spec bytes" /
      "A prose-only edit is not a change". **Verify**: the failing run's changed-key count for the prose-only
      mutation and both exit codes in the change report.

## 3. The catalogue — `FEATURES.md`

- [x] 3.1 Add `tools/feature-list/prompts/` — the phrasing instructions, one file per role (area section
      composition, merge guidance) — and the prompt digest over those files, then implement the phrasing seam: invoke
      `--phrase-cmd` once per dirty area with the prompt and a bundle of its capabilities, read the section back from
      stdout. `design.md` D3, D7. **Spec**: `feature-list-generation` "Only dirty areas are regenerated" /
      "An instruction change dirties every area". **Verify**: editing a byte of a prompt file changes the prompt
      digest and, in the fixture suite, dirties every area; the digest is a function of the file contents and nothing
      else.

- [x] 3.2 Implement area rendering, dirty detection, the cache and the whole-file write: compute each area's cache
      key over its definition, its members' spec digests, their requirement-text digests, their classification
      entries and the prompt digest; regenerate only a cache miss; splice hits verbatim; write the document through
      a temporary file and rename; emit a stable order with no generation timestamp (`design.md` D6, D7, D11).
      **Spec**: `feature-list-generation` "Only dirty areas are regenerated" / "A run with nothing changed invokes no
      model", "One area is dirty", "A classification change dirties two areas", "A change to a spec outside an area's
      members does not dirty it"; "The catalogue is a living document, regenerated rather than appended" / "Two runs
      produce the same file", "Regeneration drops a capability that is gone". **Verify**: the fixture cases of both
      requirements pass; on the real tree a second run with no change invokes the phrasing command zero times and
      reproduces the file byte for byte (`sha256sum`), and the file contains no date or time.

- [x] 3.3 Implement the source markers and the prose gates: emit `<!-- cap: … -->` after every entry
      (`design.md` D8), fail on an entry carrying no key, fail on a user-visible key no entry carries, fail on a
      entry stating a figure that appears in none of its cited specs, and report without failing every content word
      of an entry that appears in none of the cited specs. **Spec**: `feature-list-generation` "Every entry carries
      the capabilities it came from" / "A entry with no key fails the run", "A entry's keys resolve"; "The
      catalogue accounts for every user-visible capability" / "A capability merged into an entry is accounted for",
      "A dropped capability fails the run", "A capability that should not be published is classified internal";
      "A stated figure appears in a spec the entry cites" / "An invented number fails the run", "A figure taken
      from a cited spec passes", "Marketing phrasing does not change the check"; "Prose the run cannot verify is
      reported for review" / "A word the cited specs do not contain is reported", "A entry whose words are all
      sourced is not reported", "The report does not stand in for the figure gate". **Verify**: the eleven fixture
      cases pass, an entry whose wording is rephrased without changing its cited specs still passes, an entry with a
      fabricated figure fails, and an entry with an unsourced word is reported while the run stays green.

- [x] 3.4 Implement the surface and car-platform presentation: render each area's surfaces as the union of its
      user-visible members' surfaces, present a dual-surface capability once under its area, keep a
      car-available-only capability of a feature area under that area tagged through the area, place capabilities
      whose subject is the car platform in the `carOnly` area and nowhere else, render that area under its own
      heading, and render a heading only for an area holding at least one user-visible capability.
      **Spec**: `feature-list-generation` "Car support is a tag, not a separate copy of the feature" / "A capability
      on both surfaces is tagged once", "A car-only capability of a feature area stays under that area",
      "Searching the catalogue for a surface finds every claim about it"; "The car platform owns one area of its own" /
      "Exactly one area is the car platform's own", "A capability about the car platform is not spread into a feature
      area", "The car-only area is rendered last and heading-named"; "The catalogue presents only user-visible
      capabilities" / "An area holding only internal capabilities gets no heading", "The catalogue names every
      user-visible area". **Verify**: the eight fixture cases pass; on the real catalogue, grepping the car tag plus
      the car-only area yields every spec id whose classification includes the car surface, and no area heading
      exists for an area whose capabilities are all internal.

- [x] 3.5 **Revert-check** for the coverage gate: mutate by dropping one capability key from an entry's marker
      while leaving its text; the dropped-capability case MUST fail and the catalogue on disk MUST be unchanged;
      restore; re-run green. One mutation only. **Spec**: `feature-list-generation` "The catalogue accounts for every
      user-visible capability" / "A dropped capability fails the run". **Verify**: both exit codes and the named key
      in the change report.
- [x] 3.6 **Revert-check** for the sourcing gate: mutate by disabling the figure check so an invented figure passes;
      the invented-number case MUST fail; restore; re-run green. One mutation only. **Spec**:
      `feature-list-generation` "Prose introduces no fact the cited specs do not contain" / "An invented number fails
      the run". **Verify**: both exit codes and the fabricated figure in the change report.
- [x] 3.7 **Revert-check** for the cache key: mutate the key to omit the member requirement-text digests; the
      one-area-dirty case MUST fail — a change inside a member spec then leaves the area stale or dirties every
      area; restore; re-run green. One mutation only. **Spec**: `feature-list-generation` "Only dirty areas are
      regenerated" / "One area is dirty". **Verify**: the failing run's report of dirty areas and both exit codes in
      the change report.
- [x] 3.8 **Revert-check** for byte-identity: mutate the renderer to emit a generated-at timestamp in the document
      header; the two-runs-same-file case MUST fail; restore; re-run green. One mutation only. **Spec**:
      `feature-list-generation` "The catalogue is a living document, regenerated rather than appended" / "Two runs
      produce the same file". **Verify**: the two differing digests from the mutated run and both exit codes in the
      change report.

## 4. The release notes — `RELEASE-NOTES.md`

- [x] 4.1 Implement the entry renderer: one entry per version carrying its version, grouped under the catalogue's
      area headings, written in place so that a re-run for the same version replaces that entry and leaves every
      other entry untouched. `design.md` D9. **Spec**: `release-notes-generation` "One entry per version, derived
      from that version's baseline" / "Re-running a version replaces its entry", "A new version adds one entry",
      "The entry names its version"; "The note is grouped by feature area and derived without a model" / "Changed
      capabilities are grouped by area". **Verify**: the four fixture cases pass; on the real tree, generating for
      one version twice leaves exactly one entry for it, byte-identical.

- [x] 4.2 Implement selection and the empty-release refusal: include a changed capability only when its spec is
      classified user-visible, and when the resulting set is empty write no entry, exit successfully, report the
      verdict and invoke no phrasing command. **Spec**: `release-notes-generation` "Only user-visible capabilities
      appear" / "An internal change produces no entry line", "A mixed version lists only the user-visible part";
      "Nothing user-visible moved means no entry, with a verdict" / "A maintenance-only version reports and writes
      nothing", "The notes document is not created empty", "The refusal does not invent filler".
      **Verify**: the five fixture cases pass; a run whose only changed specs are internal writes nothing, exits 0,
      reports the verdict, and the invocation log stays empty.

- [x] 4.3 Implement the vanished-key accounting: pair a vanished key with an added key in the same existing spec
      and report a rename as a change rather than a removal; report a requirement gone from a spec that still exists
      as a removal when that spec is user-visible; report a spec that left the shipped set in the run report and keep
      its capabilities out of the entry; report every unaccounted disappearance. `design.md` D10.
      **Spec**: `release-notes-generation` "Every vanished capability key is accounted for" / "No disappearance goes
      unreported", "An accounted disappearance is not an error"; "A renamed requirement is a change, not a removal" /
      "A renamed requirement is a change", "The renamed capability keeps its place"; "A requirement that is gone is
      reported as a removal" / "A deleted requirement is a removal", "An internal removal stays out of the entry";
      "A spec leaving the shipped set is reported, not published". **Verify**: the six fixture cases pass; a fixture
      whose pairing is not confident reports the key in the run report instead of publishing it.

- [x] 4.4 Implement the first-run behaviour: a run whose requested version has no earlier snapshot records the
      snapshot, writes no entry, and reports that it established the baseline rather than that nothing changed.
      **Spec**: `release-notes-generation` "The first run establishes a baseline instead of an entry" / "The first
      ever run writes no entry", "The run after the baseline writes an entry". **Verify**: the two fixture cases
      pass; the report's wording for the first run distinguishes "baseline established" from "nothing user-visible
      changed".

- [x] 4.5 **Revert-check** for the empty-release refusal: mutate the renderer to emit a placeholder line when the
      changed user-visible set is empty; the maintenance-only case MUST fail; restore; re-run green. One mutation
      only. **Spec**: `release-notes-generation` "Nothing user-visible moved means no entry, with a verdict" /
      "A maintenance-only version reports and writes nothing". **Verify**: both exit codes and the placeholder text in
      the change report.
- [x] 4.6 **Revert-check** for idempotency: mutate the writer to append the new entry instead of replacing the
      version's entry; the re-running-a-version case MUST fail with two entries; restore; re-run green. One mutation
      only. **Spec**: `release-notes-generation` "One entry per version, derived from that version's baseline" /
      "Re-running a version replaces its entry". **Verify**: the duplicated entry from the failing run and both exit
      codes in the change report.
- [x] 4.7 **Revert-check** for rename pairing: mutate by disabling the pairing so a renamed requirement is reported
      as a removal plus an addition; the renamed-requirement case MUST fail; restore; re-run green. One mutation
      only. **Spec**: `release-notes-generation` "A renamed requirement is a change, not a removal" / "A renamed
      requirement is a change". **Verify**: the failing entry text and both exit codes in the change report.

## 5. Self-test and scenario traceability

- [x] 5.0 Scenario → case table, filled in with each row's result (no scenario unnamed):

      | capability | requirement | scenario | case | result |
      |---|---|---|---|---|
      | index | key identifies one requirement | keys derived for every shipped capability | 2.2 | ok |
      | index | key identifies one requirement | a key survives other specs changing | 2.2 | ok |
      | index | key identifies one requirement | an in-flight change contributes no key | 2.2 | ok |
      | index | delta compares requirement text | a prose-only edit is not a change | 2.3, 2.10 | ok |
      | index | delta compares requirement text | a requirement text edit is a change | 2.3 | ok |
      | index | delta compares requirement text | an added requirement is an addition | 2.3 | ok |
      | index | unclassified spec id stops the run | a new spec id fails the run | 2.4, 2.8 | ok |
      | index | unclassified spec id stops the run | a classified spec id passes | 2.4 | ok |
      | index | unclassified spec id stops the run | a removed spec id is reported without failing | 2.4 | ok |
      | index | unclassified spec id stops the run | a surface outside the known set fails the run | 2.4 | ok |
      | index | unclassified spec id stops the run | a capability's surface is reported | 2.4, 3.4 | ok |
      | index | snapshot records the state | the baseline is the most recent earlier snapshot | 2.5 | ok |
      | index | snapshot records the state | versions order as the release format orders them | 2.5, 2.9 | ok |
      | index | snapshot records the state | no earlier snapshot exists | 2.5, 4.4 | ok |
      | index | snapshot records the state | a snapshot carries what a later run needs | 2.5 | ok |
      | index | runs offline and model-free | the index runs with no model available | 2.7 | ok |
      | index | runs offline and model-free | the index runs over the whole shipped set | 2.7 | ok |
      | index | run reports what moved | a no-op run is visibly a no-op | 2.6, 3.2 | ok |
      | index | run reports what moved | a one-spec change is visibly one change | 2.6 | ok |
      | catalogue | only user-visible capabilities | an internal capability is absent | 3.4, 4.2 | ok |
      | catalogue | only user-visible capabilities | an area holding only internal capabilities gets no heading | 3.4 | ok |
      | catalogue | only user-visible capabilities | the catalogue names every user-visible area | 3.4 | ok |
      | catalogue | car is a tag | a capability on both surfaces is tagged once | 3.4 | ok |
      | catalogue | car is a tag | a car-only capability of a feature area stays under that area | 3.4 | ok |
      | catalogue | car is a tag | searching for a surface finds every claim about it | 3.4 | ok |
      | catalogue | car platform owns one area | exactly one area is the car platform's own | 1.1, 3.4 | ok |
      | catalogue | car platform owns one area | a capability about the car platform is not spread | 3.4 | ok |
      | catalogue | car platform owns one area | the car-only area is rendered last and heading-named | 3.4 | ok |
      | catalogue | every entry carries its capabilities | an entry with no key fails the run | 3.3 | ok |
      | catalogue | every entry carries its capabilities | an entry's keys resolve | 3.3 | ok |
      | catalogue | every entry leads with a keyword | an entry leads with a keyword | 3.3 | ok |
      | catalogue | every entry leads with a keyword | the keyword is short enough to scan | 3.3 | ok |
      | catalogue | every entry leads with a keyword | the keyword comes from the cited specs | 3.3 | ok |
      | catalogue | written at feature level | one entry per capability fails the run | 3.3, 3.4 | ok |
      | catalogue | written at feature level | an entry covering several capabilities passes | 3.3, 3.4 | ok |
      | catalogue | written at feature level | a small area may still use two entries | 3.4 | ok |
      | catalogue | written at feature level | the run reports the density it achieved | 3.4 | ok |
      | catalogue | accounts for every user-visible capability | a capability merged into an entry is accounted | 3.3 | ok |
      | catalogue | accounts for every user-visible capability | a dropped capability fails the run | 3.3, 3.5 | ok |
      | catalogue | accounts for every user-visible capability | a capability that should not be published | 1.3, 3.3 | ok |
      | catalogue | the figure gate | an invented number fails the run | 3.3, 3.6 | ok |
      | catalogue | the figure gate | a figure taken from a cited spec passes | 3.3 | ok |
      | catalogue | the figure gate | marketing phrasing does not change the check | 3.3 | ok |
      | catalogue | unverifiable prose is reported | a word the cited specs do not contain is reported | 3.3 | ok |
      | catalogue | unverifiable prose is reported | an entry whose words are all sourced is not reported | 3.3 | ok |
      | catalogue | unverifiable prose is reported | the report does not stand in for the figure gate | 3.3, 3.6 | ok |
      | catalogue | only dirty areas are regenerated | a run with nothing changed invokes no model | 3.2 | ok |
      | catalogue | only dirty areas are regenerated | one area is dirty | 3.2, 3.7 | ok |
      | catalogue | only dirty areas are regenerated | a classification change dirties two areas | 3.2 | ok |
      | catalogue | only dirty areas are regenerated | an instruction change dirties every area | 3.1, 3.2 | ok |
      | catalogue | only dirty areas are regenerated | a change outside an area's members does not dirty it | 3.2 | ok |
      | catalogue | living document | two runs produce the same file | 3.2, 3.8 | ok |
      | catalogue | living document | regeneration drops a capability that is gone | 3.2 | ok |
      | notes | one entry per version | re-running a version replaces its entry | 4.1, 4.6 | ok |
      | notes | one entry per version | a new version adds one entry | 4.1 | ok |
      | notes | one entry per version | the entry names its version | 4.1 | ok |
      | notes | only user-visible capabilities appear | an internal change produces no entry line | 4.2 | ok |
      | notes | only user-visible capabilities appear | a mixed version lists only the user-visible part | 4.2 | ok |
      | notes | nothing user-visible moved | a maintenance-only version reports and writes nothing | 4.2, 4.5 | ok |
      | notes | nothing user-visible moved | the notes document is not created empty | 4.2 | ok |
      | notes | nothing user-visible moved | the refusal does not invent filler | 4.2 | ok |
      | notes | first run establishes a baseline | the first ever run writes no entry | 4.4 | ok |
      | notes | first run establishes a baseline | the run after the baseline writes an entry | 4.4 | ok |
      | notes | vanished key is accounted for | no disappearance goes unreported | 4.3 | ok |
      | notes | vanished key is accounted for | an accounted disappearance is not an error | 4.3 | ok |
      | notes | renamed requirement is a change | a renamed requirement is a change | 4.3, 4.7 | ok |
      | notes | renamed requirement is a change | the renamed capability keeps its place | 4.3 | ok |
      | notes | requirement gone is a removal | a deleted requirement is a removal | 4.3 | ok |
      | notes | requirement gone is a removal | an internal removal stays out of the entry | 4.3 | ok |
      | notes | spec leaving the shipped set | a spec leaving the shipped set is reported, not published | 4.3 | ok |
      | notes | grouped by area, derived without a model | changed capabilities are grouped by area | 4.1 | ok |
      | notes | grouped by area, derived without a model | the changed set is reproducible without a model | 2.3, 4.2 | ok |
      | notes | version is supplied, never read | a run without a version fails | 2.1 | ok |
      | notes | version is supplied, never read | the run works where the version-state file is absent | 2.1 | ok |

      **Spec**: all 27 requirements. **Verify**: every row names a case and every case number in the table exists
      as a task above.

      What `ok` in the last column means, per capability, with the suite case names that produced it. The suite's
      own 87 case lines are the record; the evidence file's §5.1 quotes the run.

      | capability | scenarios | result |
      |---|---|---|
      | `spec-feature-index` — key identifies one requirement | keys derived, key survives, in-flight contributes none | `keys: one per requirement of every shipped spec`, `keys: derived as <spec id>#<requirement name>`, `keys: an in-flight change contributes no key`; real tree: 929 keys = `openspec`'s requirement sum |
      | `spec-feature-index` — delta compares requirement text | prose-only, text edit, addition | `delta: a prose-only edit is not a change`, `delta: a requirement text edit is a change`, `delta: an added requirement is an addition`, `delta: the spec's other keys are unchanged`; real tree: 0 changed / 929 unchanged for a `## Purpose` edit, 1 changed for a requirement edit, and revert-check 2.10 fails the first of these when the digest basis is mutated |
      | `spec-feature-index` — unclassified spec id stops the run | new id fails, classified passes, removed id reported, unknown surface fails, surface reported | `gate: an unclassified shipped spec id fails the run`, `gate: an unclassified spec id writes no document`, `gate: a stale classification entry does not fail the run`, `gate: a surface outside the vocabulary fails the run`, `gate: a user-visible spec with no renderable surface fails`, `gate: not exactly one car-only area fails the run`, `gate: an unknown area id fails the run`, `keys: the surface a spec is classified for is reported`; real tree: `--check-classification` 153/0/0 and revert-check 2.8 |
      | `spec-feature-index` — snapshot records the state | baseline is the most recent earlier, versions order as the release format, no earlier snapshot, snapshot self-sufficient | `snapshot: the baseline is the most recent earlier snapshot`, `snapshot: versions order as the release format orders them (-1 < -10 < -2)`, `snapshot: no earlier snapshot is reported`, `snapshot: every key carries a digest`, `snapshot: no requirement text is recorded, only a digest`; real tree: five baseline cases and revert-check 2.9 |
      | `spec-feature-index` — runs offline and model-free | runs with no model available, runs over the whole set | `catalogue: mechanical assembly works with no model available`, `keys: every shipped spec is classified`; real tree: 5.8 s against a 30 s bound, no model invoked (the tool has no model call) |
      | `spec-feature-index` — run reports what moved | no-op run, one-spec change | `report: a no-op run is visibly a no-op`, `report: a one-spec change is visibly one change`, `report: the whole fixture set is indexed` |
      | `feature-list-generation` — only user-visible capabilities | internal absent, internal-only area gets no heading, every user-visible area named | `catalogue: an internal capability is absent`, `catalogue: a nested internal spec is absent`, `catalogue: an area holding only internal capabilities gets no heading`, `catalogue: every user-visible area is named`; real catalogue: `build-tooling` has no heading, 12 of 13 areas do |
      | `feature-list-generation` — car is a tag | tagged once, car-only capability of an area stays under it, searching finds every claim | `catalogue: a dual-surface capability tags both surfaces`, `catalogue: a dual-surface capability appears once`, `catalogue: the car-only capability of a feature area stays under that area`, `catalogue: it is not moved into the car-only area`, `catalogue: a capability available in the car tags its area as car too`; real catalogue: car 130 / phone 311 / both 141 user-visible capabilities by `--list` |
      | `feature-list-generation` — the car platform owns one area | exactly one car-only area, not spread into a feature area, rendered under its own heading | `gate: not exactly one car-only area fails the run`, `catalogue: the car-only area is rendered under its own heading`, `catalogue: the car-only area states the car surface`; falsified by the 5.2 mutation |
      | `feature-list-generation` — every entry carries its capabilities | entry with no key fails, keys resolve | `gates: an entry with no key fails the run`, `gates: the keyless entry is named`, `catalogue: every entry carries a marker`, `gates: a key citing a spec that does not exist fails the run`, `gates: a key citing an internal spec fails the run` |
      | `feature-list-generation` — every entry leads with a keyword | keyword present, at most six tokens, in the cited specs | `gates: an entry with no bold keyword fails the run`, `gates: the missing keyword is named`, `gates: a keyword longer than six words fails the run`, `gates: a keyword word the cited specs lack fails the run`, `gates: a capitalised stopword in a keyword is accepted`, `gates: a well-formed entry passes the keyword gates`, `catalogue: every entry leads with a bold keyword` |
      | `feature-list-generation` — a selection, not an inventory | unlisted capability absent, unreached selling point fails, a spec serving two selling points, nothing is a selling point, a departed highlight is stale | `shortlist: a capability outside the shortlist fails the run`, `shortlist: the unlisted spec is named`, `gates: an unreached selling point fails the run`, `catalogue: a capability outside the shortlist is absent`, `catalogue: a shortlisted-away capability is not published`; real tree: 12 bundles carrying 19 selling points |
      | `feature-list-generation` — an entry is one selling point | a spanning entry fails, one entry passes, a selling point may take two entries | `shortlist: an entry spanning two selling points fails the run`, `shortlist: the spanning entry is named` |
      | `feature-list-generation` — a paragraph stays short | an over-long paragraph fails, a short one passes, the keyword does not count | `length: an over-long paragraph fails the run`, `length: the paragraph and its length are named`, `length: a short paragraph passes`; real tree: 19 entries at about 286 characters |
      
      | `feature-list-generation` — a stated figure is in a cited spec | invented number fails, sourced figure passes, phrasing does not change the check | `gates: an invented figure fails the run`, `gates: the invented figure is named`, `gates: a figure taken from a cited spec passes`, `gates: a sourced figure is accepted`; revert-check 3.6 |
      | `feature-list-generation` — unverifiable prose is reported | word reported and green, fully sourced not reported, report does not replace the figure gate | `gates: an unsourced word is reported and the run stays green`, `gates: the unsourced word is named`, `gates: a fully sourced entry is not reported`, `gates: a well-sourced section passes`, `gates: the report does not stand in for the figure gate` |
      | `feature-list-generation` — only dirty areas regenerated | nothing changed invokes no model, one area dirty, classification change dirties two, instruction change dirties all, outside-area change does not | `regeneration: nothing changed means no dirty area`, `regeneration: an instruction change dirties every area`, `delta: a requirement text edit dirties its area`, `delta: a prose-only edit does not dirty an area`; real tree: `dirty areas: 0` on a no-op and 13 on a prompt change; revert-check 3.7 |
      | `feature-list-generation` — living document | two runs same file, regeneration drops a gone capability | `regeneration: two runs produce the same file`, `regeneration: a merged capability survives`, `catalogue: no generation timestamp`; real tree: byte-identical across runs; revert-check 3.8 |
      | `release-notes-generation` — one entry per version | re-running replaces, new version adds one, the entry names its version | `notes: one entry per version carrying a change`, `notes: the entry names its version`, `notes: re-running a version leaves one entry`, `notes: re-running a version replaces its entry byte for byte`, `notes: the changed capability appears`; real tree: two entries newest-first with the older untouched; revert-check 4.6 |
      | `release-notes-generation` — only user-visible capabilities appear | internal change no line, mixed version lists only the visible part | `notes: an internal-only change writes no entry`, `notes: an internal-only change still reports the delta` |
      | `release-notes-generation` — nothing visible moved means no entry | maintenance-only reports and writes nothing, document not created empty, refusal invents no filler | `notes: the empty-release verdict is reported`, `notes: an internal-only change writes no entry`, `notes: the first run writes no entry`; real tree: the file stayed absent; revert-check 4.5 |
      | `release-notes-generation` — first run establishes a baseline | first ever run writes no entry, the run after writes one | `notes: the first run writes no entry`, `notes: the first run says the baseline was established`, `notes: one entry per version carrying a change` |
      | `release-notes-generation` — every vanished key accounted for | no disappearance unreported, an accounted one is not an error | `notes: a spec leaving the shipped set is reported in the run report`, `notes: a departed spec is not published as a change`, `notes: a deleted requirement is published as a removal`, `notes: a rename is not published as a removal` |
      | `release-notes-generation` — a renamed requirement is a change | renamed is a change, keeps its place | `notes: a renamed requirement is a change, not a removal`, `notes: the rename is reported in the run report`; real tree: `1 renames`, the note reporting 1 changed and 0 removed; revert-check 4.7 |
      | `release-notes-generation` — a requirement gone is a removal | deleted is a removal, internal removal stays out | `notes: a deleted requirement is published as a removal`, `notes: an internal-only change writes no entry` |
      | `release-notes-generation` — a spec leaving the shipped set | reported, not published | `notes: a spec leaving the shipped set is reported in the run report`, `notes: a departed spec is not published as a change`; this is the case that found the stale-classification coverage defect (§5.1) |
      | `release-notes-generation` — grouped by area, derived without a model | changed capabilities grouped by area, changed set reproducible without a model | `notes: the entry is grouped by feature area`, `notes: the changed set is reproducible with no model`, `notes: the changed capability appears` |
      | `release-notes-generation` — the version is supplied | run without a version fails, works where the version-state file is absent | `version: a run without a version fails with a usage error`, `version: a run without a version writes nothing`, `version: the version-state file is never read`, `version: the run works where the version-state file is absent` |

      Every row above names cases that pass in the suite, except where the row names real-tree evidence instead
      (a 153-spec set, a release version and the build's version-state file are things a fixture cannot honestly
      stand in for). Two rows name a revert-check rather than a suite case: the figure gate and byte-identity, whose
      falsification the real tree can exercise and the fixture cannot.
- [x] 5.1 Create `tools/gen-feature-list-selftest.sh` with its own fixture tree — a scratch root with a handful of
      synthetic specs, a classification, prompt stubs and a stub phrasing command — and implement every case the
      table in §5.0 assigns to a fixture, including the broken-fixture cases that must exit non-zero. It reads no
      project document and needs no build, device, network or model, as
      `tools/check-doc-routes-selftest.sh` does. **Spec**: all three capabilities. **Verify**: `bash
      tools/gen-feature-list-selftest.sh` exits 0 and prints one line per case; running it from a directory outside
      the repository still exits 0.
- [x] 5.2 Falsify the suite: mutate the tool once — e.g. report the surface by spec-id prefix instead of by
      classification — and confirm the named fixture case fails; restore; re-run green. One mutation only. **Spec**:
      all three capabilities. **Verify**: the failing case's name and both exit codes in the change report; a
      mutation that no case catches is a gap in §5.0 and §5.1, which must then be closed.
- [x] 5.3 Prove the fixture suite is hermetic: run it with no network and with Gradle unavailable (`PATH` without
      the wrapper's JDK, or a scratch root outside the repository) and confirm it passes. **Spec**:
      `spec-feature-index` "The index runs offline, without a model and without a build". **Verify**: the run's exit
      code and the environment it ran in, quoted in the change report.

## 6. Documentation and wiring

- [x] 6.1 Write `guidelines/FeatureList.md`, the owning document for this concern (`design.md` D1): that a shipped
      spec id must be classified by the change that archives it and that the run refuses to publish an unclassified
      one; that `FEATURES.md` is regenerated and never hand-edited while `RELEASE-NOTES.md` is derived per version
      and never appended; that the phrasing instructions are files whose digest is the cache version; and that the
      prose gates, not a reviewer's memory, are what keeps the two documents content-wise correct. State each rule
      next to the measurement or case that enforces it. **Spec**: `documentation-ownership` "A concern has one owning
      document" (already states the requirement; this task satisfies it) — and the change's own capabilities, whose
      rules the document must not contradict. **Verify**: every rule in the document names the task or case that
      enforces it, and no rule contradicts a requirement of the three capabilities.
- [x] 6.2 Route the new document: add one row to `AGENTS.md`'s Documentation Map for the kind of change that
      regenerates the feature documents, and add the document to the module/tech-stack facts where it belongs.
      **Spec**: `documentation-ownership` "A change is routed to the sections that own its conventions" /
      "Sections outside the map stay reachable". **Verify**: `bash tools/check-doc-routes.sh` exits 0 and stops
      reporting `guidelines/FeatureList.md` as unrouted; the row names the document, its section number and its
      heading text.
- [x] 6.3 Measure the routed read and record it in the row: the cost of reading `guidelines/FeatureList.md`
      (`wc -c` ÷ 3.6) against the cost of the documents the route replaces, as `documentation-ownership`'s "The
      routing cost is measured" requires, with the date. **Spec**: `documentation-ownership` "The routing cost is
      measured". **Verify**: the number appears in the `AGENTS.md` row and matches a fresh `wc -c` of the document.
- [x] 6.4 Link the two generated documents from `README.md` with the command that regenerates them.
      **Spec**: `feature-list-generation` "The catalogue is a living document" and `release-notes-generation` "One
      entry per version" — a reader must be able to find both documents and reproduce them. **Verify**: the command
      as written in `README.md` runs and regenerates both documents.

## 7. First real run and close-out

- [x] 7.1 Run the tool on the real tree with no phrasing command configured, for the current version — this is the
      baseline run — and record the report: specs read, keys, keys added/changed/removed, dirty areas, unclassified
      and stale ids, and the wall time. **Spec**: `spec-feature-index` "The index runs offline, without a model and
      without a build"; `release-notes-generation` "The first run establishes a baseline instead of an entry" /
      "The first ever run writes no entry". **Verify**: `RELEASE-NOTES.md` holds no entry for that version after the
      run, the run reports the baseline as established and not as "nothing changed", and no phrasing command was
      invoked.
- [x] 7.2 Run it with the phrasing command configured to produce `FEATURES.md` over the 153 specs, and record the
      invocation count, the dirty-area count and the wall time. **Spec**: `feature-list-generation` "Only dirty
      areas are regenerated" / "One area is dirty"; "The catalogue names every user-visible area".
      **Verify**: the invocation count equals the dirty-area count; both documents exist; the report's numbers are
      pasted into the change report.
- [x] 7.3 Re-run with no spec, classification or prompt change and assert the no-op: zero invocations and a
      byte-identical `FEATURES.md`. **Spec**: `spec-feature-index` "A run reports what it read and what moved" /
      "A no-op run is visibly a no-op"; `feature-list-generation` "Only dirty areas are regenerated" / "A run with
      nothing changed invokes no model". **Verify**: the two `sha256sum` values are identical and the invocation log
      is empty; both quoted in the change report.
- [x] 7.4 Prove mechanically that no Gradle gate is owed: `git status --porcelain` plus `git diff --name-only` list
      only `*.md`, `*.json`, `tools/gen-feature-list*.sh`, `tools/feature-list/**` — no source, test, build script,
      manifest, resource, Gradle or native file. The change's own directory does not appear: `.gitignore` has
      `openspec/changes/*/`, so open changes are local. Two modifications in this working tree belong to another
      session and are **not** this change's: `.gitignore` (`.pi/skills` being versioned) and
      `auto/src/test/java/com/naviveylin/auto/AutoMapRendererRenderCadenceTest.kt`; name them as such rather than
      attributing them here. **Spec**: all 27 requirements (the change alters no application behaviour). **Verify**:
      the file list is pasted into the change report with those two attributed; if any other kind of path appears, a
      compile/test gate becomes owed and §7.6 is not optional.
- [x] 7.5 Run the project checks this change can affect and quote their output: `bash
      tools/check-doc-routes-selftest.sh`, `bash tools/check-doc-routes.sh` (the change edits `AGENTS.md`),
      `bash tools/gen-feature-list-selftest.sh`, `openspec validate generate-feature-list --strict`, and
      `openspec doctor`. **Spec**: all 27 requirements. **Verify**: every command exits 0 and its output is quoted
      in the change report.
- [x] 7.6 Run the project's test gate once before declaring the change complete (`./gradlew test` via the
      `run-tests` skill, both flavors). §7.4 predicts it is unaffected; this task exists to falsify that prediction
      rather than assert it, as `openspec/config.yaml`'s apply guidance asks. **Result 2026-10-08**: two earlier
      attempts were **void, not failed** — both died with
      `java.nio.file.NoSuchFileException: …/build/test-results/<task>/binary/in-progress-results-generic.bin` on
      `:auto:testDebugUnitTest` and `:app:testAutomotiveDebugUnitTest` while another session held a **BUSY** Gradle
      daemon on this working tree (one builder per working tree, `AGENTS.md`; logged). With that daemon `STOPPED`:
      `BUILD SUCCESSFUL in 3m 7s`, 188 actionable tasks: 20 executed, 1 from cache, 167 up-to-date — and the JUnit
      XML reads **4 729 tests, 0 skipped, 0 failures, 0 errors** across 568 report files (`:app` mobile 1 746,
      `:app` automotive 1 746, `:auto` 789, `:core` 448). The prediction held. **Spec**: all 27 requirements.
      **Verify**: quoted above; the gate is green.
- [x] 7.7 File the deferred work in `TODO.md` as entries with id, category, class and status: wiring the generator
      into CI or a schedule and choosing the harness that fills the phrasing seam (`design.md` D3, Open Questions);
      trimming the note to a store listing's character limit; and enforcing the classification of a newly archived
      spec in the change that archives it. **Spec**: not derived from a scenario; recorded because `AGENTS.md`
      requires a detected but unowned problem to be filed. **Verify**: three entries exist, each with the metadata
      line and a pointer to where the decision was deferred.
- [x] 7.8 State what this change does not owe, so no reader infers it: no on-device, emulator or logcat evidence and
      no `pixel-check` measurement (no runtime, UI, rendering, car-surface, template or lifecycle behaviour changes),
      and no new JVM unit test (the tool is a shell script with its own device-free fixture suite, §5.1).
      **Spec**: all 27 requirements. **Verify**: both sentences appear in the change report.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, then verify the archived result: the
  three capability specs appear under `openspec/specs/` with their purposes carried over, and no requirement was
  dropped by the archive.
- Keep the classification files in step with new specs: the change that archives a spec is the change that
  classifies it (6.1).
