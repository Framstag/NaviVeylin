#!/usr/bin/env bash
# child.sh — the child's supervision: the four bounds a child is held to, the role and the command it is
# started with, `run_child` itself, the tail that follows the gate, and the stop arms a child's own end runs.
# Fourth in the engine's order (`layout.tsv`), after `lib.sh`, `evidence.sh` and `leftovers.sh`; the entry that
# sources it follows, so `run_child` may read the evidence and adopt the leftovers and the entry may call it.
#
# Sourced by `run.sh` only, and once: the guard below is what makes a second read side-effect free.
[ -n "${LOOP_CHILD_SH:-}" ] && return 0
LOOP_CHILD_SH=1

# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"
# shellcheck source=tools/loop/evidence.sh
source "$here/evidence.sh"
# shellcheck source=tools/loop/leftovers.sh
source "$here/leftovers.sh"

# What one child may run (issue #663). Two recorded numbers decide it: the floor a child may run even when the
# profile records no gate cost (`LOOP_CHILD_MAX_SECONDS`, 45 min), and what one item's gate owes
# (`LOOP_ITEM_GATE_SECONDS`) — an item owes a red gate round and a green one, and the phases the gate is not. The
# child is given the larger of the two, because a child ended inside its own item's cost is a green change the run
# pays for and then discards: measured 2026-10-26, one gate round of a `tools/loop/**` item cost 971 s over 23
# suites, an item owed 957 s + 233 s + 972 s of gate and 542 s of phases — 2 704 s against the 2 700 s constant —
# and the child was ended 8 s after its green `gate-summary.xml` was written (#310, and #664, #666 the same way).
child_cap() { # → the seconds a child may run
  local floor owes
  floor="${LOOP_CHILD_MAX_SECONDS:-2700}"
  case "$floor" in ''|*[!0-9]*|0) floor=2700 ;; esac
  owes="${LOOP_ITEM_GATE_SECONDS:-0}"
  case "$owes" in ''|*[!0-9]*) owes=0 ;; esac
  if [ "$owes" -gt "$floor" ]; then printf '%s\n' "$owes"; else printf '%s\n' "$floor"; fi
}

# How many turns a child may take before the run ends it, **per role** (issues #686, #784). The wall clock bounds
# how long a child runs and says nothing about how much it spends being slow to converge: `cacheRead` is 97 % of
# the archive's spend, a turn re-reads the whole conversation, and no turn amortises two calls, so a heavy item is
# a chain of single-call turns at 85–106 k context each. The bound used to be the **item's**, one count over the
# event stream every child of the item appends to, so the worker, the round before the gate, the gate, the tail and
# each review round spent from one pot and the last phase inherited the remainder: measured 2026-11-05 over the ten
# items that ran all the phases, #780 spent 80/34/42/41 turns on its first four children and ended its round-1
# reviewer at 53 of the same 250, and #767 gave its round-2 worker three. The bound is the **child role's own** now,
# read through `child_role`, so a phase is bounded by what that phase costs and not by what the phases before it
# spent. The item's total is still counted and reported — `metrics.turns`, the report's `calls:` line, the band
# `LOOP_ITEM_TOKENS_WARN` records — and it stops nothing. The count is read from the stream the child appends to
# (`child_turns`), never from a child's claim. `0` is no bound, and a role this project records no key for has none:
# a project with no such record owes nothing by it, the shape `LOOP_MIN_FREE_MIB` and `LOOP_ITEM_TOKENS_WARN`
# already have. The derivation of this project's six values is beside them in `loop.conf`.
child_turn_bound() { # child_turn_bound <role> → the turns that role may spend; 0 = no bound
  local role="$1" value
  value=$(loop_conf_value "LOOP_CHILD_TURNS_MAX_$(printf '%s' "$role" | tr '[:lower:]-' '[:upper:]_')")
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  printf '%s\n' "$value"
}

# What the item's key above cannot say: the phases it bounds are not one population. Measured on 2026-11-10 over the
# day's three runs (`.pi/issue-loop/runs/2026-11-09T21:42:41Z.json`, `runs/2026-11-10T08:19:24Z.json`, `run.json`):
# 19 items attempted, 9 recorded `cut`, and 8 of the 9 ended at a largest context of 199 178–211 475 against the
# recorded 200 000 — 0.4 % under it to 5.7 % over. Each of those belonged to the role whose work **is** reading the
# item: four to the rework worker that answers a findings list, three to the review round that reads the diff, one to
# the tail. The items that closed the same day read 114 522, 116 750, 155 145, 174 700, 191 637 and 192 886 — the
# largest 3.6 % **under** the stop — so the item's one number sat inside the population that finishes, and whether a
# session was converging or running away was decided by which side of 200 000 it happened to be on.
#
# A role's own key is therefore read the way its turns are (`LOOP_CHILD_TURNS_MAX_<ROLE>`, issue #784): what a reading
# phase may spend is a fact about that phase. A role whose key is absent inherits the item's, so a project that
# records nothing new is bounded exactly as before, and a role recorded `0` is unbounded by it, as the item's `0` is.
child_context_bound_key() { # child_context_bound_key <role> → the key that role's context stop is read from
  printf 'LOOP_ITEM_CONTEXT_MAX_%s' "$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')"
}

child_context_bound() { # child_context_bound <role> → the tokens that role's child may reach; 0 = no bound
  local role="$1" value
  value=$(loop_conf_value "$(child_context_bound_key "$role")")
  case "$value" in
    ''|*[!0-9]*) value="$item_context_max" ;;
  esac
  printf '%s\n' "$value"
}

# Which bound ended a child, read back from the transcript the run wrote it into. Three of them end an item's worker
# and two of them are a phase a fresh session is the cheaper half of (issues #851, #856): the context bound and the
# checkpoint above it are what a *new* session pays instead of the re-read of a grown one, while the turn bound and
# the wall clock measure what the child spent failing to converge. `run_child` already names the bound it enforced and
# the key it is recorded in, so this is one read of one line and not a second reading of the stream.
child_ended_by_context_bound() { # <report> → 0 when that child's own LOOP-TIMEOUT line names a context split
  case "$(report_field "$1" LOOP-TIMEOUT 2>/dev/null)" in
    *"the recorded context bound of"*|*"the recorded context checkpoint of"*) return 0 ;;
  esac
  return 1
}

