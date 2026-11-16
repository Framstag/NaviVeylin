---
name: answer-decision
description: Put one decision the tracker holds in front of a person who does not read code — what the item states, every option with what you get, whether it closes the stated problem, how you would know, and what its risk is, then a recommendation and one question. one sheet, presented once, verdict first, with no interim report and one question in one call. Takes a GitHub issue URL, an issue number, a pre-migration `§N`, or no argument (then it lists the queue from `tools/issues/decisions.sh` and lets the owner pick from that list). Stops for the owner's choice, writes nothing before it, and then leaves through `tools/issues/answer.sh`. Use when asked "decide #N", "what are the options on <issue>", "which decisions are waiting", or when `agent/needs-decision` items have accumulated.
---

# Answer a decision

The decision queue is a query over one label, and a label is a mode rather than an argument: the item is parked
until somebody states a choice in words. This skill is that reader — the only path from a labelled item to an
answered one — and its act has three parts: **read the item, lay the fork out once, stop.** The owner decides a
design question and only the owner does (`guidelines/Backlog.md` §5, "Who may write what"); the skill states the
fork, prices it, and recommends, but never chooses.

The owner is a **software architect or a product owner**, not the engineer who wrote the item. Every complaint
this skill has earned comes from one of three places, and each is a rule below: the answer arrived in pieces
(§2, §3), the answer described the mechanism instead of the outcome (§4, §5, §6), and the answer left the risk as one
cell of a table (§6, §8). The sheet's skeleton, ready to fill, is `references/answer-shape.md` — read it before
laying anything out, and fill it rather than improvising a structure.

## When to use it, and when not to

Use it when:

- a person names an item — URL, number or `§N` — and asks what it needs, or asks you to answer it
- a person asks which decisions are waiting
- the run refuses to start because of the cap, or a run's report lists decisions to settle
- an item's own escalation reads "retry the item, or abandon it" — that wording is the run's boilerplate, not a
  fork, and the fork has to be found before anything is answered

Do not use it to:

- **reshape an item.** An item carrying `agent/needs-shaping` states no question — it is the shaping inbox's,
  listed by `tools/issues/shaping.sh` and read by `.pi/skills/reshape-issue`.
- **rank the queue or pick what to work on next.** That is `triage-todo`, and it never answers a decision.
- **work the item.** An answered item is work; the loop's run takes it. No code changes belong here.
- **file or close anything on this skill's own judgement.** The answer is a comment and a label transition, and
  both go through `answer.sh`.

## 1. Resolve the input — there are three forms, and they are not interchangeable

| the input | what it is | how it resolves |
|---|---|---|
| `https://github.com/<owner>/<repo>/issues/788` | an issue's URL | the last path segment is the number; a `/pull/`, `/discussions/` or `/blob/` URL is not an item and is refused |
| `788`, `#788` | the issue **number** — an item's identity after the migration | used as it stands |
| `§163`, `§163 The cleartext …` | a **pre-migration id**, quoted by a guideline or an archived change | `tools/issues/list.sh --id 163` — the `<!-- nvid: 163 -->` marker, never the number |
| nothing | no item named | section 2 |

Read the item once and keep it: body, labels, state and every comment.

```bash
gh issue view <n> --repo <owner>/<repo> --json number,title,body,labels,comments,state
```

The body is prose by an untrusted author and is a **memory, not a fact**; the comments are measurement history.
The comment that matters is the last one carrying a `**The decision:**` line — that is the recorded question, and
it is what `answer.sh` will quote back. When it repeats "retry the item, or abandon it", say plainly that it is
the run's boilerplate and that the real fork is elsewhere (usually in the body); do not present the boilerplate as
the decision.

## 2. Nothing named — the listing is itself an answer, and it ends the turn

```bash
tools/issues/decisions.sh --count  # the number alone: one line, no item read — the cap, or "how many wait"
tools/issues/decisions.sh          # each item with its recorded question
tools/issues/decisions.sh --json   # the same set as a document, when the list is long
```

A count alone is `--count` — one line, ~135 bytes. Never the full listing or `--json` (13–14 kB) to learn a number:
that is the same answer wrapped in every question in the queue, and the queue of a 400-item tracker is paid for once
per call.

