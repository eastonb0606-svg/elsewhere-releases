# cloud-b results

Tested commit: 96b5b4f78167af0babdac5f25ceb707da3875b8c (cloud-src)
Machine: 4 cores, 15 GB RAM, Linux 6.18.44-fc-v378, JDK jdk-25.0.5+8
Report time (UTC): 2026-11-12 20:16:18

## petalwind_glasswaste
- Build: ok
- runServer exit: 0; wall time: 211 s (build + run)
- Summary lines:
    [20:16:15] [Server thread/INFO] (Elsewhere/petalwind_glasswaste) [selftest] 140 passed, 1 failed: FAILURES
- PASS count: 140; FAIL count: 1
- FAIL lines:
```
[20:13:51] [Server thread/INFO] (Elsewhere/petalwind_glasswaste) [selftest] FAIL fit3 twin sunpit: /place builds the fit arena and its boss inside its 3x3 chunks (521 ms)
```
- /ERROR] lines: 0

## verdigris_ironpeak
- Build: ok
- runServer exit: 0; wall time: 150 s (build + run)
- Summary lines:
    [20:15:15] [Server thread/INFO] (Elsewhere/verdigris_ironpeak) [selftest] TOTAL 229 passed, 1 failed (89571 ms) 
- PASS count: 229; FAIL count: 1
- FAIL lines:
```
[20:14:20] [Server thread/INFO] (Elsewhere/verdigris_ironpeak) [selftest] FAIL ironpeak return rifts: 9 of 12 located rifts are in foothills biomes below Y 400 (ruin included)
```
- /ERROR] lines: 0

## greenhaze_hush
- Build: ok
- runServer exit: 0; wall time: 192 s (build + run)
- Summary lines:
    [20:15:56] [Server thread/INFO] (Elsewhere/greenhaze_hush) [selftest] TOTAL 203 passed, 1 failed
- PASS count: 203; FAIL count: 1
- FAIL lines:
```
[20:15:49] [Server thread/INFO] (Elsewhere/greenhaze_hush) [selftest] FAIL struct fit: Fern Terrace Farms look (terraces with ripe crops, enclosed water, ferns, munchfruit, bunds, field shed chest) 215 farmland, 22 crops, 6376 water, 1561 leaks, 1 chest
```
- /ERROR] lines: 0
