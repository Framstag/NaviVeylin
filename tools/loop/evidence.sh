#!/usr/bin/env bash
# evidence.sh — what the run records and reads back: the report vocabulary the item loop prints with, the
# outside-write rule, the pids the tree holds, the review's scope and the bound on its input, and the gate's
# attribution of a red run. Second in the engine's order (`layout.tsv`), after `lib.sh`; it calls nothing a
# later file defines, which is what `driver#aCallToALaterFilesFunctionFails` refuses.
#
# Sourced by `run.sh`, and by the owner files whose functions call one of these (`leftovers.sh`, `child.sh`), so
# the guard below is what makes a second read of it cheap and side-effect free.
[ -n "${LOOP_EVIDENCE_SH:-}" ] && return 0
LOOP_EVIDENCE_SH=1

# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

# A block names the class of the arm that stopped it (issue #865). The mechanism was an optional field written beside
# the result by three arms and omitted by the fifteen others, so `stopReason` was `null` on 108 of the archive's 127
# blocked items and the report's `stop` column printed `-` for each of them: a reader could not tell a tree that
# committed nothing from an escalation from a child that broke, and the count the report exists for — the items a bound
# ended — read 6 of 127. The result, the class and the reason are therefore written in one place, so an arm cannot
# block without naming the class it stopped on and the class cannot drift from the result it belongs to.
block() { # <class> <reason> — the item is blocked, with the class of the arm and the reason beside it
  outcome="blocked"
  stop_reason="$1"
  blocked_reason="$2"
}

# A report's own lines, with the fence a child may have put around its closing block taken off. A reviewer copies the
# closing block out of the prompt that shows it as a code block, and writes it as inline code — `` `LOOP-BLOCKING: 3` ``
# on one line and `` `VERDICT: FAIL` `` on the next — which is the same line, so one backtick at each end is removed
# before any field is read. Measured 2026-11-06 on item #788: both of its pre-gate reports ended exactly that way, each
# with the adapter's newline, and the run read a complete refusal as an answer the transport had cut (issue #793).
report_lines() { # report_lines <file>
  sed -e 's/^`//' -e 's/`$//' "$1" 2>/dev/null
}

report_field() { # report_field <file> <FIELD>
  report_lines "$1" | sed -n "s/^${2}: //p" | tail -1
}

# The bound a child's own `LOOP-TIMEOUT` line names, as an item's record carries it: the key the bound is recorded in
# and the figure that key holds (issue #923). `bound` alone says the run ended the item and not which of the three
# spend bounds it enforces it hit, so the archive could be totalled by the class and never by the key — the report
# printed 6 items a bound ended and no figure for what they had spent. The line is the one the child's transcript
# carries for a bound (`run.sh` writes it below): a turn bound names `LOOP_CHILD_TURNS_MAX_<ROLE>`, the context bound
# names the key the phase inherits (`LOOP_ITEM_CONTEXT_MAX`, or the role-qualified `…_REVIEWER`), and the check bound
# names `LOOP_CHILD_CHECKS_MAX`. A child the item's clock, the run's deadline or a vanished writer ended carries no
# key — none of the three is a value recorded in `loop.conf` — so it prints nothing, and the item's record keeps the
# class with no key rather than a key guessed from its neighbours.
bound_key_value() { # bound_key_value <timeout line> → "<KEY> <value>", or nothing
  local line="${1:-}" key="" value=""
  value=$(printf '%s' "$line" | sed -n 's/.*recorded turn bound of \([0-9][0-9]*\) turns.*/\1/p')
  key=$(printf '%s' "$line" | sed -n 's/.*turns (\([A-Z][A-Z0-9_]*\) for the .*/\1/p')
  if [ -z "$key" ]; then
    value=$(printf '%s' "$line" | sed -n 's/.*recorded context bound of \([0-9][0-9]*\) tokens.*/\1/p')
    key=$(printf '%s' "$line" | sed -n 's/.*context bound of [0-9][0-9]* tokens (\([A-Z][A-Z0-9_]*\)).*/\1/p')
  fi
  if [ -z "$key" ]; then
    value=$(printf '%s' "$line" | sed -n 's/.*recorded check bound of \([0-9][0-9]*\) checks.*/\1/p')
    key=$(printf '%s' "$line" | sed -n 's/.*check bound of [0-9][0-9]* checks (\([A-Z][A-Z0-9_]*\)).*/\1/p')
  fi
  [ -n "$key" ] && [ -n "$value" ] || return 0
  printf '%s %s\n' "$key" "$value"
}

# The word on the verdict line, and nothing else about it: the fence a child may have written around the line is not a
# cut answer, so every reader of `VERDICT:` reads the line where it stands (#793). A report that states no verdict
# prints nothing, which is the reading each caller already had for a report that never carried the line.
report_verdict() { # report_verdict <file> → PASS, FAIL, or empty
  local value
  value=$(report_field "$1" VERDICT)
  case "$value" in
    PASS*) printf 'PASS\n' ;;
    FAIL*) printf 'FAIL\n' ;;
  esac
}

# The child's own last words, when it wrote no report line: a process that never got to work the item prints a
# complaint instead of a verdict, and that line is the whole of what the run has to name (issue #212).
last_line() { # last_line <file>
  grep -v '^[[:space:]]*$' "$1" 2>/dev/null | tail -1
}

# Whether a child's answer arrived whole — the shape of the report, never its content (#224). Two facts make it
# checkable without a model: the adapter prints exactly one newline after every answer it hands the run, and the
# prompt asks for its closing line last (the reviewer's `VERDICT:`, after the count it licenses). An answer that
# ends mid-word, or carries no closing line at all, was cut on its way to the file. Measured 2026-10-20: a
# round-2 report ended `… and the post-restore gre` while its own text recorded all seven scenarios PASS, and the
# run read the absence as a refusal, took another worker round and escalated work that had passed. The fence a child
# may have put around that closing line is not a fact about its arrival: the line is read where it stands (#793).
answer_complete() { # answer_complete <report file> <closing-line regex>
  [ -s "$1" ] || return 1
  [ -z "$(tail -c 1 "$1" 2>/dev/null)" ] || return 1
  report_lines "$1" | grep -qE "$2" 2>/dev/null
}

