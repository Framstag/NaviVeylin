> **Status of this document.** The independent reviewer's verdict on the change as it stood before the fixes
> below: a fresh, read-only agent process that read the artifacts and the diff and re-ran the suites (it wrote
> no file of the change). Nothing here was edited afterwards.
>
> Fixed since, in this change, each with a case:
>
> - **P2-2** — the prefix memoization was dead and silently separator-less before the resolve. The prefix is
>   now a parameter of `issues_status_displaced` (one profile read per writer, none inside the filter), and
>   `issues_status_prefix` refuses to answer before `issues_resolve`. Case
>   `lib#statusPrefixNeedsTheProfile`.
> - **P2-3** — `--json` printed nothing when a state write added and removed nothing. Both arrays default to
>   `[]`. Case `update#statusWriteReportsAnEmptyTransition`.
> - **P2-5** — `grep -q` inside a `pipefail` pipeline could flip the idempotency check. The label list is
>   captured into a variable first.
> - **P2-6** — `.pi/skills/update-issue/SKILL.md` now states the replacement, the exit-2 refusal and
>   `removedLabels`.
> - **P2-4** — narrowed, not changed: design D4's wording now claims the state namespace only, which is what
>   the code does.
>
> **P1 — fixed, owner-approved into this change** (task group 7): `update.sh` now folds a state named as the
> namespaced label into the value spelling, so `sensors/run.sh`'s escalation replaces the item's state; the
> pinned sensor assertion was strengthened and shown red against the pre-fix `update.sh`; a fifth spec scenario
> (`update#stateNamedAsALabelReplacesTheOldState`), decision D8 and mutation record `7.1` came with it.
>
> **Still open, filed as its own item:** **P2-1** (`create.sh --status` can file an item beside the state its
> labels already carry — a different lifecycle moment and a different fix from P1's, so it is worked on its own
> rather than folded in here).

VERDICT: FAIL

Read-only review of the uncommitted change `fix-status-label-replacement` (`git diff` of
`.github/workflows/build.yml`, `guidelines/Backlog.md`, `tools/issue-tooling-cases.sh`,
`tools/issues/lib.sh`, `tools/issues/reopen.sh`, `tools/issues/update.sh`, `tools/loop-cases.sh`, plus the
untracked `tools/status-mutations.json`) against the delta spec and the eight questions. No file was
modified. Suites re-run for this review, all green: issue-tooling 73, resolve-stale 20, sensor 36, loop 53,
check-portability 21; the tracker-wide dual-state query returns none.

## Q1 — the four delta scenarios: all four hold in the code

- `update#statusReplacesTheOldStatus` — holds. `tools/issues/update.sh:130-133` fills `implied_removals`
  from `issues_status_displaced "$issue" "$status_label"`, and `tools/issues/update.sh:143-145` appends one
  `--remove-label` per displaced label to the same `args` array the add goes into
  (`update.sh:136-138`), so one `gh issue edit` carries both. Measured write for an item carrying
  `status/open`: `gh issue edit 11 --add-label status/fixed-by --remove-label status/open`. Case passes
  (`bash tools/issue-tooling-selftest.sh --case update#statusReplacesTheOldStatus` → PASS).
- `update#statusReplacementIsIdempotent` — holds. `tools/issues/lib.sh:211`
  (`select(startswith($prefix)) | select(. != $target)`) keeps the target out of the removals, and
  `tools/issues/update.sh:120-129` drops the target from `add_labels` when `issues_labels_of "$issue"` shows
  it is already there, so no label is added or removed. Case PASS; the second half of the case also shows
  `--label category/car` still written while `status/open` is not re-added.
- `update#statusRejectsTwoStatesInOneCall` — holds. `tools/issues/update.sh:74-88` runs the two
  contradiction loops immediately after `issues_resolve`, i.e. before the vocabulary gate (`:105-112`), before
  the read (`:117`) and before any write (`:170`, `:173`), and refuses with `issues_die`
  (`lib.sh:41-44` → exit 2). Measured: `update.sh 11 --status open-device --label status/on-hold` → exit 2,
  `… name two states — a state write sets one`; `--status on-hold --remove-label status/on-hold` → exit 2,
  `… removes the state it sets`; no `gh issue edit` recorded. Case PASS.
