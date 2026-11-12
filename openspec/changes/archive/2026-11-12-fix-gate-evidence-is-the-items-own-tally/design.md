# Design — the run's gate column is the tally the item's own gate wrote

## Root cause

Two predicates over one tree. The gate writes its tallies into the single path the profile names
(`LOOP_GATE_TALLY_DIR`, `tools/loop/loop.conf:721` → `app/build/test-results/loop-gate`) and, since issue #848, one
gate's tallies are that run's alone: the gate claims the directory's lock and sweeps every `*.xml` still in it before
its first step. The worker's own start clears the wider directory `*/build/test-results` (`run.sh:1435`), which holds
that one. So the directory is empty when an item starts and holds exactly what the item's gate wrote when its children
have ended.

The run does not read that directory. At this change's base (`4a14574f`) `run.sh:2842` read every XML under any path holding `test-results` in the
whole worktree, capped at 20, so the value is the tree's constant — 24 tracked matches in this repository, of which
`head -20` reads 20 — and the one arm that asks whether a gate result exists (`run.sh:2925`) is answered by files that
stood before the item.

## The fix (one mechanism)

Read the directory the gate writes, and nothing else:

```sh
gate_tally=$(loop_conf_value LOOP_GATE_TALLY_DIR)          # the profile's path, relative to the tree
gate_xmls=$(find "$gate_tally_dir" -maxdepth 1 -name '*.xml' | head -20)
```

with `<empty>` for a profile that names no directory (a project with no gate, D16) and an absolute value taken as it
stands, because `loop.conf` may name either. The tally is one level deep (`$tally_root/$name.xml`, `gate.sh:565`), so
`-maxdepth 1` is the whole of it.

Three consequences, all wanted:

1. `gate_found` is the number of tallies **this item's gate** wrote, and 0 when it wrote none — so the no-evidence
   arm can fire, which is the defect.
2. The progress line and the arm's reason name the directory the run read, so a log says what was looked at rather
   than "the tree".
3. `evidence_paths` (`run.sh:3282`) names a tally the item's gate wrote, never a file that stood before the item —
   the `NOTE` half of the tracker entry.

And one thing the count must not decide, from the owner's decision of 2026-11-11T19:18:12Z: what an item records when
it claimed nothing.

```sh
if   [ -z "$LOOP_GATE_CMD" ]; then gate_field="skipped (no gate configured)"
elif [ -n "$gate_claim"   ]; then gate_field="$gate_claim"
elif [ "$gate_found" -gt 0 ]; then gate_field="$gate_found xml"
else                              gate_field=""
fi
```

`0 xml` was a claim about the tree, not about a gate: `tools/loop/report.sh:688` reads an empty `metrics.gate` as the
result nobody recorded and lists the item on its `re-run the gate for #N` line (`report.sh:687`), so the constant hid
exactly the items whose gate no child ran. The refusal arm is that field's other side and now reads the claim, not
the count: `[ -n "$LOOP_GATE_CMD" ] && [ -n "$gate_claim" ] && [ "$gate_found" -eq 0 ]` blocks a report that **claims**
a gate result over an empty directory — and leaves an item that claimed none to record the absence.

## Why not the alternatives

- **The XMLs that appeared in the tree after the item started.** The run would need a start instant and a comparison,
  and in a fresh worktree every tracked file carries the checkout's own mtime: the 24 tracked matches of this
  repository arrive at the item's start and would be read as new. Measured 2026-11-11 in this change's worktree —
  `git ls-files | grep -c 'test-results.*\.xml$'` = 24 against `find … | wc -l` = 24 — so the alternative answers
  exactly what the broad `find` answers, which is the defect. It is also the reading issue #830's change rejected for
  its own side of this rule ("excluding them by mtime window took three attempts").
- **Keep the `find` and subtract what git tracks.** It still counts anything untracked any child wrote under a
  `test-results` path — a hand-copied evidence file, a fixture written by a case — so it does not answer "did this
  item's gate run", and it adds a `git ls-files` call whose answer changes with the item's own commit. It also leaves
  two predicates over the tree, which is the defect itself.
- **A marker file or an attribute filter inside the tally** (`run="<pid>"`, #848): the run does not know the gate
  child's pid when it reads, and a filter over a directory-wide scan still scans the directory-wide set.
- **Read both the tally directory and the broad `find`, preferring the former when it is non-empty**: an item whose
  own gate wrote nothing would fall back to the tree's constant, which is the arm that must fire.

## Blast radius

- `tools/loop/run.sh` — one reading, its progress line, one refusal arm and its message, the item's gate field, and
  the comment above `clear_stale_test_results` that quotes the old message.
- `tools/loop/lib.sh` — one comment, the shape list above `gate_tally_state`, whose count is now the tallies the run
  found rather than the test-results XMLs of the tree.
- `tools/loop/report.sh` — untouched: its selector already reads the empty field this change makes reachable, and
  `run#theGateColumnNamesThisItemsGateResult` is what asserts the two agree.
- `tools/loop/tests/loop-cases.sh` — one case added; the shared `stub_write` action and seven hand-written actions
  write their gate XML where the gate writes it; two assertions on the refusal's reason follow the new message. Every
  one of those cases was writing the XML where the *old* reading looked, which is why the realignment is part of the
  fix and not fixture noise.
- `tools/loop/tests/loop-mutations.sh` — two records (`849.1` the reading, `849.2` the empty field); `9.5`'s anchor on
  the no-evidence arm follows the arm's new condition.
- `guidelines/Backlog.recipes.md:755` "The gate and its suite" — one paragraph.
- No compiled file, no Gradle task, no profile key.

## Rollback

Revert the five files. Nothing is left behind: the reading is a `find` over a build directory the gate already owns,
and no data moves — a tree that keeps running the old reading sees the tallies the new one read, in the directory the
gate has always written.

## Open decisions

none

The item names no fork the recorded decision leaves open, and the evidence eliminated every alternative it considered
(above). The decision recorded on the item at 2026-11-11T19:18:12Z settles the scope: the owner rejected the branch as
it stood because its case called the ungated item `blocked`, and rejected a snapshot diff because #848 already made
the tally directory one gate's own. It answers the one fork this change met — whether an item that ran no gate is a
block or a record — and this design implements that answer rather than re-deriving it.

## Failure modes the fix keeps visible

- **A project whose gate writes no tally** (`LOOP_GATE_CMD` set, no `LOOP_GATE_TALLY_DIR`) reads 0, and a report that
  **claims** a gate result is then blocked — the refusal it was meant to be — while one that claims none records an
  empty field. `run#noGateStillRuns` covers the empty `LOOP_GATE_CMD` case, which takes the `skipped (no gate
  configured)` branch before this reading is asked.
- **A gate child that wrote its tally into a tree the run does not read** (a gate run by hand in the project's own
  tree rather than the worktree) still reads 0: the run's gate is the one its own child runs, and the profile beside
  the worktree's engine names the directory it writes.
- **A stale tally left by a killed gate** is cleared by the next item's worker start, so it cannot be read as a later
  item's — and if a worker were ever started without that sweep, the tally still carries the `run=` marker and a
  failed step's own `LOOP-GATE-STEP:` line, so the record says which run wrote it.
