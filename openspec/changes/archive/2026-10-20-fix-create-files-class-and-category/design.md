# fix-create-files-class-and-category — design

## The root cause

`tools/issues/create.sh` reads a finding's `class` and `category`, refuses a finding that omits either
(`:78-79` at base `db1fe8e`), and then composes the label set from `.labels[]`, `--label` and `--status` only
(`:109-118`). The two required fields are used for the fingerprint (`:165`) and for nothing else, so the one
script that insists on the item's kind is the one script that never writes it. The defect is invisible on the
path the tooling's own checks drive, because the sensor path spells both labels into `.labels[]` itself
(`tools/sensors/run.sh:218-219`); it is reachable exactly by the caller the issue records — a person filing a
finding whose `class` and `category` sit in the fields where they belong (#199, #200, `"labels": []` at
creation).

## The single fix

After every other label route has contributed, the finding's fields join the set:

```
labels+=("class${separator}${clazz}" "category${separator}${category}")
```

and the merged set is de-duplicated by exact label before the existing gates run. The three properties come for
free from the machinery already in the file, which is why this is one line plus a de-duplication rather than a
new rule:

- **one label per value.** The exact-duplicate drop keeps the sensor path and the fixtures — where `.labels[]`
  already names `class/<row class>` — at one label each, so no caller's set grows.
- **a contradiction is a refusal.** A field naming a different value than `.labels[]` leaves two distinct
  strings in the set, and the namespace-uniqueness loop (`:151-159`) refuses with exit 2, naming both — the
  same refusal `--status` already gets. Nothing is resolved in the writer's favour.
- **a value outside the closed vocabulary is refused.** The fields now pass `issues_check_labels` like any
  other label, so a misspelt `class` exits 3 `label-opaque` instead of vanishing.

## Blast radius

`tools/issues/create.sh` only, plus its cases and mutation set. The writer's output for a finding that already
spelled both labels is byte-identical to what it was; its output for a finding that did not gains two `--label`
arguments; its exit code changes from 0 to 2 or 3 for the two kinds of finding it used to file wrongly. Every
in-tree caller (`tools/issues/import.sh`, `tools/sensors/run.sh`, the fixtures, `tools/fixtures/foreign-project`)
already passes both fields with matching labels, so none of them changes behaviour — confirmed by the whole
suite, not by inspection: 102 of 102 cases pass, including the sensor, portability, import and duplicate paths.

## Rollback

Revert the commit. No tracker state is written by this change by itself, so there is nothing to undo beyond the
code: a rollback restores the defect (an unlabelled item for a finding whose kind lives only in the two required
fields) and nothing else.

## Alternatives the evidence eliminated

- **"Keep the label list as it is and refuse a finding whose `.labels[]` disagrees with its fields."** This is
  the `#179` refusal, and it does not reach this defect: #199 and #200 had **no disagreement to detect** — their
  `labels` arrays were empty. A rule that only refuses conflicts leaves every finding that states its kind in
  the fields alone filing an unlabelled item, which is the observed symptom.
- **"Make `.labels[]` the only required route and stop requiring `class` and `category`."** The two fields are
  the fingerprint's input (`:165`), so dropping them changes every item's identity: the dedup anchor (I3) would
  move, and #199/#200's re-filing would no longer be recognised as a repeat of themselves. `import.sh` and
  `tools/sensors/run.sh` also build findings from those fields, so the writer would be the only place that
  disagreed with its producers.
- **"Add the tracker-wide assertion the issue sketches to `tools/issues/check-metadata.sh` (`--live`)."** Two
  facts eliminate it as part of this fix. First, the metadata pass's own input is an item with no kind:
  `tools/issues/fixtures/metadata/ok.json` carries an unclassified, unranked issue as a *valid* state and
  `tools/issues/classify.sh` exists to select exactly those, so "every item carries one `class/*`" would report
  the pass's input as a violation. Second, CI runs `check-metadata.sh` with no argument (the token-free self
  mode), so a `--live`-only assertion would not run where the defect happened; what does run there is the filing
  path's own gate, whose cases and mutation now assert the invariant at the point of failure.
- **"Have the writer add `.labels[]` entries to the finding before hashing."** The fingerprint would then differ
  for two findings that state their kind in different places, so the same finding filed by the sensor path and
  by a person would no longer dedupe against itself (I3). The label join must stay downstream of the hash.

## Verification

- `create#carriesTheFindingsClassAndCategory` and `create#refusesAClassItsLabelsContradict` — red on `db1fe8e`,
  green after the fix.
- `bash tools/issue-tooling-selftest.sh` — the gate for `tools/issues/**`, 102 of 102, offline, no token, no
  Gradle (no compiled file is touched, `guidelines/Build.md` §2 scope block).
- Mutation `1.3` removes the join; the named case must go red and the restore green
  (`.pi/skills/revert-check`), together with the set's existing `1.1` and `1.2`.
- No threading, lifecycle or on-device surface: this is a shell writer with no runtime component
  (`guidelines/Design.md` §4 does not apply).
