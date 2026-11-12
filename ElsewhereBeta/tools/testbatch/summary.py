"""Summarise a test-batch run (s-testbatch): python3 summary.py <dir with run-tb-<profile>/boot-N.log + batch.txt>.

Per module, the same lines as the per-module boots gave: its own TOTAL/DONE line, its FAIL lines and the ERROR scan
(`/ERROR]` or `Exception` lines, as imac_int.sh counts them), cut from the log between [testbatch] BEGIN and END.
Lines outside every module (boot, cleanup, shutdown) are scanned as "(boot/between)". Exit 0 only if every module
ended DONE with a passing total and no ERROR lines.
"""
import glob, os, re, sys

base = sys.argv[1]
TOTAL = re.compile(r"selftest\].*?(TOTAL|DONE \d+ passed|DONE pass=\d+|\b\d+ passed, \d+ failed)")  # the last such line of a module is its total
FAIL = re.compile(r"\bFAIL(ED)?\b")
ERR = re.compile(r"/ERROR\]|Exception")
BEGIN = re.compile(r"\[testbatch\] BEGIN (\S+)")
END = re.compile(r"\[testbatch\] END (\S+) (\S+) ([\d.]+)s(.*)")
ok_all = True
for pdir in sorted(glob.glob(os.path.join(base, "run-tb-*"))):
    prof = os.path.basename(pdir)[7:]
    boots = sorted(glob.glob(os.path.join(pdir, "boot-*.log")), key=lambda p: int(re.findall(r"(\d+)\.log$", p)[0]))
    batch = open(os.path.join(pdir, "batch.txt")).read() if os.path.exists(os.path.join(pdir, "batch.txt")) else ""
    secs = sum(int(x) for x in re.findall(r"exit \S+ (\d+) s", batch))
    print(f"######## profile {prof}: {len(boots)} boot(s), wall {secs} s ({secs / 60:.1f} min)")
    mods, order, between = {}, [], []
    for b in boots:
        cur = None
        for ln in open(b, errors="replace"):
            ln = ln.rstrip("\n")
            m = BEGIN.search(ln)
            if m:
                cur = m.group(1)
                order.append(cur)
                mods[cur] = {"lines": [], "end": None}
                continue
            m = END.search(ln)
            if m and cur == m.group(1):
                mods[cur]["end"] = (m.group(2), float(m.group(3)), m.group(4).strip())
                cur = None
                continue
            (mods[cur]["lines"] if cur else between).append(ln)
    for d in re.findall(r"JVM-DIED (\S+)", batch):
        if d in mods and mods[d]["end"] is None:
            mods[d]["end"] = ("JVM-DIED", 0.0, "")
    for mod in order:
        info = mods[mod]
        lines = info["lines"]
        totals = [(re.search(r"\[[\w-]*selftest\].*", l) or re.search(r".*", l)).group(0)[:160] for l in lines if TOTAL.search(l)]
        errs = [l for l in lines if ERR.search(l)]
        fails = [l for l in lines if FAIL.search(l) and not TOTAL.search(l) and "[testbatch]" not in l]
        res, s, why = info["end"] or ("NO-END", 0.0, "")
        tot = totals[-1] if totals else "(no TOTAL line)"
        passed = res == "DONE" and totals and not re.search(r"FAIL|[1-9]\d* failed|fail=[1-9]", tot) and not fails
        ok_all &= bool(passed) and not errs
        print(f"== {mod}: {tot} | {res} {s:.0f}s | ERROR lines: {len(errs)}{' ' + why if why else ''}")
        for l in fails[:40]:
            print("   " + l[:240])
        for l in errs[:5]:
            print("   ERR " + l[:240])
    errs = [l for l in between if ERR.search(l)]
    print(f"== (boot/between) ERROR lines: {len(errs)}")
    for l in errs[:8]:
        print("   ERR " + l[:240])
    ok_all &= not errs
seen = sum(1 for _ in glob.glob(os.path.join(base, "run-tb-*", "boot-*.log")))
if not seen:
    print("no batch logs found")
    ok_all = False
print("ALL GREEN" if ok_all else "NOT GREEN")
sys.exit(0 if ok_all else 1)
