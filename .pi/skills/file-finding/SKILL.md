---
name: file-finding
description: File one finding as a tracker item in the shape the writer requires — a proven cause, or a change that is ready — and report its refusal verbatim. Use when a session has finished a study, a diagnosis or a plan and the finding must become work. Do not use it to authorise work, or to decide the shape for the filer.
---

# file-finding

File **one** finding. The writer decides nothing: it refuses a finding whose shape is missing, ambiguous or
incomplete, and it is the one place those rules live. This skill gathers what the shape needs and calls it.

**Prerequisites.** `gh` authenticated as an allowlisted author, `jq` and a JDK ≥ 17 on `PATH`. Nothing here needs
network access beyond the `gh` calls the writer makes.

## When to use it, and when not to

Use it when a session ends with something the tracker should hold: a defect with its mechanism, or work that needs
a plan before anyone starts it.

Do not use it to:

- **authorise work.** The opt-in label is reserved and refused in every input; a person applies it in the UI.
- **pick the shape.** If the session cannot say whether the fix is proven or the plan is needed, the finding is not
  ready — say so and stop. The writer refuses rather than choosing.
- **record a comment or a measurement.** Comment on the item that exists.

## What it gathers

Two shapes, and the session supplies exactly one of them:

| shape | what the finding carries | when it is the right one |
|---|---|---|
| **A — a proven cause** | `cause.file`, `cause.line`, `cause.case`, and the tallies the case reported before the fix | the mechanism is named and a case exercises it |
| **B — a ready change** | `change` (the OpenSpec change's directory name) and `questions` — each fork with its `decision` | the work needs a plan, and the plan's forks are already answered |

A tier-B finding is refused unless the change directory exists and the project's readiness check reports it
`ok`; the writer reads that check's path from the profile, so it never has to be named here.

## The call

```bash
tools/issues/create.sh <<'FINDING'
{
  "title": "The phone card header shows a number the step list disagrees with",
  "class": "bug",
  "category": "ui",
  "claim": "the header and the sum of the steps disagree",
  "anchor": "app/src/main/java/com/naviveylin/…/Card.kt:HeaderStatistics",
  "labels": ["class/bug", "category/ui", "status/open", "source/agent"],
  "cause": { "file": "app/src/main/java/com/naviveylin/…/Card.kt", "line": 84,
             "case": "…Test#theHeaderAgreesWithTheSteps", "tests": 1, "failures": 1 },
  "body": "- **Observed** ✗: the header and the sum of the steps disagree by 34 %.\n- **Evidence** ⊨: the case above, red before the fix."
}
FINDING
```

and, when the work needs a plan:

```bash
tools/issues/create.sh --finding finding.json --json
```

with `"change": "<the change's directory name>"` and `"questions": [{"question": "…", "decision": "…"}]` in the
payload. The answers are written to the item as comments in the form the answer path records, before its state
label moves; the item carries no decision label, because it arrives answered.

The finding's other fields, the fingerprint rule and the exit codes are the `create-issue` skill's — the same
writer, the same call.

## On a refusal

Report the refusal's own sentence and stop. Do not re-shape the finding to make it fit, do not pass a flag that
skips a refusal (`--allow-duplicate` answers a *duplicate*, not a shape), and do not file the same finding twice.
A refusal names the half that is missing; the fix is in the session's work, not in the call.
