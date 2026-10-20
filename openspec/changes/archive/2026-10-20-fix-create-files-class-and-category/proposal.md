# fix-create-files-class-and-category

## Why

A finding that states its kind files an issue that carries no kind. `tools/issues/create.sh` requires a
finding's `class` and `category` — `[ -n "$clazz" ] || issues_die "create: the finding has no class"` and the
same for `category` (`tools/issues/create.sh:78-79` at base `db1fe8e`) — and then builds the label set from
`.labels[]`, `--label` and `--status` only (`:109-118`). Both required fields are read, validated and dropped.
Measured in the tracker: #199 and #200 were filed on 2026-10-19 through `--finding <file>` from findings that
carried `"class": "bug"` and `"category": "build-and-harness"`, and arrived with `"labels": []`; both were
labelled by hand afterwards. An unlabelled item is in the tracker and in no queue — `list.sh --eligible-only`,
`--class`, `--category`, `--status` and the loop's pre-screen all select on labels — and the filing reported
success, so the caller had no reason to look.

**Root cause.** The finding's `class` and `category` never enter the label set, so the two facts the script
insists on are the two facts it discards: the kind and the area exist only in the fingerprint input
(`Fingerprint class=… category=…`, `:165`) and never become labels. The working precedent is the sensor path,
which files through the same writer and spells both labels into `.labels[]` itself
(`tools/sensors/run.sh:218-219`) — the duplication that makes the defect invisible on the path the tooling's
own checks use.

**Red tests on HEAD** (`db1fe8e`), added to `tools/issue-tooling-cases.sh` (2026-10-20T20:03Z,
`evidence/red-carries.txt`, `evidence/red-contradict.txt`):

- `create#carriesTheFindingsClassAndCategory` — a finding whose `class` and `category` are the only place its
  kind and area are stated, filed with an empty `labels` array:
  `the finding's class reaches the tracker as a label: '--label class/bug' not found in: gh issue create
  --title a\ finding\ whose\ kind\ is\ in\ its\ fields\ alone --body $'the body, verbatim\n<!-- nvid: 199
  -->\n<!-- fingerprint: a346ca9c… -->'` (exit 1). The recorded invocation carries no `--label` at all.
- `create#refusesAClassItsLabelsContradict` — a finding whose `.labels[]` names `class/improvement` and whose
  `class` field names `bug`: `a filing naming two classes is a usage error:
  https://example.invalid/Framstag/NaviVeylin/issues/1: expected '2', got '0'` (exit 1). HEAD files the
  contradiction and keeps whichever value the array happened to carry.

**Owning capability:** `issue-backlog`, requirement "One issue per distinct finding" — its scenario "A new
finding is filed" requires that the issue's "labels record the kind and the area" — and requirement "Labels
carry the item's kind, area, state and provenance", whose one-value-per-namespace refusal the contradiction
contradicts. **Owning guideline section:** `guidelines/Backlog.md` §4 "The invariants" (I16 states the state
namespace; the kind and the area are the same rule on the filing path) and §6 "How an item becomes work" (the
"intake" line: an item is born classified by what filed it). The gate is `tools/issue-tooling-selftest.sh`,
the CI step "Check issue tooling".

## What Changes

- `tools/issues/create.sh`: the finding's `class` and `category` join the label set as `class/<value>` and
  `category/<value>` after every other route, the merged set is de-duplicated by exact label (so a caller that
  already spells them in `.labels[]` still files one label per value — the sensor path does), and the fields
  therefore go through the same vocabulary gate as any other label: a misspelt class is refused with exit 3
  instead of dropped, and a value that contradicts `.labels[]` is refused with exit 2 naming both values by the
  existing namespace-uniqueness rule. The header documents the contract.
- `tools/issue-tooling-cases.sh`: the two red cases above.
- `tools/create-mutations.json`: mutation `1.3` removes the join, so the invariant has its revert-check; the
  set's comment states what the set proves.

## Spec

`openspec/specs/issue-backlog/spec.md`: two requirements MODIFIED — "One issue per distinct finding" gains the
sentence that the finding's required `class` and `category` are filed as labels and one scenario naming
`create#carriesTheFindingsClassAndCategory`; "Labels carry the item's kind, area, state and provenance" gains
the filing-path clause that a field cannot contradict the label it duplicates, and one scenario naming
`create#refusesAClassItsLabelsContradict`. No other requirement's text is touched.

## Cases and evidence

- The two red cases above — red on HEAD, green after the fix (`evidence/red-*.txt`, `evidence/green-cases.txt`).
- `bash tools/issue-tooling-selftest.sh` — 102 of 102, offline, no token, no Gradle
  (`evidence/issue-tooling-selftest.txt`).
- Mutation `1.3` (and the set's existing `1.1`, `1.2`) — red with the join removed, green again after the
  restore (`evidence/mutations-create.txt`).

## Impact

`tools/issues/create.sh`, `tools/issue-tooling-cases.sh`, `tools/create-mutations.json` and the spec above. No
compiled file is touched, so no Gradle gate applies (`guidelines/Build.md` §2, the scope block); the structural
gate is `tools/issue-tooling-selftest.sh`, which CI runs, with `tools/create-mutations.json` listed by the same
step.

Behavioural change, visible on the filing path only: an issue filed from a finding now always carries
`class/*` and `category/*`, a finding whose fields contradict its `labels` array is refused where it used to be
filed, and a class or category outside the project's vocabulary is refused where it used to be dropped. Every
finding that already spelled both labels in `.labels[]` (the import, the sensor path, the fixtures) files the
same label set as before.

Rollback: revert the commit. The writer then files from `.labels[]` alone again — an unlabelled item for a
finding whose kind lives only in the two required fields — with no tracker state to undo, because this change
writes nothing to the tracker by itself.