# The host is part of a run's numbers, so the run reads it and names it beside the budget. Both values are
# recorded (`loop.conf`, `LOOP_MIN_FREE_MIB` and `LOOP_MEMINFO_PATH`) because both are facts about the host and
# neither is a fact about a phase (D16). Measured 2026-10-19 (issue #209): item #126's gate found 194 MB free
# and 18 GB of shared swap and spent 30 min inside `:app:kspMobileDebugUnitTestKotlin` that takes 3m47s on a
# quiet host, and a `free` taken during another run of the same tree read 467 MB free against 13.9 GB of swap
# used. The run's wall time then measures the desktop session, not the change. A host that publishes no
# `MemAvailable` — a container without procfs, a platform that keeps the number elsewhere — makes the check
# pass: a check that cannot be made is not a refusal, and the engine must run there too.
host_free_mib() { # → MiB the host has available, or nothing when this host publishes no such number
  local meminfo kb
  meminfo="${LOOP_MEMINFO_PATH:-/proc/meminfo}"
  [ -n "$meminfo" ] || return 0
  [ -r "$meminfo" ] || return 0
  kb=$(sed -n 's/^MemAvailable:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$meminfo" 2>/dev/null | head -1)
  case "$kb" in ''|*[!0-9]*) return 0 ;; esac
  printf '%s\n' $((kb / 1024))
}

# A repository whose `core.bare` is true is not a work tree: `git status` still answers, so a run looks healthy,
# but every merge, fast-forward and commit in it is refused with "this operation must be run in a work tree".
# The run merges the branch it closes (#229) and so does the caller's publish, and both then read as "the branch
# could not be merged" — the real cause never named (issue #241, measured 2026-10-21: five refused
# fast-forwards, `git config --get core.bare` → `true`, `git rev-parse --is-inside-work-tree` → `false`, and one
# `git config core.bare false` fixed all of it). The run refuses before it writes anything, and says what to do.
# The command a child is started on, resolved from the two dimensions the run knows before it forks that child (issue
# #821): the role its prompt's name gives it (`child_role`, defined below beside `run_child`) and the class the item's
# own label carries (`class/<value>`, read before the item's first child starts). The chain is most specific first:
#
#   LOOP_AGENT_CMD_<ROLE>_<CLASS>   the role, refined by the item's class
#   LOOP_AGENT_CMD_<ROLE>           the role
#   LOOP_AGENT_CMD_<HEAD>_<CLASS>   the role it belongs to, refined by the class
#   LOOP_AGENT_CMD_<HEAD>           the role it belongs to — where `reviewer-pre-gate` inherits `REVIEWER`
#   LOOP_AGENT_CMD                  the project's one command
#
# `<HEAD>` is the role up to its first hyphen, so a qualifier inherits the command of the role it qualifies by one rule
# and not by a case per family. The class is a refinement of the role it is spelled after, which is why a role that
# records a command of its own still beats its head's class key. The spelling is derived and not invented: the role's
# transform is the one `run_child` already applied, extended to the class.
child_agent_cmd_key() { # child_agent_cmd_key <role> <class> → the key recording that child's command
  local head candidate cls
  head="${1%%-*}"
  cls=$(printf '%s' "$2" | tr '[:lower:]' '[:upper:]' | tr -c 'A-Z0-9' '_' | sed 's/_*$//')
  for candidate in \
    "LOOP_AGENT_CMD_$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')${cls:+_$cls}" \
    "LOOP_AGENT_CMD_$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')" \
    "LOOP_AGENT_CMD_$(printf '%s' "$head" | tr '[:lower:]-' '[:upper:]_')${cls:+_$cls}" \
    "LOOP_AGENT_CMD_$(printf '%s' "$head" | tr '[:lower:]-' '[:upper:]_')"; do
    if [ -n "$(loop_conf_value "$candidate" | tr -d '[:space:]')" ]; then printf '%s' "$candidate"; return 0; fi
  done
  printf 'LOOP_AGENT_CMD'
}

# How many turns the child running now has taken, read from the stream the adapter keeps for it (issue #686):
# `tools/pi-child-events.sh` appends one JSON object per event to the file `LOOP_CHILD_EVENTS` names, and a turn
# ends in exactly one `turn_end` line — the unit `tools/loop/state.sh` sums a child's spend per and the archive's
# 1.05–1.16 tool calls per turn are amortised over. A stream that is absent, empty or unreadable is no count
# rather than an error: a child whose command writes no events is bounded by its clock alone, as it was before,
# and a count this wait cannot take never ends a child.
child_turns() { # child_turns <events file> → the `turn_end` lines it holds, plus the turns a rotation dropped
  local n r
  [ -n "${1:-}" ] && [ -f "$1" ] || { printf '0\n'; return 0; }
  n=$(grep -cE '"type": *"turn_end"' "$1" 2>/dev/null) || true
  case "$n" in ''|*[!0-9]*) n=0 ;; esac
  # A rotated stream no longer holds the turns it dropped, and the count a bound compares against must not fall when
  # the writer rotates (issue #802): the record the adapter writes first carries them, cumulative, so a reader adds
  # what the stream says it dropped to what it still holds.
  r=$(sed -n 's/.*"type": *"stream_rotation".*"dropped_turn_ends": *\([0-9][0-9]*\).*/\1/p' "$1" 2>/dev/null |
    awk '{ sum += $1 } END { printf "%d", sum + 0 }')
  case "$r" in ''|*[!0-9]*) r=0 ;; esac
  printf '%s\n' "$(( n + r ))"
}

