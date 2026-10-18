# Verification evidence — fix-issue-filing-state-namespace

All commands offline, no token, no Gradle (this change touches no compiled file).

## Red on HEAD

`bash tools/issue-tooling-selftest.sh --case create#refusesTwoValuesOfOneNamespace`
→ `FAIL create#refusesTwoValuesOfOneNamespace` (expected `2`, got `0`) — see `red-on-head.log`.

The issue's own reproduction, run offline against the stub `gh`:

```
$ PATH=tools/issues/fixtures:$PATH GH_STUB_DIR=$tmp GH_STUB_RECORD=$tmp/log \
    bash tools/issues/create.sh --finding "$tmp/f.json" --status open
https://example.invalid/Framstag/NaviVeylin/issues/1
exit=0
$ cat "$tmp/log"
gh issue create --title t --body … --label class/bug --label category/ui --label status/fixed-by --label status/open
```

## Green after the fix

`bash tools/issue-tooling-selftest.sh --case create#refusesTwoValuesOfOneNamespace` → `PASS` (exit 0).

The same reproduction after the fix:

```
$ bash tools/issues/create.sh --finding "$tmp/f.json" --status open
create: status/fixed-by and status/open name two values of one namespace — an item carries one value per namespace
exit=2
$ cat "$tmp/log"   # empty — no gh issue create was made
```

## Regression

- `bash tools/issue-tooling-selftest.sh` → `all 77 cases passed (JDK 27, offline, no token)`.
- `bash tools/sensor-selftest.sh` → `all 43 cases passed`.
- `bash tools/check-portability-selftest.sh` → `all 21 cases passed`.

## Falsification

`bash tools/mutate.sh --mutations tools/create-mutations.json`:

```
1.1  create#refusesTwoValuesOfOneNamespace  …
  RED(exit 1)  1 occurrence(s) replaced in tools/issues/create.sh
  GREEN(exit 0)  restored, and the case passes again
mutate: 1 mutation(s) run, 1 went red and restored green, 0 problem(s)
```

`bash tools/mutate.sh --mutations tools/create-mutations.json --list` exits 0 (registered in the
"Check issue tooling" CI step of `.github/workflows/build.yml`).

## OpenSpec

`openspec validate fix-issue-filing-state-namespace --type change` → `Change 'fix-issue-filing-state-namespace' is valid` (exit 0).

## Stub `gh` decision

`tools/issues/fixtures/gh` is deliberately unchanged: the real `gh` does not refuse a duplicate-namespace
label set. Measured during triage — a real `gh issue create --label status/fixed-by --label status/open`
created the item carrying both labels (issue #180, since deleted). A stricter stub would assert a refusal the
client does not make.