- `update#statusReportNamesImpliedRemoval` — holds. `tools/issues/update.sh:158-164` builds `removedLabels`
  from `remove_labels` **and** `implied_removals`. Measured `update.sh 12 --status fixed-by --json` →
  `"removedLabels": ["status/open"]` for a label the caller never named. Case PASS.

## Q2 — other writers: every `--status` caller is fixed, one `--label` writer is not

- Fixed, verified by reading the write they produce: `tools/loop/labels.sh:108` (every loop verdict reaches
  `update.sh … --status`), `tools/issues/resolve-stale.sh:230` (`--status on-hold` parking),
  `tools/sensors/run.sh:258-266` (reopen → `reopen.sh --status`, whose replacement is
  `tools/issues/reopen.sh:99-114`).
- Call sites that write no status label and are therefore unaffected: `tools/issues/prioritise.sh:343`,
  `tools/issues/classify.sh:191`, `tools/sensors/run.sh:238/247/282` (body/comment/escalation),
  `tools/loop/labels.sh` `pick`/`unpick`.
- **Not fixed — `tools/sensors/run.sh:270-283` (escalation).** It reads the registry's
  `escalation.labels` (`tools/sensors/sensors.json:9-13`) — `["agent/needs-decision", "status/on-hold"]` —
  and passes every one of them as `--label` (`run.sh:277-280`), never as `--status`. `update.sh`'s
  replacement is gated on `--status` alone (`update.sh:117`), so the write stays additive. The escalated
  item is a *closed* sensor item, which still carries `status/open`: filed labels come from the registry's
  `.labels` (`sensors.json:5-8` → `run.sh:182-191`), and `sensors_close` (`run.sh:250-260`) calls only
  `close.sh`, which writes no status label at all. So escalation produces `status/open` + `status/on-hold`
  — the exact defect this change exists to remove. See Finding P1 for the reproduction.
- A second writer outside `update.sh`: `tools/issues/create.sh:83-91` merges the finding's own `labels`,
  `--label` and `--status` with no contradiction or replacement rule, so `create.sh --status open` on a
  finding whose labels carry `status/fixed-by` creates an item with two states. See Finding P2-1.
  (The migration path is safe: `tools/issues/import.sh:74` calls `create.sh` without `--status`.)

## Q3 — the diff-only rule (D4)

- True for the status namespace: the target can never be a removal (`lib.sh:211`), it is removed from
  `add_labels` when the item already carries it (`update.sh:120-129`), and `--remove-label <target>` beside
  `--status <target>` is refused (`update.sh:85-88`). No `gh issue edit` from a `--status` call can carry
  `--add-label X` and `--remove-label X` for the same X.
- False as D4 words it ("never an `--add-label` and `--remove-label` for the same label in one edit") for
  *non*-status labels, and a displaced label named explicitly is removed twice. Both are pre-existing, not
  introduced by the diff — Finding P2-4 has the recorded writes.

## Q4 — invariant I16 (`guidelines/Backlog.md:135`)

- The "held by" half is true of the two writers it names: the read + single-edit replacement is
  `update.sh:117-145`, the one shared scan is `lib.sh:207-212` (used by `update.sh:133` and
  `reopen.sh:103`), and the named case `update#statusReplacesTheOldStatus` exists and passes.
- The invariant itself — "one item carries exactly one value of the state namespace" — is **false of the
  code** as landed: `tools/sensors/run.sh:270-283` still adds a state label without removing the one it
  displaces (P1), so the row's own falsifying observation ("an item carries two `status/*` labels") is
  reachable by one escalation run. The row's measurement does not cover that writer, which is why the row
  reads green.
- The tracker itself is clean as measured: `gh issue list --state all --limit 400 --json number,labels` +
  the `status/` filter → `OK: no item carries two status labels`; #109 and #110 both now show
  `["status/on-hold"]` only, as `evidence/revert-check.md` claims.

