# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- `tools/issues/create.sh:81-92` merges the finding's `.labels[]`, each `--label` and `--status <value>` (as
  `status<sep><value>`) into one flat `labels` array. The only guard over that array is
  `issues_check_labels` (`tools/issues/create.sh:99-108`), which asks the profile whether each value exists and
  is not reserved (`tools/issues/core/Policy.java:163-167`, `Taxonomy.known`). It never compares two labels.
- Every label the gate passes is a label in a namespace the profile **declares** (`Profile.namespaces()`,
  `tools/tooling.json` `namespaces`), because `Taxonomy.namespaceOf` returns null for an undeclared prefix and
  `known` then refuses it. So after the gate, the substring before the namespace separator *is* a declared
  namespace, and a shell prefix scan is exact — no second profile read (a JVM start) is needed.
- `update.sh` already carries the same invariant for the state namespace, as a *replacement*
  (`fix-status-label-replacement`): a `--status` write reads the item and removes every displaced value. The
  filing path has nothing to displace, so the invariant takes the other form — a refusal.
- `tools/issues/fixtures/gh` is the offline stand-in for `gh`. It records `issue create`/`edit` writes and
  returns a plausible result. It does not model GitHub's own label-contradiction behaviour — because there is
  none.
- This is shell tooling: no dispatcher, no lifecycle, no native boundary. The "threading model" is one process
  per call, and nothing here may block an Android thread because nothing here runs on one.

## Goals / Non-Goals

**Goals**

- One filing is one label set with at most one value per namespace: a set that names two values of one
  namespace is refused before `gh issue create`, with exit 2 and a reason naming both.
- The rule holds for every namespace the profile declares, not the state namespace alone.
- The behaviour is proven offline: a named case that is red on HEAD, and a mutation of the guard that makes it
  red again.

**Non-Goals**

- No change to `update.sh`'s replacement path, to the reserved-label gate, or to any caller
  (`tools/loop/labels.sh`, `import.sh`, `tools/sensors/run.sh`).
- No strictifying of the stub `gh` beyond what the real client does (see D4).
- No periodic dual-state check; that is a report, not this write path's fix.

## Decisions

**D1 — The refusal lives in `create.sh`'s label merge, not in a caller and not in the profile gate.**
The root cause is the flat merge with no cross-label rule, so the guard belongs where the merge happens, before
any `gh issue create`. Alternatives the root-cause evidence eliminates: extend `Policy labels`
(`issues_check_labels`) with the rule — impossible as the fix, because that gate is shared by `update.sh`, which
names two *valid* values of one namespace on purpose (a `--label status/x` beside a displaced one is removed, not
refused), so a global rule there would break the replacement path; a per-caller fix in `import.sh` or
`tools/sensors/run.sh` — impossible, because they are two of the callers and neither knows the merged set
`create.sh` builds; fix it in `gh`'s caller by inspecting the recorded write — impossible, the write must not
be attempted at all.

**D2 — The guard is per namespace, not status-only.** The invariant is one value per namespace
(`guidelines/Backlog.md` §4, I16; `issue-backlog` → "Labels carry …", "exactly one value per namespace where the
item has that fact"), and the profile's namespace model is general: a declared name, a separator, a value set.
After the vocabulary gate every label's prefix is a declared namespace, so one prefix scan covers `class`,
`category`, `status`, `source`, `agent`, `prio` and `sensor` alike. Alternatives the evidence eliminates:
status-only — impossible to state honestly, since the same defect (`--label class/bug` on a finding carrying
`class/feature`) exists and I16 is not status-specific; read every namespace's value set from the profile to
confirm the prefix — impossible to need, because `Taxonomy.known` already refused anything whose prefix is not a
declared namespace, so the profile read (a JVM start) would buy nothing.

**D3 — The contradictory request exits 2 with a reason naming both values, matching `update.sh`.**
`update.sh` refuses the same class of mistake with `issues_die` (exit 2, usage) and a message naming the two
values (`fix-status-label-replacement`, D5), and the filing path's other contradictory request (a bad argument)
already exits 2. Alternatives the evidence eliminates: exit 3 with a reason code — impossible, exit 3 is the
policy/vocabulary refusal (`label-opaque`, `label-reserved`) and both values here are valid, so reusing it would
blur what the skills retry on; resolve it silently (let `--status` win, or the finding's label) — impossible, it
is a decision about which state the item has, and a silent one leaves the item's reader unable to tell what the
caller meant.

**D4 — The stub `gh` is not changed, because the real `gh` accepts the shape.** The filing-path refusal is the
tool's, and the case proves it with an empty write log (`[ ! -s "$log" ]`). Per #165's lesson the risk is a stub
*more permissive than the tool* hiding a defect class; here the would-be stricter stub would assert a refusal the
real client does not make — measured during triage, a real `gh issue create --label status/fixed-by --label
status/open` created the item carrying both labels (issue #180, since deleted) — so a stub that refused it would
be wrong and would hide *that the refusal must live in `create.sh`*. Alternatives: make the stub refuse a
duplicate-namespace `issue create` — impossible without lying about the client; leave the case without a write-log
assertion — impossible, the assertion is what proves the refusal precedes the write.

**D5 — The new invariant gets a mutation set of its own, `tools/create-mutations.json`.** The set is versioned
like `status-mutations.json` and `resolve-stale-mutations.json`, and its `--list` is printed by the "Check issue
tooling" CI step, so a set that no longer parses fails the pipeline instead of rotting. The mutation removes
the guard's predicate (`if [ -n "$previous" ] && [ "$previous" != "$label" ]` → `if false`), the named case must
go red, the file is restored, the case must go green.

**D6 — Verification is the offline case, the mutation and the suites that drive `create.sh`.** The case lives in
`tools/issue-tooling-cases.sh` under the name the spec scenario carries
(`create#refusesTwoValuesOfOneNamespace`) and runs through `tools/issue-tooling-selftest.sh --case <name>`. Its
red-on-HEAD run is `evidence/red-on-head.log`. Full regression: `tools/issue-tooling-selftest.sh` (77 cases),
`tools/sensor-selftest.sh` and `tools/check-portability-selftest.sh` (both drive `create.sh`). No Gradle, no
device: nothing here is a compiled file, renders or runs on Android (`guidelines/Build.md` §2, the scope block).

## Risks / Trade-offs

- **A namespace the profile declares but whose value set is empty (`sensor`)** → a finding carrying a `sensor/*`
  label is already refused by the vocabulary gate before the namespace scan, and `sensor` is scanned like any
  other prefix; `tools/sensors/run.sh` adds one sensor label, so no false refusal.
- **A label with no separator** → the vocabulary gate refuses it (`label-opaque`); only labels whose prefix is a
  declared namespace reach the scan, so the scan never sees a bare word.
- **A duplicate of the *same* value** (the finding's `status/open` and `--status open` together) → deliberately
  not refused: it names one value of the namespace twice, not two values, and `gh` would add one label.
- **`declare -A` requires bash 4** → the tooling already requires bash and uses arrays throughout; the CI and
  the self-test run bash.
- **A `gh` write could still fail after the refusal passes** (pre-existing: `create.sh` does not branch on `gh`'s
  exit status in the human path) → out of this change's way; it is not the duplicate-namespace defect and a
  separate observation.

## Migration Plan

1. Land the guard, the case and the mutation set.
2. `guidelines/Backlog.md` §4 needs no edit: I16 already states the rule; this change makes the filing path obey
   it as well.
3. Rollback: revert the commit. Nothing is persisted, no schema, no dependency, and a tracker item already filed
   after the fix is a normal item.