# Whether a phase's own report is an answer at all (issue #810). A child that exits 0 and leaves a report with no
# closing line has answered nothing, and every arm the run decides an item from reads such a file as an answer it does
# not carry — so the shape of the answer is asked before any of them: the file, the newline the adapter prints after
# every answer, and the last line the phase's own prompt asks for. A child the run itself ended at a bound never returns 0
# (`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`), so the retry's `gate_exit -eq 0` guard leaves it as it stands (#224).
gate_answered() { # gate_answered <gate report> → 0 when the phase's answer arrived whole
  [ -z "$(report_field "$1" LOOP-TIMEOUT)" ] && [ -z "$(report_field "$1" LOOP-DEADLINE)" ] &&
    answer_complete "$1" '^LOOP-(GATE|VERDICT):'
}

# Whether the **change** child's report carries any line the run's arms read (issue #827). The phase that runs the
# gate is entered on the report's `LOOP-DIFF-READY` alone, and every arm the item is decided from reads a verdict or
# a gate line — so a child that exits 0 and writes nothing falls between the two: the round before the gate, the gate
# child and the tail are skipped without a word, and the outcome reads the item's own tree. Measured 2026-11-07 on
# item #85: `worker-report.md` was 0 B and the child's stream ended `auto_retry_end success=false`, its branch carried
# a commit, and the run recorded `blocked` on "reported success but no archived change appears in the run's diff" — a
# reason that claims a child reported success for a child that reported nothing. The same silence was the run's own
# stop for #95 of that run, where the tree stood at the base; the two readings are one fact about the phase and
# differ only in the tree. This is what separates that silence from the three answers the prompt asks for, so the
# gate phase is never skipped on a report that says nothing, and the reason names the phase.
change_answered() { # change_answered <change report> → 0 when the report carries a line the arms read
  [ -n "$(report_field "$1" LOOP-DIFF-READY)" ] || [ -n "$(report_field "$1" LOOP-GATE)" ] ||
    [ -n "$(report_field "$1" LOOP-VERDICT)" ] ||
    [ -n "$(report_field "$1" LOOP-TIMEOUT)" ] || [ -n "$(report_field "$1" LOOP-DEADLINE)" ]
}

# A case that fails in the gate and passes when it is run once more is a flake, and one rerun in the base tree cannot
# tell it from the delta's own red (issue #861). Measured 2026-11-11: #849's gate recorded
# `worktree#keepsSourcesNotBuildOutput` as its single failure, that case passed at HEAD (two runs) and at #849's own gate
# head, and the item blocked for 2 714 s on a red that reproduced at neither. The second look is the item's own
# worktree — the tree the gate itself ran in, which already carries the build artifacts a fresh tree does not — so the
# probe costs one case and not one build. `0` is the whole of it: a case that fails again, a tree that cannot run it and
# a bound all answer `delta`, so a probe that could not be taken fails the item exactly as it did before (the item's own
# direction: "re-run the failing case once ... `delta` when it fails again").
flake_probe() { # <tree> <rerun> <bound> → flaked|delta
  local root="${1:-}" rerun="${2:-}" bound="${3:-}" status=1
  [ -n "$root" ] && [ -n "$rerun" ] || { printf 'delta\n'; return 0; }
  case "$bound" in ''|*[!0-9]*) bound=900 ;; esac
  ( cd "$root" && timeout "$bound" bash -c "$rerun" ) >/dev/null 2>&1
  status=$?
  case "$status" in 0) printf 'flaked\n' ;; *) printf 'delta\n' ;; esac
}

# Which tree fails the case a red gate names (issue #853). The record a red tally left said what the gate reported —
# the failing case's name — and nothing about the tree that made it fail, so a pre-existing red and the delta's own red
# were the same verdict. `guidelines/BuildEvidence.md` §4d is the project's rule for the question, and both halves of
# it are here: the tally, and the base commit the item descends from.
#
# The facts are the gate's own log — `tools/loop/gate.sh` writes it beside its tallies — read through the one derivation
# `tools/gate-attribute.sh` owns of the failing case and the rerun command; nothing about a case is parsed a second
# time here. `delta` is the case the item's own `LOOP-RED-TEST` line already names, and it pays no base tree: the
# owner's recorded decision on #853 (`2026-11-12T16:00:04Z`, option 1) is that a red case the change itself named is
# not a pre-existing red. Any other case is rerun once in a detached tree at the run's base, inside one case's bound:
# only a rerun that ran and failed answers `base`. A tree that cannot be made, a case the base cannot select (exit 2),
# a bound (124/137) and a log naming no case or no rerun command all answer `unattributable` — a red gate that cannot
# be attributed must not fail the item a second time.
attribute_red_gate() { # <tally> <tally dir> <change root> <base> → delta|flaked|base|unattributable, nothing when not red
  local tally="${1:-}" tally_dir="${2:-}" root="${3:-}" base="${4:-}"
  local log block case_name rerun tmp bound status
  [ "$(gate_tally_state "$tally")" = red ] || return 0
  log="$tally_dir/gate.log"
  if [ -z "$tally_dir" ] || [ ! -r "$log" ]; then printf 'unattributable\n'; return 0; fi
  block=$(bash "$LOOP_DIR/../gate-attribute.sh" "$log" 2>/dev/null) || block=""
  case_name=$(printf '%s\n' "$block" | sed -n 's/^case: //p' | head -1)
  rerun=$(printf '%s\n' "$block" | sed -n 's/^rerun: //p' | head -1)
  case "$case_name" in ''|*'#'*) ;; *) case_name="" ;; esac
  case "$rerun" in ''|'not derivable from this log') rerun="" ;; esac
  [ -n "$case_name" ] && [ -n "$rerun" ] || { printf 'unattributable\n'; return 0; }
  # The bound is the profile's per-case bound, read once for both reruns this function can take: the one the item's own
  # worktree pays and the one a detached base tree pays.
  bound=$(loop_conf_value LOOP_CASE_MAX_SECONDS)
  case "$bound" in ''|*[!0-9]*) bound=900 ;; esac
  # The item's own red test is the free text `state.sh done` keeps in `metrics.redTest`; its case names are
  # `<group>#<name>` tokens of it, so the token is matched whole and never as a prefix of a longer name. Such a case pays
  # no base tree, and since issue #861 it is run once more in the item's own worktree before it is called the delta's.
  if printf '%s' "${red_test:-}" | tr ' \t\n' '\n\n\n' | grep -qxF -- "$case_name"; then
    flake_probe "$root" "$rerun" "$bound"
    return 0
  fi
  tmp=$(mktemp -d "${TMPDIR:-/tmp}/loop-attribute.XXXXXX") || { printf 'unattributable\n'; return 0; }
  # `mktemp` made the directory and `git worktree add` wants to create its own.
  rmdir "$tmp" 2>/dev/null || true
  if ! git -C "$root" worktree add --detach "$tmp" "$base" >/dev/null 2>&1; then
    rm -rf "$tmp" 2>/dev/null || true
    printf 'unattributable\n'
    return 0
  fi
  ( cd "$tmp" && timeout "$bound" bash -c "$rerun" ) >/dev/null 2>&1
  status=$?
  # The tree goes whatever the rerun answered: a base tree left behind is a worktree the next run reads as its own.
  git -C "$root" worktree remove --force "$tmp" >/dev/null 2>&1 || rm -rf "$tmp" 2>/dev/null || true
  case "$status" in
    0) flake_probe "$root" "$rerun" "$bound" ;;
    2|124|137) printf 'unattributable\n' ;;
    *) printf 'base\n' ;;
  esac
}

