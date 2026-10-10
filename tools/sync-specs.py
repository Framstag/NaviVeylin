#!/usr/bin/env python3
"""Merge an OpenSpec change's delta specs into the main specs (archive's inline sync).

This is the block-level half of `.pi/skills/openspec-sync-specs`: every MODIFIED block in this change's
deltas carries the *whole* requirement (description plus every surviving scenario), so the merge is a
substitution of the named requirement blocks, an append for the named ADDED blocks, and nothing else —
everything the delta does not mention stays exactly where it is. Run with `--dry-run` to see the plan.

Usage:
  python3 tools/sync-specs.py --change <name> [--dry-run]
"""
import argparse
import os
import re
import sys

REQ_RE = re.compile(r"^### Requirement: (.+)$", re.M)


def split_requirements(body: str) -> list[tuple[str, str]]:
    """Split a requirements body into (name, block) pairs, keeping any preamble out."""
    matches = list(REQ_RE.finditer(body))
    blocks: list[tuple[str, str]] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        blocks.append((match.group(1).strip(), body[match.start():end].rstrip() + "\n"))
    return blocks


def parse_main(text: str) -> tuple[str, str, list[tuple[str, str]]]:
    """Return (title, purpose section, requirement blocks) of a main spec."""
    lines = text.splitlines()
    title = next((line for line in lines if line.startswith("# ")), "# Specification")
    purpose = ""
    if "## Purpose" in text:
        purpose = text.split("## Purpose", 1)[1].split("\n## ", 1)[0].rstrip() + "\n"
    requirements_body = text.split("## Requirements", 1)[1] if "## Requirements" in text else ""
    return title, purpose, split_requirements(requirements_body)


def parse_delta(text: str) -> tuple[str, list[tuple[str, str, str]]]:
    """Return (purpose body, [(op, name, block)]) of a delta spec."""
    purpose = ""
    if "## Purpose" in text:
        purpose = text.split("## Purpose", 1)[1].split("\n## ", 1)[0].strip() + "\n"
    operations: list[tuple[str, str, str]] = []
    for op in ("ADDED", "MODIFIED", "REMOVED", "RENAMED"):
        marker = f"## {op} Requirements"
        if marker not in text:
            continue
        section = text.split(marker, 1)[1].split("\n## ", 1)[0]
        if op == "RENAMED":
            # The documented format is a list: `- FROM: \`### Requirement: X\`` / `- TO: \`### Requirement: Y\``.
            pairs = re.findall(
                r"(?:[-*]\s*)?FROM:\s*`### Requirement: (.+?)`\s*\n\s*(?:[-*]\s*)?TO:\s*`### Requirement: (.+?)`",
                section,
            )
            for old, new in pairs:
                operations.append(("RENAMED", old.strip(), new.strip()))
            continue
        for name, block in split_requirements(section):
            operations.append((op, name, block))
    return purpose, operations


def merge(main_text: str | None, delta_text: str, capability: str, dry_run: bool) -> tuple[str, list[str]]:
    """Merge one delta into its main spec; return (new text, log lines)."""
    log: list[str] = []
    purpose_delta, operations = parse_delta(delta_text)

    if main_text is None:
        allowed = {op for op, _, _ in operations}
        if allowed - {"ADDED"}:
            raise SystemExit(
                f"{capability}: no main spec exists and the delta has {sorted(allowed - {'ADDED'})} "
                "operations — only ADDED requirements can create a new main spec"
            )
        title = f"# {capability} Specification"
        blocks = [(name, block) for op, name, block in operations if op == "ADDED"]
        for name, _ in blocks:
            log.append(f"added requirement: {name}")
        body = "\n".join(block for _, block in blocks)
        text = f"{title}\n\n## Purpose\n{purpose_delta}\n## Requirements\n\n{body}"
        return text.rstrip() + "\n", log

    title, purpose, blocks = parse_main(main_text)
    order = [name for name, _ in blocks]
    by_name = dict(blocks)

    for op, name, value in operations:
        if op == "ADDED":
            if name in by_name:
                by_name[name] = value
                log.append(f"updated requirement (already present): {name}")
            else:
                by_name[name] = value
                order.append(name)
                log.append(f"added requirement: {name}")
        elif op == "MODIFIED":
            if name not in by_name:
                raise SystemExit(f"{capability}: MODIFIED names a requirement the main spec lacks: {name}")
            by_name[name] = value
            log.append(f"modified requirement: {name}")
        elif op == "REMOVED":
            if name not in by_name:
                log.append(f"removed requirement (already absent): {name}")
                continue
            by_name.pop(name)
            order.remove(name)
            log.append(f"removed requirement: {name}")
        elif op == "RENAMED":
            if name not in by_name:
                raise SystemExit(f"{capability}: RENAMED names a requirement the main spec lacks: {name}")
            block = by_name.pop(name)
            order[order.index(name)] = value
            by_name[value] = block.replace(f"### Requirement: {name}", f"### Requirement: {value}", 1)
            log.append(f"renamed requirement: {name} -> {value}")

    if not order:
        raise SystemExit(f"{capability}: the merge would leave no requirement — refusing to write an empty section")

    body = "\n".join(by_name[name] for name in order)
    text = f"{title}\n\n## Purpose{purpose}\n## Requirements\n\n{body}"
    return text.rstrip() + "\n", log


