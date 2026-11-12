# cloud-d results

Tested commit: 6c83a4b4226f343e67855d2b9d140d22a80c6bbe
Machine: 4 cores, 15 GB RAM, Linux 6.18.44-fc-v378, JDK 25.0.5

## kingdom (flag kingdom)
- Build: ok
- Wall time (build+run): 524s
- Self-test summary:
```
[kingdom-selftest] TOTAL 397/397 PASS
```
- Every FAIL line:
```
```
- PASS lines: 397
- /ERROR] count: 0
```
```

## kingdom_duelist (flag duelist)
- Build: ok
- Wall time (build+run): 132s
- Self-test summary:
```
[duelist-selftest] DONE 287 passed, 8 failed
```
- Every FAIL line:
```
[duelist-selftest] FAIL B5 foresight: gear check and the particle gate: the foresight tag has an item in this build (Air)
[duelist-selftest] FAIL B5 foresight: gear check and the particle gate: foresight off for a bare player, on with a foresight item in the off hand
[duelist-selftest] FAIL B5 foresight: gear check and the particle gate: inside a hint scope only the player with foresight gets the particle
[duelist-selftest] FAIL B5 foresight: duelist wind-up particles and the bar's move label: foresight on: the wind-up particles reach the player with gear; foresight off: they are held back from the other
[duelist-selftest] FAIL B5 foresight: duelist wind-up particles and the bar's move label: the player with foresight gear was sent Name · Foresight Slam
[duelist-selftest] FAIL B5 foresight: taking the gear off mid-label re-sends that player's bar and the client flag: gear on: Test Warlord · Cleave
[duelist-selftest] FAIL B5 foresight: taking the gear off mid-label re-sends that player's bar and the client flag: the client flag went out on the change
[duelist-selftest] FAIL B5 foresight: taking the gear off mid-label re-sends that player's bar and the client flag: gear back on: Cleave shows again
```
- PASS lines: 287
- /ERROR] count: 0
```
```

## kingdom_lore (flag lore)
- Build: ok
- Wall time (build+run): 62s
- Self-test summary:
```
[lore-selftest] TOTAL 122/122 PASS
```
- Every FAIL line:
```
```
- PASS lines: 122
- /ERROR] count: 0
```
```
