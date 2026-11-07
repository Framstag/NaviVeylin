# Design — a prompt names the window its child was handed

## Root cause

Two mechanisms bound what a child reads, and neither is visible to the child:

- the adapter's trimmer, `tools/pi-trim-result.mjs:55`, cuts every tool result above `LOOP_TOOL_RESULT_MAX_CHARS`
  (`tools/loop/loop.conf:372`, `8000`) and appends one line that names the tool, the size it had and the range syntax
  — `[LOOP-TRIMMED: … read the rest in ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]`;
- the read tool itself stops at its own cap, ~2 000 lines or 50 kB per result
  (`docs/2026-11-07-reviewer-model-analysis.md:47`, measured by the probe that paged an 891 687 B diff), and a result
  that stops before the file's last line is a window whatever the tool's own notice says.

The bound is asserted where it is applied — `trim#aResultAboveTheBoundIsTrimmedAndNamed` and its siblings in
`tools/loop-cases.sh` — and the one place a child learns what a result *means* did not state it: on the base,
`./tools/look.sh 'LOOP-TRIMMED' --files` read 6 matches in 4 files (the trimmer, those cases, an archived design and
its hook probe) and no file under `tools/loop/prompts/`. So a review round's diff arrives as its first 8 000
characters, the round grades what it holds, and `PASS` with a timestamp is indistinguishable from one written over the
whole file — and the probe's own measurement is that of seven children over one 891 687 B diff, one answered the
diff's last line byte-exact (`docs/2026-11-07-reviewer-model-analysis.md:55`).

**In one sentence:** the result bound is applied and asserted at the trimmer, and never *named* at the prompt, so a
child can hold a window without a fact in its session saying so.

## The fix

One paragraph in each of the four phase prompts, at the place each child is told what it is given, naming the
recorded bound, the marker, the call and arguments that page the rest, and the rule that an anchor comes only from a
window that arrived whole — the same sentence in `worker.md`, `worker-gate.md` and `worker-tail.md`, and in
`reviewer.md` with the consequence its report owes ("a scenario you never saw is `UNPROVEN`, never `PASS`").

Nothing new is enforced. The trimmer keeps cutting, the bound keeps its value, and no prompt asks the child to read
less than it did: the sentence only makes the cut a fact the child can see, which is what the prompt already does for
the spend rule beside it ("a file you have read once is cited, not read again", `tools/loop/prompts/worker.md:106`).

The case that holds it is the one that already walks the four skeletons —
`prompt#thePhasePromptsCarryTheirOwnRoute` — extended with five assertions per prompt, and one mutation record
(`258.1`) mutating the sentence in `reviewer.md` so the case goes red when the marker stops being named.

## Blast radius

- `tools/loop/prompts/worker.md`, `worker-gate.md`, `worker-tail.md`, `reviewer.md`: one paragraph each. No
  placeholder, no key and no report line changes, and the two mutation records that already quote `worker.md`
  (`66.2`, `87.3`) target text elsewhere in that file.
- `tools/loop-cases.sh`: `case_prompt_thePhasePromptsCarryTheirOwnRoute` gains five assertions inside the loop it
  already runs over the four prompts, and its `# files:` declaration gains `tools/pi-trim-result.mjs` — the file whose
  notice the prompts quote, so a change to the marker reaches that case. The suite's declared count stays 353.
- `tools/loop-mutations.sh`: one record, `258.1`, which is the revert-check for this invariant.
- `guidelines/Backlog.recipes.md` section 7a: its result-bound paragraph gains the sentence that the prompt names the cut,
  and the case that holds it.

## Alternatives eliminated

- **A new case named for the invariant (`prompt#aCutReadIsPaged`).** The case that renders the four skeletons already
  exists and is the one assertion surface for what a prompt carries; a second case would walk the same four files,
  add a suite entry and owe a second mutation record for one invariant. The item's own evidence names the existing
  case as the place the assertion lands.
- **The sentence in `reviewer.md` and `worker.md` only.** Two of the four phase children read project text, and the
  gate child and the tail read a diff, a change directory and a findings file through the same bounded tools. A rule
  that holds for two prompts and not the other two is a fork this change would have to leave open; the uniform rule
  settles it, and the case's existing loop asserts it four times for the price of one.
- **Name no number and only the marker.** The marker is what a child sees, so naming it is necessary; naming the
  bound it is cut at (`LOOP_TOOL_RESULT_MAX_CHARS`, a recorded key) and the tool's own cap (documented at
  `docs/2026-11-07-reviewer-model-analysis.md:47`) is what lets a child tell a window from a file it should have
  paged more than once. A prompt that names only the marker tells a child what to look for and nothing about when.
- **Move the cap instead (#815).** The sibling bound, and out of scope here: the prompt is the part this tree can fix
  without changing what a result costs.
- **Refuse a review over a diff it cannot hold (`tools/loop/run.sh`).** A second mechanism, and a budget decision
  nobody made for this item: the prompt names the page turn, and a round that pages is a round that read.

## Rollback

Revert the change: the four prompts lose one paragraph each, the case loses four assertions, the mutation record is
removed and section 7a loses its sentence. No state, no key and no result changes, so the revert is the deleted paragraphs.

## Open decisions

none
