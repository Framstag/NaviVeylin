# Design: fix-the-usage-case-owns-its-bounds

## The root cause, in one sentence

`run#childUsageIsRecordedPerItem` inherits the profile's checkpoint while its fixture's stream stands above it, so
whether the run splits its first worker into a continuation — a third child in the stub log — is decided by how long
the host holds the stub child across the wait's one-per-second reading, not by the tree the case judges.

Where that is: the fixture's compaction (`tools/loop/tests/loop-cases.sh:12983`, `tokensBefore: 150000`) against
`LOOP_ITEM_CONTEXT_WARN=90000` (`tools/loop/loop.conf:269`); the split's condition and its one-per-second reading
(`tools/loop/child.sh:427` and `:552`, `ended_by=checkpoint` at `:556`); the continuation it starts
(`tools/loop/run.sh:1282`).

## The fix

Two hunks in the case's own body, and one requirement:

1. The case writes a profile copy with the two context keys recorded by it — `LOOP_ITEM_CONTEXT_WARN=0` (the
   documented off) and `LOOP_ITEM_CONTEXT_MAX=200000` (the recorded stop, above the fixture's 150 000) — and exports
   `LOOP_CONF`. `LOOP_CONF` is the self-test's own seam (`tools/loop/run.sh:90`, `tools/loop/lib.sh:159`), and the
   context-bound cases already record the bounds they enforce this way (`tools/loop/tests/loop-cases.sh:23133`).
2. The count assertion becomes the list of prompts the stub recorded. A list of two names is a count of two with the
   names kept, so nothing is weakened; an extra child is named (`worker-prompt-continuation.md`) instead of reported
   as `got '3'` — the item's own ask (issue #1023), and the reason the observed red could not say which child ran.
3. `tools/loop/tests/loop-cases.sh`'s `# files:` declaration gains `tools/loop/loop.conf`, because the case now reads
   the profile it copies.

## Blast radius

- The case only. No engine, no profile value, no fixture, no other case.
- The mutation set still holds: `76.1` (`tools/loop/tests/loop-mutations.sh:806`) makes the tail child unreachable and
  the case must go red; it now goes red on the prompt list, which names the one child that ran.
- Two open changes mention the case by name in their own evidence; neither reads its assertion text.

## Rollback

Revert the change directory and the two hunks in `tools/loop/tests/loop-cases.sh`: the case returns to the inherited
checkpoint and the count assertion. Nothing else in the tree depends on either.

## Alternatives the evidence eliminated

- **Raise the fixture above the checkpoint** (e.g. 150 000 → 60 000). It makes today's tree green and hides the
  coupling, which is the defect: the next move of `LOOP_ITEM_CONTEXT_WARN` (it moved 143 124 → 90 000 in #1003)
  re-creates the flake, and nothing in the case would say so.
- **Raise the checkpoint above 150 000 in the profile.** The number is the measurement of #1003 (1 055 Mtok as-is
  against 866 at 90 000); a case may not price the profile it runs under.
- **Retry the case, or count only the prompts the case expects.** A retry hides a red rather than answering it, and a
  filter would drop the third child the case exists to see.
- **Fix the engine** — take the split only for a child that is still working after the crossing. The split *is* taken
  for a child that is still working; the engine's behaviour is the requirement
  (`openspec/specs/agent-issue-loop/spec.md`, "The bound on a child is priced in what it spends"), and the case is the
  one that staged a worker past the checkpoint by accident.

## Evidence the red was taken on

`evidence/red-on-HEAD.log` — the stub held past one reading (`sleep 3` after the fixture's events in
`tools/loop/tests/fixtures/pi`, restored byte for byte, `sha256 c06d45a1…`), the case failing with
`expected '2', got '3'`; `evidence/green-with-the-hold.log` — the same hold on the changed tree, `PASS`.
