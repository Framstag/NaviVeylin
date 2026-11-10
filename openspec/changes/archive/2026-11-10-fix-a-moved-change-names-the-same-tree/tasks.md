# Tasks

Reference: `proposal.md` for the measurements, `design.md` for the fix and the arms it eliminated,
`specs/agent-issue-loop/spec.md` for the clause this change modifies.

## 1. The case, red first

Requirement: "A child pays for the full gate at most the recorded number of times" — its digest paragraph and the
scenario "A change's own files name one tree, archived or not".

- [x] 1.1 `gate#aMovedChangeNamesTheSameTree` added to `tools/loop/tests/loop-cases.sh:13980`, registered at the group
      list and at the dispatch, with a first line that declares what it can be reached through:
      `# files: tools/loop/lib.sh tools/loop/loop.conf tools/pi-child-events.sh tools/tooling.json` — the last two
      because `new_project` copies them, which `driver#everyCaseDeclaresTheFilesItCanReach` refused the declaration
      without. The case carries this repository's two ignore rules into its throwaway project (`.gitignore:76-77`), so
      the change it works is one git does not see, and it asserts in order: the change's own files are in the digest at
      all; the archive's move gives the same digest; the archive's commit gives the same digest; a file written into
      the change's `evidence/` after the gate gives the same digest; a non-evidence file rewritten after the gate gives
      a different one.
- [x] 1.2 Red on the base (`4c400715`), the case as written: `evidence/red-on-HEAD-aMovedChangeNamesTheSameTree.log`
      (2026-11-10T08:24:52Z), `1 of 1 cases failed`, on *a change being worked is in the tree the gate names, evidence
      or not: e3b0c44298fc1c14* — the sha256 of the empty string, which is the item's own probe figure.

## 2. The change

- [x] 2.1 `tools/loop/lib.sh`: `loop_change_path` (`:296`) — the path under the change's own name, the profile's
      `LOOP_CHANGE_ARCHIVE_PREFIX` removed from an archived directory's name and only what it matches; and the family
      enumeration inside `loop_tree_identity` (`:335`) — `git ls-files --others --ignored --exclude-standard` over
      `LOOP_CHANGE_SOURCE_DIR` less its `archive/` less the review's exclude pathspec, its listing normalised by the
      helper and de-duplicated after it. `loop_change_identity` (`:356`) and the `head=… diff=…` line are unchanged, so
      mutations `262.3`/`262.4` still apply and neither call site holds a path set of its own.
- [x] 2.2 `tools/loop/loop.conf:486`: `LOOP_CHANGE_ARCHIVE_PREFIX="[0-9]{4}-[0-9]{2}-[0-9]{2}-"`, beside
      `LOOP_CHANGE_SOURCE_DIR`, with the derivation in the block: a project's archive shape, never a path in the
      engine.
- [x] 2.3 Green, focused: `gate#aMovedChangeNamesTheSameTree` PASS
      (`evidence/green-aMovedChangeNamesTheSameTree.log`); `gate#theGateAndTheRoundNameOneTree` and
      `gate#aThirdFullRunInOneSessionIsRefused` inside `--group gate`, 31 of 31 PASS in 24.73 s
      (`evidence/group-gate.log`); `run#aReviewDiffNamesTheTreeItWasWrittenFrom` PASS
      (`evidence/green-aReviewDiffNamesTheTreeItWasWrittenFrom.log`); `driver#everyCaseDeclaresTheFilesItCanReach`
      PASS.
- [x] 2.4 No other reader moves: 1 occurrence of `loop_change_identity` in `lib.sh` and 2 in each of `gate.sh` and
      `run.sh` — the definition and the two call sites, neither naming a path
      (`evidence/surface-counts.log`); the digest's shape and length are unchanged. Cost measured on this working tree
      with its 18 open changes: 290 ms and 83 hashes added (`evidence/probe-cost.log`).

## 3. The falsification

- [x] 3.1 `262.5` (`tools/loop/tests/loop-mutations.sh:981`) drops the family enumeration → `RED
      gate#aMovedChangeNamesTheSameTree` on 1.2's first assertion (*a change being worked is in the tree the gate
      names, evidence or not: e3b0c44298fc1c14*); restore green.
- [x] 3.2 `262.6` (`:982`) takes the other half — an archived path keeps its date, so the move is a correction —
      → `RED` on the move's own assertion (*expected '077cde14780a260c', got '6ead8ac2b170551a'*); restore green.
- [x] 3.3 `--check` over the whole set: 319 records apply to the files they name (`evidence/mutation-check.log`).

## 4. The prose the change makes false

- [x] 4.1 `guidelines/Backlog.recipes.md:722` — the paragraph that states the derivation: the change family's own
      files and the archive's normalised name in the path set, the same evidence exclusion on both halves, the #837
      measurement (the empty string's digest, the archive's `5439d8da57fe3501`, the three surveyed pairs), the new case
      and the two mutation records in the falsification clause, and the #819 measurement kept whole.
- [x] 4.2 `tools/check-doc-routes.sh` at 0 (`evidence/doc-routes.log`).

## 5. The structural checks this delta owes

- [x] 5.1 `tools/check-portability.sh` 0 — no project path, and no project value, in the engine
      (`evidence/portability.log`).
- [x] 5.2 `tools/check-openspec-hygiene.sh` 0 (`evidence/openspec-hygiene.log`), and
      `openspec validate fix-a-moved-change-names-the-same-tree --strict` valid.
- [x] 5.3 `tools/loop/evidence-check.sh` PASS on the change (`evidence/evidence-check.log`).

## 6. The gate, which this pass does not run

- [x] 6.1 `./tools/loop/gate.sh --base <the run's base>` on the change, once, by the gate child — the diff it gates is
      the one this session hands over.
- [x] 6.2 The whole loop suite standalone, the independent read-only review, and only then the archive.
- [x] 6.3 The gate's one red step attributed from the tree, never re-run: `bash tools/gen-feature-list.sh
      --check-classification` fails on four delta ids two other open changes introduce
      (`evidence/gen-feature-list-attribution.log`), waived as `LOOP-GATE-WAIVED` — this change's own delta id is
      already classified and moves the unclassified set by nothing.