# The reviewer's severity count, and nothing else about its prose (#205). A report may say `VERDICT: FAIL` and
# count zero blocking findings, which is a pass whose notes are recorded; a report that says nothing at all is
# empty here, and an empty count keeps the loop's conservative reading — an old-shaped review never passes.
blocking_count() { # blocking_count <report file> → the count, or empty when the line is absent or malformed
  local value
  value=$(report_field "$1" LOOP-BLOCKING)
  case "$value" in ''|*[!0-9]*) return 0 ;; esac
  printf '%s\n' "$value"
}

# What a round that refused nothing leaves behind: its `NOTE:` findings, one per line as bullets, or the path of
# the report when it graded none. This is what the item's closure comment and its state note carry, so a note is
# recorded rather than spent on another round (#205).
review_notes() { # review_notes <report file>
  local notes
  notes=$(sed -n 's/^NOTE: //p' "$1" 2>/dev/null)
  if [ -z "$notes" ]; then
    printf 'no NOTE: finding — the report is %s\n' "$1"
  else
    printf '%s\n' "$notes" | sed 's/^/- /'
  fi
}

slugify() { # a change name out of an item's title: lowercase, dashes, bounded
  printf '%s' "$1" |
    sed -e 's/^[^A-Za-z]*//' -e 's/§[0-9]*//g' |
    tr '[:upper:]' '[:lower:]' |
    sed -e 's/[^a-z0-9]\+/-/g' -e 's/^-//' -e 's/-$//' |
    cut -c1-40 |
    sed -e 's/-$//'
}

# The project's own tracked state, read around every child. A child is started in the project (a fresh
# process resolves the project's own tool policy at the directory it is started in), and the worktree is
# the only tree a phase may write — so a write the child left in the project's tree is caught here and
# never carried into the item's commit (D4, "one writer per tree").
project_state() {
  # The tree's own answer to "am I a work tree" travels with its status: a run whose project repository is flipped
  # to `core.bare=true` by something it started keeps answering `git status` — with nothing — while every merge
  # and commit in it is refused (#241). Compared before and after a child, the two lines disagree, and that
  # disagreement is the report.
  printf 'work-tree=%s\n' "$(git -C "$LOOP_ROOT" rev-parse --is-inside-work-tree 2>/dev/null | head -1)"
  # `-uall` because `git status` otherwise collapses an untracked directory to its root (`?? architecture/`), and a
  # build directory is exactly that shape: the pattern that names it (`*/bin`) then has nothing to match, and the very
  # write #958 measured would still stop an item. The landing reads its own tree the same way (issue #962).
  git -C "$LOOP_ROOT" status --porcelain -uall 2>/dev/null
}

# The difference between two readings, which is what a report needs: the lines one reading carries and the other
# does not. A constant sentence said only that the tree had changed, so neither the run nor a later reader of the
# item could tell which path to restore — or whether a path had changed at all, since the tree's own answer to
# "am I a work tree" travels in the same reading (issue #285).
project_state_diff() { # project_state_diff <before> <after> → the lines only one of the two readings carries
  { printf '%s\n' "$1"; printf '%s\n' "$2"; } | sed '/^[[:space:]]*$/d' | loop_drop_ignorable_writes | sort | uniq -u
}

# One clause out of a list of lines, quoted as it was read and bounded: a damaged tree carries thousands of
# `git status` lines, and a note is a sentence a person reads on the tracker.
outside_write_clause() { # <cap> — stdin: one line per item → `a; b; …`, `; and N more` past the cap
  awk -v cap="$1" 'NF { n++; if (n <= cap) printf "%s%s", (n > 1 ? "; " : ""), $0 }
                   END { if (n > cap) printf "%s and %d more", (cap > 0 ? "; " : ""), n - cap }'
}

# What changed, as the clause a note carries — the difference between the two readings, bounded. A constant
# sentence said only that the tree had changed, so neither the run nor a later reader could tell which path to
# restore (issue #285).
outside_write_clause_of() { # <before> <after> → one clause, quoted as read
  local paths
  paths=$(project_state_diff "$1" "$2" | cut -c1-120 | outside_write_clause 10)
  printf '%s' "${paths:-(no status line differs — only the work-tree answer changed)}"
}

# The processes of the tree the run started, asked of the kernel one call per level: the group the child leads
# (`set -m` before it is started) and everything descended from it, because a descendant that gave itself its own
# session is in no group the cap signals but is still this child's (the #272 survivor). One call per moved process
# instead of a sweep over every process on the host, which is what the attribution is read with and cannot afford:
# measured 2026-10-24 (issue #288), a host-wide sweep of 711 processes costs 1.0 s, so an answer read after it
# names processes that lived a second ago.
loop_tree_pids() { # <pid> → one pid per line: the child, its process group, and everything below it
  local child="${1:-}" frontier next seen=" " out="" pid
  [ -n "$child" ] || return 0
  frontier="$child $(pgrep -g "$child" 2>/dev/null | tr '\n' ' ')"
  while [ -n "${frontier// /}" ]; do
    next=""
    for pid in $frontier; do
      case "$seen" in *" $pid "*) continue ;; esac
      seen="$seen$pid "
      out="$out$pid"$'\n'
      next="$next $(pgrep -P "$pid" 2>/dev/null | tr '\n' ' ')"
    done
    frontier="$next"
  done
  printf '%s' "$out"
}

