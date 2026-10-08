#!/usr/bin/env bash
#
# update-to-current-libosmscout-master — bump libosmscout submodule to current upstream master,
# merge into naviveylin-local, push the branch, and commit the new submodule
# pointer in the parent NaviVeylin repo.
#
# Safe by default: requires clean trees, no force pushes, aborts merges on
# conflict, and verifies the remote branch has not moved before pushing.
# Run from anywhere inside the NaviVeylin repo.
#
# Usage:
#   update-libosmscout.sh [--check-non-marisa]
#
#   --check-non-marisa  After the merge, additionally compile the JNI translation unit in the
#                       NON-marisa configuration (the PR's CI default, which this repo never
#                       builds): temporarily insert "#undef OSMSCOUT_HAVE_LIB_MARISA" after the
#                       include block of OSMScoutClient.cpp, run the single-ABI Gradle build,
#                       then restore the file from a backup (trap-protected) and verify the
#                       restore. See SKILL.md "Configuration parity".
set -euo pipefail

SUBMODULE_PATH="app/src/main/cpp/libosmscout"
BRANCH="naviveylin-local"
UPSTREAM_BRANCH="master"
PARENT_COMMIT_MSG="Update libosmscout submodule to master (merge into ${BRANCH})"
JNI_CPP_REL="libosmscout-client-java/src/OSMScoutClient.cpp"
ANCHOR_INCLUDE="#include <osmscout/navigation/Engine.h>"
CHECK_NON_MARISA=0
NON_MARISA_LOG="${TMPDIR:-/tmp}/libosmscout-non-marisa-check.log"

for arg in "$@"; do
    case "$arg" in
        --check-non-marisa) CHECK_NON_MARISA=1 ;;
        *) echo "ERROR: unknown argument '$arg' (supported: --check-non-marisa)" >&2; exit 2 ;;
    esac
done

fail() { echo "ERROR: $*" >&2; exit 1; }

PARENT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PARENT_ROOT"
SUB="$PARENT_ROOT/$SUBMODULE_PATH"
JNI_CPP="$SUB/$JNI_CPP_REL"

# --- preflight ---
[ -e "$SUB/.git" ] || fail "submodule not initialized — run: git submodule update --init --recursive"

# The submodule gitlink being the only parent change is the EXPECTED state after a
# conflicted merge: the branch is already merged locally, and this run (re-)does the
# push + parent bump. Any other parent change still blocks.
PARENT_OTHER="$(git status --porcelain | grep -v -E "^[ M]M? +${SUBMODULE_PATH}$" || true)"
[ -z "$PARENT_OTHER" ] || fail "parent repo has uncommitted changes; commit or stash first:
$PARENT_OTHER"

[ -z "$(git -C "$SUB" status --porcelain)" ] || fail "submodule has uncommitted changes; commit or stash first"

# --- fetch upstream ---
git -C "$SUB" fetch origin --prune || fail "fetch origin failed (network/credentials?)"

# --- sync local branch to its remote ---
git -C "$SUB" checkout "$BRANCH" || fail "cannot checkout $BRANCH"
git -C "$SUB" merge --ff-only "origin/$BRANCH" || fail "local $BRANCH diverged from origin/$BRANCH (unpushed commits?) — reconcile manually, never force-push"

OLD_TIP="$(git -C "$SUB" rev-parse HEAD)"
NEW_UPSTREAM_COMMITS="$(git -C "$SUB" log --oneline "HEAD..origin/$UPSTREAM_BRANCH" 2>/dev/null | wc -l | tr -d ' ')"

# --- merge upstream master into naviveylin-local ---
if git -C "$SUB" merge-base --is-ancestor "origin/$UPSTREAM_BRANCH" HEAD; then
    echo "naviveylin-local already contains origin/$UPSTREAM_BRANCH — no merge needed"
else
    echo "merging origin/$UPSTREAM_BRANCH into $BRANCH ($NEW_UPSTREAM_COMMITS new commit(s) upstream)..."
    if ! git -C "$SUB" merge "origin/$UPSTREAM_BRANCH" --no-edit; then
        git -C "$SUB" merge --abort
        echo >&2
        echo "Conflicting files:" >&2
        git -C "$SUB" diff --name-only --diff-filter=U >&2 2>/dev/null || true
        echo >&2
        fail "merge of origin/$UPSTREAM_BRANCH into $BRANCH conflicted; merge ABORTED (no changes left).
