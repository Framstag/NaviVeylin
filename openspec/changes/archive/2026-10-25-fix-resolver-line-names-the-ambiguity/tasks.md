# fix-resolver-line-names-the-ambiguity — tasks

- [x] 1.1 A case that pins the defect: two tracked paths end with the cited name, the resolver is run without
      `--json`, and the item line must read `AMBIGUOUS` beside `repaired=0`
      (`resolver#ambiguityLineNamesTheAmbiguity`). Red on HEAD, where the line reads `repaired`.
- [x] 1.2 The label chain names the state it has: `reopened` → `stillParked` → `unresolved` → `repaired` →
      `AMBIGUOUS` → `skipped`.
- [x] 2.1 Mutation 5.16: `"AMBIGUOUS"` → `"repaired"`, red then green
      (`bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.16`).
- [x] 2.2 `bash tools/resolve-stale-selftest.sh` — 34/34 (33 before, plus the new case).
- [x] 3.1 No spec delta is owed; recorded under "## Spec" in the proposal.
