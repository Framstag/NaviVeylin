# Design

## Context

The sensor engine already supports everything this row needs, and that is why the design is about *where the
number comes from* and *what the row may declare*, not about new machinery:

- `tools/sensors/sensors.json` declares 15 rows; every project fact of a check lives there (D12 boundary).
- `tools/sensors/lib.sh` validates a row's required fields, and reads an optional `.k` per row
  (`sensors_row_max_recurrences`, line 136) before falling back to the registry's `escalation.maxRecurrences`.
  That override has no test and no requirement — this change is its first user.
- `tools/sensors/run.sh` exports the row as `$SENSORS_ROW` (line 182), so a row's command can read keys only the
  row declares.
- `tools/check-portability.sh` (line 219) scans `tools/sensors/metrics/*.sh` as **engine** files and forbids
  project values in them.
- `.github/workflows/sensors.yml` runs three `--rows` steps, and `workflow#aRedRecordRunsNoRecordRow` asserts
  those three lists together equal the registry exactly.
- `SENSORS_TOKEN` is documented (`guidelines/BacklogTooling.md` §9) as Issues RW + PR read, access **limited to
  this repository**; scenario `sensors#theDocumentedCredentialCoversEveryDeclaredRow` exists to keep that true.

The submodule is pinned by the parent repository's gitlink (`160000 commit 7c449bf66…`), and the pin is a **merge
commit** of upstream `master` into the submodule's local branch — so a landed update never makes the pin equal to
upstream's head.

## Goals / Non-Goals

**Goals**

- One daily verdict: "upstream carries commits the pin does not", with the count, from two cheap reads.
- A verdict that clears *because the update landed*, not because the check stopped looking.
- A row that does not escalate ordinary maintenance into a person's decision queue.
- No new credential, no submodule checkout, no change to the engine's write paths.

**Non-Goals**

