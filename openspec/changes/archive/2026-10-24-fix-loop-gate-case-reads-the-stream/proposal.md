# The gate case reads its suite's stream instead of sampling at a fixed instant

## Why

`gate#theSuitesOutputReachesTheConsoleWhileItRuns` — the case the shipped requirement names for *The gate streams
a suite's output while it runs*, scenario *A suite's output reaches the console before the suite exits* — starts
`gate.sh` in the background and reads its console once, `sleep 1.5` later, then asserts three things about that
one reading:

```bash
( cd "$p" && LOOP_CONF="$conf" bash "$LOOP_ENGINE/gate.sh" --base "$base" ) > "$log" 2>&1 &
pid=$!
sleep 1.5
out=$(cat "$log")
assert_contains "$out" "gate: bash" "the suite is announced before it runs"
assert_contains "$out" "case: a note is read" "…reaches the console while the suite runs"
assert_not_contains "$out" "notes: all 1 cases passed" "…the output is streamed, not replayed"
```

The instant is a bound on the **host**, not on the streaming. Everything the gate does before it prints its first
line is start-up: `loop_resolve`, the four `loop_conf_value` reads of the profile, `git rev-parse --verify`
beside `git diff --name-only` and `git status --porcelain`, the pattern matching over the owed paths and the
`sed`/`awk` pipeline that de-duplicates them. None of it is the stream, none of it is bounded, and all of it sits
in front of the two lines the case reads.

**Measured on this host, the start-up is small — and that is not a defence of the reading.** `timing-probe.sh`
polls the console (beside this change, `evidence/timing.log`) and the gate reaches its announcement and the
suite's first line 43–141 ms after it is started: 43–45 ms on a quiet host and 115–141 ms while 20 spinning
shells at nice 0 held it, against the 1500 ms window the case sampled. The case is therefore **green here, and
was not seen red on an uncontented host**. The defect the item names is that the reading is a host bound at all,
and it is demonstrated by making
the host shape the item describes: 24 spinning shells at nice 0 beside the case run at nice 19 (`contend.sh`,
`evidence/red-before.log`), and the same unmodified case on the base `0f366176` fails with the sample holding
the gate's first line and nothing else —

```
the suite is announced before it runs: 'gate: bash' not found in: LOOP-GATE-PROFILE: …/gate.conf — pinned; …
FAIL gate#theSuitesOutputReachesTheConsoleWhileItRuns
```

— while the same case, same tree, same day passes with the host left alone. That is the class #295 owns: a case
that reddens on a loaded host while the engine works.

The same defect was fixed in the sibling case by the change that fixes #287 (branch
`fix/issue-287-the-prescreen-case-reads-the-stream`, not on main yet): `run#prescreenAnnouncesItselfBeforeItReturns`
had this shape and was filed red for it. That reading is the item's own and is not re-measured here — #287's
branch is not on this tree — and this change states it as the item's claim, not as a measurement taken here.
Open item: #296.

## What Changes

- `tools/loop-cases.sh`, `case_gate_theSuitesOutputReachesTheConsoleWhileItRuns`: the single `sleep 1.5` reading
  is replaced by a bounded wait (`for _ in $(seq 600)` with `sleep 0.05`, so 30 s) for the stand-in suite's own
  first line, after which the case's assertions run unchanged — the `gate: bash` announcement, the suite's first
  line present, its summary absent, the rest of the stream including stderr, the gate's tally and the tally file.
  The comment above the case records why the reading waits and what a fixed one cost.
- `openspec/specs/agent-issue-loop/spec.md` after archive: one `MODIFIED` requirement — *The gate streams a
  suite's output while it runs* — with one scenario added (*A suite's line is read from the stream, not at an
  instant*), the requirement's body and its other two scenarios repeated verbatim.

## Capabilities affected

`agent-issue-loop` — one scenario added to an existing requirement; no requirement text, no engine behaviour and
no interface changes. Additive: the gate's stream, its tally, its exit status and the suite's own numbers are
untouched, and the case still asserts all of them.

Rollback path: revert the change — the case samples the console at a fixed instant again and is red again on a
host whose gate start-up outlasts 1.5 s, which is the state #296 reports.

## Impact

| path | what changes |
|---|---|
| `tools/loop-cases.sh` | the reading in `case_gate_theSuitesOutputReachesTheConsoleWhileItRuns`, and its comment |
| `openspec/specs/agent-issue-loop/spec.md` | the added scenario, after archive |

No engine script, no mutation record, no profile and no `guidelines/**` file is touched: the defect is in how the
case reads an artifact the gate already prints, and the rule the case states is owned by the requirement whose
`**Case**:` line names it. No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched — the delta
compiles nothing, so no Gradle suite is owed (`guidelines/Build.md` §2, the scope block); a delta to
`tools/loop-cases.sh` is a core file of the loop's suite, so the suite the gate owes is its whole set.

## Not in this change

- The gate's stream, the `tee` that feeds the console and the tally, the gate's start-up and its profile reads:
  unchanged, and guarded by the same case as before.
- The sibling case `run#prescreenAnnouncesItselfBeforeItReturns`: the same reading, fixed by the change that
  owns #287, which is not on main. This change does not carry it, and its own gate re-runs it green.
- The gate's start-up cost itself — a `git status` over the tree, four profile reads — is not shortened here. A
  case whose reading waits for an observable does not care what the start-up costs, and a case that bounded the
  start-up would be measuring the host again.
