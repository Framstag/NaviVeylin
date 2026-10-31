#!/usr/bin/env python3
# One-off cut of guidelines/Backlog.md into a rules base plus its two companions.
# Original line numbers are 1-based and inclusive. Reads guidelines/Backlog.md, writes the base back.
import io, sys

SRC = "guidelines/Backlog.md"
with open(SRC, encoding="utf-8") as f:
    L = f.readlines()  # index 0 == line 1

def keep(a, b):  # inclusive 1-based
    return L[a-1:b]

out = []

# lines 1..54 (title, intro, sec1, sec3 heading? no: 1..53 + 54 heading)
out += keep(1, 54)

out.append("""
The participants and the flow are the procedure layer — the producers, the tracker and the three consumers, the
run's script and the prompts its phases use. The diagram, the daily metadata pass and the write boundaries it
rests on are `guidelines/Backlog.recipes.md` §2.
""".lstrip("\n"))

# 91..174: sec3, sec4, sec5 (head through line 174)
out += keep(91, 174)

out.append("""
Five rules keep the decision queue answerable, because it is a query over one label and the run's ceiling counts
that query (`tools/issues/decisions.sh`, `LOOP_MAX_DECISIONS_DEFAULT`):

- **Rule: an answer reaches the next worker's package.** The answer is a comment, and the item's body still states
  the question it settled — so nothing but the package can carry it: the run asks the tracker for the item's
  comments and the package renders the last recorded decision beside the question it answered (`<<<ITEM_DECISION`,
  `recorded: none` when there is none), and `answer.sh` writes that question into the answer with an
  `<!-- answers: … -->` marker. The question is the escalation's `**The decision:**` line, never the verdict word
  (`needs-decision`) that opens its comment, and the queue reads it the same way (`tools/issues/lib.sh`,
  `ISSUES_QUESTION_JQ`). Without the package carrying the answer a fresh worker re-derives the same fork with a
  fresh option list, and the recorded `Decision: 2` silently means a different option. A worker that reads a
  recorded decision treats it as settled and says what it leaves open when it has to ask again.
- **Rule: a closure leaves no decision label behind.** An item that carries the label and is closed would stay in
  the queue for ever and keep spending a decision slot; `tools/issues/close.sh` takes the label off in the same
  write that records the settled state, for every path that closes — the run, a person, a declared check.
- **Rule: a block asks its own question.** When the reviewer wrote no decision, the run states the only two-way
  choice a block leaves — retry the item, or abandon it — because a reason is not something a person can answer.
- **Rule: a stale skip asks its own question, and is never parked on a bare hold.** The verdict says the item's
  claim no longer holds in the tree, which the run cannot close on: the falsification closure owes a measurement
  and a review that a stale skip does not carry. The run writes the decision label, the hold and the one comment
  naming the reason and the choice — retire the item as stale, or re-diagnose it — because a bare hold is reported
  by the pre-screen as held, which is neither work nor a decision, and leaves the item open for good (spec
  `agent-issue-loop`).
- **Rule: a reviewed falsification closes the item as mis-diagnosed, and only on an unchanged tree.** A run that
  measured that the item's premise does not hold holds the evidence that settles it, so it closes the item in the
  settled state a decision not to act records, with the measurement as the comment — under the same `--may-close`
  permission a fix closure rides, and only after the review read the claim. A falsification whose tree moved
  during the review is a change, so it is escalated instead (openspec change `falsified-items-close-in-the-loop`).
""".lstrip("\n"))

out.append("\n")  # line 207 blank

out += keep(208, 223)

out.append("""
The intake step is a write, so it is the filing path's job to make an item exist in a selector's queue: a finding
states its `class` and its `category`, and `tools/issues/create.sh` files both as labels of their namespaces — the
fields are required, a value outside the closed vocabulary is refused rather than dropped, and a field that
contradicts the labels the same finding carries is the one-value-per-namespace refusal.
""".lstrip("\n"))

out.append("\n")

# 230..248
out += keep(230, 248)

# add §6 named rules after 248? keep as-is; rules named inline below in §6 body are not added. We add a couple.

# §7 heading (249)
out += keep(249, 249)
out.append("""
`Backlog.md` §7 is an index. The loop's procedure — its phases, the bounds on a child, the gate and its suite,
the refusals it records and the leftovers it reports — is `guidelines/Backlog.recipes.md`, one sub-section per
anchor below. Each anchor names a section number this document carries, so a citation of `§7` still resolves and a
reader lands on the phase they need.

## 7a. Running one item: the script, the child and the package

The run's script, the child's prompt and the result bound are `guidelines/Backlog.recipes.md` §7a.

## 7b. What an item costs, and the bounds on it

The item's cost, the clock, the memory floor, the turn bound and the gate-step bound are
`guidelines/Backlog.recipes.md` §7b.

## 7c. The gate and its suite

The gate's configuration, `{base}` narrowing, the case pool and the suite lease are
`guidelines/Backlog.recipes.md` §7c.

## 7d. Refusals, records and the landing

A child that never started, a dirty tree, commit identity, the branch and the merge, adoption and the numbers are
`guidelines/Backlog.recipes.md` §7d.

## 7e. Leftovers, the report and the queue

The scratch root, the leftovers sweep and the report are `guidelines/Backlog.recipes.md` §7e.
""".lstrip("\n"))
out.append("\n")  # blank before §8 heading

