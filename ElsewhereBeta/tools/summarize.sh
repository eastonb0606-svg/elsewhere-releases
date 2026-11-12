#!/bin/zsh
# summarize a server log: betatest/selftest totals, FAIL lines, ERROR/exception lines
L="$1"
echo "== totals"; grep -E '\[betatest\] TOTAL|-selftest\] TOTAL|selftest\].*(passed|ALL PASSED|failed)|SELFTEST|self-test.*(passed|total)' "$L" | grep -iE 'total|passed,|ALL PASSED' | cut -c1-400
echo "== FAIL lines"; grep -E '\bFAIL\b' "$L" | cut -c1-300 | head -60
echo "== ERROR/WARN-exception lines"; grep -nE '/ERROR\]|ERROR\]|Exception|Caused by|\tat dev\.elsewhere' "$L" | grep -v '\[betatest\] PASS' | cut -c1-300 | head -60
echo "error-ish line count: $(grep -cE '/ERROR\]|Exception' "$L")"
