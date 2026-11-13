# Proposal — the tracker's own classification is a second axis, and a container is never work

## Why

**Root cause.** The backlog's whole selection model is one axis: every fact a reader selects on is a label
(`guidelines/Backlog.md` §3), and the tooling therefore knew no other way to classify an item. GitHub grew a second
axis — the **issue type** (`Task`, `Bug`, `Feature`, and an account-defined `Epic`), account-scoped and orthogonal to
the labels — and there was no path from this repository to it: `create.sh` and `update.sh` had no flag for a type, the
loop's read asked the tracker for `number,title,body,labels,author,state` and nothing else, and nothing anywhere could
answer "is this item a container whose children carry the work?".

The consequence is the one measured on item #838: six design decisions settled, a change of 35 tasks refused as
`too-wide`, eight children filed in its place — and the parent had to be parked with `status/on-hold` to keep the run
from handing it the same 35 tasks, because there was no way to say *this item is a container*. A hold says something
about a run's capacity; "this is an Epic" says something about the item (invariant I18's distinction, applied to a
classification rather than to a state).

**Evidence** (measured 2026-11-12).

- The account declares `Task`, `Bug`, `Feature` and, since a person added it that day, `Epic` —
  `gh api graphql` → `repository.issueTypes`.
- `gh` itself is not a guard. `gh issue create --type Bogus --title 'type-refusal probe, ignore'` printed
  `type "Bogus" not found; available types: Task, Bug, Feature` **and filed issue #874**, untyped and unlabelled. The
  refusal arrives after the item exists, so a typo leaves a half-write behind.
- Nothing in the tree read a type: `grep -rn 'issueType' tools/**/*.sh` on `HEAD` found no occurrence.
- Item #838 and its eight children are the first users: `Epic` on the parent, `Task` on the seven that were filed as
  work, and every one of the eight set through the flags this change adds.

**What this change is, and is not.** It adds the writer's two flags, the reader's one verdict, and the profile key
that names the container type. It does **not** retype existing items, and it does not make a parent close when its
children are done — the tracker has no such rule and neither has this change.

## What Changes

- `tools/tooling.json` gains `roles.containerType` (`Epic`), an **optional** key: a profile that names none, or omits
  it, declares that no type is a container and the guard then refuses nothing.
- `tools/issues/create.sh` and `tools/issues/update.sh` gain `--type` and `--parent` (a finding may also carry `.type`
  and `.parent`). Neither is a label: a type is account-scoped configuration and a parent is an issue, so neither
  travels through the vocabulary gate.
- `tools/issues/lib.sh` gains `issues_declared_types`, which asks the account which type names it may use. Both
  writers refuse an undeclared name with `type-opaque` **before** writing, because `gh --type` files the item first.
- `tools/loop/prescreen.sh` refuses an item whose type is the declared container — verdict `container`, before every
  other judgement — and `tools/loop/lib.sh`'s per-item read now asks for `issueType` and `parent`.
- `guidelines/Backlog.md` §3 gains the rule, with the separation from the body's `tier:` record stated; the rule is
  routed in `tools/doc-routes.json` and named in the `AGENTS.md` backlog row.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

`issue-backlog` — one requirement added, "The tracker's own classification is a second axis". Its existing
requirements are unchanged: the labels keep their namespaces, the vocabulary gate keeps refusing an unknown label,
and the authorisation rule is untouched.

## Impact

**Tooling.** `tools/issues/{lib,create,update}.sh`, `tools/loop/{lib,prescreen}.sh`, the two suites
(`tools/issues/tests/issue-tooling-cases.sh`, `tools/loop/tests/loop-cases.sh`), one stub fixture and one scenario
fixture.

**No production code.** No file under `app/`, `auto/`, `core/`, `:osmscout-client-java`, the native tree or
`buildSrc` changes, so no flavor, no ABI and no device evidence is owed.

**Portability.** The type is account-scoped, so a copy of this tooling into another project may have no such type:
the key is optional, an empty value is a declaration, and `tools/check-portability.sh` sweeps the declared value like
every other role.

surface: 1 role key, 2 writers with 2 flags each, 1 read helper, 1 reader verdict, 3 cases (2 extended, 1 new), 1 rule
section and its route — `tools/look.sh 'containerType' --path tools` names 5 files, `tools/look.sh 'issueTypes'
--path tools` the four that speak to the tracker, and the suites' `--list` counts the three cases.
