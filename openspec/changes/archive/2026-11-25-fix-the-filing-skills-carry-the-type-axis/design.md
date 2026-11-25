# Design — the filing skills carry the type axis, and the case reads them

## Root cause

The requirement ("The skill a filer is routed to names the type axis") says *every* skill the entry document names
as the tracker's filing surface. The case that pins it, `case_docs_theFilingSkillsNameTheTypeAxis`
(`tools/issues/tests/issue-tooling-cases.sh`), iterated over a two-file list —
`.pi/skills/create-issue/SKILL.md` and `.pi/skills/update-issue/SKILL.md` — which were exactly the two that already
named both flags. Every other skill that documents a `tools/issues/create.sh` call was outside the case's reach, so
its prose could lose `--type` and `--parent` silently. Two had: `.pi/skills/file-finding/SKILL.md:41` (the heredoc
form) and `.pi/skills/reshape-issue/SKILL.md:94` (the per-child form). The nine unlinked siblings of #911 are what
the omission looks like on the tracker: a container whose children carry no type and no parent.

## The single fix

1. Both skills carry the two flags on the call line they document (`--type Bug --parent 906` on
   `file-finding`'s heredoc call, its example finding's own type; `--type Task --parent <the container's number>` on
   `reshape-issue`'s per-child call), and one sentence below each says
   what the two flags write, that they are not labels, and where the type's name comes from — `create-issue`, which
   names the flags and files a series, and `roles.containerType` in `tools/tooling.json`, which is the name this
   project treats as a container (`guidelines/Backlog.md` §3 owns the rule).
2. The case's list grows to four files: `create-issue`, `update-issue`, `file-finding`, `reshape-issue`. The delta
   MODIFIES the requirement's scenario to name those four, so the case and the spec agree.
3. The case also asserts the flags on the documented call line. The presence grep alone is satisfied by the sentence
   *below* the call — it greps the whole file (`grep -qF -- "$flag" "$file"`), so prose passes it — and the clause that
   must fail on a lost pointer would otherwise be an assertion about prose rather than about the filing it guards.
   The clause is falsified on either file: `evidence/mutation-red.log` (the call's flags removed from
   `reshape-issue`, the sentence left in place) and `evidence/mutation-red-file-finding.log` (the same on
   `file-finding`), each `1 of 188 cases failed` with the message naming the file.

No value is invented: the skills carry the *flag* and point at the map for the name, which is what the item asks
("Which value a class maps to is not a judgement for this item").

## Blast radius

Three files. Two are prose read by a session, not by a check, so nothing compiles and nothing else reads them; the
third is one case body in the offline issue-tooling suite (188 cases on HEAD, `LOOP-MEASURE-SUITE` aside — that
number is the loop's own suite, and the issue tooling's is its own line). The gate's plan is shell-only: no build
step is owed, and the gate is the project's own command.

## Rollback

`git checkout -- .pi/skills/file-finding/SKILL.md .pi/skills/reshape-issue/SKILL.md` and revert the case's four-file
list; the change directory is archived with the delta it merges, so the rollback of the spec half is the archive's
own reverse patch. Nothing reads the two skills at runtime, so a rollback cannot red anything the fix made green
except the case itself — which is the point.

## Alternatives eliminated

- **A case that discovers the callers itself.** `rg -l 'issues/create\.sh' .pi/skills` matches prose that merely
  mentions the writer (`portability-check`, `run-tests`), so the case would red on a file that documents no call,
  naming no missing flag. The enumerated list is checkable and its failure message names the file.
- **Pointing at `roles.typeForClass`.** The key does not exist in the tree today (change `shape-idea` adds it), so a
  pointer to it would be a pointer to nothing — the very defect this item files against.
- **Extending the case to every skill that mentions the writer.** That is a different set (7 files, 5 calls), and the
  two further skills that document a call without the flags (`process-failure-log`:25,
  `openspec-proof-gap-audit`:175) are outside the item's own scope; they are named in the report as a follow-up, not
  fixed here.
