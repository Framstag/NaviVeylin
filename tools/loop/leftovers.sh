#!/usr/bin/env bash
# leftovers.sh — what the run keeps, adopts, merges and discards: the worktree it holds after a refusal, the
# branch a landed child leaves behind, the markers that say what was already recorded, and the retry that
# follows a dirty tree. Third in the engine's order (`layout.tsv`): it reads `lib.sh` and the outside-write rule
# `evidence.sh` declares, and nothing a later file defines.
#
# Sourced by `run.sh` and by `child.sh`, which supervises the work this file adopts; the guard below makes the
# second read side-effect free.
[ -n "${LOOP_LEFTOVERS_SH:-}" ] && return 0
LOOP_LEFTOVERS_SH=1

# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"
# shellcheck source=tools/loop/evidence.sh
source "$here/evidence.sh"

adopt_finished_attempt() { # <issue> <branch> → 0 adopted and closed, 5 not adoptable (the reason printed)
  local issue="$1" branch="$2" out merged_branch merged_commits merged_change merged_evidence
  # The tree this adoption merges into, as the run's own reading of it stands (#862). The pre-flight refused a project
  # tree that was not clean (#798) and no child of this run can have started before the pre-screen, so a difference
  # the adoption meets here arrived after the run began and this run can tie it to no child of its own — the run's
  # rule for such a difference is that it is recorded and decides nothing (#286), and `worktree.sh` names the paths it
  # reads beside it, because a record that names none is a sentence nobody can act on (#285). `--strict-outside-write
  # yes` is the run that asked for that tree to be judged, so there the difference still refuses the merge.
  local land_args=()
  land_args+=(--outside-write-decides "$(if [ "$strict_outside_write" = yes ]; then printf yes; else printf no; fi)")
  land_args+=(--outside-write-note "read before the pre-screen: this run's pre-flight found the project tree clean and no child of this run has started since (#798), so the difference is one this run can tie to nothing of its own")
  out=$(bash "$LOOP_DIR/worktree.sh" adopt-item "$issue" "$branch" ${land_args[@]+"${land_args[@]}"} 2>&1) || {
    # Kept for the caller that records the refusal, exactly as `merge_refusal` is kept for the run's own merge
    # (#794): the text `adopt-item` printed is the item's record, not a line for the terminal alone.
    adoption_refusal="$out"
    printf '%s\n' "$out"
    return "$LOOP_REFUSE"
  }
  printf '%s\n' "$out"
  # The merge moved the base branch itself, so the base the run recorded is the commit every later item must
  # descend from — without this the next `start-item` would refuse the run's own merge as a stale base.
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)") || base=""
  [ -z "$base" ] || loop_state set-base "$base" >/dev/null 2>&1 || true
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^adopted: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\) ·.*/\1/p')
  merged_change=$(printf '%s\n' "$out" | sed -n 's/.* · change \([^ ·]*\) ·.*/\1/p')
  merged_evidence=$(printf '%s\n' "$out" | sed -n 's/.* · evidence \(.*\)$/\1/p')
  local close_args=("$issue" \
    --reason "a previous attempt finished this item and was merged, not re-run" \
    --evidence "branch $merged_branch (commits $merged_commits), $worktree/openspec/changes/archive/$merged_change, evidence checked: $merged_evidence")
  # The commit the landing made is what the closure's claim rests on (#715): `base` is the branch this function
  # has just re-read, the same commit it records as `merged.sha` below.
  [ -z "$base" ] || close_args+=(--landed "$base")
  if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
  local closure="no"
  if loop_labels close "${close_args[@]}" >&2; then
    closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"
  fi
  loop_state done "$issue" --result done --verdict closed --change "$merged_change" \
    --closed "$closure" --note "$out" \
    --merged-branch "$merged_branch" --merged-commits "$merged_commits" --merged-evidence "$merged_evidence" \
    --merged-sha "$base" \
    --review-verdict off \
    >/dev/null
  score_landings
  # The branch is merged, so the work is in the base and the branch itself is a leftover: the run removes
  # the branch it merged (a cherry-picked adoption is not an ancestor of the base, so git's safe delete
  # refuses it and the branch stays for the sweep — which asks the same question by patch identity).
  bash "$LOOP_DIR/worktree.sh" release-branch "$branch" >&2 || true
  printf 'item #%s: closed (merged %s — a finished attempt the base did not have) · %s review round(s)\n' \
    "$issue" "$merged_branch" 0
}

