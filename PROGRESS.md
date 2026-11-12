# Elsewhere 2.1 build progress
Updated 07:30 · deadline 11:00 · phase: Lanes building and testing (until 09:15)

**6 of 17 milestones done (35%)**

- [x] 2.0.2 skin fix published on GitHub
- [x] Boss redesign v3 (15 unique fighters, audit passes)
- [x] Field-tactics framework (kingdom 0.4.1)
- [x] Council page v6 (bosses + trading)
- [x] Wiki draft for the rivals (6 items to verify)
- [x] QA preflight: Mother Creak, Selenne, village, Ankhara settled
- [~] running: QA: 2 core self-test failures
- [~] running: War lane: tabards/capes, tests, MSPT
- [~] running: Duelist 0.2.0 plumbing + 300 HP cap (critical path, due 08:00)
- [~] running: Court: unique trading + Aldermark HP cap
- [~] running: Hrafnholt: 6 bosses from v3 cards
- [~] running: Mirewick: 25 test fixes + 5 Magnates rework
- [~] running: Azmar: 4 bosses, headdresses, falcon
- [~] running: Villages + lore
- [ ] 09:15 integrate into one jar
- [ ] 10:00 release tests + final review
- [ ] 10:45 package + publish 2.1.0

## Latest self-tests on the iMac
- kingdom: [kingdom-selftest] TOTAL 280/280 PASS (log 07:30)
- kingdom_duelist: [duelist-selftest] DONE 251 passed, 1 failed (log 07:21)
- kingdom_court: [court-selftest] TOTAL 274/274 PASS (log 03:13)
- rival_hrafnholt: [hrafnholt-selftest] TOTAL 336/356 FAIL (log 07:08)
- rival_azmar: [azmar-selftest] TOTAL 138/156 FAIL (log 07:23)
- rival_mirewick: no self-test yet (log 07:30)
- kingdom_villages: no self-test yet (log 07:30)
- kingdom_lore: [lore-selftest] TOTAL 122/122 PASS (log 07:15)

## iMac build slots
- heavy1: azmar
- heavy2: mire
- heavy3: vill
- client: free