Resolve manually — see SKILL.md 'Conflict handling': keep the NaviVeylin-local side per hunk in
OSMScoutClient.cpp / NavigationPosition.java, take upstream for add/add RoadInfo.java, do NOT use
'git checkout --ours' on the whole .cpp (it drops upstream's non-conflicting changes), verify with
'git diff origin/master -- <file>', then 'git -C $SUBMODULE_PATH commit' the merge and
'git -C $SUBMODULE_PATH push origin $BRANCH'. Then re-run this script for the parent bump."
    fi
fi

NEW_TIP="$(git -C "$SUB" rev-parse HEAD)"

# --- pre-push guard: the remote branch must not have moved (a parallel session may rewrite it) ---
REMOTE_TIP="$(git -C "$SUB" ls-remote origin "refs/heads/$BRANCH" | cut -f1)"
TRACKED_TIP="$(git -C "$SUB" rev-parse "origin/$BRANCH")"
if [ "$REMOTE_TIP" != "$TRACKED_TIP" ]; then
    fail "origin/$BRANCH moved since the fetch (remote $REMOTE_TIP, fetched $TRACKED_TIP) — most likely a parallel session rewrote it.
Re-run the script (it fetches again) or reconcile by hand: 'git -C $SUBMODULE_PATH fetch origin',
'git -C $SUBMODULE_PATH merge origin/$BRANCH' (never force-push), then push the merge."
fi
if ! git -C "$SUB" merge-base --is-ancestor "$REMOTE_TIP" "$NEW_TIP"; then
    fail "the push would NOT be a fast-forward (remote $REMOTE_TIP is not an ancestor of $NEW_TIP) — reconcile by merging origin/$BRANCH, never force-push."
fi

# --- push branch: new submodule SHA must exist on origin for other clones ---
git -C "$SUB" push origin "$BRANCH" || fail "push origin $BRANCH failed"

# --- optional: non-marisa configuration parity check ---
NON_MARISA_RESULT="skipped (run with --check-non-marisa)"
if [ "$CHECK_NON_MARISA" = "1" ]; then
    if [ ! -f "$JNI_CPP" ]; then
        NON_MARISA_RESULT="SKIPPED — $JNI_CPP_REL not found"
    elif ! grep -qxF "$ANCHOR_INCLUDE" "$JNI_CPP"; then
        NON_MARISA_RESULT="SKIPPED — anchor '$ANCHOR_INCLUDE' not found in $JNI_CPP_REL"
    else
        BACKUP="$(mktemp)"
        cp "$JNI_CPP" "$BACKUP"
        restore_jni() { cp "$BACKUP" "$JNI_CPP"; rm -f "$BACKUP"; }
        trap restore_jni EXIT

        # Insert the simulation right after the anchor include.
        awk -v anchor="$ANCHOR_INCLUDE" '
            { print }
            $0 == anchor {
                print ""
                print "// TEMP non-marisa check (restored automatically by update-libosmscout.sh)"
                print "#undef OSMSCOUT_HAVE_LIB_MARISA"
            }' "$BACKUP" > "$JNI_CPP"

        echo "non-marisa parity: compiling the JNI translation unit without OSMSCOUT_HAVE_LIB_MARISA (log: $NON_MARISA_LOG)..."
        if (cd "$PARENT_ROOT" && ./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a --console=plain > "$NON_MARISA_LOG" 2>&1); then
            NON_MARISA_RESULT="PASS (compiles without marisa)"
        else
            NON_MARISA_RESULT="FAIL (does not compile without marisa — see $NON_MARISA_LOG; the PR's meson/cmake jobs build this configuration)"
            grep -m3 -E "error:" "$NON_MARISA_LOG" >&2 || true
        fi

        restore_jni
        trap - EXIT
        # The restore must leave the tracked file untouched.
        if [ -n "$(git -C "$SUB" status --porcelain -- "$JNI_CPP_REL")" ]; then
            fail "the non-marisa check did not restore $JNI_CPP_REL cleanly — inspect it before continuing"
        fi
    fi
fi

# --- bump submodule pointer in parent repo ---
git add "$SUBMODULE_PATH"
if git diff --cached --quiet; then
    echo "submodule pointer unchanged (already at ${NEW_TIP:0:12})"
else
    git commit -m "$PARENT_COMMIT_MSG"
fi

# --- summary ---
echo
echo "Summary:"
echo "  upstream master : $(git -C "$SUB" rev-parse --short "origin/$UPSTREAM_BRANCH") (contained in HEAD: $(git -C "$SUB" merge-base --is-ancestor "origin/$UPSTREAM_BRANCH" HEAD && echo yes || echo NO))"
echo "  naviveylin-local: ${OLD_TIP:0:12} -> ${NEW_TIP:0:12}"
echo "  pushed          : origin/$BRANCH"
echo "  non-marisa check: $NON_MARISA_RESULT"
echo "  parent commit   : $(git rev-parse --short HEAD) (not pushed)"
echo "  residual diff   : $(git -C "$SUB" diff --shortstat "origin/$UPSTREAM_BRANCH" HEAD | sed 's/^[[:space:]]*//')"
echo "                    (this is the whole cost of carrying $BRANCH — the burn-down metric;
                     every slice of NaviVeylin work that lands upstream shrinks it permanently)"
echo "  verify          : git submodule status"
echo
echo "Follow-up checklist (SKILL.md 'After the update'):"
echo "  1. upstream merged   : git -C $SUBMODULE_PATH merge-base --is-ancestor origin/master HEAD"
echo "  2. pointer matches   : git submodule status   # SHA == $(git -C "$SUB" rev-parse "$BRANCH" | cut -c1-12)"
echo "  3. JNI symbols       : parent 'public native' methods must have a symbol in $JNI_CPP_REL"
echo "                         (overloads are name-mangled — confirm before acting on a 'MISSING')"
echo "  4. config parity     : re-run this script with --check-non-marisa if it was skipped"
echo "  5. smoke build       : ./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a"
echo "                         (check ':app:buildCMakeDebug[arm64-v8a]' really executed)"
echo "  6. PR checks         : gh pr checks 1773 -R Framstag/libosmscout   # compare with the pre-push failures"
echo "  7. push the parent commit only on request (see SKILL.md step 7)"
echo "  8. residual diff     : git -C $SUBMODULE_PATH diff --shortstat origin/$UPSTREAM_BRANCH HEAD"
echo "                         (conflict surface for the next merge; see SKILL.md 'Burn-down')"
