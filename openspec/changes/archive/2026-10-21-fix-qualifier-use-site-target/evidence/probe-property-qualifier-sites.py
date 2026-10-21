#!/usr/bin/env python3
"""Broader probe than the guard's pattern: any qualifier applied to a property declaration.

The guard matches `@Qualifier [private|internal|public|protected] val|var`, so a `lateinit` or
`override` property carrying a qualifier is invisible to it. This probe widens the modifier list to
"anything" and is run over the same six source sets to show the tree holds no site of that shape:
every qualifier on a property in the tree writes `@param:` first, which makes the `@` in
`@param:Qualifier` the start of a use-site target rather than of the annotation name, so this probe
does not match it either.

Comment lines are reported separately: they cannot be a site, and a guard's own fixtures and prose
naming the refused form live there.

Run from the change root: `python3 probe-property-qualifier-sites.py .`
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

QUALIFIER_PROP = re.compile(
    r"@(ApplicationContext|ActivityContext|Named)(\([^)]*\))?\s+"
    r"(?:[\w:]+(?:\s*<[^>]*>)?\s+)*(?:val|var)\s"
)


def is_comment(line: str) -> bool:
    t = line.lstrip()
    return t.startswith("//") or t.startswith("*") or t.startswith("/*")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    files = 0
    code, comments = [], []
    for rel in ROOTS:
        for path in sorted((root / rel).rglob("*.kt")):
            files += 1
            for no, line in enumerate(
                path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1
            ):
                if not QUALIFIER_PROP.search(line):
                    continue
                where = f"{path}:{no}: {line.strip()}"
                (comments if is_comment(line) else code).append(where)
    print(f"scanned={files} code_findings={len(code)} comment_lines={len(comments)}")
    for f in code:
        print("CODE " + f)
    for f in comments:
        print("COMMENT " + f)
    return 0 if not code else 1


if __name__ == "__main__":
    sys.exit(main())
