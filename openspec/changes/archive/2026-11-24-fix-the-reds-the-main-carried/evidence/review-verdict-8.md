# Review verdict — round 8 (archive gate)

Filed by the parent from the reviewer's returned text. The reviewer was a read-only subagent: no writes (its
sandbox denies them), no gate, no suite, no network. It ran `openspec validate … --strict`,
`tools/check-change-readiness.sh`, `yq -e` on both workflows, `check-sonar-workflow.sh`, `check-action-rules.sh`,
`git rev-list/numstat/show/diff`, `sha256sum`, `stat` and `grep`.

**The verdict text was truncated in transit.** The parent received the verdict header, the "Resolved from earlier
rounds" section, B13 in full, and B14 up to its quoted sentence. B14's remaining measurement, the NOTEs and the
per-scenario table did not arrive and are not reproduced here — the parent did not invent them. Both blockers
were corrected and round 9 (`review-verdict-9.md`) confirms them RESOLVED and returns PASS.

## VERDICT: FAIL — LOOP-BLOCKING: 2

The eight causes are correct, the archive gate passes on the current tree, and round 7's B12 is genuinely
corrected. Two items block archive, both in `evidence/gate-change.txt` and both created by the *fix* to B12 (it
edited `.openspec.yaml` at 11:39:09Z, ten seconds after round 7's verdict was filed, and nothing re-recorded that
file). This is B10's block, re-broken.

### Resolved from earlier rounds

- **B12 → RESOLVED.** Re-measured on the current tree: `git rev-list --count d885082d..HEAD` = **12**;
  `git diff --numstat d885082d..HEAD` = **11 files, 276 insertions, 14 deletions**. `proposal.md:8` "eleven
  files", `:251` "twelve commits", `:283` the revert line, and `:295` "11 files, 276 insertions, 14 deletions … 3
  test files" all match, and the per-file split matches the eleven changed paths (2 workflows,
  `tools/gradlew-retry.sh`, `tools/doc-routes.json`, `tools/loop/tests/loop-cases.sh`, `tools/sensors/sensors.json`,
  `tools/sensors/tests/sensor-mutations.sh`, `DiagnosticsLog.kt`, `DiagnosticsLogTest.kt`,
  `SettingsSurfaceTest.kt`, `MapManagerSourceSwitchPlanTest.kt`). The line reads "Counted with
  `git diff --numstat d885082d..HEAD`" — the right command.
- **NOTE 1 → RESOLVED.** `tasks.md:137` now reads "against causes 5, 6 and 7", matching `review-verdict-3.md:26`
  (B1 targets §7.2).

### B13 — `evidence/gate-change.txt:12-16` mis-quotes the change's own `.openspec.yaml`

The block is filed as a verbatim `$ cat openspec/changes/fix-the-reds-the-main-carried/.openspec.yaml` (line 11)
and its first line read:

> `# This change contradicts no requirement's text: each of the seven checks enforced its contract and refused a`
> … `# and a fold in the diagnostics log's write seam — so there is no spec delta to write.`

The live file (mtime `2026-11-24 11:39:09Z`, i.e. written ten seconds after round 7's verdict was filed and as
part of B12's fix) reads:

> `# … each of the eight checks enforced its contract … a retry wrapper two workflow steps call, a fold in the`
> `diagnostics log's write seam and a case's registry fetcher — so there is no spec delta to write.`

Two deltas: the count (`seven` → `eight`) and the eighth cause's clause, which the quotation lacked entirely.
Precedent: round 5's **B10** was this same block (then `five checks` against the live seven) and round 6 confirmed
B10 **resolved by equality of the quoted block with the live file** — an equality the B12 fix destroyed.

**Measurement that contradicts it:** `ctx_read` of the live `.openspec.yaml` (full, 10 lines) versus
`gate-change.txt:12-16`; `stat` gives the live file's mtime as `2026-11-24 12:39:09.615337735 +0100`
(11:39:09Z), later than the artifact's last write `12:10:43.888796326 +0100` (11:10:43Z).

**Correction asked:** replace `gate-change.txt:12-16` with the live `cat` output and note that the file changed
when the eighth cause was added (11:39:09Z).

### B14 — `evidence/gate-change.txt:22-24`'s recency claim is false, and the file's own run labels cannot all be right

The sentence read:

> `Both commands were run in this tree on 2026-11-24, after the change's last edit, against the tree of the`
> `change's final commit.`

The reviewer's remaining measurement for B14 was truncated in transit; its objection was that the change's last
edit (11:39:09Z) came after the sentence claimed to be after it, and that "the tree of the change's final commit"
names nothing, the change's directory being git-ignored.

## The parent's answer to each item

- **B13 → corrected.** `gate-change.txt`'s `cat` block is now the live 13:00Z file: "each of the **eight**
  checks … a retry wrapper two workflow steps call, a fold in the diagnostics log's write seam **and a case's
  registry fetcher** — so there is no spec delta to write." A sentence beneath it records that the file changed
  twice under this evidence file, at 11:39:09Z and at the 13:00Z re-read.
- **B14 → corrected.** The false sentence is gone. In its place: "This quotation is the live file as of
  2026-11-24T13:00Z … The change's own directory is git-ignored (`.gitignore:82`), so 'the tree of the change's
  final commit' names nothing — the tree is the working tree at HEAD `c76b38f9`, and the change's artifacts are
  not part of any commit." A dated `Re-run 2026-11-24T13:00Z — the last run in this file` block was appended, so
  every earlier label (09:05Z, 09:24Z, 11:48Z, 12:20Z) now precedes the file's own last write.
- **B12, NOTE 1 → already corrected** before this round; confirmed RESOLVED here.