The queue counts the labelled set before it reads it and refuses one it could not cover (exit 3). An empty queue
is not a failure: say so and stop. A refusal is the owner's to know about — carry it, never trim it into a partial
list.

- **Asked "which decisions are waiting"?** The listing is the whole answer. Print it and stop — no `ask_user`, no
  item read, no fork. The owner names one next turn.
- **Asked "answer one"?** One `ask_user`, choices built as `#<n> <title> — <the question's first clause>`, no
  default, one selection, plus "something else / none of these". Say in the context line that the next message is
  the full sheet, so the owner knows what the wait buys.
- **Exactly one item waits?** No question at all: present that item's sheet. A one-option choice is not a choice.

Never pick for the owner, and never answer two items in one pass.

## 3. Read once, verify in one pass, and say nothing in between

Every claim the item makes is re-read in this tree, and the ones you cannot resolve are named rather than dropped:

- a `file:line` — does it still say what the item says it says
- a branch, commit or change — `git branch -a --contains <sha>`, `git log --oneline <base>..<branch>`: is the
  work already there, already merged, or only claimed
- a run-state or report path — does it exist, and does the number quoted match
- the item's own labels — a decision label with a hold is this queue's; `agent/needs-shaping` is another skill's;
  no decision label at all means there is nothing to answer and `answer.sh` will refuse (exit 3)
- when the item is about the cap: the same population `tools/loop/run.sh` counts — `tools/issues/list.sh --json
  --limit 400 --eligible-only`, filtered by the decision label. Quote that number; do not re-derive one.

**One pass, one message.** Batch the reads into one turn — the item, its pointers, its evidence — and keep the
narration out of the conversation: the first thing the owner sees after the reads is the sheet. A turn that ends
on "I have read the item, now let me check the branch" spends a roundtrip and buys nothing; the owner cannot act
on it. A claim that fails verification is not a separate message either — it becomes the Evidence line of the
sheet, where it changes how the options read.

**Cheap pointers, so verification does not cost more than the answer.** The item's own evidence directory
(`.pi/issue-loop/items/<n>/`) is the small source and the branch is the large one:

```bash
sed -n '/^\*\*Findings/,$p' .pi/issue-loop/items/<n>/reviewer-report-<last>.md   # 5 kB of a 22 kB report
sed -n '/LOOP-DECISION:/,$p' .pi/issue-loop/items/<n>/reviewer-report-<last>.md  # the run's own fork
git show <branch>:<path> | sed -n '<a>,<b>p'                                    # one hunk, not a worktree
tail -5 .pi/issue-loop/items/<n>/events.jsonl                                     # what the run last did
```

Read the whole reviewer report or the whole diff only when the findings block does not name the claim you must check.

## 4. Two lanes, and a thing named twice is a sheet that failed

Everything the owner reads sits in one of two lanes:

- **the owner's lane** — outcomes, in product language: what happens to the app, to the map, to a person using
  it. No file path, no identifier, no tool name, no command, no jq. A term of art that cannot be avoided is
  glossed where it first appears, in the same sentence, in the reader's words.
- **the architect's lane** — the mechanism: the file, the function, the label, the gate minutes, the command.
  This lane is what §6's "For the architect" line and §7's two views are for.

The rule that follows, and it is the one an owner notices: **a code token in the owner's lane is a defect of the
sheet, not a detail of the mechanism.** The owner cannot judge `prescreen.sh:384` and should not have to; the same
fact is "the item stays parked even though the count allows it". Where a name is genuinely the thing being
decided — a label, a setting, an option letter — it appears once, glossed, and then only as the thing's name.

## 5. Contents — six labelled lines, no prose

**Observed** (what the item says is wrong), **Evidence** (the measurements it rests on, with their dates, each
marked measured or claimed — a number the run wrote is not a number this tree produced), **Why it matters** (the
cost of leaving it), **What the user sees** (today → after, in product language, and who is affected: which screen,
flow, or car surface — a fork whose two sides read the same to a user says so), **Settled, not to reopen** (the
decision of record, its date, and what it rejected), **The decision actually being asked** — separating the recorded
escalation question from the design fork, and naming which options the item already rejects and on what ground.

A decider signs the outcome, not the mechanism, so the two lines without code in them (**What the user sees**,
**Settled**) are the ones a product owner decides from; the other four are the architect's. Write all six — a line
left out is a decider translating for you.

