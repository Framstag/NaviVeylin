# Design — the scan is handed the files the suite reads

## The root cause

On the base commit `37b3b83f`, `tools/loop/tests/loop-selftest.sh:519-566` runs one awk over one path. The program
derives three sets from the lines
it is handed — bodies (`case_<group>_<Name>() {`), arms (`'<group>#<Name>') fn=case_<group>_<Name> ;;`) and the
declared rows (`loop_shell_cases`) — and every branch it takes is a whole-scan decision:

- `nb == 0` → the file dispatches by convention, nothing is compared (`:544`);
- `na == 0` → the file has bodies but no arm table, so rows are held only to bodies (`:551-556`);
- otherwise the arm set is compared with the rows and the bodies (`:557-565`).

One path means each of those is a claim about one file. When the arm table lives elsewhere — which is the direction
issue #1102's change takes, the declared set and the arm table moving to `tools/loop/tests/loop-case-index.sh` — the
scan sees bodies and no arms and takes the second branch: the arm half of the guard compares nothing and says nothing.

## The fix

The scan is handed every file the suite reads its case names from:

```
# the cases file always, and the arm table's own file once the tree carries it (issue #1102's split writes it)
case_scan_files=("$root/tools/loop/tests/loop-cases.sh")
if [ -f "$root/tools/loop/tests/loop-case-index.sh" ]; then
  case_scan_files+=("$root/tools/loop/tests/loop-case-index.sh")
fi
registration=$(awk … "${case_scan_files[@]}")
```

Nothing else changes: `awk`'s per-line program already accumulates over every file its command line names, so the
three comparisons become unions for free, and every message keeps its text. The exemption branches stay whole-scan and
stay correct: "defines no body at all" is now "no scanned file defines a body", and the bodies-only branch is taken
only when no scanned file carries an arm — which is exactly the shape that reads green today on a tree with one file.

The second path is conditional because an awk handed a path the tree does not have exits 2 with a diagnostic, which
would refuse every run of the suite on today's tree. On the base commit the file is absent
(`tools/loop/tests/loop-case-index.sh` — 0 bytes there), so the scan's input and its behaviour are byte-identical to
the base until issue #1102's split lands.

## Blast radius

- `tools/loop/tests/loop-selftest.sh` — the input list and its conditional block added above the awk (`:523-528`), the
  comment block at `:513-524` stating "the files that were just sourced" and the branch it explains, and the
  invocation's argument list at `:575` changed from one path to the array.
- `tools/loop/tests/loop-cases.sh` — one row in `LOOP_SHELL_CASES`, one arm in `loop_run_shell_case`, one case body
  with its `# files:` declaration.
- No profile key, no `tools/loop/layout.tsv` row: the table carries one row per engine script and neither file is one
  (`./tools/look.sh 'loop-selftest|loop-cases' --path tools/loop/layout.tsv` → 0 matches).
- No guideline edit is owed, and the reason is a pointer rather than a judgement: the gate-and-suite section of
  `guidelines/Backlog.recipes.md` carries one sentence about the case file, and it is the *hunk* reader's
  (`:1197-1201`, "The case file is the one path whose own hunks are read instead of its name (issue #917)"); that
  section states nothing about what the registration guard is handed. The
  rule this change alters is the capability's own — `openspec/specs/agent-issue-loop/spec.md:5829`, "The declared set
  agrees with the bodies and the dispatch arms of the cases file" — and it carries the delta.

## Rollback

Revert the commit. Nothing is migrated, stored or written outside the two files; the guard's input returns to one path
and the added case returns to red, which is the base's own state.

## Alternatives the evidence eliminated

1. **`LOOP_CASE_FILES` in the profile** (the mechanism `openspec/changes/fix-the-case-readers-take-a-file-set` records,
   its task 3 being this guard's union). Eliminated as this item's change, not as a mechanism: that key makes a second
   cases file *sourced*, so it moves the suite's behaviour and all three readers together, and its own task list keeps
   the `# files:` reader and the hunk→case map (`tools/loop/tests/loop-selftest.sh:606`, `:643`) as its work. The item
   bounds itself to the guard (`work-package.md`: "the other two readers … are #1101's remaining two thirds"), and the
   key is absent from this tree (`tools/look.sh 'LOOP_CASE_FILES' --path tools/loop` → 0 matches), so taking it would
   be a second, ungated change. When that change lands, its sourcing block hands the guard a list and this scan reads
   the list it is given — the fix is the scan's input, not the input's origin.
2. **Naming the index path unconditionally.** Eliminated by measurement of the failure mode: `awk` over a path that
   does not exist exits 2 ("cannot open file"), and the base commit has no index file, so every suite run and every
   gate on today's tree would have refused.
3. **Reading bodies from the cases file and arms from the index.** Eliminated by what the split moves: the declared set
   moves with the arm table, so a comparison that reads rows from one file and arms from another would compare a file
   with itself. The union is the only shape that survives a split at either of the two boundaries.
4. **A per-file scan (one awk per file, then a merge).** Eliminated as strictly more code with no new evidence: the
   program's accumulators are already whole-scan, and every refusal names the case, not the file — a per-file scan would
   have to re-state that naming to stay honest.