# §8 heading 827
out += keep(827, 827)
out.append("""
The human lane is the sibling workflow that answers what the loop may not decide. Its protocol and the escalation
it carries are `guidelines/Backlog.recipes.md` §8.
""".lstrip("\n"))
out.append("\n")

# §9 heading and opening
out += keep(854, 855)  # heading + blank (the opening paragraph 856-858 is restated as the named rule below)
out.append("""
- **Rule: a check is a declared row, not a script.** A check is the command to run, how often, the bound it
  compares against, how many consecutive runs its verdict needs, how many items one run of it may file, the kind
  and area its items carry, and whether it owns the item's closure. Adding a check is a row.

The three-value outcome a run of a check writes — a present condition files, reopens or annotates an item; a
cleared one comments the measurement and closes once the consecutive count is met; a failed one writes nothing and
fails the run loudly — is `guidelines/Backlog.recipes.md` §9.

""".lstrip("\n"))

out.append("\n")  # blank line 868

out.append("""Three rules make a check trustworthy rather than noisy:

- **Rule: identity is finer than the check.** The ownership label says which check owns an item; a condition
  fingerprint says which item it is. The same condition returning reopens; a different cause under the same
  check is a new item. A graveyard of near-duplicate items would lose the one signal that matters — that a
  fix did not hold.
- **Rule: hysteresis in both directions.** A flapping condition churns nothing; and past a recurrence bound
  it escalates instead of reopening forever, because a condition that keeps returning is a specification
  or design problem rather than an implementation one.
- **Rule: the issue is the measurement history.** Numbers live as comments; nothing is committed, because a
  check must not become a second committer (I12). The previous measurement *is* the baseline, so growth and
  level are both answerable without a store.
""".lstrip("\n"))
out.append("\n")  # blank 882

out.append("""
- **Rule: one run of one check files a bounded number of condition items** — `maxFilings` in the registry, or the
  count the row declares in its place. Past it the run files nothing further, and the conditions it did not file
  reach the tracker as one summary item carrying the count, the bound and each condition's identity and measurement
  (at most twenty of them, the rest named by number), so a mis-fitted bound costs one item instead of one per
  condition it happens to hold. The summary is not a condition of the check's — its identity is the engine's own
  and it carries no measurement marker, so it never reads as one of the row's measurements — and the first run
  that leaves nothing over closes it, while a run in which a finding failed closes nothing and a closure a person
  made is annotated instead of reopened. An annotation, a reopen and a comment spend nothing of the bound: none of
  them makes the tracker grow.
""".lstrip("\n"))
out.append("\n")  # 893

out.append("""
- **Rule: a row's first exercise is reported, not spent.** A row's first exercise is the one run whose blast radius
  nobody has seen, and the state is already printed: the report calls it `stale row=<id> no measurement on record`.
  The unattended tick therefore reports that run — it prints `first exercise: row=<id> findings=<n>` and writes
  nothing at all, no condition, no record issue and no summary — and the first exercise is spent by a dispatch that
  asked to write (`apply=true`), which records the measurement every later run is compared against. The engine reads
  the trigger the pipeline names (`SENSORS_TRIGGER`, GitHub's own `event_name`), so only the scheduled tick prints
  that count line: a run a person started records a measurement only when it asked to write, and a hand run — whose
  mode defaults to `--dry-run` — writes nothing and so leaves the row's first exercise unspent.
""".lstrip("\n"))
out.append("\n")  # 903

out += keep(904, 924)  # repairs + pipeline
out.append("\n")
out.append("""
The declared rows and what each measures — `broken-main`, `stale-citations`, `metadata-invariant`, `hotspot`,
`source-size`, `routed-section-size`, `entry-document-size` and `loop-spend` — are `guidelines/Backlog.recipes.md` §9.
""".lstrip("\n"))
out.append("\n")

# §10 heading + body, minus the fence 1030-1041
out += keep(1025, 1029)   # heading + blank + opening
out.append("\n")          # replaces the fence
out += keep(1042, 1096)   # after fence through §12 heading

# §12 pointer
out.append("""
The decisions and the alternatives each eliminated are `guidelines/Backlog.why.md` §12.
""".lstrip("\n"))
out.append("\n")
# §13 heading 1136
out += keep(1136, 1136)
out.append("""
What is deferred, and why it is deferred is `guidelines/Backlog.why.md` §13.
""".lstrip("\n"))
out.append("\n")
# §14
out += keep(1150, len(L))

with open(SRC, "w", encoding="utf-8") as f:
    f.writelines(out)
print("wrote", SRC, "lines", len(out))
