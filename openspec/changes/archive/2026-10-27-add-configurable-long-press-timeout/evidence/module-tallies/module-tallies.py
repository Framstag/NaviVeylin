"""Aggregate a module's JUnit XML results into one <testsuite> tally line.

Usage: python3 module-tallies.py <out-dir> <name>=<results-dir> [<name>=<results-dir> ...]

Writes <out-dir>/<name>.xml holding the summed classes/tests/failures/errors of every
XML in <results-dir>, and prints the per-module summary the report quotes.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)
summary = []
for spec in sys.argv[2:]:
    name, results_dir = spec.split('=', 1)
    classes = tests = failures = errors = skipped = 0
    files = sorted(glob.glob(os.path.join(results_dir, '*.xml')))
    if not files:
        sys.exit('no XML under ' + results_dir)
    for path in files:
        root = ET.parse(path).getroot()
        for suite in ([root] if root.tag == 'testsuite' else root.iter('testsuite')):
            classes += 1
            tests += int(suite.get('tests') or 0)
            failures += int(suite.get('failures') or 0)
            errors += int(suite.get('errors') or 0)
            skipped += int(suite.get('skipped') or 0)
    with open(os.path.join(out_dir, name + '.xml'), 'w') as fh:
        fh.write('<testsuite name="%s" classes=%d tests="%d" failures="%d" errors="%d" skipped="%d"/>\n'
                 % (name, classes, tests, failures, errors, skipped))
    summary.append('%s %d/%d/%d (%d classes)' % (name, tests, failures, errors, classes))
print(' \u00b7 '.join(summary))
