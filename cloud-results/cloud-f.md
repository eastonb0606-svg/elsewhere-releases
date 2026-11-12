# cloud-f results

Tested commit: 96b5b4f78167af0babdac5f25ceb707da3875b8c
Machine: 4 cores, 15 GB RAM, Linux x64, Temurin JDK 25

## rival_hrafnholt
- build: ok
- runServer exit code: 0
- wall time (build+run): 342 s
- self-test TOTAL/PASS/FAIL lines:
```
[hrafnholt-selftest] TOTAL 494/497 FAIL
PASS lines: 494
[hrafnholt-selftest] FAIL B5 foresight hrafnholt_jarl: the player with foresight gear sees, the one without does not (gear Air)
[hrafnholt-selftest] FAIL B5 foresight hrafnholt_jarl: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 208 in 13 boss ticks)
[hrafnholt-selftest] FAIL B5 foresight hrafnholt_jarl: the bar's move label goes only to the player with gear (with gear [High Jarl Halvard Ironbrow, High Jarl Halvard Ironbrow], without [High Jarl Halvard Ironbrow, High Jarl Halvard Ironbrow])
```
- /ERROR] lines in latest.log: 0

## rival_azmar
- build: ok
- runServer exit code: 0
- wall time (build+run): 305 s
- self-test TOTAL/PASS/FAIL lines:
```
[azmar-selftest] TOTAL 230/233 FAIL
PASS lines: 230
[azmar-selftest] FAIL B5 foresight azmar_sultana: the player with foresight gear sees, the one without does not (gear Air)
[azmar-selftest] FAIL B5 foresight azmar_sultana: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 208 in 13 boss ticks)
[azmar-selftest] FAIL B5 foresight azmar_sultana: the bar's move label goes only to the player with gear (with gear [Sultana Nahira Zahr-Qadir, Sultana Nahira Zahr-Qadir], without [Sultana Nahira Zahr-Qadir, Sultana Nahira Zahr-Qadir])
```
- /ERROR] lines in latest.log: 0

## rival_mirewick
- build: ok
- runServer exit code: 0
- wall time (build+run): 152 s
- self-test TOTAL/PASS/FAIL lines:
```
[mirewick-selftest] TOTAL 510/515 (5 failed) FAIL
PASS lines: 510
[mirewick-selftest] FAIL B5 foresight mirewick_marrow: the player with foresight gear sees, the one without does not (gear Air)
[mirewick-selftest] FAIL B5 foresight mirewick_marrow: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 156 in 13 boss ticks)
[mirewick-selftest] FAIL B5 foresight mirewick_marrow: the bar's move label goes only to the player with gear (with gear [Ottoline Marrow, Ottoline Marrow], without [Ottoline Marrow, Ottoline Marrow])
[mirewick-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.28 ms)
[mirewick-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (3.38 ms)
```
- /ERROR] lines in latest.log: 0

