#!/usr/bin/env bash
# The source adapter for this project's prose backlog — the one project-specific half of the import
# (design D11). It parses `TODO.md`'s `## N.` grammar with `core/TodoSections.java`, applies the profile's
# renames and value sets, and emits the neutral findings stream the reconciler consumes.
#
# The grammar lives here and in `core/TodoAdapter.java`; the renames and the value sets come from the profile
# (`sourceRenames`, the namespaces), applied by `core/Taxonomy.java`. `import.sh` — the reconciler — knows no
# backlog format, so a second project writes one adapter and edits one profile, and reuses the reconciler.
#
# Usage: todo-md.sh [--todo <path>] [--profile <path>] [--import [--dry-run|--apply] [--json]]
# Exit:  0 ok, 2 usage, 3 refusal
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/issues/lib.sh
source "$here/../lib.sh"

profile_arg=""
todo_path=""
run_import=0
import_args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --profile) need_value "$@"; profile_arg="${2:-}"; shift 2 ;;
    --todo) need_value "$@"; todo_path="${2:-}"; shift 2 ;;
    --import) run_import=1; shift ;;
    --dry-run|--apply|--json) import_args+=("$1"); shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) issues_die "todo-md: unknown argument $1" ;;
  esac
done

issues_resolve "$profile_arg"
profile="$ISSUES_PROFILE"
if [ -z "$todo_path" ]; then
  todo_path="$ISSUES_ROOT/TODO.md"
fi
[ -f "$todo_path" ] || issues_die "todo-md: no such backlog file: $todo_path"

if [ "$run_import" -eq 1 ]; then
  issues_core TodoAdapter "$profile" "$todo_path" | "$here/../import.sh" --profile "$profile" "${import_args[@]}"
  exit $?
fi

issues_core TodoAdapter "$profile" "$todo_path"
exit $?