# Whether a pid still names the project as its working directory, and the worktree — the one tree a phase may
# write — as something else. The note's list and the attribution both ask it, so it is asked in one place.
pid_stands_outside_worktree() { # <pid> <project root> <worktree> → 0 when it does
  local cwd
  cwd=$(readlink "/proc/$1/cwd" 2>/dev/null) || return 1
  cwd=${cwd% (deleted)}
  [ -n "$cwd" ] || return 1
  case "$cwd" in "$2"|"$2"/*) ;; *) return 1 ;; esac
  case "$cwd" in "$3"|"$3"/*) return 1 ;; esac
  return 0
}

# The parent of a pid, read from `/proc/<pid>/stat` and not from `ps`: the attribution asks it for every ancestor
# of every candidate, and a fork per question would cost what the reading cannot pay (issue #288) — so the line is
# read with the shell's own `read` and split in the shell (issue #950). The command name is the one field that can
# carry spaces and parentheses, so the fields are taken after the **last** `") "`: a `sed` per hop is one more
# process on a host the pool has already filled, and the reading's cost is charged to the reader that names who
# stood in the project. A pid that has gone answers nothing, and the group's stderr redirect keeps that from being
# printed as a redirection error of the run itself.
pid_ppid() { # <pid> → its parent, or nothing when the process is gone
  local line rest
  { read -r line < "/proc/$1/stat"; } 2>/dev/null || return 0
  case "$line" in *') '*) ;; *) return 0 ;; esac
  line=${line##*) }
  rest=${line#* }
  case "$rest" in ''|"$line") return 0 ;; esac
  rest=${rest%% *}
  case "$rest" in ''|*[!0-9]*) return 0 ;; esac
  printf '%s\n' "$rest"
}

# Whether a process got into the project's own tree *from* the worktree: an ancestor of it worked in the worktree,
# so the process that stands in the project deliberately left the tree it may write. This is what separates a
# writer from the run's own child chain, which `run_child` starts **in the project** — the child itself and every
# shell it was started through stand there for the whole item, so marking those would make every difference in
# the project's tree decide every item, which is the verdict #274 and #286 refused (issue #288).
# The ancestors' working directories are not asked of the kernel one hop at a time: the reading walks that chain
# for every candidate of the child's tree, and a `readlink` per hop was a process per hop on a host the pool has
# already filled — 5 253 ms of one reading's 6 000, measured at forty instances (issue #950). The standers of the
# worktree are read **once**, in one pass of their own taken before the sweep asks about the project root again —
# one pass per reader, not one per reading; an ancestor that is already gone is not among them, which is the
# answer the kernel gave for it before.
pid_left_the_worktree() { # <pid> <the pids standing in the worktree, space-delimited> → 0 when an ancestor is one
  local p hops=0 set=" $2 "
  p=$(pid_ppid "$1")
  while [ -n "$p" ] && [ "$p" != "0" ] && [ "$p" != "1" ] && [ "$hops" -lt 64 ]; do
    case "$set" in *" $p "*) return 0 ;; esac
    p=$(pid_ppid "$p")
    hops=$((hops + 1))
  done
  return 1
}

# Milliseconds since the epoch, for the cost the reading states about itself (issue #950): `date`'s sub-second
# field is a GNU extension, so a host whose `date` prints something else answers in whole seconds — a coarse
# figure rather than a wrong one.
loop_now_ms() {
  local t
  t=$(date +%s%3N 2>/dev/null)
  case "$t" in ''|*[!0-9]*) t=$(( $(date +%s) * 1000 )) ;; esac
  printf '%s' "$t"
}

# How many processes the host carries, which is the number a walk of `/proc` pays for. The glob is expanded by
# the shell itself, so the figure costs one `readdir` and no process: a count taken by forking per entry would be
# the defect it exists to report (issue #950).
loop_proc_count() {
  local entries
  entries=(/proc/[0-9]*)
  [ -d "${entries[0]}" ] || { printf '0'; return 0; }
  printf '%s' "${#entries[@]}"
}

# The processes the project shows that no phase may be working in: a survivor still names the tree it works in as
# its working directory, which is the question `loop_pids_under` already answers for the cap's own sweep (issue
# #272), and a working directory inside the worktree is a tree a phase *may* write. A caller that is itself
# standing in the project passes its own pid as an extra skip; the sweep is run from `/` so its own shell is never
# part of the answer.
outside_write_pids() { # <root> <worktree> [pid to skip …] → one `<pid> <command>` line per process
  local root="$1" wt="$2" pid skip s; shift 2
  for pid in $(loop_pids_under "$root"); do
    skip=0
    for s in "$@"; do [ "$pid" = "$s" ] && skip=1; done
    [ "$skip" -eq 1 ] && continue
    pid_stands_outside_worktree "$pid" "$root" "$wt" || continue
    printf '%s %s\n' "$pid" "$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | cut -c1-80 | sed 's/[[:space:]]*$//')"
  done
}

# The one line a child's transcript gains when the project's own tree moved while it ran: what changed, by the
# status line it changed to, and who else could have changed it (issue #285: #274's round-2 worker was blocked by a
# line that named no path, and the work was lost to a decision nobody could answer).
outside_write_line() { # outside_write_line <before> <after> → one `LOOP-OUTSIDE-WRITE: …` line
  local holders
  holders=$( cd / && outside_write_pids "$LOOP_ROOT" "$LOOP_WORKTREE" | cut -c1-120 | outside_write_clause 5 )
  printf 'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran: %s' \
    "$(outside_write_clause_of "$1" "$2")"
  [ -z "$holders" ] || printf '; process(es) whose working directory is inside the project: %s' "$holders"
  printf '\n'
}

# What decides an item: a difference the run can tie to the tree it started — a process of the child's own tree
# was alive when the tree changed — or a run that declared the tree exclusive (`--strict-outside-write yes`). A
# difference it can tie to nothing is recorded and does not decide anything (issue #286).
outside_write_decides() { # → 0 when the recorded difference decides the phase
  [ -n "${outside_write_note:-}" ] &&
    { [ -n "${outside_write_attributed:-}" ] || [ "$strict_outside_write" = "yes" ]; }
}

# The difference the run recorded while this item's child ran, said where a reason would otherwise report that the
# item was not worked at all (issue #983): a child that writes the caller's own tree by absolute path — the shape of
# item #894 of the run of 2026-11-20T09:58:14Z, six modified paths and one untracked file under the project root —
# leaves the item's branch at the run's base and the work in the caller's tree, and the run read that as `blocked`
# `uncommitted` on "nothing was committed" with no path named anywhere. The reading is the run's own outside-write
# walk, already recorded for every phase (`worker_write_note`, then the gate child's and the tail's); what this adds
# is the sentence that reads the two facts together, so a reader of the reason knows where the work went. It is a
# qualification and never a replacement: the arm's own sentence stays, and an item whose record carries no reading
# gets an empty string and keeps exactly what it wrote before.
#
# The clause is conditional and places nothing (review round 1, 2026-11-21). The reading it names is usually the one
# the run declined to place — `attributed=none`, which is what item #894's own record carries — and this loop's spec
# says a difference nobody can place is not evidence about one child (`agent-issue-loop`, "A difference the run cannot
# attribute does not decide the item"): a peer session writes the same tree. So the sentence says where the work is
# *if* the difference is this item's, and never that the child wrote it.
item_outside_write_clause() { # → ` — …if that difference is this item's work it is in the caller's tree…: <reading>`
  local note="${worker_write_note:-}"
  [ -n "$note" ] || note="${gate_write_note:-}"
  [ -n "$note" ] || note="${outside_write_note:-}"
  [ -n "$note" ] || return 0
  printf " — the run nevertheless recorded a difference in the project's own tree while that child ran: if that difference is this item's work it is in the caller's tree and not on the item's branch — %s" "$note"
}

# The difference read *while the child runs*, with the processes that were alive at that moment and the ones of
# them that belong to this child's own tree. A write is only evidence about the child when the two coincide, and
# the child is gone by the time its transcript is written — so the reading has to be taken during its life, from
# the heartbeat that is already ticking beside it (issue #286's attribution step).
#
# The child's own tree is therefore read FIRST and whole, and the sweep over the host comes after it (issue #288):
# the sweep is the whole host's walk of `/proc` — 1.0 s for 711 processes when #288 measured it by `readlink`, and
# a writer that was alive when the difference was read is gone by the time it ends, so an attribution read after
# it records a difference the run *can* place as one it cannot. The sweep answers who *else* stands in the
# project, which is context for a reader of the transcript and may cost what it costs; it must not be what decides.
#
# What both halves cost is a fact about the host, and it is the *reader's* fact: the writer a difference names is
# standing while the reading runs, so a reading that takes seconds answers about a moment that has passed. The
# figures are measured here and printed with the answer (issue #950): a transcript that prints `(none)` states
# beside it how many processes were walked and what each half cost, so a reader can tell a reading taken inside
# the second from one taken twenty-two seconds later — measured on this repository's host at a pool of forty
# instances, 1 010 processes: 6 194 ms for the child's tree and 16 125 ms for the sweep, against a writer that
# stands eight seconds.
outside_write_seen() { # <before> <now> <child> <own pid> → the `LOOP-OUTSIDE-WRITE-*` lines
  local before="$1" now="$2" child="$3" own="$4" alive attributed="" pid t0 t1 t2 in_worktree
  t0=$(loop_now_ms)
  in_worktree=$( cd / && loop_pids_under "$LOOP_WORKTREE" | tr '\n' ' ' )
  for pid in $(loop_tree_pids "$child"); do
    [ "$pid" = "$own" ] && continue
    pid_stands_outside_worktree "$pid" "$LOOP_ROOT" "$LOOP_WORKTREE" || continue
    pid_left_the_worktree "$pid" "$in_worktree" || continue
    attributed="${attributed:+$attributed,}$pid"
  done
  t1=$(loop_now_ms)
  alive=$( cd / && outside_write_pids "$LOOP_ROOT" "$LOOP_WORKTREE" "$own" | cut -c1-120 | outside_write_clause 5 )
  t2=$(loop_now_ms)
  # The FIRST line is printed only when a path no child could have written is not the whole difference (issues #958,
  # #962): the readings differ whenever an editor writes into the project tree, and a line saying so is what decides
  # the phase (`outside_write_decides`), so the filter has to be applied where the difference is read and not only
  # where it is described.
  if project_state_differs "$before" "$now"; then
    printf 'LOOP-OUTSIDE-WRITE-FIRST: the project tree changed while this process ran: %s — read while the child was alive at %s\n' \
      "$(outside_write_clause_of "$before" "$now")" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  fi
  printf 'LOOP-OUTSIDE-WRITE-ALIVE: %s · attributed=%s\n' "${alive:-(none)}" "${attributed:-none}"
  printf 'LOOP-OUTSIDE-WRITE-COST: procs=%s attributed_ms=%s standers_ms=%s\n' \
    "$(loop_proc_count)" "$((t1 - t0))" "$((t2 - t1))"
}

# The diff a review round is handed is the change, not the generated output a change is archived beside:
# item #126's round-3 diff (issue #204) carried 9 935 changed lines, 9 364 of them gate HTML and XML, against
# 112 lines of Kotlin. Which directories hold generated output is a fact about a project's change records,
# so the exclusion is a recorded pathspec (`loop.conf`'s `LOOP_REVIEW_EXCLUDE_PATHSPEC`) and this phase names
# no path of its own (D16). It excludes a *directory tree* and not a file extension on purpose: a `.txt` a
# reviewer must read can live outside an evidence directory, and an `.md` blended into one is still generated.
write_review_diff() { # write_review_diff <base> <file>
  local spec
  spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
  if [ -n "$spec" ]; then
    git -C "$worktree" diff "$1"..HEAD -- . "$spec" > "$2"
  else
    git -C "$worktree" diff "$1"..HEAD > "$2"
  fi
  # The tree the diff was written from, beside it (issue #819): the commit and a digest of the change, which is what the
  # gate's own record names for the tree it measured. A gate run before a correction and a diff written after it then
  # read as two trees, which is the whole of what the reviewer's item 3 asks — and the digest is `lib.sh`'s
  # `loop_change_identity`, derived from the run's own base whatever range this diff is taken over, so a correction
  # round's narrower diff is still compared against the change the gate measured. Both moments take that one derivation,
  # so both take one path set: the generated trees this diff leaves out leave the digest out too.
  loop_change_identity "$worktree" "${base:-$1}" > "$2.identity"
}

# The round a corrective refusal buys is handed the corrections, not the change again (issue #245; the decision
# recorded on the item, option 3). The refusing round said every finding was subtractive, so what the next
# reviewer has to decide is whether those findings were answered — and the whole change since the base is the
# wrong reading surface for it: it is the #204 measurement (9 935 changed lines, 9 364 of them generated output)
# at a smaller scale, and two rounds have already graded it. So the range is the refused round's own HEAD
# (`git diff <refused>..HEAD`, the ref `write_review_diff` is called with) and the findings are the refusing
# report, quoted. Nothing is promised that the run does not know: the range is named, and a finding the tree
# still carries is the round's to refuse.
review_correction_scope() { # <refused head> <refusing report path>
  local head="$1" report="$2"
  printf '## This round verifies the corrections, not the change again\n\n'
  printf 'The round before this one refused the change and stated `LOOP-CORRECTIONS-ONLY: yes`: every finding it\n'
  printf 'raised was subtractive, so one worker pass was what it asked for. This round decides whether that pass\n'
  printf 'answered them.\n\n'
  printf -- '- the diff under review at `{{DIFF_PATH}}` is `git diff %s..HEAD` — the correction round alone, not\n' "$head"
  printf '  the whole change since the base. A hunk of the change is absent from it on purpose; read the change\n'
  printf '  itself in `{{CHANGE_ROOT}}` where a correction needs its context.\n'
  printf -- '- the refusing round is `%s`, quoted in full below. Each of its findings is what this round\n' "$report"
  printf '  decides: a finding the tree still carries is a `BLOCKING:` finding, and one the correction answers is\n'
  printf '  recorded as closed. A correction whose hunk is missing is an unanswered finding, not an absence to\n'
  printf '  report against the diff.\n\n'
  printf '### The refusing round%ss findings\n\n' "'"
  cat "$report"
  printf '\n'
}

review_input_bytes() { # <file> → its size, or `0` when it cannot be read
  local size
  size=$(wc -c < "$1" 2>/dev/null | tr -d ' \n')
  case "$size" in ''|*[!0-9]*) printf '0\n' ;; *) printf '%s\n' "$size" ;; esac
}

# The head of one part at the cap, and the number of lines it kept. A partial line is dropped rather than handed as
# if it were a whole one: a hunk cut in the middle of a line reads as a hunk that is there.
review_input_head() { # <cap> <source> <destination> → the lines kept
  local cap="$1" src="$2" dest="$3" lines
  { head -c "$cap" "$src" 2>/dev/null || true; } | sed '$d' > "$dest" || : > "$dest"
  lines=$(wc -l < "$dest" 2>/dev/null | tr -d ' \n')
  case "$lines" in ''|*[!0-9]*) lines=0 ;; esac
  printf '%s\n' "$lines"
}

# The last line of a capped part, so the round reads the marker before it reads the head as the file.
review_input_mark() { # <capped file> <the file it heads> <cap> <the cut index>
  printf '\n[LOOP-CUT: %s is the head of %s — the input a round is handed is bounded at %s bytes a part, and\neverything this cut left out is named, by path and line range, in %s. Do not page past this line to recover it]\n' \
    "$1" "$2" "$3" "$4" >> "$1"
}

# The three parts, capped, and the index of what the cap left out. Prints the `{{REVIEW_INPUTS}}` section and sets
# `review_input_cut_scale`; prints nothing and writes nothing when every part is under the cap.
bound_review_inputs() { # <diff file> [<the round-before-the-gate report>]
  local diff="${1:-}" report="${2:-}" cap label cut_file family_dir change_dir
  local diff_bytes report_bytes change_bytes=0 parts=0 cut_parts=0 total=0 cut_total=0 kept_lines used size line rel
  local index_diff="" index_report="" index_change="" rest="" pct
  review_input_cut_scale=""
  [ -n "$diff" ] && [ -f "$diff" ] || return 0
  cap=$(loop_conf_value LOOP_REVIEW_INPUT_MAX_BYTES)
  case "$cap" in ''|*[!0-9]*) return 0 ;; esac
  [ "$cap" -gt 0 ] || return 0
  label="${diff%.diff}"
  cut_file="$label.cut.md"
  rm -f "$cut_file" "$label.scale" "$label.before-gate.md" "$label.change-files.md" "$label.change-files.list" 2>/dev/null || true

  # 1. the diff — the part the round pages today, and the one the item's own round ended on.
  parts=1
  diff_bytes=$(review_input_bytes "$diff")
  total=$diff_bytes
  if [ "$diff_bytes" -gt "$cap" ]; then
    cut_parts=$((cut_parts + 1))
    kept_lines=$(review_input_head "$cap" "$diff" "$diff.head")
    index_diff=$(awk -v keep="$kept_lines" '
      /^\+\+\+ / { p = substr($0, 7); sub(/\t.*$/, "", p); next }
      /^@@ / && NR > keep && p != "" && p != "/dev/null" {
        h = $0; sub(/^@@ [^+]*\+/, "", h); sub(/ .*$/, "", h)
        split(h, a, ","); n = (a[2] == "" ? 1 : a[2] + 0)
        if (n == 0) next
        printf "- `%s` lines %d-%d (%d lines)\n", p, a[1] + 0, a[1] + n - 1, n
      }' "$diff")
    cut_total=$((cut_total + diff_bytes - $(review_input_bytes "$diff.head")))
    review_input_mark "$diff.head" "$diff" "$cap" "$cut_file"
    mv "$diff.head" "$diff"
  fi

  # 2. the paths the round before the gate named — its findings, where a path is written down as one.
  if [ -n "$report" ] && [ -f "$report" ]; then
    parts=$((parts + 1))
    report_bytes=$(review_input_bytes "$report")
    total=$((total + report_bytes))
    if [ "$report_bytes" -gt "$cap" ]; then
      cut_parts=$((cut_parts + 1))
      kept_lines=$(review_input_head "$cap" "$report" "$label.before-gate.md")
      index_report="- \`$report\` lines $((kept_lines + 1))-$(wc -l < "$report" | tr -d ' \n') — the findings beyond the cap, named where they were written
"
      cut_total=$((cut_total + report_bytes - $(review_input_bytes "$label.before-gate.md")))
      review_input_mark "$label.before-gate.md" "$report" "$cap" "$cut_file"
    fi
  fi

  # 3. the change's own files, as an inventory: a round paging an evidence tree is reading names and sizes, so a
  # bounded list of them is the part, and the tree itself is not the run's to cut.
  change_dir=""
  if [ -n "${change:-}" ] && [ -n "${worktree:-}" ]; then
    family_dir=$(loop_change_family)
    change_dir=$(loop_change_archive_dir "$worktree" "$change")
    [ -n "$change_dir" ] || change_dir="$family_dir/$change"
    [ -d "$worktree/$change_dir" ] || change_dir=""
  fi
  if [ -n "$change_dir" ]; then
    parts=$((parts + 1))
    ( cd "$worktree" || exit 1; find "$change_dir" -type f | LC_ALL=C sort ) > "$label.change-files.list" 2>/dev/null || true
    : > "$label.change-files.md"
    used=0
    while IFS= read -r rel; do
      [ -n "$rel" ] || continue
      size=$(review_input_bytes "$worktree/$rel")
      change_bytes=$((change_bytes + size))
      line=$(printf '%s B\t%s' "$size" "$rel")
      if [ "$used" -lt "$cap" ] && [ $((used + ${#line} + 1)) -le "$cap" ]; then
        printf '%s\n' "$line" >> "$label.change-files.md"
        used=$((used + ${#line} + 1))
      else
        rest="$rest$rel
"
      fi
    done < "$label.change-files.list"
    rm -f "$label.change-files.list" 2>/dev/null || true
    total=$((total + change_bytes))
    if [ -n "$rest" ]; then
      cut_parts=$((cut_parts + 1))
      while IFS= read -r rel; do
        [ -n "$rel" ] || continue
        index_change="$index_change- \`$rel\` lines 1-$(wc -l < "$worktree/$rel" 2>/dev/null | tr -d ' \n')
"
      done <<< "$rest"
      cut_total=$((cut_total + change_bytes - used))
      review_input_mark "$label.change-files.md" "$change_dir" "$cap" "$cut_file"
    else
      rm -f "$label.change-files.md" 2>/dev/null || true
    fi
  fi

  [ "$cut_parts" -gt 0 ] || return 0
  pct=$(awk -v c="$cut_total" -v t="$total" 'BEGIN { printf "%d", (t > 0 ? (c * 100 + t / 2) / t : 0) }')
  review_input_cut_scale="$cut_parts of $parts parts over the ${cap} B cap · ${cut_total} of ${total} B cut (${pct} %) · ${cut_file}"
  # The scale is written down beside the index as well as printed: the run calls this in a `$( )`, so the variable it
  # sets never reaches the round's own shell, and the state write that records the cut (`state.sh done --review-input`)
  # reads it back from here.
  printf '%s\n' "$review_input_cut_scale" > "$label.scale"
  {
    printf '# The round%ss input, and what the bound cut — item #%s, `%s`\n\n' "'" "$issue" "$label"
    printf 'Bounded at %s bytes a part (`LOOP_REVIEW_INPUT_MAX_BYTES`, `tools/loop/loop.conf`): %s.\n' "$cap" "$review_input_cut_scale"
    printf 'This file is the index of what the cap left out — one entry a line, a path and the lines it holds — so a\n'
    printf 'finding can name a range the round was not handed. It is an index and not a part: read it, never page it as\n'
    printf 'if the rest of a part were behind it.\n\n'
    printf '## the parts this round is handed\n\n'
    printf -- '- the change%ss diff: `%s`%s\n' "'" "$diff" "$(if [ -n "$index_diff" ]; then printf ' — its head'; fi)"
    [ -z "$index_report" ] || printf -- '- the round before the gate: `%s` — its head\n' "$label.before-gate.md"
    [ -z "$index_change" ] || printf -- '- the change%ss own files: `%s` — the inventory%ss head, of `%s`\n' "'" "$label.change-files.md" "'" "$change_dir"
    [ -z "$index_diff" ] || printf '\n## the change%ss diff — what the cut left out\n%s\n' "'" "$index_diff"
    [ -z "$index_report" ] || printf '\n## the round before the gate — what the cut left out\n%s\n' "$index_report"
    [ -z "$index_change" ] || printf '\n## the change%ss own files — what the cut left out\n%s\n' "'" "$index_change"
  } > "$cut_file"

  # The section is placed on the bullet%ss own line in `prompts/reviewer.md` and starts with its blank line, so an
  # empty one leaves the prompt byte for byte as it was: a round under the cap reads what it always read.
  printf '\n\n**Your input this round is bounded, and what the bound cut is named for you.** %s.\n\n' "$review_input_cut_scale"
  printf 'A part over the cap is handed as its **head** — cut at a line boundary and marked `[LOOP-CUT: …]` — and every\n'
  printf 'line the cut left out is named, by path and line range, in `%s`, which you read by path before you grade.\n' "$cut_file"
  printf 'A `BLOCKING:` finding may name a range the head did not carry. **Do not page a part past its `[LOOP-CUT:`\n'
  printf 'marker** to recover the rest: that cut is the run%ss own bound and not a tool window, so the index is what\n' "'"
  printf 'the round was handed instead of those lines, and a scenario only the cut could show stays `UNPROVEN`.\n'
}

# What the bound cut from the round whose diff this is, in the one line `bound_review_inputs` composed — empty when
# that round's three parts were all under the cap, and read after the call because the call runs in a subshell.
review_input_scale_of() { # <diff file> → the recorded scale, or nothing
  local label="${1%.diff}"
  if [ -f "$label.scale" ]; then cat "$label.scale" 2>/dev/null || true; fi
}

# Whether the share the scale line records is past half — the precondition the round before the gate is read on
# (issue #969). The figure is the one the line already carries (`… cut (68 %) …`), so the decision and the record are
# the same string and a later reader re-takes it from `<round>.scale` or from the item's `metrics.reviewInput`; no
# measurement is taken here and no key is added. A round whose input is cut past half cannot read the diff whole; the
# record of 2026-11-18T22:18Z does not make such a cut the cause of the rounds that ended at the bound — none of the
# three pre-gate rounds `LOOP_CHILD_TURNS_MAX_REVIEWER_PRE_GATE=110` ended had its pre-gate input cut past half.
# Half and not "any cut": #963's 5 % cut is a diff a round reads whole enough to judge, and skipping it would drop a
# round that was going to answer. An empty or unreadable scale is no cut and skips nothing.
review_input_cut_halved() { # <the recorded scale> → true when the recorded share is past half
  local scale="${1:-}" pct
  [ -n "$scale" ] || return 1
  pct=$(printf '%s\n' "$scale" | sed -n 's/.* (\([0-9][0-9]*\) %).*/\1/p' | tail -1)
  case "$pct" in ''|*[!0-9]*) return 1 ;; esac
  [ "$pct" -gt 50 ]
}

