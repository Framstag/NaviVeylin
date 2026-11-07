# The four small guidelines are layered and their rules named, without a document split

## Why

Four guideline documents are under the 48 000-byte document bound and none of them owes a split: `Regulatory.md`
24 942 bytes, `FeatureList.md` 17 221, `Metrics.md` 9 551, `Logging.md` 7 033. What they owe is the other half of the
layer rule, and the reason is not size:

- **They carry dated figures, issue references and command transcripts inside their rules.** Measured, that is what
  the mechanism's purity condition reports, and it reports it per file and line: the condition is what makes this
  change necessary for documents the bound leaves alone.
- **Their rules are unnamed**, so the rule-level reading set `guideline-layers-mechanism` derives has nothing to
  reach in them: a `Logging.md` change would be reviewed against a section, not against the rule it breaks.
- **`Regulatory.md` is the document where prose is most tempting.** Its §2 "Publisher duties" and §9 "Repo inventory
  and open items" mix what the project must do with what it did and when, and the disclosure statement a log line
  may carry is a rule a change must be able to cite by name.

By document, what the bytes are:

| document | bytes | routed sections | what it holds |
|---|---|---|---|
| `Regulatory.md` | 24 942 | §2, §3, §4, §6, §9 | duties and platform gates (rules), plus inventory and review dates (history), plus source lists (procedure) |
| `FeatureList.md` | 17 221 | §1, §3, §4, §6, §8 | what the two documents are (rules), plus the classification vocabulary and the gates' commands |
| `Metrics.md` | 9 551 | §1–§6 | what one run measures (rules) plus the invocations that produce it (procedure) |
| `Logging.md` | 7 033 | §1, §2, §3 | the rules, plus the NDK bridge detail and the logcat recipes |

**Acceptance scenario.** After the change, all four documents carry named rules, their dated figures, issue
references and command transcripts are in their companions, the purity condition reports nothing in any of them, and
none of their section numbers has moved.

**Owning guideline.** Each document owns its own rules — `Logging.md` §3 states what a log line may carry,
`Regulatory.md` §2 the publisher duties, `FeatureList.md` §1 what the two documents are, `Metrics.md` §2 what one
run measures. The layer rule is `openspec/specs/documentation-ownership/spec.md`, "A guideline document carries one
kind of text".

**Surface.** Measured with `tools/check-doc-routes.sh --sizes` and `grep -c`:

```
documents past the 48 000 bound                  0 of 4
documents with a purity condition               to be measured by the row this change clears
rule names carried today:  Regulatory 0, Logging 0, FeatureList 0, Metrics 0
existing **Rule: <name>** names in the whole tree:  5, all in MapRendering.md — these four carry none
unnamed rules these four carry (`- **Never …**` bullets):  Logging 3, others 0
dated figures and issue references inside rules  present in all four
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`), 4 normative documents kept whole, up to 8
companion files created, 0 existing rule names carried forward (this change is where four documents gain their
first), 3 unnamed `- **Never …**` rules named, 0 fixtures realigned —
`tools/sensors/metrics/doc-context.sh` and `grep -c 'Rule:' guidelines/{Regulatory,FeatureList,Metrics,Logging}.md`

## What Changes

- **Rule naming in all four** — every rule becomes `**Rule: <name>** <statement>.`, unique in its document and
  across `guidelines/*.md`. None of the four carries a name today (`grep -c '**Rule:' guidelines/*.md` finds 5, all
  in `MapRendering.md`), so this is the change where four documents gain their first names — and the obligations that
  read as rules without a name, `Logging.md`'s three `- **Never …**` bullets included, are what has to be found by
  reading rather than by a regex (`design.md` D2).
- **`Regulatory.md`** gains `Regulatory.why.md` (the inventory history, the review record, why a duty was added) and
  `Regulatory.recipes.md` (the source list, the checklists, the review commands).
- **`FeatureList.md`** gains `FeatureList.recipes.md` (the generator's invocations and its gate commands) and, where
  its classification vocabulary is rationale rather than rule, `FeatureList.why.md`.
- **`Metrics.md`** gains `Metrics.recipes.md` (the invocations and the sample output the rules are read against).
- **`Logging.md`** gains `Logging.recipes.md` (the logcat recipes and the NDK bridge's configuration detail), keeping
  the rule that no log or diagnostics line may carry a coordinate where it is.
- **`AGENTS.md`** adds one row for the companions convention — already added by the mechanism change — and its four
  existing rows for these documents keep naming the same sections.

No requirement changes. `skip_specs: true` — the layer rule and the guards landed in `guideline-layers-mechanism`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes. No rule's text changes; each gains a name and loses the evidence around it.

## Impact

| path | what changes |
|---|---|
| `guidelines/Regulatory.md`, `FeatureList.md`, `Metrics.md`, `Logging.md` | rules named, evidence moved out; no section number moves |
| `guidelines/Regulatory.why.md`, `Regulatory.recipes.md`, `FeatureList.recipes.md`, `Metrics.recipes.md`, `Logging.recipes.md` | new companions |
| `AGENTS.md` | no new row owed — the four documents are routed already, and the companions need no row |

No build gate, Gradle file, string table, licence input or Android component is touched. Scope: **documentation** —
although two of these documents state rules a build gate enforces (`checkNoCoordinatesInLogs`, the licence policy's
gate), and this change explicitly does **not** wire a gate's message to a rule name; that is a separate change named
in the mechanism proposal.

**Rollback path.** Revert the commit; the companions are new files and every line returns to its document.

## Not in this change

- **Tying a build gate's message to the rule name it enforces**, so `checkNoCoordinatesInLogs` names
  `Logging.md §3 "<rule name>"` and a check verifies the name resolves. Named rules make it possible; it edits the
  gates, which is a change of its own.
- **A document split for any of the four.** None is past the bound, and splitting a document the bound leaves alone
  would add routing rows for no reader.
- **The other five documents** and the four open loop changes.
