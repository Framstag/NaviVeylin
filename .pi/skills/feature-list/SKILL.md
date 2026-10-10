---
name: feature-list
description: Generate `FEATURES.md` and `RELEASE-NOTES.md` from the shipped capability specs by filling the tool's phrasing seam with one subagent per feature area. Use when asked to regenerate the feature list, the feature catalogue, or the release notes, or after a change has archived a capability spec.
allowed-tools: Bash(bash tools/gen-feature-list.sh:*), Bash(openspec:*)
---

# Regenerate the feature catalogue and the release notes

`tools/gen-feature-list.sh` owns everything deterministic: which capabilities exist, what moved since a version,
which feature areas are dirty, and the gates that decide whether the prose may be published. It never calls a
model. This skill fills the one seam it leaves — the prose — with one subagent per feature area, then lets the
tool assemble and gate the result.

Read `guidelines/FeatureList.md` §1, §4, §6 and §7 before changing anything about how this is driven.

## 1. Work out the version

The version is an **input**; nothing discovers it. Use the `versionName` of the release being prepared, in the
form `<yyyy>-<MM>-<DD>-<N>` (`app/release-version.properties` holds the last one on this machine, but a fresh
checkout has none — ask if it is not on screen).

## 2. Ask the tool what needs writing

```bash
bash tools/gen-feature-list.sh --emit-bundles .pi/feature-list/<version>/bundles --release <version>
```

It prints one line per area that needs prose and writes `<area id>.json` per area plus `<area id>.notes.json` for
areas with a capability to report in the release note. **No bundle means nothing to do** — say so and stop; the
existing documents are current. It also reports the delta on stdout, which is what the release note entry will
be derived from.

## 3. Fan out one subagent per bundle

One child per `<area id>.json`, all of them in parallel, each writing exactly one file. Give each child this
task, with the paths substituted:

> Read `.pi/feature-list/<version>/bundles/<area id>.json`. It names a `promptFile`; read that file and follow it
> exactly — it is the instruction for this job. Write the section body it describes to
> `.pi/feature-list/<version>/sections/<area id>.md`. Write nothing else and touch no other file. Print the
> capability keys you cited, one per line.

Do the same for each `<area id>.notes.json`, writing `<area id>.notes.md` into the same sections directory. The
two roles differ in what they cover: the catalogue section covers every user-visible capability of its area,
the note section covers only the changed ones the bundle lists.

A child that writes nothing, or writes a bullet without a `<!-- cap: … -->` marker, is not a failure to hide:
the next step fails loudly and names it.

## 4. Assemble, gate and write

```bash
bash tools/gen-feature-list.sh --release <version> --sections .pi/feature-list/<version>/sections
```

Exit 0 means the documents were written and every gate passed. Anything else means **nothing was overwritten**;
read the report:

- `gen-feature-list: the prose gates failed:` on stderr — a bullet with no key, a key that does not resolve, a
  figure no cited spec contains, or a user-visible spec no bullet reaches. Fix the section named and re-run.
- `gen-feature-list: prose worth a reviewer's eye (not a failure):` on stdout — words the run could not find in
  the specs a bullet cites. Read them and judge; they are the only place a wrong claim can hide, because a claim
  made of true words in the wrong order passes every gate.

Then look at the diff of `FEATURES.md`: the gates check facts, not judgement, and a section that is merely dull
still passes them.

## 5. Report

Say which areas were regenerated, which were reproduced from cache, the delta the note was derived from, and —
if the note was withheld — that it was withheld and why. Do not summarise the documents back to the reader; they
can open them.