## 6. Options — every option answers the decider's four questions, then the architect's

One block per option, five lines, in this order — the first four in the owner's lane, the fifth in the
architect's:

- **What you get** — the deliverable, once, concretely. Not "the cap learns to skip the wording" but "the loop
  stops asking you about items it abandoned itself". If you cannot say what the owner ends up with, you have not
  understood the option yet.
- **Does it solve it** — *full*, *partial* or *no*, and then the residue in the same line: what is still broken,
  still parked, still asked, once this is done. A "full" with no residue is a claim to check, not a default.
- **How you'll know** — the observable check that would show it worked, dated or reproducible: the count that
  drops, the label that disappears, the run that starts, the case that turns green. "It works better" is not one.
- **Risk** — the level (**Low / Medium / High**) and the clause that sets it, then its four fields:
  - *if it is wrong and nobody notices* — what the reader would live with, in the owner's terms
  - *who it would hit* — which surface, flow, item or session
  - *can it be undone* — and at what cost
  - *what would catch it* — or the plain words "nothing would", which is the answer that matters most
  A level with no clause behind it is refused: an unbased risk is a feeling with a colour.
- **For the architect** — the mechanism in one sentence, the files it touches, the gate minutes and the loop's
  `rounds to land`, and — under the same heading — **Example:** a concrete state and its consequence,
  `before → after`. An example is checkable: a command with its output, a `jq` field of a run state, the label set
  before and after, a `file:line` whose code changes, a number measured on this tree. Where you had to reason
  rather than measure, write "unmeasured" beside it. An option you cannot exemplify is not offered.

Name any option the tree suggests but the item does not list, and mark it as added.

The options the escalation already carries are a **starting list, not the list**: it is the run's own reading of the
fork and it may have pre-rejected an option on a ground you can check, or missed one the tree offers. Re-price every
option it names, keep its rejections only where their clause survives, and add the one it missed.

## 7. Judge on stated criteria — one table, then two views

The owner cannot compare five paragraphs, and this is the part that fails most often: state the criteria **before**
the options are ranked, take them from the item's own observation so that "solves the problem" means the stated
problem, and fill one table. Three to six criteria, no more; each cell is *yes*, *partly* or *no* plus the basis,
or "unmeasured".

```
| criterion | A | B | C |
|---|---|---|---|
| closes the stated observation | | | |
| what the owner stops seeing | | | |
| what it costs in the loop's rounds | | | |
| risk if it is wrong | | | |
```

The criteria are the owner's; the two views below are the architect's, and they stay **two views, never one score**:

**Short term** — what the next run, or this week, actually sees. Priced in this tree's own units: gate minutes for
the architect, the loop's rounds for the person deciding whether to spend a cycle on it.

| option | gains | costs | rounds to land | what breaks if it is wrong |
|---|---|---|---|---|
| | | files touched, gate minutes, refusals added, decisions still parked | worker pass + review rounds + gate, as the last comparable item measured them | |

**Strategic** — what recurs, and what the item becomes once it is taken.

| option | removes the cause, or re-counts it? | survives the next items that hit the same mechanism? | effect on the invariants (`guidelines/Backlog.md` §4) |
|---|---|---|---|
| | | the count is measured, not guessed: `decisions.sh --json` filtered over the items' bodies and labels for the paths and mechanism the option touches — an unfilled cell says "unmeasured", never a claim | | |

No cell without a basis: each names the measurement or the pointer it rests on, or says "unmeasured".

## 8. Risk, side by side — with the fourth option nobody prices