merge_committed_branch() { # <issue> <branch> → 0 merged and the line printed, 5 refused (the reason in merge_refusal)
  # `base` is deliberately not in this list: the merge moves the run's own base, and every later item is cut from
  # it. Declared here, the assignment below wrote a copy — the state file advanced and the run kept the commit it
  # read once at its start, so the next item was handed a base several merges old (issue #252).
  local issue="$1" branch="$2" out landing_line landing_write_note landing_write_decides refused_path
  # What the landing hands `merge-item` about the project's own tree (#862): the item's own readings, as its children
  # took them — the first one that recorded a difference names the paths, and whether the difference decides is what
  # those readings said about it, never a second judgement taken here.
  local land_args=()
  landing_write_note=""
  [ -z "${worker_write_note:-}" ] || landing_write_note="$worker_write_note"
  if [ -z "$landing_write_note" ] && [ -n "${gate_write_note:-}" ]; then landing_write_note="$gate_write_note"; fi
  if [ -z "$landing_write_note" ] && [ -n "${outside_write_note:-}" ]; then landing_write_note="$outside_write_note"; fi
  landing_write_decides=""
  if [ "${worker_write_decides:-0}" = 1 ] || [ "${gate_write_decides:-0}" = 1 ]; then landing_write_decides=yes; fi
  if [ -z "$landing_write_decides" ] && [ -n "$landing_write_note" ]; then
    if outside_write_decides; then landing_write_decides=yes; else landing_write_decides=no; fi
  fi
  # The run that declared the tree exclusive made every difference a verdict, and that verdict is the landing's too.
  [ "$strict_outside_write" != yes ] || landing_write_decides=yes
  [ -z "$landing_write_decides" ] || land_args+=(--outside-write-decides "$landing_write_decides")
  [ -z "$landing_write_note" ] || land_args+=(--outside-write-note "$landing_write_note")
  merge_refusal=""
  out=$(bash "$LOOP_DIR/worktree.sh" merge-item "$issue" "$branch" ${land_args[@]+"${land_args[@]}"} 2>&1) || {
    merge_refusal="$out"
    # The record of the refusal (issue #883): the branch this call asked to land, and the lines the refusal printed
    # after its `onto the base:` — the paths the porcelain read named. A refusal that names no path (a base that
    # moved, a branch this tree does not hold) records the branch alone and the report prints no path clause for it.
    merge_refused_branch="$branch"
    merge_refused_paths=()
    while IFS= read -r refused_path; do
      [ -n "$refused_path" ] || continue
      merge_refused_paths[${#merge_refused_paths[@]}]="$refused_path"
    done <<< "$(printf '%s\n' "$out" | sed -n '/onto the base:/,$p' | sed '1d')"
    printf '%s\n' "$out" >&2
    return "$LOOP_REFUSE"
  }
  printf '%s\n' "$out"
  # The difference the landing merged over is the item's record of it (#862): a run that lands over work it could not
  # place says so where a reader looks for a reading, beside the ones its children took — `outside-write` for the
  # worker's, `outside-write-gate`, `outside-write-tail` and `outside-write-review-<round>` for the later ones.
  landing_line=$(printf '%s\n' "$out" | sed -n 's/^LOOP-OUTSIDE-WRITE: //p' | head -1)
  if [ -n "$landing_line" ]; then
    loop_state step A --name outside-write-landing --status ok --note "$landing_line" >/dev/null 2>&1 || true
    progress note "$landing_line"
  fi
  # The merge moved the base branch itself, so the base the run recorded is the commit every later item must
  # descend from — without this the next `start-item` would refuse the run's own merge as a stale base.
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)") || base=""
  # A base the tree moved to but the state does not carry is not a warning to swallow: the run hands the next item
  # a commit the tree has left, which is the same defect one step further on. The merge is refused instead, so the
  # item blocks with its branch named rather than closing on a base nobody can reproduce (issue #252).
  if [ -z "$base" ] || ! loop_state set-base "$base" >/dev/null 2>&1; then
    merge_refusal="the base could not be recorded after the merge — $(loop_base_ref) did not resolve, or the run's state refused it, so the state and the tree would disagree about what every later item descends from"
    # This refusal is about the base record rather than the merge, and it is still a landing the run could not take:
    # the branch is the fact the run holds (issue #883), and the refusal named no path.
    merge_refused_branch="$branch"
    merge_refused_paths=()
    printf '%s\n' "$merge_refusal" >&2
    return "$LOOP_REFUSE"
  fi
  # The branch and its commits are what a later reader checks the closure against: git lists the branch, nothing
  # else does once the run's state is replaced by the next run's.
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^merged: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\)$/\1/p')
  # The commit the landing made is the base this function has just re-read (`base` above), not the abbreviated one
  # the merge line prints: `gh run list --commit` matches a commit, and the score of the pipeline it caused is what
  # the report's `landings:` line prints (issue #709).
  merged_sha="$base"
}

