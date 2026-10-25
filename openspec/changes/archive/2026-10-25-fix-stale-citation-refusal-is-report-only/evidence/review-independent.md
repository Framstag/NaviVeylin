Read both diffs, artifacts, shipped spec, and the surrounding consumers. Findings below, by your five questions. No files edited.

## 1. Consumers still treating `absent` as a refusal/park; now-unreachable behaviour

- **`tools/issues/create.sh:84-89`** — the only surviving refusal driven by `absent`: filing-time `issues_die` for the doubled-directory shape. It still says a citation the pre-screen "cannot resolve costs the item every run — it is excluded as `stale`". The behavioural guard is intentional; the sentence is false post-#281.
- **`tools/loop/labels.sh:87`** — `stale|unspecifiable|…` still parks (`status/on-hold`). Reachable via the worker's `stale` sub-verdict (`run.sh:1703` passes `skip_verdict`), *not* via the pre-screen. Correct behaviour; but see Q5 for the header comment that claims this row is gone.
- `tools/loop/prescreen.sh:102,105,381-385` keep reading `refusal_stale` only to clear a historical `agent/excluded-stale`; `clear-stale` in `labels.sh:98` is still reachable. No new dead code found.
- Nothing else reads the removed `.parked` counter — `tools/sensors/metrics/stale-citations.sh:32` correctly reads `.unresolved + .stillParked`; no other consumer of the resolver JSON exists.

## 2. Removed invariant and its replacement

- **The cheap pre-worker refutation is gone and its claimed replacement is inert.** `tools/loop/prescreen.sh:313-314` says "The symbol sensor below still refuses", but the loop at `:319-329` only assigns `evidence`, never `verdict` (same as before the change). So no code path refuses an item over a citation or a non-existent symbol any more. The only remaining cheap verdicts are class/status/author and `names_the_submodule` (`:363`). This is a false comment and, if the symbol refusal was intended, a latent hole.
- Genuine stale citations are still **detected** (`issues_citation_class` → `absent` in `tools/issues/lib.sh`; `absentCitation` in `prescreen.sh:399`; `unresolved` in `resolve-stale.sh:428`), but by design never refuse or park. That part matches the owner's ruling.

## 3. Cases/mutations that no longer assert what their name claims

- Renamed cases (`resolve-stale-cases.sh`, `loop-cases.sh`) all assert report-only correctly; the three re-aimed mutations (5.7/5.8/5.9) each still kill their named case.
- **`tools/resolve-stale-cases.sh:209`** — `parked_tracker` always injects `agent/excluded-stale`, so **no case exercises `parked_by_tool`'s comment-only branch** (`resolve-stale.sh:151`), which is the *primary* historical park shape (pre-#281 `resolve-stale.sh` posted only the comment). `resolver#cleanParkedItemIsReopened` therefore does not prove the comment marker is what reopens an item.
- Minor: `resolve-stale-cases.sh` (`case_resolver_titleSuffixBesideItsCanonicalPathIsSettled`) message reads "is reported unresolved for nothing" while asserting `0`.
- Minor: `tools/resolve-stale-mutations.json` header says "The nine revert-checks … (tasks 5.1–5.9)" but ships 15; mutation 5.15's `why` still says "parks the item".

## 4. Shipped spec vs code disagreements

- **`openspec/specs/stale-citation-resolution/spec.md:205`** — scenario heading "A stale citation in the title is parked where it stands", while its THEN and its `Case:` (`resolver#titleAbsentCitationIsReportedWhereItStands`) both say *reported*. The delta changed the Case id but not the heading.
- **`spec.md:6-7`** — Purpose still says "a quarantine for the ones that cannot be repaired" and "an authorised item is excluded only when its citation really is gone". Both are removed behaviour.
- **`absentCitation` is dropped before the run's state**: `tools/loop/state.sh:325` projects the queue to `{issue, class, category, verdict, reason, rank, gates}`, and the loop invokes the pre-screen without `--json` (`run.sh:566`). So in a real run the token survives only in the console line `prescreen.sh:457`; `report.sh` no longer surfaces it. The spec scenario "the token is named in the item's record" is only true of the pre-screen's transient JSON.
- **`--revisit-parked` has no requirement or scenario in the shipped spec**, though it is new user-visible behaviour with a bound cost (`resolve-stale.sh:76,131-134,156-166`). The resolver counters `unresolved`/`stillParked` and the metric's `.unresolved + .stillParked` do match the code.
- Validator: `openspec validate stale-citation-resolution --type spec --strict` warns `requirements[0,1,3]` exceed 500 chars; the modified "One definition" body is 987 chars and the change added a sentence to it.

## 5. Docs still describing the removed behaviour

- **`.github/workflows/sensors.yml:117-121`** — comment: "parking what nothing accounts for — with the attempts recorded, so the item leaves the eligible queue visibly". And `:126` runs `resolve-stale.sh $SENSORS_MODE --max-items 25` **without `--revisit-parked`**, so the scheduled repair never reopens historical parks; the change's stated cleanup (`--revisit-parked`) has no automated writer, and the proposal's "Open" ("Nothing calls the resolver on a schedule in-repo") is contradicted by this very step.
- **`tools/tooling.json:38`** — "`tools/issues/resolve-stale.sh` reads `holdStatus` to park an item". It now reads it to recognise a historical park.
- **`tools/loop/labels.sh:19`** — "so there is no `stale` row here and no `exclude-stale`", but `:87` still has the `stale` row (needed for the worker sub-verdict).
- **`tools/loop/state.sh:326`** — still cites "`stale`'s path" as the canonical exclusion evidence.
- **`tools/issues/create.sh:85`** — see Q1.
- **`openspec/changes/archive/2026-10-25-fix-stale-citation-refusal-is-report-only/proposal.md:48`** claims "`tools/issues/create.sh`: … one point of the comment made explicit"; `create.sh` is absent from both diffs, so that task/claim is unmet.
- `PORTING.md`, `README.md`: no references to the removed behaviour (nothing to report).
- `guidelines/Backlog.md:149,220-224,581-590,700-704` and `AGENTS.md:48` are correctly updated.

## Additional gaps (ranked low)

- **`tools/issues/resolve-stale.sh:150-166`** — `reopen_item` unconditionally passes `--remove-label agent/excluded-stale`. For the comment-only park it calls `gh issue edit --remove-label` on a label the item does not carry; `update.sh:134-174` only validates the label against the vocabulary, never checks presence. If gh rejects an absent label, every comment-only reopen dies at `issues_die`. No case covers this (see Q3).
- **`openspec/changes/archive/2026-10-25-fix-stale-citation-refusal-is-report-only/tasks.md:30-31`** — two unchecked tasks in an *archived* change (5.1 full loop suite owed); the archive has no `design.md` (every peer archived change in that directory has one) and no `evidence/` even though `resolve-stale-mutations.json` references "this change's `evidence/revert-checks.md`".