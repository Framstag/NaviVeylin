# Design — fix-routing-failure-reporting-classification

## D1 — The three values of the classification entry

`"routing-failure-reporting": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] }`

- **`area: "routing"`** — the capability is how a failed route calculation is reported, and it sits beside the
  other `routing` entries its predecessor change `distinguish-routing-failures` modified
  (`route-calculation-feedback`, `route-panel-ui`, `navigation-engine` — that change's other two *modified* deltas
  classify elsewhere: `i18n-l10n` under `device-fit`, `osmscout-jni` under `build-tooling`; its sixth delta is
  this capability itself, `routing-failure-reporting`, added new and also in `routing`). Every area id referenced
  must exist in the classification's own area list, and `routing` is one of them. This change itself adds no spec
  delta, so it adds no area either.

- **`userVisible: true`** — the change's whole point is that the user is told *why* a calculation failed
  instead of one sentence for ten causes, and that a reason with an action offers it (`guidelines/UI.md` §1's
  parity row for this capability). This is not an internal contract like `osmscout-jni` or `navigation-symbols`
  (`userVisible: false`): it is text the driver reads on both surfaces.

- **`surfaces: ["phone", "car"]`** — the change's own proposal states the scope as both variants: the reason
  reaches the phone's route panel and its navigation snackbar, and the car's error notice, with the wording
  shared from `:core` and localized. `navigation-status-details` (phone-only) and `auto/navigation-view`
  (car-only) are the neighbouring single-surface entries; this capability is deliberately neither — a
  deliberate parity claim, which is what `guidelines/UI.md` §1 records.

The alternative readings the evidence eliminates:

- `userVisible: false` because "a failure notice is not a feature" — contradicted by the capability's own
  requirement text: the reason has per-reason user-actionable wording and may offer an action, so a user reads
  it and can act on it.
- `surfaces: ["phone"]` — contradicted by the change's car half (`FailureNoticeText`, the car error notice,
  `car_failure_maps_on_phone` standing in for the action the platform forbids the car to take). The car sees
  the same reason, worded from the same shared resource.

## D2 — No highlight, so the catalogue does not move

Classification and publication are separate judgements. `guidelines/FeatureList.md` §9 makes the classification
line owed by the archiving change, and the *highlight* an editorial decision taken "when what it ships is worth
telling a user about". This change pays the owed line only: no entry is added to
`tools/feature-list/highlights.json`, no feature area becomes dirty and no prose is regenerated. The coverage
gate measures highlights rather than specs, so the line alone cannot leave a red run behind.

## D3 — Why the CI step belongs in this change

`TODO.md` §157 names two fix candidates and states that nothing enforces the classification rule at the moment
it matters. Its first candidate is "a model-free CI check (the tool already has `--check-classification`, which
exits 1 and names the id) run on every pull request". Three instances of the same defect have now been recorded
(this one, `routing-max-speed`, and §157's own `generate-feature-list` instance), each fixed by hand a change
late. Landing the check in the same change that pays the third instance is what turns the rule from a
convention into a gate, and it is the cheapest item the repository can hold: two files read, no Gradle, no
model, exit status only.

The step is placed beside the existing OpenSpec artifact-hygiene step, so CI's "structural checks" group holds
both cheap data checks together. It is green on this tree after D1 (`classified: 167 / unclassified: 0`), so it
does not turn the workflow red on landing. `TODO.md` §157 stays `open`: its second candidate — running the same
check inside the archive step before `changeRoot` is moved — is a change to the archiving workflow, and this
change does not modify the skills.

## D4 — Verification shape, and what stands in for the revert-check

Nothing compiled changes: `specs.json` is data read by a shell tool, and the workflow file is CI configuration.
Per `guidelines/Build.md` §2 ("Scope — what the changed files owe"), a delta that touches no compiled file owes
no build and no suite; the affected case of the change's declared scenario and the classification gate's exit
status are the verification instead.

The **revert-check is the red run itself**: the fix is the presence of one key, so mutating it is deleting the
line, and `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml` (with
`evidence/classification-gate-red-on-HEAD.txt`) is that mutation's recorded result — the case fails for the
right reason, naming `routing-failure-reporting`, and the gate exits 1. Restoring the line is the green pair in
`evidence/`. Re-running the mutation would reproduce the same XML with a later timestamp and no new
information, which is why the change cites the red pair rather than a third run.
