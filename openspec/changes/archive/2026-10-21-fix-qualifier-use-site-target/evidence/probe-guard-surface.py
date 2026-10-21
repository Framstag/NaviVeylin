"""Replica of QualifierUseSiteTargetTest's scan, run outside Gradle before the JUnit cases are run.

Run from the change root: `python3 probe-guard-surface.py .` — prints the source count, the findings
and the detector's result on the fixtures, so a run's numbers can be checked against the guard's own
`system-out` without trusting either.
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

PAT = re.compile(
    r"@(ApplicationContext|ActivityContext|Named)(\([^)]*\))?\s+"
    r"(?:(?:private|internal|public|protected)\s+)*(?:val|var)\s"
)


def is_comment(line: str) -> bool:
    t = line.lstrip()
    return t.startswith("//") or t.startswith("*") or t.startswith("/*")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    srcs = sorted(
        p
        for rel in ROOTS
        for p in (root / rel).rglob("*.kt")
        if p.is_file()
    )
    findings = []
    for p in srcs:
        for no, line in enumerate(p.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if is_comment(line) or not PAT.search(line):
                continue
            findings.append(f"{p}:{no}: {line.strip()}")
    print(f"sources={len(srcs)} findings={len(findings)}")
    for f in findings:
        print("FINDING " + f)
    impl = [
        "    @" + "ApplicationContext private val context: Context,",
        "    @" + 'Named("clock") val clock: Clock',
        "@" + "ActivityContext var activity: Context",
    ]
    expl = [
        "    @param:ApplicationContext private val context: Context,",
        "    @field:ApplicationContext lateinit var context: Context",
        "    fun provideViewportStorage(@ApplicationContext context: Context): ViewportStorage {",
        "    // the qualified form `@ApplicationContext private val` is refused, see the case above",
        "    @Singleton class Controller @Inject constructor(private val context: Context)",
    ]
    print("implicit fixtures hits:", [len(PAT.findall(l)) for l in impl])
    print("explicit fixtures hits:", [len(PAT.findall(l)) for l in expl])
    return 0 if not findings else 1


if __name__ == "__main__":
    sys.exit(main())
