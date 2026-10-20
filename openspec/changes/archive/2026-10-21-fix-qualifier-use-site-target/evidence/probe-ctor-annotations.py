#!/usr/bin/env python3
"""Probe: annotations on primary-constructor property parameters with no use-site target.

Mirrors the detector the guard test will use: find the paren region of a primary
constructor (`constructor(` or the `(` after a `class`/`data class` header) and
report every annotation applied to a `val`/`var` parameter in it whose name is
not followed by `:target`.
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

CTOR_OPEN = re.compile(r"\b(constructor|class\s+\w+)\s*\(")   # header that opens a ctor list
ANNOTATED_PARAM = re.compile(
    r"@([A-Z]\w*)(\([^)]*\))?\s+"
    r"(?:(?:private|internal|public|protected|override|open)\s+)*"
    r"(?:val|var)\s"
)
USE_SITE = re.compile(r"^[a-zA-Z]+:")


def findings_in(path: Path):
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    out = []
    depth = 0
    for no, line in enumerate(lines, start=1):
        if depth == 0:
            m = CTOR_OPEN.search(line)
            if m:
                depth = line[m.end() - 1:].count("(") - line[m.end() - 1:].count(")")
                if depth <= 0:
                    # single-line constructor list: nothing annotated spans a line here
                    depth = 0
            continue
        depth += line.count("(") - line.count(")")
        for m in ANNOTATED_PARAM.finditer(line):
            name = m.group(1)
            tail = line[m.start() + 1 + len(name):]
            if tail.startswith(":"):
                continue
            out.append(f"{path}:{no}: {line.strip()}")
        if depth <= 0:
            depth = 0
    return out


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    files = 0
    findings = []
    for rel in ROOTS:
        for path in sorted((root / rel).rglob("*.kt")):
            files += 1
            findings.extend(findings_in(path))
    print(f"scanned={files} findings={len(findings)}")
    for f in findings:
        print(f)
    return 0 if not findings else 1


if __name__ == "__main__":
    sys.exit(main())
