# cloud-a results
Commit tested: 96b5b4f78167af0babdac5f25ceb707da3875b8c (cloud-src)
Machine: 4 vCPU Intel Xeon @2.1GHz, 15 GB RAM, Linux 6.18, Temurin JDK 25.0.5

## core (Elsewhere)
- Build rc=0 (0=ok)
- Wall (build+run): 165 s (3 targets concurrent)
- Summary lines:
```
[20:13:07] [Server thread/INFO] (Elsewhere) [selftest] 296 passed, 4 failed - FAILURES: [spawn_groups_core: a natural pack of bog_wight forms one leader + 3 followers (steer true, leader scale 1.0), spawn_groups_core: hurting one member alerts the whole group (HUNT), spawn_groups_core: a dead leader hands the group to one follower, spawn_groups_core: swarm-as-one eyeblossom_wisp: the swarm's hitbox is wider (0.6 -> 0.6)]
```
- FAIL lines:
```
[20:12:39] [Server thread/INFO] (Elsewhere) [selftest] FAIL spawn_groups_core: a natural pack of bog_wight forms one leader + 3 followers (steer true, leader scale 1.0)
[20:12:39] [Server thread/INFO] (Elsewhere) [selftest] FAIL spawn_groups_core: hurting one member alerts the whole group (HUNT)
[20:12:39] [Server thread/INFO] (Elsewhere) [selftest] FAIL spawn_groups_core: a dead leader hands the group to one follower
[20:12:39] [Server thread/INFO] (Elsewhere) [selftest] FAIL spawn_groups_core: swarm-as-one eyeblossom_wisp: the swarm's hitbox is wider (0.6 -> 0.6)
```
- /ERROR] count: 19; first 10:
```
[20:13:14] [Server thread/ERROR] (Minecraft) Exception stopping the server
[20:13:34] [Worker-Main-3/ERROR] (Minecraft) Caught exception in thread Thread[#43,Worker-Main-3,5,main]
[20:13:35] [IO-Worker-5/ERROR] (Minecraft) Caught exception in thread Thread[#54,IO-Worker-5,5,main]
[20:13:38] [IO-Worker-10/ERROR] (Minecraft) Caught exception in thread Thread[#59,IO-Worker-10,5,main]
[20:13:43] [IO-Worker-6/ERROR] (Minecraft) Caught exception in thread Thread[#55,IO-Worker-6,5,main]
[20:13:44] [IO-Worker-12/ERROR] (Minecraft) Caught exception in thread Thread[#62,IO-Worker-12,5,main]
[20:13:44] [IO-Worker-11/ERROR] (Minecraft) Caught exception in thread Thread[#61,IO-Worker-11,5,main]
[20:13:43] [IO-Worker-13/ERROR] (Minecraft) Caught exception in thread Thread[#63,IO-Worker-13,5,main]
[20:13:47] [IO-Worker-14/ERROR] (Minecraft) Caught exception in thread Thread[#64,IO-Worker-14,5,main]
[20:13:49] [IO-Worker-9/ERROR] (Minecraft) Caught exception in thread Thread[#58,IO-Worker-9,5,main]
```

## arsenal (ElsewhereContent/arsenal)
- Build rc=0 (0=ok)
- Wall (build+run): 81 s (3 targets concurrent)
- Summary lines:
```
[20:12:30] [Server thread/INFO] (Minecraft) [STDOUT]: [arsenal-selftest] TOTAL 128/128 PASS
```
- FAIL lines:
```
```
- /ERROR] count: 0; first 10:
```
```

## summoner (ElsewhereContent/summoner)
- Build rc=0 (0=ok)
- Wall (build+run): 81 s (3 targets concurrent)
- Summary lines:
```
[20:12:30] [Server thread/INFO] (elsewhere_summoner) [selftest] 126 passed, 0 failed: ALL PASSED
```
- FAIL lines:
```
```
- /ERROR] count: 0; first 10:
```
```

Note: core server did not exit after the self-test (OutOfMemoryError in IO-Workers); killed manually.
