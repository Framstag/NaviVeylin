# Design — fix-classification-before-archive

## D1 — The root cause, in one mechanism

The gate's view of "which ids must be classified" was `find "$specs_dir" -name spec.md`, and `specs_dir` is
`openspec/specs/` (`tools/gen-feature-list.sh`). A delta spec lives under `openspec/changes/<change>/specs/`
until the archive moves it. So the gate could only ever see a capability after the move — and the move is the one
step that was never gated. That single line explains all three recorded instances, the "one line in a different
change's file" fix, and the loop of `--check-classification` being green while the repository was one archive away
from red.

## D2 — The single fix

Read the delta specs of open changes into the same gate:

```bash
changes_dir="$root/openspec/changes"
delta_origins=$(find "$changes_dir" -mindepth 3 -name spec.md -not -path "$changes_dir/archive/*" | sed ...)
required_ids=$(printf '%s\n%s\n' "$shipped_ids" "$delta_ids" | sed '/^$/d' | sort -u)
unclassified=$(comm -23 <(printf '%s\n' "$required_ids") <(printf '%s\n' "$classified_ids"))
```

- `unclassified` is measured over shipped ∪ open-delta, so an unclassified capability fails while its change is
  open — before the archive, which is the moment §157's second candidate wanted the check at.
- `stale` stays measured over the shipped set alone, which is why a line added ahead of the archive is reported
  (the capability is not shipped yet) and does not fail — the rule `guidelines/FeatureList.md` §4 already states for
  a removed id. No new failure mode is introduced for a classification entry that is simply early.
- The message names the id **and** the open change, because "add a line" without the file it belongs to is the
  three-change delay this item is about.

## D3 — Why the versioned archive step is not edited

`TODO.md` §157's second candidate says "the archive step". The archive step is
`.pi/skills/openspec-archive-change/SKILL.md`, which the OpenSpec CLI **generates** (`generatedBy: 1.14.3`) and
which `.gitignore`d `.pi/` state does not version — `AGENTS.md` "Agent iteration loop" states the split. Editing it
would be lost on the next CLI upgrade and would not exist in a fresh clone. The gate therefore moves the *check's
view* one call earlier instead of moving the *call* into a generated file: every path an archive takes — the loop's
`gate.sh` (profile), the CI step, and any agent running the documented `--check-classification` — now sees the
delta. `guidelines/FeatureList.md` §4 states the rule where a change author reads it.

## D4 — Alternatives the evidence eliminated

1. **Run `--check-classification` from `openspec/changes/*/tasks.md`'s verification or from the archive guidance in
   `openspec/config.yaml`.** Not eliminated as harmful, but eliminated as *this* fix: it is a rule a model is asked
   to follow, and the item's whole finding is that a rule without a check at the moment of the move did not hold
   three times. A prose line cannot be a red test, and gate 4 requires a falsifiable WHEN/THEN that is false today.
2. **Classify on the shipped set but fail the archive by moving the check into the CI artifact-hygiene step.**
   Eliminated: that step already runs after the move — it is what named the last three instances, three changes
   late. Nothing about its placement can see a delta.
3. **Make `specs.json` derive its entries from the specs instead of naming them.** Eliminated: the classification is
   the one *human judgement* in the pipeline (`guidelines/FeatureList.md` §4) — area, visibility and surfaces cannot
   be derived from the text, and a derived entry would publish an unclassified capability silently.
4. **Edit the generated archive skill.** Eliminated by D3: unversioned, regenerated on upgrade.
5. **Keep the tool's suite unreachable and rely on the CI classification step alone.** Eliminated: the new behaviour
   would have no runner. `tools/gen-feature-list-selftest.sh` ran in no CI step and no loop suite before this change
   (greps in `evidence/surface.txt`), so the case set that pins the widened gate would be a test nobody starts — the
   same defect one level down.

## Blast radius

- `tools/gen-feature-list.sh`: the classification gate's input set and its failure message; the run-mode report is
  unchanged except the same widened count.
- `tools/gen-feature-list-selftest.sh`: 7 new assertions, 1 existing fixture realigned, a gate-shaped summary line.
- `tools/loop/loop.conf`: three `<glob>|<command>` pairs — `tools/gen-feature-list*.sh`, `tools/feature-list/*`
  (the suite) and `openspec/*` (the classification gate). The engine (`gate.sh`) is untouched; the pairs are the
  project's own mapping, which is what that key exists for.
- `.github/workflows/build.yml`: the classification step's comment and error message; one new step running the
  suite.
- `guidelines/FeatureList.md`, `README.md`: the rule and its consequence.
- No compiled file, so no Gradle task, no ABI, no device, no coverage figure moves. The gate a change of this
  pathspec owes is `tools/gen-feature-list-selftest.sh`, `tools/loop-selftest.sh`, `tools/check-doc-routes.sh`,
  `tools/check-action-runtimes-selftest.sh` and the classification gate itself — all offline.

## Verification

- `tools/gen-feature-list-selftest.sh` — the 3 red cases pass, and the 4 green-pair cases (`gate: an archived
  change's delta is not read`, `gate: a classified delta passes before it ships`, `gate: the delta-only
  classification leaves nothing unclassified`, `gate: a delta-only classification is reported as stale`) hold.
- `bash tools/gen-feature-list.sh --check-classification` on this tree: exit 0 with `delta spec ids: 1` (this
  change's own delta, whose capability `spec-feature-index` is already classified).
- The **falsification** (the revert-check this change owes for the new invariant): delete
  `"spec-feature-index"`'s line from `tools/feature-list/specs.json` and the gate must fail naming it from the
  *delta* — `evidence/mutation-delta-id.txt` records that run and the restored green pair.
- `tools/loop-selftest.sh` (the profile's mapping is asserted by its own cases) and
  `tools/check-action-runtimes-selftest.sh` (the workflow file), `tools/check-doc-routes.sh` (the guideline route).
- One gate run, `./tools/loop/gate.sh`, quoted in `evidence/gate.log`.