def selftest() -> int:
    """Exercise the merge on inline fixtures: create, modify, append, rename, refuse. No repo writes."""
    main = "# x Specification\n\n## Purpose\nDoes a thing.\n\n## Requirements\n\n" \
        "### Requirement: A\nOld A text.\n\n#### Scenario: a1\n- **WHEN** x\n- **THEN** y\n\n" \
        "### Requirement: B\nB text.\n\n#### Scenario: b1\n- **WHEN** x\n- **THEN** y\n"
    delta = "## MODIFIED Requirements\n\n### Requirement: A\nNew A text.\n\n" \
        "#### Scenario: a1\n- **WHEN** x\n- **THEN** y\n\n" \
        "#### Scenario: a2\n- **WHEN** z\n- **THEN** w\n\n" \
        "## ADDED Requirements\n\n### Requirement: C\nC text.\n\n#### Scenario: c1\n- **WHEN** x\n- **THEN** y\n"

    text, log = merge(main, delta, "x", dry_run=False)
    assert "New A text." in text and "Old A text." not in text, "MODIFIED replaces the block"
    assert "#### Scenario: a2" in text and "#### Scenario: a1" in text, "MODIFIED keeps surviving scenarios"
    assert "### Requirement: B" in text, "untouched requirements survive"
    assert "### Requirement: C" in text, "ADDED is appended"
    assert text.index("Requirement: A") < text.index("Requirement: B") < text.index("Requirement: C"), "order"
    assert not any(h in text for h in ("## ADDED", "## MODIFIED")), "no delta headers in a main spec"
    assert "## Purpose\nDoes a thing." in text, "Purpose untouched for an existing spec"
    assert len(log) == 2, f"one log line per operation, got {log}"

    created, _ = merge(None, "## Purpose\nBrand new.\n\n## ADDED Requirements\n\n### Requirement: N\nN text.", "y", dry_run=False)
    assert created.startswith("# y Specification"), "a new spec is titled after the capability"
    assert "## Purpose\nBrand new." in created and "### Requirement: N" in created, "delta purpose copied"

    renamed, _ = merge(main, "## RENAMED Requirements\n\n- FROM: `### Requirement: B`\n- TO: `### Requirement: B2`", "x", dry_run=False)
    assert "### Requirement: B2" in renamed and "### Requirement: B\n" not in renamed, "RENAMED"

    for bad, why in (
        ("## MODIFIED Requirements\n\n### Requirement: A\nA.", "a new spec cannot be built from MODIFIED"),
        ("## RENAMED Requirements\n\n- FROM: `### Requirement: Z`\n- TO: `### Requirement: Z2`", "renaming an absent requirement"),
    ):
        try:
            merge(None if "new spec" in why else main, bad, "x", dry_run=False)
            raise AssertionError(f"expected a refusal: {why}")
        except SystemExit:
            pass

    print("sync-specs selftest: OK (create, modify, append, rename, refuse)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--change")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        return selftest()
    if not args.change:
        parser.error("--change is required (or use --selftest)")

    root = os.getcwd()
    change_dir = os.path.join(root, "openspec", "changes", args.change)
    delta_root = os.path.join(change_dir, "specs")
    if not os.path.isdir(delta_root):
        print(f"no delta specs under {delta_root}", file=sys.stderr)
        return 2

    written = []
    for capability in sorted(os.listdir(delta_root)):
        delta_path = os.path.join(delta_root, capability, "spec.md")
        if not os.path.isfile(delta_path):
            continue
        main_path = os.path.join(root, "openspec", "specs", capability, "spec.md")
        main_text = open(main_path).read() if os.path.isfile(main_path) else None
        text, log = merge(main_text, open(delta_path).read(), capability, args.dry_run)
        action = "create" if main_text is None else "update"
        print(f"[{args.dry_run and 'dry-run' or 'write'}] {action} openspec/specs/{capability}/spec.md")
        for line in log:
            print(f"    {line}")
        if not args.dry_run:
            os.makedirs(os.path.dirname(main_path), exist_ok=True)
            open(main_path, "w").write(text)
            written.append(main_path)

    print(f"\n{len(written)} main spec(s) written" if written else "\ndry run: nothing written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
