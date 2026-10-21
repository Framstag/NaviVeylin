#!/usr/bin/env bash
# Probe (repeatable, throwaway repo under .pi/tmp): the shape the loop lands work in — a branch whose patch the
# base carries as a *new* commit — read by ancestry and by patch identity.
set -eu
root=$(cd "$(dirname "$0")" && pwd)
while [ ! -e "$root/.git" ]; do root=$(dirname "$root"); [ "$root" != "/" ] || exit 2; done
mkdir -p "$root/.pi/tmp"
cd "$root/.pi/tmp"
rm -rf cb
mkdir cb
cd cb
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=a@b.c GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=a@b.c
git init -q -b main
echo x > f
git add -A
git commit -qm init
git checkout -q -b fix/issue-21-demo
echo y > landed.txt
git add -A
git commit -qm "the attempt's own work"
git checkout -q main
git cherry-pick fix/issue-21-demo >/dev/null 2>&1
git commit -q --amend -m "fix: the landed work (#21)"
echo "objects:"
git log --oneline --graph --all
echo
echo "ancestry      : git rev-list --count main..fix/issue-21-demo  → $(git rev-list --count main..fix/issue-21-demo)"
echo "merge-base     : git merge-base --is-ancestor fix/issue-21-demo main → $(git merge-base --is-ancestor fix/issue-21-demo main && echo ancestor || echo 'not an ancestor')"
echo "patch identity : git cherry main fix/issue-21-demo               → $(git cherry main fix/issue-21-demo | grep -c '^+ ' || true) unlanded"