# Whether the run may start another child at all. A restart begun past the run's own deadline is ended on its first
# second, and its `LOOP-DEADLINE` reason would replace the bound's — the distinction #829 and #785 paid for — so a
# clock that has run out is a cut and never a restart.
review_restart_before_deadline() {
  local deadline_epoch
  deadline_epoch=$(jq -r '.run.deadlineEpoch // 0' "${LOOP_STATE_FILE:-}" 2>/dev/null)
  case "$deadline_epoch" in ''|*[!0-9]*) deadline_epoch=0 ;; esac
  [ "$deadline_epoch" -le 0 ] || [ "$(date +%s)" -lt "$deadline_epoch" ]
}

# The section a restarted round reads: what the cut round wrote before the run ended it, and the finding that this is
# the same round. It is a **placeholder value** (`prompts/reviewer.md`'s `{{REVIEW_RESTART}}`) rather than a block
# appended to the prompt, because the prompt is rendered per attempt: the first attempt's carries nothing, the
# restart's carries this, and an appended block would be in both or in neither.
review_restart_scope() { # <the cut report> <its LOOP-TIMEOUT line> <which round it is> [<the round before the gate's report>]
  local report="$1" bound="$2" what="$3" earlier="${4:-}" cap findings
  cap=$(loop_conf_value LOOP_REVIEW_INPUT_MAX_BYTES)
  case "$cap" in ''|*[!0-9]*) cap=0 ;; esac
  printf '## This round continues a round the run%ss own bound ended\n\n' "'"
  printf 'The %s was ended by a bound the run enforces, before it answered: `%s`.\n' "$what" "$bound"
  printf 'This is the same round read again in a fresh session, and not a second one: it is the round the run'
  printf "%ss\n" "'"
  printf 'review budget counts, its announcement on the branch stands, and what it answers is what the item is read\n'
  printf 'from. It is handed to you because the reading it was doing is the reading it still owes.\n\n'
  printf -- '- what that round wrote before the run ended it: `%s`\n' "$report"
  findings=$(grep -E '^(BLOCKING|NOTE):' "$report" 2>/dev/null || true)
  if [ -z "$findings" ]; then
    printf '  It wrote no `BLOCKING:` or `NOTE:` line down, so there is nothing to decide first: read the diff the\n'
    printf '  way this task asks and grade it.\n'
  else
    printf '  Its findings are the first thing this round decides — a finding nobody answered is the one a person\n'
    printf '  would be asked about:\n\n'
    if [ "$cap" -gt 0 ]; then
      printf '%s\n' "$findings" | head -c "$cap"
    else
      printf '%s\n' "$findings"
    fi
    printf '\n\n  A finding the tree still carries is a `BLOCKING:` finding, and one the change answers is recorded as\n'
    printf '  closed. Then grade the change as this task asks: the findings are the start of the round, not all of it.\n'
  fi
  if [ -n "$earlier" ] && [ -f "$earlier" ]; then
    printf -- '- the round that read this diff before the gate: `%s` — what it found is an input to this round,\n' "$earlier"
    printf '  never a substitute for it.\n'
  fi
  printf '\nDo not page past a `[LOOP-CUT:` marker to recover what the input bound cut: the index names what this\n'
  printf 'round was handed instead of those lines.\n'
}