- Automating the update. The loop may not commit or push inside the submodule (`tools/loop/prescreen.sh`, #254),
  and a sensor may not authorise work (`guidelines/Backlog.md` §5, I1). Detection only.
- Measuring NaviVeylin's own residual (`behind_by` = 147 today: the local commits upstream has not taken). That is
  a different condition with a different owner and would be a second row.
- Rationing when an update happens. That is the bound decision below, and the owner chose "any drift".

## Decisions

### D1 — The measurement is `ahead_by` from one `compare` call, not `rev-list` on a checkout

| option | cost | why it fails |
|---|---|---|
| `git submodule update --init` + `git rev-list --count <pin>..origin/master` | hundreds of MB cloned into every run | the pin is a merge commit in the submodule's branch, so its upstream history is only complete if the clone carries that branch; and the pipeline would spend minutes a day on a number that one request returns |
| `git ls-remote` alone (pin ≠ master ⇒ condition) | one cheap call | it cannot express the **clear**: after a merge the pin is deliberately not `master`, so the condition would never clear and the issue would never close |
| GitHub `GET /repos/…/compare/<pin>...<master head>` (**chosen**) | one or two unauthenticated requests | — |

The chosen call answers both halves from ancestry: `ahead_by` is exactly "commits upstream carries that the pin
does not", and it is `0` after the merge commit is pinned, whatever else the pin contains.

### D2 — The bound is "any drift" (`comparison: boolean`, `threshold: 1`)

`lib.sh:586` reads `boolean` as `m >= b`, so `1` means "one upstream commit is already the condition".

Alternatives rejected: a lag bound in commits (~30 ≈ a week, ~75 ≈ two missed cycles) rations the update and can
sit silent for a fortnight through a fast upstream month; days-behind fires during an upstream **quiet** period
when there is nothing to merge, which is a false condition. The owner's decision (2026-11-04) is that the sensor
says *there is work to merge*, and when to spend it stays the person's.

Accepted consequence: the condition is present on most days, so this row's issue is a standing item that closes
when an update lands and reopens when upstream moves again. The row's report line is red nearly every day by
design; the number that carries information is the trend in the item's comments.

### D3 — The row declares its own recurrence bound (`k`), and a declared bound is validated

`run.sh:509` escalates past `sensors_row_max_recurrences`, which reads the row's `.k` first. At the registry's 3,
this row would escalate in its fourth **routine** cycle, writing `agent/needs-decision` and `status/on-hold` —
labels that state "a person must decide" about a check that is behaving exactly as declared.

- Chosen: `"k": 100` (about two years at the observed weekly cadence), with the reason written beside the row.
- Rejected: `lifecycle: file` — never closing loses the one fact `file+close` buys here, that the update **landed**
  and the pointer now carries upstream's head.
- Rejected: raising the registry's `escalation.maxRecurrences` to 100 — that widens the bound for the thirteen
  rows whose recurrence really is a defect, to serve one maintenance row.

Because the registry now has a row that relies on `.k`, a declared bound is validated as `maxFilings` was in #666
(`row-k-not-a-count`, refusing `0` and non-numbers): without it `"k": 0` escalates on the first recurrence and a
typo fails inside a numeric comparison rather than by name.

### D4 — The row runs in the "condition rows and repository rows" step

It reads no build output, so it must run for a red build too: drift is independent of the build's verdict, and the
record step is gated on `conclusion == success`. The cost is one more id in a list that
`workflow#aRedRecordRunsNoRecordRow` holds to the registry exactly.

### D5 — The project facts live in the row, and the script validates them

`tools/check-portability.sh` scans `tools/sensors/metrics/*.sh` and forbids project values there, while
`$SENSORS_ROW` hands the row to the command. So `upstream: {repo, branch, submodule}` sits in the row and the
script reads it with `jq`.

Rejected: reading `loop.conf`'s `LOOP_SUBMODULE_PATH` — a layering dependency from the sensors onto the loop, and
it names no upstream repository anyway. Rejected: naming the repository in the script — the portability check
refuses it, correctly.

Cost: `upstream` is a key the engine's validator does not know, so the script validates its own input and emits
`sensors_emit_error` with a named reason when a key is empty — a typo is then a loud failed check, never a quiet
`0` that reads as a clear.

### D6 — The call is unauthenticated

Rejected: widening `SENSORS_TOKEN` to read `Framstag/libosmscout`. The documented scope is repository-limited, and
the spec's credential scenario exists so that a secret created as the documentation says can run every row. A
public repository needs no token; a 403 or a 5xx becomes an error verdict.

## Open decisions

None

## Risks / Trade-offs

- **API dependence.** The verdict depends on api.github.com answering. A rate limit, a 5xx or a 404 is an `error`
  finding and fails the run — never a `clear` (spec, "The check itself failed"). A 404 on the pin is also a real
  defect: the pin is pushed to `Framstag/libosmscout` precisely so it stays fetchable.
- **A permanently red row teaches people to ignore red.** Mitigated only by the item's trend and by the fact that
  the close is a signal (it means an update landed). This is the accepted price of D2.
- **Upstream force-pushes `master`.** Ancestry breaks and `ahead_by` reports a large number; the row is still
  correct to say "there is work to merge".
- **`git ls-tree HEAD` reads the checkout's HEAD.** The guard already refuses a run whose HEAD is not `origin/main`
  (exit 5), so the read is of the pinned pointer on main.
- **The row's own key can be mistyped silently** unless D5's validation is implemented; that is a task, not a hope.

**Threading and lifecycle:** none. No app component, no dispatcher, no lifecycle owner — the row is one shell
process on a CI runner, and the only concurrency question is the pipeline's, which `concurrency: sensors-main`
already answers.

**Verification (no compiled file, no device step):** `tools/sensor-cases.sh` (the three new cases plus
`workflow#aRedRecordRunsNoRecordRow`), `tools/check-portability.sh`, `tools/check-metadata.sh` (token-free),
`openspec validate sensor-libosmscout-drift --strict`, and one dry run of the row against the real pin and the real
upstream head, whose measured number must be a number and not an error.