# The size of the conversation the child is paying for (issue #760), read from the same stream as the turn count and
# in the same whole second. A turn is not a unit of cost — every turn re-reads the whole context — so the reading
# that says what a further turn will cost is the largest one the stream has reported: `input + cacheRead` of a usage
# record (cumulative within a response, so the peak is what the window had to hold) or a `compaction_end`'s
# `tokensBefore` (the size it had to hold before it shrank), whichever is greater. It is the number
# `state.sh`'s `child_usage_json` takes for the report, taken while the child still runs.
#
# Only the complete lines appended since the last look are read: `wc -l` is the boundary, and a line still being
# written carries no newline, so a partial record is never parsed. That is what keeps a once-a-second reading cheap
# on a stream that runs to megabytes, where a re-parse of the whole file would spend on `jq` what the bound saves on
# tokens. A stream that is absent, empty or unreadable is no reading rather than an error, exactly as `child_turns`
# treats one: a child whose command writes no events stays bounded by its clock and by its turns.
child_context() { # child_context <events file> <lines already read> → "<largest context in the new lines> <lines read>"
  local f="${1:-}" done_lines="${2:-0}" total blob max
  [ -n "$f" ] && [ -f "$f" ] || { printf '0 0\n'; return 0; }
  total=$(wc -l < "$f" 2>/dev/null) || true
  case "$total" in ''|*[!0-9]*) total=0 ;; esac
  case "$done_lines" in ''|*[!0-9]*) done_lines=0 ;; esac
  [ "$total" -ge "$done_lines" ] || done_lines=0
  max=0
  if [ "$total" -gt "$done_lines" ]; then
    blob=$(tail -n +$((done_lines + 1)) "$f" 2>/dev/null | head -n $((total - done_lines)) | jq -s -c '
      (([.[] | select(.type == "message_update" or .type == "turn_end") | (.usage? // .message.usage?) | select(type == "object")
          | ((.input // 0) + (.cacheRead // 0))] | max) // 0) as $turn
      | (([.[] | select(.type == "compaction_end") | (.result?.tokensBefore? // empty) | numbers] | max) // 0) as $compacted
      | [$turn, $compacted] | max' 2>/dev/null) || blob=""
    case "$blob" in ''|*[!0-9]*) blob=0 ;; esac
    max=$blob
  fi
  printf '%s %s\n' "$max" "$total"
}

# Why a child that answered nothing may have answered nothing (issue #828): a provider that refuses every request
# leaves the proof inside the child's own event stream — a `turn_end` on `stopReason: error` carrying the provider
# and the model, and an `auto_retry_end` carrying the `finalError` — and the arm that records the run's "could not
# start" stop read none of it, so a reader of the stop reason could not tell a refused provider from a broken child
# (measured 2026-11-01: `.pi/issue-loop/items/85/events.jsonl` ends on a 429 weekly-limit refusal beside a 0-byte
# `worker-report.md`, and the run's stop named no part of it). Read from the tail of the stream, because the
# refusal is the newest thing in it and the file runs to megabytes; a stream that is absent, unreadable or carries
# no refusal is no reading rather than an error, exactly as `child_turns` treats one. The refusal's own text is
# squeezed to one line so the reason stays one line.
child_refusal() { # child_refusal <events file> → "<provider>/<model>: <refusal>" for the last refused request, or empty
  local f="${1:-}" out
  [ -n "$f" ] && [ -f "$f" ] || return 0
  out=$(tail -n 200 "$f" 2>/dev/null | jq -rs '
    [ .[] ] as $e
    | ($e | map(select((.finalError? // "") != ""
                        or (.message?.errorMessage? // "") != ""
                        or (.message?.stopReason? // "") == "error")) | last) as $refused
    | select($refused != null)
    | ($e | map(select((.message?.provider? // "") != "")) | last) as $named
    | (($named.message.provider // "")
       + (if ($named.message.model // "") != "" then "/" + $named.message.model else "" end)) as $who
    | (($refused.finalError // $refused.message.errorMessage // "")
       | gsub("[\\r\\n\\t]+"; " ")) as $why
    | select($why != "")
    | (if $who == "" then "" else $who + ": " end) + $why' 2>/dev/null) || out=""
  printf '%s' "$out"
}

# Which role a child plays, derived from the prompt's name — the same ones the fixture stub distinguishes
# (`worker`, `worker-rework`, `worker-gate`, `worker-tail`, `reviewer`, `reviewer-pre-gate`) and the naming `phase`
# already carries. The role names two configuration keys — `LOOP_AGENT_CMD_<ROLE>` and `LOOP_CHILD_TURNS_MAX_<ROLE>` —
# so a prompt file renamed for a round or a retry cannot move a child to another command or under another bound:
# `reviewer-prompt-2-retry.md` and `reviewer-prompt-1.md` are both `reviewer`. The order of the arms is the rule: a
# tail and the round before the gate are named before the plain worker and reviewer shapes they also match, and the
# rework round before the worker it is rendered from (issue #784).
#
# The rework round is a role and not a round (issue #821): the child a refusal buys answers a findings list and plans
# nothing, so it is priced and bounded apart from the item's first worker pass. What makes it a role is that the run
# renders its prompt at a site of its own — `worker-prompt-round2.md` — and the prompt's name is the only input
# `child_role` reads, so the arm reads the artifact the run already writes.
child_role() { # child_role <prompt-file> → worker | worker-rework | worker-gate | worker-tail | reviewer | reviewer-pre-gate
  case "${1##*/}" in
    *-tail*) printf 'worker-tail' ;;
    *pre-gate*) printf 'reviewer-pre-gate' ;;
    *gate*) printf 'worker-gate' ;;
    *round2*) printf 'worker-rework' ;;
    reviewer-*) printf 'reviewer' ;;
    worker-*) printf 'worker' ;;
    *) printf '%s' "$(basename "$1" .md)" ;;
  esac
}

run_child() { # run_child <prompt-file> <report-file> — every child goes through LOOP_AGENT_CMD (D3)
  # The child is bounded by the recorded wall-clock (`LOOP_CHILD_MAX_SECONDS`, default 2700 = 45 min) and not
  # only by the run's `--minutes`: the deadline is consulted between items, so a child that never returns
  # spends the whole budget inside one wait and nothing notices (measured 2026-10-19: an item's worker phase
  # ran 83 min that way — issue #203). A child that outlasts the cap is ended together with everything it
  # started and its transcript says so, so the run's ordinary "exited N" path records the item `blocked`.
  # A third bound lives beside it, the child's own turns (`LOOP_CHILD_TURNS_MAX_<ROLE>`, issues #686 and #784), and
  # it ends the child the same way: the wall clock says how long a child may run, not how much it spends failing to
  # converge, and each role is bounded by what that role costs rather than by what the item has already spent.
  # The working directory is the project and not the worktree on purpose: the project's own tool policy is
  # resolved at the directory the process starts in, and every path the child is handed — the work package,
  # its evidence directory, the change root its prompt names — lies inside the project. The tree it is told
  # to change is still the worktree, by the path in its prompt.
  local before after rc phase child_cmd role role_key role_cmd turns_max turns_base stop hb started interval poll cap child timed_out=0 sweep status_file ended_by deadline_epoch now last_second hb_base next elapsed turns ctx_lines=0 ctx_max=0 ctx_warned=0 ctx_has_work=0 ctx_read ctx_new ctx_bound ctx_bound_key checks_max checks_seen=0 checks_done=0 chk_new chk_read chk_done
  # The patch the child this call ends left its work in, and nothing from the child before it: `keep_leftovers`
  # sets it, and the item's record must name the patch of the child that just ended (issue #674).
  leftover_patch=""
  before=$(project_state)
  phase=$(basename "$1" .md)
  # Which command starts this child — the same one indirection, one step finer, and now read from two dimensions rather
  # than one (issue #821): the role, its head, and the item's class, in that order of specificity
  # (`child_agent_cmd_key`, above). A role whose key the project recorded runs its children through it
  # (`LOOP_AGENT_CMD_REVIEWER`, say); a role that records none inherits the key of the role its name qualifies, and only
  # a role whose head records nothing keeps the single `LOOP_AGENT_CMD`, so a project that configures nothing runs one
  # command everywhere (the keys are empty in this project's `loop.conf`). What it buys is the difference the transcript
  # already measures: over 227 items (2026-11-03, `tools/loop-spend.py --json`) the reviewer's 7 522 turns carry 53.6 k
  # tokens each against the worker's 84.5 k at 25 654 turns, and the reviewer's job is a diff against the item's own
  # checklist. Which model a role runs on is a value this project records, not a fact the engine knows: no script under
  # `tools/loop/` names a model.
  child_cmd="$LOOP_AGENT_CMD"
  role=$(child_role "$1")
  role_key=$(child_agent_cmd_key "$role" "${class:-}")
  role_cmd=$(loop_conf_value "$role_key")
  if [ -n "$(printf '%s' "$role_cmd" | tr -d '[:space:]')" ]; then child_cmd="$role_cmd"; fi
  # …and the turns this role may spend, read from its own key rather than from one count the whole item shares (#784).
  turns_max=$(child_turn_bound "$role")
  # …and the checks of the gate's own work this child may start by hand (issue #882). One bound for every role: what
  # a child iterates on is the case its own diagnosis named, and that count is the same work in every phase.
  checks_max=$child_checks_bound
  # …and the context this role's child may reach, read from its own key where the profile records one and from the
  # item's where it does not. The key is kept beside the value because the transcript a person reads must name the
  # bound that fired, exactly as the turn bound's line names its own key.
  ctx_bound=$(child_context_bound "$role")
  ctx_bound_key="LOOP_ITEM_CONTEXT_MAX"
  if [ "$ctx_bound" != "$item_context_max" ]; then ctx_bound_key=$(child_context_bound_key "$role"); fi
  # The stream every child of the item appends to already holds what the children before this one spent, so the bound
  # is read as a **difference**: the count standing here is the baseline and what this child is bounded by is the
  # turns it adds. Read before the child starts, so a turn it takes between the fork and this line is its own.
  turns_base=$(child_turns "${LOOP_CHILD_EVENTS:-}")
  # …and the context bound is a difference for the same reason and one more (issue #851). A reading taken from the whole
  # stream is the **item's** largest context, not this child's — so a child started on a fresh window inherited the
  # number that ended the child before it, and a continuation (whose whole point is the fresh window) was ended on its
  # first reading by the bound that cut its predecessor. The lines standing here are this child's baseline, and what it
  # is bounded by is the reading it adds.
  ctx_lines=0
  if [ -f "${LOOP_CHILD_EVENTS:-}" ]; then ctx_lines=$(wc -l < "$LOOP_CHILD_EVENTS" 2>/dev/null || printf 0); fi
  case "$ctx_lines" in ''|*[!0-9]*) ctx_lines=0 ;; esac
  # …and the checks it runs by hand are a difference for the same reason (issue #882): the stream carries the checks
  # the children before this one ran, and what this child is bounded by is the count it adds. `checks_seen` holds it,
  # `checks_done` the lines already read.
  checks_seen=0
  checks_done=0
  if [ -f "${LOOP_CHILD_EVENTS:-}" ]; then checks_done=$(wc -l < "$LOOP_CHILD_EVENTS" 2>/dev/null || printf 0); fi
  case "$checks_done" in ''|*[!0-9]*) checks_done=0 ;; esac
  # The worker is the child whose gate result the run reads out of the worktree, so it starts on a tree that
  # carries no other child's test results (issue #253). The **tail** child is the exception and is why this line is
  # not a `worker*` glob (issue #744): it is started *after* the gate wrote those results because the archive it
  # writes is read from them, so clearing them there would leave the item blocked on a gate result the run itself
  # had deleted. The **gate** child is the other exception (issue #758): the change child that went before it already
  # started on a cleared tree, and what stands in the tree now is that child's own focused runs, which the gate it is
  # about to run supersedes.
  case "$phase" in
    worker-tail*) ;;
    worker-gate*) ;;
    worker*) clear_stale_test_results ;;
  esac
  interval="${LOOP_PROGRESS_SECONDS:-30}"
  case "$interval" in ''|*[!0-9]*|0) interval=30 ;; esac
  # How often the wait *looks*, which is not how often it reports (issue #259): a whole second of granularity charged
  # every child 1–2 s of sleep — the poll, and the join of the heartbeat beside it — and one run case drives up to
  # three children. Validated like the two bounds: a value that is no positive number falls back to the default rather
  # than stopping the wait, and a bare `0` is not a busy loop.
  poll="${LOOP_POLL_SECONDS:-0.05}"
  case "$poll" in ''|.|*.|*[!0-9.]*|*.*.*) poll=0.05 ;; esac
  case "$poll" in *[1-9]*) ;; *) poll=0.05 ;; esac
  # The bound this child is given, derived once by `child_cap` from the profile's two recorded numbers (issue
  # #663): the header names the same value, so a reader of the run's own line and of the transcript that ends the
  # child reads one number.
  cap="$child_max"
  stop="$2.progress-stop"
  rm -f "$stop"
  : > "$2"
  rm -f "$2.outside-write"
  # The record the wait ends on is cleared **before** the child can write it (issue #227): cleared after the
  # launch, a child that finished inside the gap between the fork and the `rm` had its own completion record
  # deleted, and the run then waited the whole cap for a child that had already finished.
  status_file="$2.status"
  rm -f "$status_file"
  started=$(date +%s)
  loop_emit child-started phase="$phase" log="$2"
  # `set -m` on the one command that starts the child, immediately undone: a background job of a shell with job
  # control is its own process group, which is what lets the cap end the child *and its descendants* — a Gradle
  # gate leaves daemons and test workers behind, and those are the memory the next item needs. Its stdin is
  # closed because a member of a background group that reads a terminal is stopped, not run, and an unattended
  # child has nobody to type at it anyway.
  set -m
  ( cd "$LOOP_ROOT" && $child_cmd "$1"; printf '%s\n' "$?" > "$2.status" ) > "$2" 2>&1 < /dev/null &
  child=$!
  active_child="$child"
  set +m
  ( hb_pid=$BASHPID
    hb_base=$SECONDS
    # The reading's own cadence, in whole seconds of this shell's clock (issue #673): the first reading is taken on
    # the first look, and the block below advances the next one to a whole second after each reading.
    read_next=$SECONDS
    next=$interval
    while [ ! -e "$stop" ]; do
      sleep "$poll"
      [ -e "$stop" ] && break
      # The difference is read *while the child runs*, because a writer is gone by the time the transcript is
      # written: only here can the run say which processes were alive at the moment the tree changed, and which of
      # them belong to this child's own tree. It is read **once per whole second of the child's life**, and not on the
      # reporting cadence (issue #673): an interval a person configures to read progress is not a resolution for
      # evidence, and a reading that arrives after a transient writer has gone places nothing — the case that pins a
      # placed write had to lower `LOOP_PROGRESS_SECONDS` to 1 s to be answered at all, which left it 1 s of phase
      # margin against the writer's own life and made it flaky. One reading per child once a difference is found — the
      # first difference is the one a reader of the item needs, and a tree another process keeps rewriting would
      # otherwise fill the item's directory.
      if [ ! -e "$2.outside-write" ] && [ "$SECONDS" -ge "$read_next" ]; then
        read_next=$(( SECONDS + 1 ))
        now=$(project_state)
        if [ "$now" != "$before" ]; then
          outside_write_seen "$before" "$now" "$child" "$hb_pid" > "$2.outside-write" 2>/dev/null || true
        fi
      fi
      # The heartbeat reports elapsed **seconds**, never the number of looks (issue #259): the wait looks twenty times
      # a second, so a count of iterations would shrink the interval along with the granularity and a configured 30
      # would have become 1.5 s of wall clock.
      elapsed=$(( SECONDS - hb_base ))
      [ "$elapsed" -ge "$next" ] || continue
      next=$(( elapsed + interval ))
      loop_emit heartbeat phase="$phase" elapsed="$(fmt_elapsed "$elapsed")" \
        log_bytes="$(wc -c < "$2" 2>/dev/null || printf 0)" \
        changed="$(git -C "$LOOP_WORKTREE" status --porcelain 2>/dev/null | wc -l | tr -d ' ')" \
        commits="$(git -C "$LOOP_WORKTREE" rev-list --count "${base:-HEAD}..HEAD" 2>/dev/null || printf 0)" --stderr
    done ) >&2 &
  hb=$!
  active_hb=$hb
  # The wait ends on the **child's own completion record**, not on `kill -0` of its pid and not on `wait`: a child
  # that the shell has already reaped answers nothing, and a `wait` on a pid bash no longer tracks returns no
  # status, so a poll that believes the child is gone can leave the run blocked behind a `wait` for ever. Measured
  # 2026-10-20/21 (issue #227): fourteen runs sat inside this poll with only a `sleep` child, printing a heartbeat
  # every 30 s for up to 4 h 52 min — 606 lines, `log 0 B`, and not one `LOOP-TIMEOUT` line, although the cap was
  # 2700 s. The subshell writes its own status when it returns, which is the one event the run can wait for.
  last_second=""
  while [ ! -e "$status_file" ]; do
    # Both clocks this wait enforces — the cap and the run's deadline — are whole seconds, and so is the turn count
    # of the third bound below, so all three are read once per second and not once per look (issue #259, #686): a
    # `date`, a `jq` and a `grep` per wake-up would spend on forks what the finer
    # granularity buys back. The guard is one line on purpose — the checks below keep the indentation they had, so
    # the records that name them (`23.1`, `23.2`) still quote them (issue #264).
    if [ "$SECONDS" = "$last_second" ]; then sleep "$poll"; continue; fi
    last_second=$SECONDS
    now=$(date +%s)
    # The run's own deadline bounds this wait too (issue #227). The deadline was consulted between items, so a
    # child was the one clock the run could not enforce: measured 2026-10-20, eleven of fifteen stuck runs were
    # past their own `--minutes 90` and the longest heartbeat read 291m30s, with no `LOOP-TIMEOUT` line and no
    # stop reason — the budget was spent invisibly. The bound is read from the run's own state, which is the
    # record `report.sh` and `state.sh next` read, so the gate and the report cannot disagree about the clock.
    deadline_epoch=$(jq -r '.run.deadlineEpoch // 0' "$LOOP_STATE_FILE" 2>/dev/null)
    case "$deadline_epoch" in ''|*[!0-9]*) deadline_epoch=0 ;; esac
    if [ "$deadline_epoch" -gt 0 ] && [ "$now" -ge "$deadline_epoch" ]; then
      ended_by=deadline
      timed_out=1
      kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
      sleep 1
      kill -KILL -"$child" 2>/dev/null || true
      break
    fi
    if [ $(( now - started )) -ge "$cap" ]; then
      ended_by=cap
      timed_out=1
      kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
      sleep 1
      kill -KILL -"$child" 2>/dev/null || true
      break
    fi
    # …and the bound that is not a clock (issues #686, #784): a child that has spent its role's turns is a decision,
    # and one more turn does not make it converge. It is read from the stream the child appends to and checked in the
    # same whole second as the two clocks, so the wait costs one `grep` per second and not one per look.
    if [ "$turns_max" -gt 0 ]; then
      turns=$(child_turns "${LOOP_CHILD_EVENTS:-}")
      if [ $(( turns - turns_base )) -ge "$turns_max" ]; then
        ended_by=turns
        timed_out=1
        kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
        sleep 1
        kill -KILL -"$child" 2>/dev/null || true
        break
      fi
    fi
    # …and the bound on the checks the gate itself would run (issue #882). `LOOP_GATE_RUNS_MAX` bounds the gate's own
    # full runs, and the same work a child starts outside `gate.sh` met no bound: it was counted after that child had
    # ended, so nothing acted on it. The count is taken here, in the same whole second as the turns, from the lines the
    # stream has gained and by the rules the report already classifies with (`lib.sh`'s `child_check_calls` counts a
    # call that **runs** one of the programs the gate's checks start, less the gate's own run). A check the gate would
    # not re-run — the focused case an item's diagnosis owes — is not counted, so the iteration
    # `guidelines/BuildGate.md` §2a gives a child keeps its room.
    if [ "$checks_max" -gt 0 ]; then
      chk_read=$(child_check_calls "${LOOP_CHILD_EVENTS:-}" "$checks_done")
      chk_new=${chk_read%% *}
      chk_done=${chk_read##* }
      case "$chk_new" in ''|*[!0-9]*) chk_new=0 ;; esac
      case "$chk_done" in ''|*[!0-9]*) chk_done=$checks_done ;; esac
      checks_done=$chk_done
      checks_seen=$(( checks_seen + chk_new ))
      if [ "$checks_seen" -ge "$checks_max" ]; then
        ended_by=checks
        timed_out=1
        kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
        sleep 1
        kill -KILL -"$child" 2>/dev/null || true
        break
      fi
    fi
    # …and the bound priced in what a turn costs rather than in how many there are (issue #760), read from the same
    # stream and in the same whole second as the turn count, so the wait pays one `wc` and one `jq` over the *new*
    # lines per second. The checkpoint names the reading once for every phase and, below the stop, splits the item's
    # worker into a continuation (issue #856, the check below). The recorded maximum ends the child, because past it
    # every further turn costs several times the first and that is a decision, not a slower convergence. Both are off
    # at `0`, which is why the whole block is skipped when neither key is recorded.
    if [ "$item_context_warn" -gt 0 ] || [ "$ctx_bound" -gt 0 ]; then
      ctx_read=$(child_context "${LOOP_CHILD_EVENTS:-}" "$ctx_lines")
      ctx_new=${ctx_read%% *}
      ctx_lines=${ctx_read##* }
      case "$ctx_new" in ''|*[!0-9]*) ctx_new=0 ;; esac
      case "$ctx_lines" in ''|*[!0-9]*) ctx_lines=0 ;; esac
      if [ "$ctx_new" -gt "$ctx_max" ]; then ctx_max=$ctx_new; fi
      if [ "$item_context_warn" -gt 0 ] && [ "$ctx_warned" -eq 0 ] && [ "$ctx_max" -ge "$item_context_warn" ]; then
        ctx_warned=1
        loop_emit context-warn ctx="$ctx_max" warn="$item_context_warn"
      fi
      if [ "$ctx_bound" -gt 0 ] && [ "$ctx_max" -ge "$ctx_bound" ]; then
        ended_by=context
        timed_out=1
        kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
        sleep 1
        kill -KILL -"$child" 2>/dev/null || true
        break
      fi
      # …and the checkpoint above the stop is taken rather than only printed (issue #856): the item's first worker is
      # the phase whose session the checkpoint can split, and the continuation #851 landed is the child it is handed
      # to. It is taken only when that child has left work in the tree, because a continuation is the kept patch applied
      # onto the branch's `HEAD` — with nothing to apply, the split would cut an item at 143 124 that the recorded stop
      # of 200 000 would have let run on, which is the bound #836 refused to lower. The tree is read in the same whole
      # second as the reading and not on every look (the wait looks twenty times a second), so a worker that writes its
      # change after the crossing is split on its next reading rather than missed. The stop's check stands first, so a
      # reading already past it is named by the stop.
      # The phase test keeps the ceiling structural: a continuation, a rework round, a gate, a tail and a review round
      # run on to their own bound, which is what keeps this from being a loop that never ends an item.
      if [ "$item_context_warn" -gt 0 ] && [ "$phase" = worker-prompt ] && [ "$ctx_max" -ge "$item_context_warn" ]; then
        ctx_has_work=0
        if [ -n "$(git -C "$LOOP_WORKTREE" status --porcelain 2>/dev/null)" ]; then ctx_has_work=1; fi
        if [ "$ctx_has_work" -eq 1 ]; then
          ended_by=checkpoint
          timed_out=1
          kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
          sleep 1
          kill -KILL -"$child" 2>/dev/null || true
          break
        fi
      fi
    fi
    # …and the one condition that is not a bound at all (issue #795): the record this wait ends on is written by
    # this shell's own subshell, so a `kill -0` on that pid is the only reading that tells a child still working
    # from a child whose writer was killed before it wrote the record. It is taken only while the record is absent,
    # and used only to end the wait: what #227 measured was a poll that believed a child gone — a pid the shell had
    # already reaped answers nothing, and a `wait` on it returns no status — so this reading never waits on the pid.
    # Measured 2026-11-06: the run started at 06:27:14Z was reparented to `systemd --user` when the session that
    # owned it died, sat in this poll 17 minutes into a 600-minute budget with its lock still held and its item still
    # `agent/in-progress`, and the cap would have ended the wait at 45 minutes — an item blocked for a dead child,
    # and a lock that would have refused every run after it.
    if [ ! -e "$status_file" ] && ! kill -0 "$child" 2>/dev/null; then
      ended_by=writer
      timed_out=1
      break
    fi
    sleep "$poll"
  done
  rc=0
  [ -e "$status_file" ] && rc=$(cat "$status_file" 2>/dev/null || printf 0)
  case "$rc" in ''|*[!0-9]*) rc=1 ;; esac
  # Nothing is left to a bare `wait`: a child that is still there after its own record was written, or after the
  # cap ended it, is ended here by pid, and the loop does not block on the shell's job table.
  if kill -0 "$child" 2>/dev/null; then loop_end_pid "$child"; fi
  active_child=""
  if [ "$timed_out" -eq 1 ]; then
    if [ "$ended_by" = deadline ]; then
      deadline_cut=1
      # The clock that ended this child is the run's budget, and the transcript says which one: "exceeded 58 s"
      # alone would name a cap that had 3 600 s to run, and the next run must read the deadline as the stop
      # instead of "the state being replaced never recorded a stop reason" (issue #227). The `LOOP-TIMEOUT` line
      # stays, because it is the one machine-readable line the run reads as its own timeout.
      deadline_at=$(jq -r '.run.deadlineAt // "an unrecorded time"' "$LOOP_STATE_FILE" 2>/dev/null)
      printf 'LOOP-TIMEOUT: the child exceeded %s s — the run%ss deadline at %s ended it and everything it started\n' \
        "$(( $(date +%s) - started ))" "'" "$deadline_at" >> "$2"
      printf 'LOOP-DEADLINE: the run%ss deadline passed at %s — the child running then was ended after %s s\n' \
        "'" "$deadline_at" "$(( $(date +%s) - started ))" >> "$2"
      stop_run "deadline passed at $deadline_at — the child running when it passed was ended"
    elif [ "$ended_by" = turns ]; then
      # The line the item's block is recorded from, and it says why in the item's own words: the bound, the role and the
      # key it is recorded in, and the count that reached it, so a person reading the blocked item does not have to
      # re-derive from a clock that had minutes left that the child was ended for what it spent (issue #686), nor
      # guess which phase had spent the item's pot (issue #784).
      printf 'LOOP-TIMEOUT: the child reached the recorded turn bound of %s turns (%s for the %s role) after %s turn(s) — the run ended it and everything it started\n' \
        "$turns_max" "LOOP_CHILD_TURNS_MAX_$(printf '%s' "$role" | tr '[:lower:]-' '[:upper:]_')" "$role" \
        "$(( $(child_turns "${LOOP_CHILD_EVENTS:-}") - turns_base ))" >> "$2"
    elif [ "$ended_by" = checks ]; then
      # The refusal `gate.sh` gives a second full run, made of the same reading (issue #882): the checks a child runs
      # by hand are the gate's own work, and past the recorded number a further one is not a measurement. The line
      # names the bound, its key and the count that reached it, as the turn bound's line does — the calls themselves
      # are in this transcript's stream, which is where the gate's own refusal cannot point.
      printf 'LOOP-TIMEOUT: the child reached the recorded check bound of %s checks (LOOP_CHILD_CHECKS_MAX) after %s check(s) — the run ended it and everything it started; those checks are the gate\047s own work and the gate runs them once\n' \
        "$checks_max" "$checks_seen" >> "$2"
    elif [ "$ended_by" = context ]; then
      # The reading the bound took, in the transcript the item is blocked on (issue #760), for the reason the turn
      # bound's line carries its count: a person reading the blocked item must not have to re-derive from an exit
      # status, on a clock with minutes left, which number ended the child. `LOOP-TIMEOUT` is the line the run reads
      # as its own timeout, so the reviewer-round and pre-gate paths that already read a cut transcript keep working.
      printf 'LOOP-TIMEOUT: the child reached the recorded context bound of %s tokens (%s) at %s tokens — the run ended it and everything it started\n' \
        "$ctx_bound" "$ctx_bound_key" "$ctx_max" >> "$2"
    elif [ "$ended_by" = checkpoint ]; then
      # The checkpoint a fresh session is the cheaper half of (issue #760) is the split this run takes (issue #856),
      # and the line says so: the key, the recorded value and the reading, in the same shape the stop's line has, so
      # the continuation block below reads which one ended the child from one line and no second reading of the stream.
      printf 'LOOP-TIMEOUT: the child reached the recorded context checkpoint of %s tokens (%s) at %s tokens — the run ended it and everything it started\n' \
        "$item_context_warn" LOOP_ITEM_CONTEXT_WARN "$ctx_max" >> "$2"
    elif [ "$ended_by" = writer ]; then
      # The event this wait ends on can only be written by a process that is running, so a writer that is gone is
      # the reason the wait ended — and the item's note must not read as a clock that fired (issue #795). The line
      # is the one the run reads as its own timeout, which is what records the item `blocked` with `bound` as its
      # mechanism instead of an escalation nobody asked for.
      printf 'LOOP-TIMEOUT: the child\047s writer (pid %s) vanished before it wrote its completion record — the run ended the wait\n' \
        "$child" >> "$2"
    else
      printf 'LOOP-TIMEOUT: the child exceeded %s s — the run ended it and everything it started\n' "$cap" >> "$2"
    fi
    # …and "everything it started" is more than the group: a descendant that gave itself its own session
    # (`setsid`) is a member of no group the signals above reach, so it kept mutating and restoring the
    # worktree for seven minutes after a run had recorded the child as ended, its last restore leaving that
    # tree dirty for the next run (measured 2026-10-22, issue #272). A survivor still names the tree it works
    # in as its working directory, which is the test `loop_pids_under` already owns (#213, #233), and each
    # process the sweep ends is named in the transcript a reader of the blocked item is handed.
    sweep=$(loop_end_pids_under "$LOOP_WORKTREE")
    if [ -n "$sweep" ]; then
      printf '%s\n' "$sweep" | sed 's/^/LOOP-ENDED: /' >> "$2"
    fi
    # …and the work it left is written down before the run moves on (issue #308): the item's own worker and the
    # rework worker of a refusing round both end here, both name the item the tree belongs to, and the tree itself is
    # still kept exactly as the child left it — the patch is a record *beside* it, never a replacement.
    keep_leftovers "$issue"
    if [ -n "$leftover_patch" ]; then
      printf 'LOOP-LEFTOVERS: %s — the work this run ended is kept there\n' "$leftover_patch" >> "$2"
      # …and this run now knows it may discard that tree to start its next item, because the record beside it is
      # one this run wrote (issue #664, the mid-run start refusal).
      own_leftovers="$own_leftovers$issue "
    fi
    rc="$LOOP_CHILD_TIMEOUT"
  fi
  : > "$stop"
  wait "$hb" 2>/dev/null
  active_hb=""
  rm -f "$stop"
  loop_emit child-finished phase="$phase" elapsed="$(fmt_elapsed $(( $(date +%s) - started )))" exit="$rc" \
    log_bytes="$(wc -c < "$2" 2>/dev/null || printf 0)"
  after=$(project_state)
  # What the in-time reading found, appended to the child's transcript so the item carries it: the paths as they
  # first differed, and the processes alive then with the ones of this child's own tree marked.
  outside_write_attributed=""
  outside_write_note=""
  if [ -s "$2.outside-write" ]; then
    outside_write_note=$(sed -n 's/^LOOP-OUTSIDE-WRITE-FIRST: //p' "$2.outside-write" | head -1)
    outside_write_attributed=$(sed -n 's/^LOOP-OUTSIDE-WRITE-ALIVE: .* · attributed=//p' "$2.outside-write" | head -1)
    case "$outside_write_attributed" in none|'') outside_write_attributed="" ;; esac
    cat "$2.outside-write" >> "$2"
  fi
  if project_state_differs "$before" "$after"; then
    outside_write_line "$before" "$after" >> "$2"
    [ -n "$outside_write_note" ] ||
      outside_write_note="the project tree changed while this process ran: $(outside_write_clause_of "$before" "$after")"
  fi
  return "$rc"
}

# The gate is where the item's first child ends (issue #744). Measured on the archive (`python3 tools/loop-spend.py`,
# 2026-11-02): of the 274 sessions that ran a gate, 1.43 B of their 1.83 B tokens were charged **after** the first
# `LOOP-GATE` line — 13 700 turns at 104 k of context per turn, against 7 868 at 52 k before it — because everything
# the derivation had put into that session (the whole of Phase A, the plan, the focused rounds, the tool results each
# of them injected) is re-read on every turn of the archive-and-evidence tail it then performs. The tail is the same
# work whatever happened before the gate: it reads the change's `tasks.md`, the gate's line and its own failing
# output, so it is a **fresh child**, charged the project's baseline once instead of the first child's context once
# per turn. What the first child found travels as prompt values — the change, the root cause, the red test and the
# gate's line — so the tail re-derives none of it; and `tail_after_gate` returns non-zero when the report carries no
# gate line at all (a skip, a falsification, a child the run ended), because then there is no tail to run.
#
# The gate's own console is cut out of the first child's stream here, once: its tool output lives in the item's
# event file (`tool_execution_end` is what the gate printed), and its closing answer in its report. Both are bounded,
# because a child that re-read a 200-line transcript on every turn is what this split exists to stop paying for —
# `jq` and a `tail` cost the run two forks, and no turn of the tail is charged for the rest.
tail_after_gate() { # tail_after_gate <gate report> <tail prompt> <tail report> — sets tail_started, tail_exit
  local gate_report="$1" prompt="$2" report="$3" gate change_name root_cause red_test tasks output archived_change
  gate=""
  gate=$(report_field "$gate_report" LOOP-GATE)
  [ -n "$gate" ] || return 1
  change_name=$(report_field "$gate_report" LOOP-CHANGE)
  root_cause=$(report_field "$gate_report" LOOP-ROOT-CAUSE)
  red_test=$(report_field "$gate_report" LOOP-RED-TEST)
  # The tasks the change planned, as the path it sits at — the one file the tail reads to know what the gate was
  # verifying. The change is completed before the gate and therefore archived by the time the tail runs (issue #837),
  # so both names are read: the archived directory of that change first, and the unarchived one for a tree the run
  # hands a change the archive step could not move. A gate report that names no change the tree holds says so instead
  # of handing the child a path that is not there.
  tasks="none — the gate report names no change whose tasks.md this tree holds"
  case "$change_name" in
    ''|-) ;;
    *) archived_change=$(loop_change_archive_dir "$worktree" "$change_name")
       if [ -n "$archived_change" ] && [ -f "$worktree/$archived_change/tasks.md" ]; then
         tasks="$worktree/$archived_change/tasks.md"
       elif [ -f "$worktree/$changes_family/$change_name/tasks.md" ]; then
         tasks="$worktree/$changes_family/$change_name/tasks.md"
       fi ;;
  esac
  output="${report%.md}-gate-output.txt"
  {
    printf '# the gate child\047s tool output, where the gate it ran printed — the last 200 lines\n'
    if [ -n "${LOOP_CHILD_EVENTS:-}" ] && [ -f "$LOOP_CHILD_EVENTS" ]; then
      jq -r 'select(.type == "tool_execution_end")
             | [.result?.content[]? | select(.type == "text") | .text? // empty] | join("\n")' \
        "$LOOP_CHILD_EVENTS" 2>/dev/null | tail -n 200
    fi
    printf '\n# the gate child\047s own closing answer\n'
    tail -n 200 "$gate_report" 2>/dev/null
  } > "$output" 2>/dev/null || : > "$output"
  render_prompt "$LOOP_DIR/prompts/worker-tail.md" "$prompt" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "SCRATCH_DIR=${scratch_dir:-$item_dir/scratch}" \
    "BASE=$base" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" "GATE_CLAIM=$gate" "GATE_OUTPUT=$output" \
    "CHANGE_NAME=${change_name:--}" "TASKS_PATH=$tasks" "ROOT_CAUSE=$root_cause" "RED_TEST=$red_test"
  tail_started=1
  run_child "$prompt" "$report"
  tail_exit=$?
  return 0
}

render_prompt() { # render_prompt <template> <out> [KEY=VALUE]…
  local template="$1" out="$2"; shift 2
  loop_render "$template" "$@" > "$out"
}

# The one section of `tools/loop/prompts/worker.md` that changes with the run's review shape (`{{GATE_STEP}}`). It is
# a section of the worker's own prompt and not a second worker prompt, because everything else about the child — the
# route, Phases A–C, the report block — is the same in both shapes and two copies of it would be two rules.
#
#   gate-own.md       the change child runs the gate; one child from the diagnosis to the gate (the shape an item
#                     worked with `--review off` keeps)
#   gate-deferred.md  the change child's session ends at the diff, a fresh child runs the gate (issue #758)
#
# The run substitutes this **first**: the section names `{{GATE_CMD}}` and `{{EVIDENCE_DIR}}` itself, so a section
# inserted after those pairs would hand the child a path called `{{GATE_CMD}}`.
gate_step_text() { # → the `{{GATE_STEP}}` section for the shape <on|off> asks for
  if [ "${1:-off}" = on ]; then cat "$LOOP_DIR/prompts/gate-deferred.md"; else cat "$LOOP_DIR/prompts/gate-own.md"; fi
}

# The readiness verdict line for the change the run handed an item, read again when the package is rendered: the
# item's answer is the copy in the worktree, so the copy is what is judged. A project that declares no check, or a
# path that is not there, is named on the line rather than left blank — the block quotes a verdict, not a promise.
change_readiness_of() { # <change dir> → the checker's one-line verdict
  local dir="$1" gate gate_path out line
  gate=$(jq -r '.checks.changeReady // ""' "$(loop_profile)" 2>/dev/null | head -1)
  if [ -z "$gate" ]; then
    printf 'LOOP-CHANGE-READY: refused gate-not-configured — the profile declares no checks.changeReady\n'
    return 0
  fi
  case "$gate" in
    /*) gate_path="$gate" ;;
    *) gate_path="$LOOP_ROOT/$gate" ;;
  esac
  if [ ! -f "$gate_path" ]; then
    printf 'LOOP-CHANGE-READY: refused gate-not-configured — checks.changeReady names %s and no file is there\n' "$gate"
    return 0
  fi
  out=$(bash "$gate_path" "$dir" 2>&1) || true
  line=$(printf '%s\n' "$out" | grep -m1 '^LOOP-CHANGE-READY:' || true)
  printf '%s\n' "${line:-LOOP-CHANGE-READY: refused change-unreadable=$dir — the check at $gate printed no verdict line}"
}

# The round's prompt, rendered once per attempt: the same contract and the same diff, only the file it is written
# to differs, so a retried answer is the same question asked again rather than a second review (#224).
# `CORRECTION_SCOPE`, `PRE_GATE` and `REVIEW_RESTART` are substituted **first**, not last: `loop_render` replaces
# one pair at a time on the template it has accumulated so far, and both sections name `{{DIFF_PATH}}` and
# `{{CHANGE_ROOT}}` themselves. A
# section inserted after those pairs would survive verbatim and hand the reviewer a file called `{{DIFF_PATH}}` — the
# round that exists to verify corrections pointing at a placeholder (item #245's round-1 review).
render_reviewer_prompt() { # render_reviewer_prompt <out file> [the corrections-only scope] [the pre-gate section] [the bounded-input section] [the restart section]
  render_prompt "$LOOP_DIR/prompts/reviewer.md" "$1" \
    "CORRECTION_SCOPE=${2:-}" "PRE_GATE=${3:-}" \
    "REVIEW_RESTART=${5:-}" \
    "REVIEW_INPUTS=${4:-}" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "DIFF_PATH=$diff_file" "DIFF_RANGE=$diff_base" "EVIDENCE_DIR=$item_dir" \
    "WORK_PACKAGE=${package:-}" \
    "REVIEW_EXCLUDE=${review_exclude_spec:-none}" "REVIEW_WALL_CLOCK=${child_max:-2700}"
}

# A review round the run's own bound ended is written on the branch as a **cut**, and the announcement of the same
# round is withdrawn in that one commit (issue #785): the branch is what a later run reads, and a round nobody
# answered beside a round the loop itself ended asks a person a question the loop already answered. Nothing is
# written for an item whose tree carries no archived change (`$change` empty) — there is no change to write against.
record_cut_round() { # <round>
  [ -n "${change:-}" ] || return 0
  bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$1" cut >&2 ||
    loop_emit cut-unrecorded round="$1" change="$change" --stderr
}

# The same statement for the other child the run ends: the rework worker of a refusing round (issue #788). Its
# record stays beside the refusal it reworks, because the round *was* asked and answered — withdrawing it would
# leave a branch a later pre-screen reads as a question its owner owes an answer to, and that question is the
# decision slot the run spent on itself.
record_rework_cut() { # <round>
  [ -n "${change:-}" ] || return 0
  bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$1" rework-cut >&2 ||
    loop_emit rework-cut-unrecorded round="$1" change="$change" --stderr
}

retry_delay_at() { # retry_delay_at <n> <delays> → the n-th delay, or the last one when there are fewer
  printf '%s\n' "$2" | awk -v n="$1" '{ v=$NF; for (i=1; i<=NF; i++) if (i==n) { v=$i; break } print v }'
}