## Q5 — portability (`tools/issues/lib.sh` is an engine)

- Passes. `bash tools/check-portability.sh --quiet` →
  `check-portability: OK — 42 engines and 2 templates name no project value (133 value(s) read from the project)` (exit 0).
- The only new literal is the namespace *name*: `tools/issues/lib.sh:187` `ISSUES_STATUS_NAMESPACE="status"`,
  which `tools/check-portability.sh:136-147` deliberately reads as the contract ("the namespace *names* stay
  the contract, so they are read and not forbidden"); the namespace's *values* are added to the forbidden
  list from `tools/tooling.json` + the registry, and the one status value this project writes,
  `status/on-hold`, stays out of every engine (it lives in `sensors.json:12`). No repository name, author or
  path was added.

## Q6 — `issues_status_prefix` memoization under `set -u`

- Safe under `set -u`: `tools/issues/lib.sh:192` guards with `${ISSUES_STATUS_PREFIX:-}`, so an unset global
  is not an unbound-variable error.
- No caller can reach it before `issues_resolve`: `update.sh:65` then `:68`/`:76`, `reopen.sh:60` then `:71`;
  `issues_status_displaced` is only called from `update.sh:133` and `reopen.sh:103`, both after the resolve.
- **But the memoization never takes effect** and the failure mode is silent — Finding P2-2. Both call sites
  read it through a command substitution, so the assignment dies with the subshell; measured: three profile
  reads (JVM starts) per state write. And a caller that did reach it unresolved gets `status` without the
  separator, because `issues_core`'s death inside `$( )` is swallowed (no `set -e`).

## Q7 — the new CI line

- It parses: `bash tools/mutate.sh --mutations tools/status-mutations.json --list` exits 0 and prints
  `# tools/status-mutations.json — 4 mutation(s), case command: bash tools/issue-tooling-selftest.sh --case {case}`
  plus the four labels; the file is valid JSON and every `search` is a single line.
- It is in the right step: `.github/workflows/build.yml:151-160` is the "Check issue tooling" step, the one
  that owns `tools/issues/**`, and it matches the precedent for the other two sets — `:234-236`
  (`portability-mutations.json` beside the check-portability step) and `:245-246`
  (`resolve-stale-mutations.json` beside the resolve-stale step). `build.yml` parses as YAML.

## Q8 — places that still carry the old additive behaviour

- `tools/sensor-cases.sh:898-901` — asserts the escalation's label write is exactly
  `--add-label agent/needs-decision --add-label status/on-hold`, i.e. it *pins* the additive status write
  and would go red at the moment P1 is fixed. This is the assertion of the old behaviour, and the reason
  `sensor-selftest` is green despite the hole.
- `.pi/skills/update-issue/SKILL.md:49` — "`update.sh` writes the label; it does not decide the transition."
  After the diff `update.sh` does decide what it removes, and the skill states neither the replacement nor
  the new exit-2 refusal; no skill or guideline line was updated by the change (Finding P2-6).
- `.pi/skills/cleanup-todo/SKILL.md:150-152` — `tools/issues/update.sh --issue <n> --class bug …` and
  `--status fixed-by --value '<change>'`; `update.sh` takes the number positionally and has no `--class` or
  `--value`, so the documented repair exits 2 (`unknown argument --issue`). Pre-existing, not additive-behaviour
  wording, reported because the question greps `--status` in `.pi/skills/`.
- Nothing stale in `guidelines/` or `PORTING.md`: `guidelines/Backlog.md:148` (resolver row, "`status/on-hold`
  to park an item") is consistent with the new replacing behaviour, and `PORTING.md:69` only names the
  namespace.

## Findings

- P1 — a state write expressed as a label still leaves two states. `tools/sensors/run.sh:277-282` +
  `tools/sensors/sensors.json:9-13`; the replacement is `--status`-only (`tools/issues/update.sh:117`), so
  the escalation adds `status/on-hold` onto a closed sensor item that still carries `status/open`.
  Evidence: recorded write below; `tools/sensor-cases.sh:898-901` pins that add-only shape. This falsifies
  `guidelines/Backlog.md:135` (I16) and the delta requirement "A state transition replaces the item's state
  label" as written. Latent in the tracker today (no `sensor/`-labelled item exists yet:
  `gh issue list --all | jq 'select(sensor/)'` → none, and the dual-state query is empty), but it is an
  automated, human-free path.
  Smallest fix, in the spirit of D1/D3 (one place, no new writer semantics): in `update.sh`'s flag
  translation, treat a `--label` inside the status namespace as the state target when `--status` is absent —
  reuse `issues_status_prefix`, then the existing read/remove path applies — and add one sensor case
  asserting an escalated item ends with exactly one `status/*` label (the exact-set assertion at
  `sensor-cases.sh:898-901` then names the displaced label, as `loop-cases.sh:693-698` already does for the
  loop). Alternative, if the registry should own it: give `sensors.json` an explicit
  `escalation.status: "on-hold"` and pass it as `--status` from `run.sh:277-282`.
  Reproduce:
  ```
  f=$PWD/tools/issues/fixtures; tmp=$(mktemp -d); cp "$f/login.txt" "$f/repo.txt" "$f/labels.json" "$tmp/"
  jq -n '[{number:11,title:"t",state:"OPEN",stateReason:"",body:"",comments:[],author:{login:"Framstag"},
          labels:[{name:"status/open"},{name:"sensor/fixture-emit"}]}]' > "$tmp/issues.json"
  PATH="$f:$PATH" GH_STUB_DIR=$tmp GH_STUB_RECORD=$tmp/log \
    bash tools/issues/update.sh 11 --label agent/needs-decision --label status/on-hold
  cat "$tmp/log"   # → gh issue edit 11 --add-label agent/needs-decision --add-label status/on-hold
  ```
- P2-1 — `create.sh` can create an item with two `status/*` labels. `tools/issues/create.sh:83-91` merges
  the finding's `labels`, `--label` and `--status` into one list and only gates vocabulary/reserved labels;
  there is no contradiction rule and no replacement (nothing to replace at creation, but the finding may
  already carry a state). The delta requirement is phrased over "a call that sets an item's lifecycle
  state", and `create.sh --status` is such a call. Pre-existing; no automated caller combines the two today
  (`import.sh:74` passes no `--status`; the sensor filing path carries `status/open` and passes no
  `--status`). Smallest fix: the same two contradiction checks `update.sh:74-88` now has, applied to
  `create.sh`'s merged list (refuse when the list names more than one value of a namespace).
  Reproduce:
  ```
  f=$PWD/tools/issues/fixtures; tmp=$(mktemp -d); cp "$f/login.txt" "$f/repo.txt" "$f/labels.json" "$tmp/"
  printf '[]\n' > "$tmp/issues.json"
  printf '{"sourceId":"903","title":"t","class":"bug","category":"ui","claim":"c","anchor":"a","body":"b",
           "labels":["class/bug","category/ui","status/fixed-by"]}' > "$tmp/f.json"
  PATH="$f:$PATH" GH_STUB_DIR=$tmp GH_STUB_RECORD=$tmp/log \
    bash tools/issues/create.sh --finding "$tmp/f.json" --status open
  cat "$tmp/log"   # → … --label status/fixed-by --label status/open
  ```
- P2-2 — `issues_status_prefix`'s memoization is dead and is silent when used before the resolve.
  `tools/issues/lib.sh:191-196`: both call sites (`update.sh:68`, `update.sh:76`, `update.sh:133` →
  `lib.sh:209`, `reopen.sh:71`) read the function through `$( )`, so the assignment at `lib.sh:193` happens
  in a subshell and never reaches the parent — three profile reads (each a JVM start) per state write,
  contradicting the comment "Resolved once per process". If a caller ever invokes it before
  `issues_resolve`, `issues_profile_value` dies inside the substitution and the prefix silently becomes
  `status` (no separator) instead of failing. One-line fix: assign `ISSUES_STATUS_PREFIX` once in each
  writer right after `issues_resolve` (`ISSUES_STATUS_PREFIX="$(issues_status_prefix)"`), and make
  `issues_status_prefix` die when `ISSUES_PROFILE` is empty.
  Reproduce: `bash -c 'source tools/issues/lib.sh; issues_status_prefix'` → `status` (no `/`, no error);
  `bash -x tools/issues/update.sh 15 --status fixed-by 2>&1 | grep -c 'issues_profile_value namespaceSeparator'` → `3`.
- P2-3 — `--json` produces no document when nothing is removed, which includes the new idempotent state
  path. `tools/issues/update.sh:158-164`: `removed_json` stays the empty string when both `remove_labels` and
  `implied_removals` are empty, and `jq -n --argjson removed ""` then fails while the script still exits 0 —
  so a caller reading `removedLabels` (D6's whole point, and the `--json` repair the change's own 6.1
  evidence shows) gets a jq error on stderr and no JSON. Pre-existing shape, newly reachable as a
  *legitimate* outcome (a state write that displaces nothing: `update#statusReplacementIsIdempotent` is
  never run with `--json`). Fix: initialise `label_json='[]'` and `removed_json='[]'` before the `if`s.
  Reproduce: issue 16 carrying only `status/open` →
  `bash tools/issues/update.sh 16 --status open --json` → `jq: invalid JSON text passed to --argjson`,
  empty stdout, exit 0.
- P2-4 — D4's blanket wording is wider than the code, and an explicitly named displaced label is removed
  twice. `update.sh` cannot emit add+remove of one *status* label, but it does for any other label
  (`--label category/ui --remove-label category/ui` → `gh issue edit 13 --add-label category/ui
  --remove-label category/ui`), and `--status fixed-by --remove-label status/open` on an item carrying
  `status/open` records `--remove-label status/open` twice and reports `"removedLabels": ["status/open",
  "status/open"]`. Both pre-existing; the first is worth narrowing in D4's wording, the second in the dedupe
  before `args` is built.
- P2-5 — `grep -Fxq` inside a `pipefail` pipeline is unsound and measurably flips the idempotency check.
  `tools/issues/update.sh:120` (`issues_labels_of "$issue" | grep -Fxq -- "$status_label"`) under
  `set -uo pipefail` (`update.sh:27`): once `grep -q` exits on the match, `jq` can die of SIGPIPE and the
  pipeline status becomes non-zero, so the target is treated as absent and re-added. Measured: an item
  carrying `status/open` plus 100 labels → `update: #11 already carries status/open — no label write`; the
  same item with 3 000 labels → `gh issue edit 11 --add-label status/open` recorded. Not reachable with
  GitHub's 100-label cap, so report-only; the one-line fix is to capture
  `labels_now=$(issues_labels_of "$issue")` and `grep -Fxq -- "$status_label" <<< "$labels_now"`.
- P2-6 — documentation drift on the new contract. `.pi/skills/update-issue/SKILL.md:49` still says
  `update.sh` "does not decide the transition", and neither that skill nor `.pi/skills/cleanup-todo/SKILL.md`
  states that `--status` replaces the old value, that the displaced label is reported in `removedLabels`, or
  that naming two states is an exit-2 refusal — the three things the diff changes for a caller. Smallest
  fix: two sentences in `update-issue/SKILL.md` under "The call" and one row in its transition table.

## Merge verdict

BLOCK — but narrowly. The diff itself is correct, minimal and self-consistent: all four delta scenarios hold
with source and case evidence, all five suites are green, the portability engine check passes, the CI line
parses and sits in the right step, the tracker is clean (#109/#110 carry one state each), and the mutation
set is real (`--list` green, every search unique). What blocks is P1: the change declares I16 as an absolute
invariant ("one item carries exactly one value of the state namespace") while one automated writer —
`tools/sensors/run.sh`'s escalation, via `--label status/on-hold` — still produces the two-state item this
change was written to make impossible, and `tools/sensor-cases.sh:898-901` currently pins that additive
write as correct. Closing it is one branch in `update.sh` plus one sensor case; P2-1 (`create.sh`) is the
same class of gap at the other end of an item's life and can follow in the same edit or a separate one.
