# Design

## Context

See `proposal.md` — Why. This document records only what shapes the pass.

Consumed from the preceding changes in this series:

```
tools/tooling.json   class/ category/ status/ source/ agent/ prio/ (p0..p3), closed vocabularies
tools/issues/list.sh     --eligible-only, --class, --category, --status, --author, --id
tools/issues/update.sh   labels / status / body / --comment, refuses reserved labels
tools/issues/policy.sh   preflight + check <issue>
```

The scoring model this pass persists is `triage-todo`'s, unchanged (`.pi/skills/triage-todo/SKILL.md`):

```
score = severity(1..5) x reach(1..3) + confidence(1..3)   ->  2..18
                                                          (the model's own minimum: 1x1+1; task 1.1's "3..6"
                                                           for P3 was an arithmetic slip, corrected in D2)
severity  5 crash / process death / host kill / data loss
          4 silent failure of a user action, wrong result, spec deviation with user impact
          3 spec deviation without user impact, latent defect, compliance deadline
          2 degraded quality: perf, memory, latency, noise, flaky gate
          1 hygiene, docs, tooling
reach     3 every user / every startup / both surfaces   2 one surface   1 developers only
confidence 3 reproduced with a citation   2 read from code with file:line   1 inference
status caps confidence: unverified = 1; open (device) <= 2
tie-break  smallest effort, then a deadline, then what unblocks siblings
```

Two facts from the repo's own experience that shape the change detector:

- `triage-todo`'s own pitfalls name the failure this pass must not create: "compressed reads lose
  entries", i.e. reading a subset and believing it is the whole. The selector therefore reads **all**
  issues (cheaply, as a script) and skips by *basis*, never by *sampling*.
- The loop's first run measured 12 candidates → 3 archived, 9 gated out, and `condition 3` rejected 5
  of the 9 — the score's components are a tool for **ordering a queue**, not for deciding eligibility.
  That is why this pass computes a band and nothing else, and why eligibility stays `agent/eligible`.

## Goals / Non-Goals

**Goals**

- The model's cost is proportional to what **changed**, not to the size of the backlog.
- Priority is one fact in one place, with the reason it has that value recorded beside it.
- An unclassified issue becomes selectable and rankable without a human remembering to classify it.
- The pass is safe to run repeatedly: running it twice changes nothing the second time.

**Non-Goals**

- Not eligibility: `agent/eligible` is untouched, still reserved, still the human's opt-in.
- Not the gate lists: `needs-device`, `needs-decision`, `in-flight`, `unverifiable` stay derived from
  labels by whoever reads them, because they change far more often than a score does and are free.