Restate the per-option risks in one table so they can be compared, and add the row the fork usually forgets: **not
deciding**. The queue does not stand still while the item waits — a candidate (the label and the loop's opt-in)
spends a decision slot, it holds the cap, and the symptom the item describes keeps happening.

| | A | B | C | **do nothing** |
|---|---|---|---|---|
| risk level, and the clause | | | | |
| if it is wrong | | | | the item keeps its label and the queue keeps its slot |
| undone at | | | | — |
| caught by | | | | nothing; it is the state you are already in |

## 9. Weigh the views, then recommend — and say which view won

State what each view favours. Where they disagree, say which one decides and why; a recommendation that quietly
averages them is the one this section forbids.

The project's standing bias, which the recommendation follows unless the tree contradicts it: **an option that
removes the question beats one that re-counts it**, and a bound raised to fit a pile is a cap in name only
(`guidelines/Backlog.md` §5; the default was raised 3 → 10 on 2026-10-20 for the same symptom, #788).

Then, in this shape — and the recommendation carries its own risk, because an owner who is told the risk of the
losers and not of the winner has been sold something:

> **Recommendation: <option>** — <one sentence>. Risk <level>: <the clause>. Rejected: <option> because <one
> clause>; <option> because <one clause>. First sign it is going wrong: <the observable that would tell you>.

## 10. Stop — one question, and it carries the verdict

Present the sheet from `references/answer-shape.md`, in the skeleton's own order — header, the six contents lines,
the options, the criteria table, the risk table, the two views, the recommendation — in **one message**, and then
call `ask_user` once. The choices are the options, each titled with what the owner gets
(`A — <what you get>`), and the `context` field repeats the recommendation and the risk levels, because the choice
is made in that dialogue and nowhere else: an owner who must scroll back to remember which option was safe is
choosing blind. Offer "park — let it wait" as a choice, since it is one of the four outcomes.

One `ask_user` call, and a clarifier that would change which option is recommended rides inside it — never a second
turn. The item keeps its label and its question and the queue keeps listing it; that is the
point, because the answer is the owner's.
It writes nothing before the owner chooses: no label, no comment and no body edit.

An owner whose choice is to wait is answered like any other, and the difference is in what the queue then says: the
park records that choice on the item and moves nothing, so the entry reads `parked: <the decision's first clause>`
rather than the question the owner has settled (section 11).

## 11. On the choice, leave through the writer that exists

```bash
tools/issues/answer.sh <issue> --continue --decision "<text>"
tools/issues/answer.sh <issue> --device   --decision "<text>"   # the device lane
tools/issues/answer.sh <issue> --park     --decision "<text>"   # the item waits; nothing moves
tools/issues/answer.sh <issue> --retire   --decision "<text>" --evidence "<proof>"   # closure
```

The decision text carries what the next worker needs and nothing it must re-derive: the option, its mechanism,
the one clause that rejects each alternative, and the pointers already established (branch, commits, change, the
cases still owed). `answer.sh` writes the comment first — quoting the question it answers, so the marker
`<!-- answers: … -->` binds to it — and only then performs the transition, so an answer whose transition fails
still leaves its reason on the item.

A park is the one answer that performs no transition, so its text carries the condition instead of a direction: the
item keeps `agent/needs-decision` and its status, it stays in the queue, and a later answer may still continue or
retire it — which is why the clause the decision opens with is the one the queue prints.

Verify, then report the number and the new label set — and in the owner's words what just changed for them:

```bash
gh issue view <n> --repo <owner>/<repo> --json labels,state
```

## What this skill never does

- chooses an option the owner has not chosen
- offers an option without an example, or prices a pro or a contra with no basis
- offers an option without saying what the owner gets, whether it closes the stated problem, and its risk
- names a risk level without the clause that sets it, or states the losers' risks and not the winner's
- merges the short-term and strategic views into one ranking, or averages them
- writes a label, a comment or a body directly — `gh issue comment`, `gh issue edit`, `gh issue create`,
  `gh issue close` are all outside it
- edits code, changes a bound or raises `LOOP_MAX_DECISIONS_DEFAULT` while answering: the answer names the
  direction, the loop's run performs it
- re-opens a fork the item records as settled, or reads the escalation's retry-or-abandon wording as the fork
- ranks the queue or says what to work next (`triage-todo`), or reshapes an item (`reshape-issue`)

## A worked example, compressed — #788, answered 2026-11-07

**Header.** What you are choosing: whether the loop may ask you about work it dropped itself. **Recommendation: A**
— a cut produces no question. Risk Low: it removes a label write, and nothing reads that label on a cut. Solves
full. Solved means: the 08:51 run's 8 self-made decisions are absent from the next count. If you choose nothing:
the cap stays at 10 and the loop keeps refusing to start.

**Contents.** Observed: the start-time cap counts every eligible `agent/needs-decision` item, and the loop writes
most of that population itself. Evidence: `.pi/issue-loop/runs/2026-11-05T08:51:02Z.json` — 14 items, 8 blocked,
6 by the turn bound; 8 of 18 listed decisions read "retry the item, or abandon it"; cap 10; the 12:45:35Z run
exited before picking an item. Why it matters: a pile the loop makes itself is invisible to the cap, so a bound
stops the work twice — once as the killed child, once as the run that may not start. What the user sees: nothing
on any screen; you stop being asked about items the loop abandoned, and runs start again. **Settled, not to reopen:**
the cap stays at 10 and is counted where it always was (2026-10-20); raising it to fit the pile was rejected then.
The decision asked: how the cap's population stops growing, not how it is counted.

**Options, one example each.**

- **A — a cut produces no question.** *What you get:* the loop stops asking you about work it dropped. *Does it
  solve it:* full — the self-made pile never forms; what remains is the decisions you actually owe. *How you'll
  know:* the next run's count is the owner's pile, not the loop's. *Risk:* Low — a label write is removed and no
  reader depends on it; if wrong, items are silently re-offered instead of parked, visible in the pre-screen and
  caught by `decisions#aCutIsNotADecision`; undone by one line. *For the architect:* `run.sh:2778-2809`, a child
  ended at a bound records `cut` + `stopReason: bound` and writes no decision label. **Example:** item #21's
  worker dies at 110 turns → state `{result: "cut", stopReason: "bound"}`, labels unchanged, cap count 9 → 9, the
  next pre-screen offers it as work.
- **B — keep the escalation, count only forks.** *What you get:* the run starts; the items stay parked. *Does it
  solve it:* partial — the count drops, the queue keeps growing. *How you'll know:* the cap count falls while
  `decisions.sh` still lists the branch items. *Risk:* Medium — the fork list drifts from the wording it matches,
  and nothing catches it. *For the architect:* the cap's `jq` learns to skip the loop's own wording. **Example:**
  #21 keeps its labels; the cap counts 1 instead of 9 and the run starts — and `prescreen.sh:384` still refuses
  the branch, so the item stays parked.
- **C — raise the default.** *What you get:* today's queue starts; the next wave does not. *Does it solve it:* no
  — the pile moves, the bound still stops work. *How you'll know:* the count returns to 10 within two runs.
  *Risk:* Low to do, High to keep — a cap in name only, raised a third time. *For the architect:* `loop.conf`
  10 → 20. **Example:** the 9-item queue starts; the next bound wave adds ~6, so the next raise is 20 → 30.
- **D — age the decision.** *What you get:* runs start; you are still asked, just later. *Does it solve it:* no —
  it makes the pile self-excusing. *How you'll know:* every run counts 0 of the items the previous run raised.
  *Risk:* High — nothing catches it, because self-excusal looks like an empty queue. *For the architect:* the
  escalation records the run that raised it. **Example:** the 08:51 run raises 8; the 12:45 run counts 0 and
  starts, every run, for ever.

| criterion | A | B | C | D |
|---|---|---|---|---|
| closes the stated observation | yes | partly | no | no |
| what you stop seeing | self-made decisions | the cap count | nothing durable | nothing |
| rounds to land | 2 | 4 | 0 | 3 |
| risk if wrong | Low | Medium | Low | High |

| | A | B | C | D | do nothing |
|---|---|---|---|---|---|
| level, and the clause | Low — a label write removed, no reader of it | Medium — a wording list that drifts | Low to do, High to keep | High — self-excusal looks like an empty queue | — |
| if it is wrong | cut items are re-offered, visible in the pre-screen | the count moves, the pile does | the pile returns | nothing catches it | the item keeps its label and the queue keeps its slot |
| undone at | one line | one `jq` filter | one constant | one field | — |

**The two views.** *Short term:* A costs a worker pass and a review round, and buys a cap that counts the owner's
pile; C costs nothing and buys one week. *Strategic:* A removes the cause, B re-counts one wording, C and D re-count
the symptom — A is the only one that survives the next bound wave.

**Recommendation: A** — it removes the question instead of re-counting it; 3 → 10 on 2026-10-20 is the
measurement that C repeats. First sign it is going wrong: items that were cut are offered a second time without
their evidence.

**Answer written.** `answer.sh 788 --continue --decision "A — …"`, naming the branch, its commits, the four
blocking findings the reviewer left, and "do not re-derive". Verified: `agent/needs-decision` and
`status/on-hold` gone, item work again.