# The third trend figure is read here, once per landing, by the state's writer (issue #709): the run has just landed
# the item and the record carries the commit, so one `gh run list --commit` answers the pipeline that commit caused.
# It is best-effort on purpose — a landing the tracker does not answer for yet is recorded unscored and named by the
# report, and a network fault may never decide an item the run has already closed on its work.
score_landings() {
  loop_state score-landings 2>&1 | sed 's/^/  /' >&2 || true
}

# The pre-screen asks git for a previous attempt's branch and records it as a decision (#175), and the start's
# own check refuses one that appears while the run is open (#163). Both are the *same* decision this function
# makes once, before anything is escalated for them, so an item whose attempt finished is merged and closed
# instead of being handed to the owner.
adopt_finished_exclusions() {
  [ "$dry_run" -eq 0 ] || return 0
  local issue branch attempt_state
  jq -r --arg d "$decision_agent" '
    .excluded[] | select(.verdict == $d) | select((.evidence // "") | startswith("fix/issue-"))
    | [(.issue | tostring), .evidence] | @tsv
  ' "$LOOP_STATE_FILE" 2>/dev/null |
  while IFS=$'\t' read -r issue branch; do
    [ -n "$issue" ] || continue
    if adopt_finished_attempt "$issue" "$branch" >&2; then
      continue
    fi
    # The status the loop used to discard says which refusal this was (#794). `adoptable` is the branch's own
    # answer that nothing on it is the owner's, so what refused the merge is the tree it lands in, the base it
    # replays onto or the replay itself — a later run finds the first clean once the dirt is gone, so the item is
    # work and the refusal is its own result, recorded beside the exclusion. Any other state is the evidence's
    # refusal, and exactly as before it stays the pre-screen's decision.
    attempt_state=$(bash "$LOOP_DIR/worktree.sh" branch-state "$issue" "$branch" 2>/dev/null | cut -f1)
    [ "$attempt_state" = adoptable ] || continue
    loop_state adoption-refused "$issue" --reason "$adoption_refusal" >/dev/null ||
      printf 'run: #%s — the adoption refusal could not be recorded, so its exclusion stands as the pre-screen read it\n' \
        "$issue" >&2
  done
}

# The work a child the run ended left is written down before the run moves on or stops (issue #308): the tracked
# diff, each untracked file, and the ignored files under the profile's record directory — this project's unarchived
# changes live under `openspec/changes/`, which is gitignored, and an unarchived change is exactly what a killed
# worker leaves behind. Every other ignored path is *named* in the header rather than copied, so no build directory
# lands in the patch, and so is any file above the size bound. Measured 2026-10-25: the item whose gate hung was
# repaired by hand from `git status` and a diff, because the run recorded that the tree was dirty and wrote the diff
# nowhere, and the only repair it named was `--reset-worktree`.
keep_leftovers() { # keep_leftovers <item> → sets $leftover_patch (+ the file it wrote)
  local item="$1" dir patch path size named
  leftover_patch=""
  case "$item" in ''|*[!0-9]*) return 0 ;; esac
  [ -d "$LOOP_WORKTREE" ] || return 0
  patch=$(loop_leftover_patch_of "$item")
  dir=$(dirname "$patch")
  mkdir -p "$dir" 2>/dev/null || return 0
  {
    printf '# the work #%s left in %s\n' "$item" "$LOOP_WORKTREE"
    # `diff HEAD` and not `diff`: a refused `end-item` leaves the work **staged** — the commit is what failed — and
    # `git diff` shows neither a staged addition nor a staged deletion, so the tracked half of the record would lose
    # exactly the work the loop's own commit path had staged (issue #308, measured on
    # `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`: `demo-fix.txt` was staged by the refused `end-item` and
    # absent from a `git diff`-only patch).
    printf '# tracked changes (staged and unstaged, against HEAD):\n'
    git -C "$LOOP_WORKTREE" diff HEAD --no-color 2>/dev/null || true
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      size=$(wc -c < "$LOOP_WORKTREE/$path" 2>/dev/null || printf 0)
      if [ "$size" -gt 262144 ]; then
        printf '# %s: %s bytes, not copied\n' "$path" "$size"
      else
        diff -u --label "/dev/null" --label "b/$path" /dev/null "$LOOP_WORKTREE/$path" 2>/dev/null || true
      fi
    done < <(git -C "$LOOP_WORKTREE" ls-files --others --exclude-standard 2>/dev/null)
    if [ -n "$record_dir" ]; then
      printf "# ignored files under %s (the unarchived changes of this project):\n" "$record_dir"
      while IFS= read -r path; do
        [ -n "$path" ] || continue
        size=$(wc -c < "$LOOP_WORKTREE/$path" 2>/dev/null || printf 0)
        if [ "$size" -gt 262144 ]; then
          printf '# %s: %s bytes, not copied\n' "$path" "$size"
        else
          diff -u --label "/dev/null" --label "b/$path" /dev/null "$LOOP_WORKTREE/$path" 2>/dev/null || true
        fi
      done < <(git -C "$LOOP_WORKTREE" ls-files --others --ignored --exclude-standard -- "$record_dir" 2>/dev/null)
    fi
    named=0
    printf '# ignored paths not carried above (their contents are not in this patch):\n'
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      if [ -n "$record_dir" ]; then case "$path" in "$record_dir"/*) continue ;; esac; fi
      if [ "$named" -lt 40 ]; then printf '# %s\n' "$path"; fi
      named=$((named + 1))
    done < <(git -C "$LOOP_WORKTREE" ls-files --others --ignored --exclude-standard 2>/dev/null)
    if [ "$named" -gt 40 ]; then printf '# … and %s more ignored path(s), not listed\n' "$((named - 40))"; fi
  } > "$patch" 2>/dev/null || { rm -f "$patch"; return 0; }
  [ -s "$patch" ] || { rm -f "$patch"; return 0; }
  leftover_patch="$patch"
}

# The gate on the work a cut keeps (issue #885). An item the run's own bound ends keeps what its child left — the patch
# `keep_leftovers` writes and the tree exactly as it stands — and until this, the caller was owed a resume action and
# nothing else: the run of 2026-11-13T05:47:11Z cut #862 at 200 297 tokens and #875 at 205 503 (38.0 Mtok), recorded no
# gate result for either, and the recorded precedent for a patch applied one run later is that it fails on a base that
# has moved (2 of 2). So the gate runs **on the tree the cut left**, before the item ends, while that work is still in
# the one tree it was taken from — and the item still ends `cut`: the result is recorded beside the cut and is not a
# verdict, so the ceiling that keeps the loop finite is untouched and incomplete work is not manufactured into
# `blocked`.
#
# Three things it must not do. It must not start after the run's own deadline, for the reason the continuation above is
# refused there: a phase started past the clock is ended on its first second, replacing the cut's own reason with the
# deadline's. It must not run over work there is none of — a patch carrying no file is `keep_leftovers`' own preamble.
# And it must not invent a gate where the change root carries none: a profile that names a gate a tree does not hold has
# no gate to run, and a `failed` recorded for a command that never existed is a finding about the work nobody made.
gate_the_kept_work() { # <item> <item directory> → sets $cut_gate_result (+ the log it was read from)
  local item="$1" dir="$2" prog="" files=0 now=0 deadline_epoch=0 bound=0 left=0
  cut_gate_result=""
  cut_gate_log=""
  case "$item" in ''|*[!0-9]*) return 0 ;; esac
  [ -n "$LOOP_GATE_CMD" ] && [ -d "$LOOP_WORKTREE" ] || return 0
  # The work there is, counted from the patch the cut left rather than from a second reading of the tree: the patch is
  # the record of what the child wrote, and `state.sh` counts its files from the same line.
  if [ -n "$leftover_patch" ] && [ -r "$leftover_patch" ]; then
    files=$(grep -c '^+++ ' "$leftover_patch" 2>/dev/null)
    case "$files" in ''|*[!0-9]*) files=0 ;; esac
  fi
  [ "$files" -gt 0 ] || return 0
  # The gate the profile names, resolved from the change root the children run in (a command that is a path) or as the
  # path it already is: absent everywhere means the tree has no gate, and this run measured none.
  prog=${LOOP_GATE_CMD%% *}
  case "$prog" in
    /*) [ -x "$prog" ] || return 0 ;;
    *) [ -x "$LOOP_WORKTREE/$prog" ] || return 0 ;;
  esac
  now=$(date +%s)
  deadline_epoch=$(jq -r '.run.deadlineEpoch // 0' "$LOOP_STATE_FILE" 2>/dev/null)
  case "$deadline_epoch" in ''|*[!0-9]*) deadline_epoch=0 ;; esac
  if [ "$deadline_epoch" -gt 0 ] && [ "$now" -ge "$deadline_epoch" ]; then return 0; fi
  # Bounded by what the run has left and by the profile's own step bound, whichever runs out first: a gate given the
  # step bound with two minutes to the deadline is a gate the deadline ends mid-step, and one given the deadline alone
  # has no bound on a host whose step hangs.
  bound="$LOOP_GATE_STEP_MAX_SECONDS"
  case "$bound" in ''|*[!0-9]*) bound=1800 ;; esac
  if [ "$deadline_epoch" -gt 0 ]; then
    left=$(( deadline_epoch - now ))
    if [ "$left" -lt "$bound" ]; then bound="$left"; fi
  fi
  [ "$bound" -gt 0 ] || return 0
  cut_gate_log="$dir/cut-gate.log"
  ( cd "$LOOP_WORKTREE" && timeout "$bound" bash -c "$LOOP_GATE_CMD" ) > "$cut_gate_log" 2>&1
  case "$?" in
    0) cut_gate_result="passed" ;;
    124) cut_gate_result="bound" ;;
    *) cut_gate_result="failed" ;;
  esac
  return 0
}

worktree_is_dirty() {
  git -C "$LOOP_ROOT" worktree list --porcelain 2>/dev/null | grep -qx "worktree $worktree" &&
    [ -n "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}

# The item whose work a worktree holds: `start-item` writes the item's branch into the tree's own branch marker,
# so a tree somebody left mid-work still says whose it is. One reading, two readers — the start-time pre-flight
# below, and the start a refusal stops mid-run (issue #284) — because a second copy of the rule is a second rule.
owner_of_marker() { # owner_of_marker <branch> → the item that branch names, or nothing
  printf '%s\n' "${1:-}" | sed -n 's#^fix/issue-\([0-9][0-9]*\)-.*#\1#p' | head -1
}

own_leftovers_of() { # own_leftovers_of <item> → 0 when this run ended that item and kept its work as a patch
  case "${1:-}" in ''|*[!0-9]*) return 1 ;; esac
  case "$own_leftovers" in *" $1 "*) ;; *) return 1 ;; esac
  [ -s "$(loop_leftover_patch_of "$1")" ]
}

# The other way a tree is left dirty by work this run is responsible for (measured 2026-11-10). A child the run ended at a
# bound is not the only producer: an item this run has **recorded** can leave its tree dirty behind it — the run of
# 2026-11-10T08:19:24Z recorded #807 at 11:27:56Z and stopped 262 of its 480 minutes later over that item's leftovers
# (`.pi/issue-loop/runs/2026-11-10T08:19:24Z.json`), which the refusal below read as "another item's" dirt because
# `own_leftovers` is only appended by the cap arm. The rule that refusal protects is that nothing is discarded
# unrecorded — and an item this run recorded, whose leftovers `keep_leftovers` has just written to its patch, is
# recorded: the record is the item's own state entry and the patch beside it. Over the archive, five of the ten runs
# that ended on this refusal had leftovers belonging to an item they had recorded themselves, 1 699 minutes unspent
# (`jq -r '.run.stop.reason'` and the run's own `items[]` over `.pi/issue-loop/runs/*.json`).
recorded_here() { # recorded_here <item> → 0 when this run's own state already holds that item
  case "${1:-}" in ''|*[!0-9]*) return 1 ;; esac
  [ -f "$LOOP_STATE_FILE" ] || return 1
  [ "$(jq -r --argjson i "$1" '[.items[] | select(.issue == $i)] | length' "$LOOP_STATE_FILE" 2>/dev/null)" != "0" ]
}

# The work of an item this run recorded is written down and then discarded, so the next item can start: the same repair
# the cap arm's leftovers take (issue #664), one condition wider. It is a refusal to *discard* without a record, never a
# refusal to *work*, and the record is the item's own patch beside its own state entry.
recorded_leftovers_of() { # recorded_leftovers_of <item> → 0 when this run recorded it and its work is now in its patch
  recorded_here "${1:-}" || return 1
  own_leftovers_of "$1" && return 0
  keep_leftovers "$1"
  [ -n "$leftover_patch" ] || return 1
  own_leftovers="$own_leftovers$1 "
  printf 'keeping the work #%s left after this run recorded it: %s\n' "$1" "$leftover_patch"
  return 0
}

# A tree whose only leftovers are the module's gitlink — the module sits on another pin and its own tree is clean —
# is not work: nothing inside the module was written, and placing the pin the base records loses nothing. The run
# repairs it where it used to stop on dirt no item owns (measured 2026-10-28): the pin was two unpublished commits ahead of
# the module's remote, the placement at `create` ended in `upload-pack: not our ref`, and the dirt that followed
# ended a run with 600 minutes left after 69 seconds. The project's configuration names the module, so a project
# without one answers no.
submodule_only_dirt() { # <the leftovers `git status --porcelain` printed> → 0 when only the module's pin differs
  local path line
  path=$(loop_conf_value LOOP_SUBMODULE_PATH)
  [ -n "$path" ] && [ -f "$worktree/.gitmodules" ] || return 1
  [ -n "${1:-}" ] || return 1
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    case "$line" in " M $path") ;; *) return 1 ;; esac
  done <<< "$1"
  [ -z "$(git -C "$worktree/$path" status --porcelain 2>/dev/null)" ]
}

place_submodule_pin() { # → 0 when the tree is clean afterwards; the placement is `worktree.sh`'s, not a second copy
  bash "$LOOP_DIR/worktree.sh" sync-module >&2 || return 1
  [ -z "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}

retry_start_item() { # → 0 when the retried start succeeded; its output is left in $start_out
  if start_out=$(bash "$LOOP_DIR/worktree.sh" start-item "$issue" "$slug" --change "${item_change:-}" 2>&1); then
    start_after_discard=1
    return 0
  fi
  printf '%s\n' "$start_out"
  retry_refusal=$(printf '%s' "$start_out" | head -1)
  return 1
}

# The repair both refusals share: discard what the tree carries and answer whether it is clean afterwards. The
# start-time refusal reaches it behind `--reset-worktree`; the mid-run refusal reaches it only for the work this
# run ended itself, whose record already exists (issue #664) — one copy of the rule, because a second copy of a rule
# is a second rule (as `owner_of_marker` says above). The caller prints the line naming what is being discarded.
discard_kept_worktree() { # discard_kept_worktree <the name this repair prints itself under> <leftovers> → 0 when clean
  local label="${1:-reset-worktree}" leftovers_to_name="${2:-}" reset_after
  printf '%s\n' "$leftovers_to_name" | sed 's/^/  /'
  git -C "$worktree" reset -q --hard || loop_die "run: could not reset the kept worktree"
  git -C "$worktree" clean -qfd || loop_die "run: could not clean the kept worktree"
  # Neither verb above reaches a submodule the previous run's building left on another commit, which is dirt this
  # repair's own refusal names: the module's index already matches its own `HEAD`, so the reset has nothing to undo
  # there, and a module's path is tracked, so the clean leaves it alone. Measured 2026-10-25 in the kept worktree at
  # #309: `reset --hard` and `clean -fd` still printed the module path as modified, and
  # `submodule update --init --checkout <path>` printed nothing (issue #310). The path is the project's own, read
  # from the profile and only where the project carries a `.gitmodules`, and the checkout is best-effort with a
  # note exactly as the fresh worktree's is (D4).
  # …and the placement itself is `worktree.sh sync-module`, which also fetches a pin only the root tree's module holds
  # (measured 2026-10-28): `git -C"…" submodule update` here would be a second copy of that rule, and a second copy is a
  # second rule.
  if [ -n "$(loop_conf_value LOOP_SUBMODULE_PATH)" ] && [ -f "$worktree/.gitmodules" ]; then
    bash "$LOOP_DIR/worktree.sh" sync-module >/dev/null 2>&1 ||
      loop_note "$label: the submodule could not be checked out — the tree may still be dirty"
  fi
  # What is still dirty after both verbs and the checkout is dirt this repair cannot discard (a file inside the
  # module, say), so the repair says so and names it instead of announcing a clean tree the next start refuses.
  reset_after=$(git -C "$worktree" status --porcelain)
  if [ -n "$reset_after" ]; then
    printf '%s: the reset cannot discard what is left in %s:\n' "$label" "$worktree"
    printf '%s\n' "$reset_after" | sed 's/^/  /'
    return 1
  fi
  printf '%s: %s is clean again — the commits on its branches are left alone\n' "$label" "$worktree"
}