review_restart_note() { # <which round it is> <its LOOP-TIMEOUT line> <the cut report>
  local line
  line="$(bound_key_value "$2")"
  line="$1 · ${line%% *}=${line##* } · ${3##*/}"
  if [ -n "$review_restart_line" ]; then
    review_restart_line="$review_restart_line; $line"
  else
    review_restart_line="$line"
  fi
}

# Test results are the gate's evidence, and the run reads them out of the worktree after a worker — so a
# directory an earlier item wrote is indistinguishable from this item's own gate. The kept worktree carries them
# across items, and a child the round cap ends leaves the half-written state of its own gate behind (issue #253:
# item #116's killed Gradle left `auto/build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin`,
# and the next gate failed with `java.io.EOFException` on infrastructure instead of on a case; before the run read the
# gate's own tally directory these leftovers also defeated the "no test-results XML exists in the tree" refusal, because
# there was always an XML — the sweep is what keeps the directory the reading below reads clean, and what makes that
# reading an answer about this item's gate).
#
# So every worker starts on a worktree without one. What is removed is a directory named `test-results` whose
# parent is a `build` directory, inside the worktree, through `loop_scratch_remove`, whose four refusals are the
# whole safety argument: only inside the tree, never through a symlink, never a path git tracks. The reviewer's
# child is deliberately not covered: the round reads the gate result the worker just wrote, and taking it out of
# the tree would hand the reviewer a tree the worker's evidence has been removed from.
clear_stale_test_results() {
  local dir rel cleared=""
  [ -d "$worktree" ] || return 0
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    rel="${dir#"$worktree"/}"
    loop_scratch_remove "$worktree" "$rel" --apply || continue
    cleared="${cleared:+$cleared }$rel"
  done < <(find "$worktree" -type d -name test-results -path '*/build/test-results' 2>/dev/null | sort)
  [ -n "$cleared" ] || return 0
  printf 'run: cleared %s from the worktree before this child — test results an earlier child wrote, which its gate would have counted\n' "$cleared"
}