- Not a scheduler or a CI pipeline: the pass runs where an agent runs, and its CI wiring is deferred
  (this keeps the model provider's key out of CI for now).
- Not a rewrite of the scoring model. Severity, reach and confidence keep their meanings; the pass only
  stores the result.
- Not the agent sandbox: the selector is a host command, and the model call happens wherever an agent
  runs — including, later, inside one.

## Decisions

### D1 — Priority is a label plus a body marker, written together by one script

```
label    prio/p0 | prio/p1 | prio/p2 | prio/p3          the cheap selector
marker   <!-- prio: band=p2 score=9 sev=4 reach=2 conf=3 occ=1 basis=<sha256> at=2026-10-17 -->
         (the band is the vocabulary's own lowercase spelling, so the marker and `prio/p2` agree literally)
```

Both are written in one operation by `prioritise.sh`, so they cannot drift; `check-metadata.sh`
asserts they agree, and a hand-edited mismatch is reported rather than silently trusted.

| alternative | why rejected |
|---|---|
| only a numeric score in the marker | selecting "the top of the queue" would need a body parse of every issue, so the cheap path disappears. |
| only a band label, no marker | the basis and the components vanish, so nothing can decide whether to recompute and `check-metadata.sh` has nothing to check against. |
| a GitHub Projects field | a second API surface with its own auth and drift; the label already selects, and `github-issue-tooling` D5 rejected a Project for status for the same reason. |
| a single numeric label (`prio/9`) | 16 labels that cannot be ordered by a label filter, and it still needs the marker for the components. |

### D2 — A fixed range with the full resolution kept

```
the profile's `bands` section (committed, tested):
  P0  15..18     crash, data loss, a deadline
  P1  11..14
  P2   7..10
  P3   2..6      hygiene, tooling
label values are lowercase: prio/p0..prio/p3  (the vocabulary's own `^[a-z0-9-]+$` rule)
```

P3 has to reach down to 2: the model's minimum is severity 1 × reach 1 + confidence 1, and the spec's
"The range is fixed and total" requires every value the model can produce to fall in exactly one band. The
edges live in the profile's `bands` section and are validated against the scoring table, so the next
re-tuning is one committed file.

| alternative | why rejected |
|---|---|
| five or more bands | the underlying score is already a coarse product of three small integers; extra bands would split one score across two labels and imply precision the model does not have. |
| keep only `severity` as the range | loses reach and confidence, so a developer-only crash would outrank a user-visible wrong result. |
| a continuous `score` as the label | labels are strings; ordering would have to be done in the client for every query. |

### D3 — A recorded basis decides recomputation

```
basis = sha256( sorted(class/*, category/*, status/*)    the labels the score depended on
              + sha256(body)                              the claim may have been edited
              + anchor-exists(path, symbol)                the code it cites may be gone
              + occurrence                                a sensor reopened it (D6 below)
              + ttl-bucket                                a score nobody revisited inside the TTL
              )
ttl = 180 days by default; an issue whose basis is inside the TTL and otherwise unchanged is skipped
```

| alternative | why rejected |
|---|---|
| recompute everything every run (today) | the cost this change exists to remove: model work proportional to the backlog. |
| recompute nothing until a human asks | the band of an issue whose cited code was deleted, or whose labels changed, becomes a lie that nothing detects. |
| a repository-wide basis (e.g. `HEAD`) | every commit would invalidate every issue, so a single merge would trigger a full sweep — worse than the status quo. |
| a body-only hash | misses a label change, which is the cheapest and most common way a context changes (`class/bug` → `class/improvement` moves it a whole band). |
| no TTL | a score can be years old and nothing revisits it; the TTL is the one rule that makes staleness self-healing. |

### D4 — The judgement is bounded, batched, and never writes

```
per run:  read titles + labels + body for every issue      (script; one page set; no model)
          recompute each basis; partition into skipped / due
          hand the due set to the model in batches of --max-rescore (default 20)
          the model returns {severity, reach, confidence, rationale} per issue
          prioritise.sh computes band = f(score), writes the label AND the marker
          a band TRANSITION gets one comment ("P2 -> P0: <rationale>"); same-band rescores do not
```

| alternative | why rejected |
|---|---|
| the model writes labels | violates the one-writer rule the whole series is built on, and it would put the model on the write path of a reserved-label-protected system. |
| no cap on a run | a vocabulary edit (a category renamed, a class reclassified) would invalidate hundreds of bases at once and turn one run into a sweep. The cap makes that a sequence of bounded runs instead. |
| one issue per model call | N round trips for a batch the model can score in one prompt; and the issue text is passed as a delimited data field either way. |
| a comment on every rescore | arithmetic noise on the timeline; the transition is the event a reader cares about. |

### D5 — Classification rides the same pass

```
queue:      issues with no class/* label            (the agreed meaning of "unbearbeitet")
the model:  proposes class + category, with a one-line reason
the script: writes them through tools/issues/update.sh (closed vocabulary, refuses reserved labels)
also fixed: an issue whose category is outside the vocabulary is reported, never silently relabelled
```

| alternative | why rejected |
|---|---|
| a separate change and a separate pipeline | two queues, two write paths and two CI steps for a pass that needs exactly the same selection mechanics; the shared infrastructure is the pass, not the judgement. |
| require an issue template and drop the loop | then a third-party issue and an agent-filed issue stay unclassifiable, and the queue for a human filing by phone stays broken. |
| classify by a deterministic keyword map | it would have to guess severity-adjacent semantics from prose; the model's job here is small and bounded, which is the right place to spend it. |
| let classification set `agent/eligible` for "obviously fixable" issues | that is the escalation the reserved label exists to prevent; the human's opt-in is not a model's judgement. |

### D6 — The gates stay derived, and `occurrence` is the one sensor input

```
derived per query (never persisted):  needs-device  -> status/open-device
                                      needs-decision -> agent/needs-decision
                                      covered        -> status/in-flight
                                      unverifiable   -> status/unverified
persisted basis input:                occ            from the sensor lifecycle's own record
```

| alternative | why rejected |
|---|---|
| persist the gates as labels too | they change constantly (an issue becomes `in-flight` the moment a change starts), so they would invalidate the basis far more often than the score changes — thousands of needless rescores. |
| ignore `occurrence` | a sensor that reopened an issue three times is telling you the previous fix did not hold, which is exactly the context that should move a band. |

### D7 — Priority is not authorisation, and the check proves it

`check-metadata.sh` asserts: every `prio/*` label is in range; the marker and the label agree; every
issue with a marker has exactly one band; no issue carries a priority outside `p0..p3`; and **no run of
this pass ever wrote `agent/eligible`** (a case over the fixture's write log, plus the reserved-label
refusal that `update.sh` already enforces).

| alternative | why rejected |
|---|---|
| treat `prio/p0` as "the loop may take it" | it would let this pass (and therefore a sensor-fed basis, and therefore a threshold) promote work into an autonomous fixing loop — the same escalation `github-issue-tooling` D2 reserved the label against. |
| document the separation without a check | every change in this series has been told to make its invariants mechanical; a rule that lives only in prose is the thing `revert-check` exists to falsify. |

### D8 — `triage-todo` reads bands; it never computes one

The skill ranks by band, then by the existing tie-breakers, and reports a band-less issue as
*unranked* — it is read-only by contract and cannot write a band. Its scoring tables stay in the skill
as the definition the pass implements, so the model that scores has a documented model to apply.

| alternative | why rejected |
|---|---|
| let the skill compute and store bands on the fly | it would put a writer inside a read-only skill, and it would recompute exactly what the pass persists. |
| delete the scoring tables from the skill once the pass exists | then the pass's model has no committed definition of severity, reach and confidence, and the two would drift. |

### D9 — Metrics that justify the scheme, and idempotence as a property

The pass reports: issues read, skipped by unchanged basis, due, scored, classified, band transitions,
wall time, and the model batch count. Running it twice with nothing changed must score nothing —
asserted as a case, because that is the property the whole change exists for.

| alternative | why rejected |
|---|---|
| no metrics | the token argument *is* the change's justification; without scored/skipped counts it cannot be checked, and concept doc §6 asks for it. |
| assert idempotence only in prose | it is the one property that can silently regress (a basis that includes a timestamp, a set that is not sorted) and a case catches it in one line. |

### D10 — Sandbox-agnostic, and no CI wiring yet

`prioritise.sh`, `classify.sh` and `check-metadata.sh` are host commands; the model call happens
wherever an agent runs. No container, no `sbx`, no network policy, and no model key in CI. If a
scheduled CI run is wanted later, it is a workflow plus a provider secret — a separate decision, and the
first place in this series where a model key would live in a pipeline.

| alternative | why rejected |
|---|---|
| wire a scheduled CI pipeline now | it introduces a provider key into CI for a pass that runs fine in an agent session, and it is the change most likely to be wanted differently once the sensors exist. |
| make the pass a skill-only flow with no script | the selector, the basis and the writer must be mechanical and testable; a skill cannot assert idempotence. |

## Risks / Trade-offs

- **A wrong basis makes a stale band invisible** → the basis covers the four ways a context changes
  (labels, body, cited code, recurrence) plus a TTL; the same rule is exercised by four cases
  (revert-checks 8.1–8.4).
- **The model's components are a judgement, so two runs could disagree** → the marker records the
  components, so a disagreement is visible rather than silent, and the band's stability is what matters
  for ordering.
- **`--max-rescore` can leave a backlog of due issues** → deliberate: the pass is safe to re-run, and a
  bounded run is what keeps a vocabulary edit from becoming a sweep. The report prints the due-but-unscored
  count, so the backlog of rescoring is never hidden.
- **A band change comment becomes churn** → only transitions comment, and a flapping band is itself
  informative (it says the score's inputs are unstable) rather than something to hide.
- **Classification could mislabel and thereby mis-rank** → the vocabulary is closed so a write outside
  it is refused, and `cleanup-todo`'s hygiene pass repairs a label the owner disagrees with; the pass
  reports what it changed.
- **`check-metadata.sh` in CI could fail on a hand-edit** → intended: the marker and the label are
  written together by one script, so a mismatch means someone edited one of them by hand, and a build
  gate is the cheapest place to notice.

## Migration Plan

1. the profile's `bands` section + the band mapping and its cases;
2. `check-metadata.sh` + its self-test (the invariant exists before anything can violate it);
3. `prioritise.sh` with `--dry-run` default: selector, basis, partition, `--max-rescore`;
4. `classify.sh` for the unclassified queue;
5. `list.sh` selectors (`--prio`, `--unranked`);
6. `.pi/skills/issue-metadata/SKILL.md` (the judgement half) and the `triage-todo` rewrite;
7. one token-free CI step, and one real run over the live tracker.

Rollback: revert the scripts, the profile's `bands` section, the two check scripts, the skill and the `list.sh`
selectors. The labels and markers may remain (inert without a reader) or be removed by
`cleanup-todo`; nothing in the app or the release path is involved, and no public state is lost.

## Open Questions

Genuinely deferrable; none changes the approach or the task breakdown:

- The TTL (180 days is a guess; the first months' skipped-against-scored numbers will set it).
- The band edges: `15..18 / 11..14 / 7..10 / 3..6` is a first cut against the existing tables, and it
  is one file to change.
- Whether a scheduled CI run for this pass is worth a provider key in the pipeline, or whether it stays
  an agent-session job forever.
- Whether `cleanup-todo` should own a "priority reviewed N months ago" hygiene list, or whether the TTL
  is enough.
