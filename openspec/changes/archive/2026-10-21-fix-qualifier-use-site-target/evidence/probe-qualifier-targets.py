#!/usr/bin/env python3
"""Probe: qualifier annotations applied to a property declaration without a use-site target.

Variant A (region-tracked): only inside a primary-constructor parameter list.
Variant B (whole file): any `@Qualifier ... val|var` declaration.
Qualifiers = ApplicationContext | ActivityContext | Named (javax.inject / Hilt).
"""
import re
import sys
from pathlib import Path

ROOTS = [
    "app/src/main/java",
    "app/src/test/java",
    "auto/src/main/java",
    "auto/src/test/java",
    "core/src/main/java",
    "core/src/test/java",
]

CTOR_OPEN = re.compile(r"\b(constructor|class\s+\w+)\s*\(")
QUALIFIER_PROP = re.compile(
    r"@(ApplicationContext|ActivityContext|Named)(\([^)]*\))?\s+"
    r"(?:(?:private|internal|public|protected)\s+)*(?:val|var)\s"
)


def scan(path: Path):
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    in_ctor, whole = [], []
    depth = 0
    for no, line in enumerate(lines, start=1):
        hit = QUALIFIER_PROP.search(line)
        if hit:
            whole.append(f"{path}:{no}: {line.strip()}")
        if depth == 0:
            m = CTOR_OPEN.search(line)
            if m:
                depth = line[m.end() - 1:].count("(") - line[m.end() - 1:].count(")")
            continue
        if hit:
            in_ctor.append(f"{path}:{no}: {line.strip()}")
        depth += line.count("(") - line.count(")")
        if depth < 0:
            depth = 0
    return in_ctor, whole


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    files = 0
    ctor, allf = [], []
    for rel in ROOTS:
        for path in sorted((root / rel).rglob("*.kt")):
            files += 1
            a, b = scan(path)
            ctor += a
            allf += b
    print(f"scanned={files} findings_in_constructor={len(ctor)} findings_whole_file={len(allf)}")
    for f in allf:
        print(("CTOR " if f in ctor else "OTHER ") + f)
    return 0 if not allf else 1


if __name__ == "__main__":
    sys.exit(main())
