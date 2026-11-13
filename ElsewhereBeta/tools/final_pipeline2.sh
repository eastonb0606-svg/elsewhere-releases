#!/bin/zsh
# Integrator final pipeline (run from ElsewhereBeta/ at GO): merge + dist, then s-testbatch's one-boot runner (3 boots, 20 modules)
# on one slot, the release suite on a second, the 2.0.2 world check on a third. Cloud runners A-H cover the same hash in parallel.
# GO needs _swarm/{gear3,kingdom,court,villages}/READY. Logs /tmp/fp-*.out.
B="${0:A:h:h}"; cd $B || exit 1; ROOT="${B:h}"
for m in gear3 kingdom court villages; do [ -e $ROOT/ElsewhereContent/_swarm/$m/READY ] || echo "WARNING: _swarm/$m/READY missing"; done
python3 tools/merge.py --sync > merge-final.out 2>&1
rm -f out-imac/build-imac.rc; HL=int1 zsh tools/imac_int.sh gradle dist > /tmp/fp-dist.out 2>&1 || { echo "BUILD FAILED"; grep -E '^[a-z_0-9]+: [0-9]+ error|^    (main|client)/' /tmp/fp-dist.out | sort -u | head -20; exit 1; }
cp dist/elsewhere-3.0.0.jar /tmp/elsewhere-3.0.0.jar; sha1sum dist/elsewhere-3.0.0.jar 2>/dev/null || shasum dist/elsewhere-3.0.0.jar
(PORT0=26100 TB_XMX=3072m LANE=/Users/eastonbrown/mcbuild/lane-int HL=int1 zsh tools/testbatch.sh fin > /tmp/fp-tb.out 2>&1; echo TB-DONE >> /tmp/fp-tb.out) &
sleep 20; (NO_FPS=1 QSUB=/qt QL=qaint3 $ROOT/ElsewhereQA/run-release-int.sh /tmp/elsewhere-3.0.0.jar server > /tmp/fp-rel.out 2>&1; echo REL-DONE >> /tmp/fp-rel.out) &
sleep 20; (NO_FPS=1 QSUB=/qu QL=qaint2 $ROOT/ElsewhereQA/run-release-int.sh /tmp/elsewhere-3.0.0.jar prod,upgrade,used > /tmp/fp-rel2.out 2>&1; echo REL-DONE >> /tmp/fp-rel2.out) &   # lead 18:50: server suite and the other phases in parallel (own lane folders + ports)
wait; echo ALL-DONE
