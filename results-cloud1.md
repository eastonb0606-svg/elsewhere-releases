# results-cloud1 (lane s-armies-fix)

Commit: f7708fa Cloud source sync 2026-11-12 13:59 CST
Machine: 4 cores, 15 GB RAM; JDK 25.0.5 (openjdk apt)
rival_mirewick compiled fine against the new kingdom jar (elsewhere_kingdom-0.4.5).

## Run new-jar
```
[20:01:57] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the player with foresight gear sees, the one without does not (gear Air)
[20:01:57] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 156 in 13 boss ticks)
[20:01:57] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the bar's move label goes only to the player with gear (with gear [Ottoline Marrow, Ottoline Marrow], without [Ottoline Marrow, Ottoline Marrow])
[20:02:07] [mirewick-selftest] FAIL B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (0 pieces)
[20:02:07] [mirewick-selftest] FAIL B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)
[20:03:18] [mirewick-selftest] BENCH start: spawned 15 Mirewick + 15 Aldermark
[20:03:34] [mirewick-selftest] BENCH 15 v 15 Mirewick (tactics on) vs Aldermark: MSPT avg 2.27 ms, p95 3.39, max 6.37 over 301 ticks | spawned 30, alive 13 + 21 | 42 brain decisions in 301 ticks
[20:03:34] [mirewick-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.27 ms)
[20:03:34] [mirewick-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (3.39 ms)
[20:03:37] [mirewick-selftest] TOTAL 508/515 (7 failed) FAIL
```

## Run profile
```
[20:04:05] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the player with foresight gear sees, the one without does not (gear Air)
[20:04:05] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 156 in 13 boss ticks)
[20:04:05] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the bar's move label goes only to the player with gear (with gear [Ottoline Marrow, Ottoline Marrow], without [Ottoline Marrow, Ottoline Marrow])
[20:04:15] [mirewick-selftest] FAIL B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (0 pieces)
[20:04:15] [mirewick-selftest] FAIL B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)
[20:05:26] [mirewick-selftest] BENCH start: spawned 15 Mirewick + 15 Aldermark
[20:05:42] [mirewick-selftest] BENCH 15 v 15 Mirewick (tactics on) vs Aldermark: MSPT avg 2.69 ms, p95 4.55, max 16.80 over 301 ticks | spawned 30, alive 11 + 21 | 42 brain decisions in 301 ticks
[20:05:43] [mirewick-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.69 ms)
[20:05:43] [mirewick-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (4.55 ms)
[20:05:45] [mirewick-selftest] TOTAL 508/515 (7 failed) FAIL
```

## Run new-jar-2
```
[20:06:21] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the player with foresight gear sees, the one without does not (gear Air)
[20:06:21] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 156 in 13 boss ticks)
[20:06:21] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the bar's move label goes only to the player with gear (with gear [Ottoline Marrow, Ottoline Marrow], without [Ottoline Marrow, Ottoline Marrow])
[20:06:31] [mirewick-selftest] FAIL B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (0 pieces)
[20:06:31] [mirewick-selftest] FAIL B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)
[20:07:43] [mirewick-selftest] BENCH start: spawned 15 Mirewick + 15 Aldermark
[20:07:59] [mirewick-selftest] BENCH 15 v 15 Mirewick (tactics on) vs Aldermark: MSPT avg 2.23 ms, p95 3.30, max 7.06 over 301 ticks | spawned 30, alive 13 + 21 | 41 brain decisions in 301 ticks
[20:07:59] [mirewick-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.23 ms)
[20:07:59] [mirewick-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (3.30 ms)
[20:08:02] [mirewick-selftest] TOTAL 508/515 (7 failed) FAIL
```

## Run old-jar
```
[20:08:27] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the player with foresight gear sees, the one without does not (gear Air)
[20:08:27] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: wind-up telegraph particles reach the player with gear and are held from the one without (shown 0, held 156 in 13 boss ticks)
[20:08:27] [mirewick-selftest] FAIL B5 foresight mirewick_marrow: the bar's move label goes only to the player with gear (with gear [Ottoline Marrow, Ottoline Marrow], without [Ottoline Marrow, Ottoline Marrow])
[20:08:37] [mirewick-selftest] FAIL B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (0 pieces)
[20:08:37] [mirewick-selftest] FAIL B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)
[20:09:48] [mirewick-selftest] BENCH start: spawned 15 Mirewick + 15 Aldermark
[20:10:04] [mirewick-selftest] BENCH 15 v 15 Mirewick (tactics on) vs Aldermark: MSPT avg 1.74 ms, p95 2.63, max 5.94 over 301 ticks | spawned 30, alive 14 + 20 | 41 brain decisions in 301 ticks
[20:10:07] [mirewick-selftest] TOTAL 510/515 (5 failed) FAIL
```

## Profile (profile run, Server thread, window 20:05:26-20:05:43 UTC = BENCH start..BENCH 15 v 15 +1s)
Note: only 80 samples fall in the window (JFR profile settings sample ~20 ms; server thread is mostly idle between 2 ms ticks), so counts are coarse.
```
window 2026-11-12 20:05:26+00:00 2026-11-12 20:05:43+00:00 samples 80

SELF top40
6 7.5% net.minecraft.world.level.chunk.LevelChunkSection.isRandomlyTickingFluids
3 3.8% net.minecraft.world.level.TicketStorage.getLowestTicket
2 2.5% net.minecraft.world.level.Level.getChunk
2 2.5% net.minecraft.world.level.TicketStorage.getAllChunksWithTicketThat
2 2.5% net.minecraft.util.ZeroBitStorage.get
2 2.5% dev.elsewhere.kingdom.KingdomSoldier.customServerAiStep
2 2.5% net.minecraft.world.level.BlockCollisions.computeNext
2 2.5% net.minecraft.network.syncher.SynchedEntityData.getItem
2 2.5% net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase.getCollisionShape
2 2.5% net.minecraft.world.level.gameevent.GameEventDispatcher.post
2 2.5% java.util.concurrent.CompletableFuture.reportJoin
2 2.5% it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap.get
1 1.2% net.minecraft.world.entity.ai.util.RandomPos$$Lambda.0x00000000433953d0.applyAsDouble
1 1.2% it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.get
1 1.2% net.minecraft.world.level.pathfinder.Path.getNodeCount
1 1.2% net.minecraft.world.entity.ai.goal.GoalSelector.goalCanBeReplacedForAllFlags
1 1.2% net.minecraft.server.level.ServerChunkCache.getChunk
1 1.2% it.unimi.dsi.fastutil.longs.LongOpenHashSet.contains
1 1.2% net.minecraft.world.item.ItemStack.getComponents
1 1.2% com.mojang.serialization.codecs.RecordCodecBuilder$Instance.lambda$ap3$6
1 1.2% net.minecraft.world.entity.Mob.tick
1 1.2% it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.insert
1 1.2% dev.elsewhere.kingdom.tactics.Behaviours.hazard
1 1.2% jdk.internal.classfile.impl.SplitConstantPool.classEntryForClassOrInterface
1 1.2% net.minecraft.world.entity.Entity.setPos
1 1.2% net.minecraft.network.syncher.SynchedEntityData$DataValue.create
1 1.2% it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.find
1 1.2% net.minecraft.world.entity.projectile.arrow.Arrow.tick
1 1.2% java.util.stream.StreamSpliterators$AbstractWrappingSpliterator.doAdvance
1 1.2% net.minecraft.world.entity.ai.goal.WrappedGoal.getFlags
1 1.2% net.minecraft.world.level.BlockGetter.lambda$clip$1
1 1.2% com.google.common.collect.RegularImmutableMap.get
1 1.2% net.minecraft.world.level.entity.EntitySection.getEntities
1 1.2% net.minecraft.world.entity.LivingEntity.travelInAir
1 1.2% net.minecraft.world.item.enchantment.EnchantmentHelper.modifyArmorEffectiveness
1 1.2% net.minecraft.world.item.enchantment.EnchantmentHelper.runIterationOnEquipment
1 1.2% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkConfig.otherSelfTest
1 1.2% dev.elsewhere.kingdom.tactics.SquadBrain.emit
1 1.2% java.util.HashMap$HashIterator.<init>
1 1.2% it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap.get

INCLUSIVE top60
16 20.0% net.minecraft.world.entity.ai.goal.WrappedGoal.tick
16 20.0% net.minecraft.world.entity.ai.goal.GoalSelector.tickRunningGoals
11 13.8% net.minecraft.world.entity.ai.goal.GoalSelector.tick
9 11.2% dev.elsewhere.duelist.boss.HumanBoss.tick
7 8.8% dev.elsewhere.kingdom.ai.SoldierMeleeGoal.tick
6 7.5% dev.elsewhere.kingdom.ai.GatedGoal.tick
6 7.5% net.minecraft.world.entity.ai.navigation.PathNavigation.moveTo
5 6.2% net.minecraft.world.entity.ai.navigation.GroundPathNavigation.createPath
5 6.2% net.minecraft.world.entity.ai.navigation.PathNavigation.createPath
4 5.0% net.minecraft.world.level.pathfinder.PathFinder.findPath
4 5.0% dev.elsewhere.kingdom.ai.RangedNav.moveTo
4 5.0% dev.elsewhere.kingdom.KingdomSoldier.doHurtTarget
4 5.0% dev.elsewhere.kingdom.KingdomSoldier.strike
4 5.0% dev.elsewhere.kingdom.KingdomSoldier.hurtServer
3 3.8% net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal.tick
3 3.8% dev.elsewhere.rivals.mirewick.DevEntry$$Lambda.0x0000000042e5f878.onEndTick
3 3.8% dev.elsewhere.rivals.mirewick.dev.SelfTest.endTick
3 3.8% dev.elsewhere.rivals.mirewick.dev.SelfTest.lambda$bench$0
3 3.8% dev.elsewhere.rivals.mirewick.dev.SelfTest$$Lambda.0x00000000432b4f18.run
3 3.8% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getCachedPathType
3 3.8% dev.elsewhere.kingdom.tactics.SquadBrain.decide
3 3.8% dev.elsewhere.kingdom.Squad.tick
3 3.8% dev.elsewhere.kingdom.KingdomContent.lambda$init$3
3 3.8% dev.elsewhere.kingdom.KingdomState.tick
3 3.8% dev.elsewhere.kingdom.tactics.SquadBrain.tick
3 3.8% dev.elsewhere.kingdom.tactics.TacticsRuntime.tick
3 3.8% dev.elsewhere.kingdom.KingdomContent$$Lambda.0x0000000042dc2f00.onEndTick
3 3.8% net.minecraft.world.entity.ai.goal.RangedBowAttackGoal.tick
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getStart
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.canStartAt
2 2.5% net.minecraft.world.entity.ai.attributes.AttributeMap.getValue
2 2.5% dev.elsewhere.kingdom.KingdomSoldier.customServerAiStep
2 2.5% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkManager.tickInner
2 2.5% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkConfig.otherSelfTest
2 2.5% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkManager$$Lambda.0x0000000042dd28b8.onEndTick
2 2.5% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkManager.tick
2 2.5% dev.elsewhere.kingdom.armies.townsfolk.TownsfolkConfig.active
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.checkNeighbourBlocks
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator$$Lambda.0x0000000043342600.get
2 2.5% dev.elsewhere.kingdom.tactics.OrderGoal.moveTo
2 2.5% dev.elsewhere.kingdom.tactics.OrderGoal.tick
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathType
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.lambda$getCachedPathType$0
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic
2 2.5% net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeOfMob
1 1.2% net.minecraft.world.entity.ai.sensing.Sensing.hasLineOfSight
1 1.2% net.minecraft.world.entity.ai.util.RandomPos$$Lambda.0x00000000433953d0.applyAsDouble
1 1.2% net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway
1 1.2% dev.elsewhere.kingdom.ai.RoutGoal.tick
1 1.2% net.minecraft.world.entity.ai.util.RandomPos.generateRandomPos
1 1.2% net.minecraft.world.level.pathfinder.Path.getNodeCount
1 1.2% net.minecraft.world.entity.ai.navigation.PathNavigation.tick
1 1.2% net.minecraft.world.entity.ai.navigation.PathNavigation.followThePath
1 1.2% net.minecraft.world.entity.ai.navigation.PathNavigation.shouldTargetNextNodeInDirection
1 1.2% net.minecraft.world.entity.ai.goal.GoalSelector.goalCanBeReplacedForAllFlags
1 1.2% dev.elsewhere.rivals.mirewick.dev.SelfTest.clearBox
1 1.2% dev.elsewhere.kingdom.KingdomSoldier.addAdditionalSaveData
1 1.2% dev.elsewhere.kingdom.tactics.Behaviours.rally
1 1.2% dev.elsewhere.kingdom.tactics.Behaviours$Base.plan
1 1.2% dev.elsewhere.kingdom.tactics.Behaviours.lambda$targets$0

STACKS top15
#1 6 (7.5%)
    net.minecraft.world.level.chunk.LevelChunkSection.isRandomlyTickingFluids
    net.minecraft.world.level.chunk.LevelChunkSection.isRandomlyTicking
    net.minecraft.server.level.ServerLevel.tickChunk
    net.minecraft.server.level.ServerChunkCache.lambda$tickChunks$0
    net.minecraft.server.level.ServerChunkCache$$Lambda.0x0000000043295350.accept
    net.minecraft.server.level.ChunkMap.lambda$forEachBlockTickingChunk$0
    net.minecraft.server.level.ChunkMap$$Lambda.0x0000000043295590.accept
    net.minecraft.server.level.DistanceManager.forEachEntityTickingChunk
    net.minecraft.server.level.ChunkMap.forEachBlockTickingChunk
    net.minecraft.server.level.ServerChunkCache.tickChunks
    net.minecraft.server.level.ServerChunkCache.tickChunks
    net.minecraft.server.level.ServerChunkCache.tick
#2 3 (3.8%)
    net.minecraft.world.level.TicketStorage.getLowestTicket
    net.minecraft.world.level.TicketStorage.getTicketLevelAt
    net.minecraft.world.level.TicketStorage.removeTicketIf
    net.minecraft.world.level.TicketStorage.purgeStaleTickets
    net.minecraft.server.level.ServerChunkCache.tick
    net.minecraft.server.level.ServerLevel.tick
    net.minecraft.server.MinecraftServer.tickChildren
    net.minecraft.server.MinecraftServer.tickServer
    net.minecraft.server.dedicated.DedicatedServer.tickServer
    net.minecraft.server.MinecraftServer.processPacketsAndTick
    net.minecraft.server.MinecraftServer.runServer
    net.minecraft.server.MinecraftServer.lambda$spin$0
#3 2 (2.5%)
    net.minecraft.world.level.TicketStorage.getAllChunksWithTicketThat
    net.minecraft.world.level.TicketStorage.updateForcedChunks
    net.minecraft.world.level.TicketStorage.removeTicket
    net.minecraft.world.level.TicketStorage.updateChunkForced
    net.minecraft.server.level.ServerChunkCache.updateChunkForced
    net.minecraft.server.level.ServerLevel.setChunkForced
    dev.elsewhere.rivals.mirewick.dev.SelfTest.lambda$bench$0
    dev.elsewhere.rivals.mirewick.dev.SelfTest$$Lambda.0x00000000432b4f18.run
    dev.elsewhere.rivals.mirewick.dev.SelfTest.endTick
    dev.elsewhere.rivals.mirewick.DevEntry$$Lambda.0x0000000042e5f878.onEndTick
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.lambda$static$3
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$$Lambda.0x0000000042db7470.onEndTick
#4 2 (2.5%)
    dev.elsewhere.kingdom.KingdomSoldier.customServerAiStep
    net.minecraft.world.entity.Mob.serverAiStep
    net.minecraft.world.entity.LivingEntity.aiStep
    net.minecraft.world.entity.Mob.aiStep
    net.minecraft.world.entity.monster.Monster.aiStep
    net.minecraft.world.entity.LivingEntity.tick
    net.minecraft.world.entity.Mob.tick
    net.minecraft.server.level.ServerLevel.tickNonPassenger
    net.minecraft.server.level.ServerLevel$$Lambda.0x00000000433026b8.accept
    net.minecraft.world.level.Level.guardEntityTick
    net.minecraft.server.level.ServerLevel.lambda$tick$0
    net.minecraft.server.level.ServerLevel$$Lambda.0x00000000432926f8.accept
#5 2 (2.5%)
    net.minecraft.world.level.BlockCollisions.computeNext
    com.google.common.collect.AbstractIterator.tryToComputeNext
    com.google.common.collect.AbstractIterator.hasNext
    com.google.common.collect.ImmutableCollection$Builder.addAll
    com.google.common.collect.ImmutableList$Builder.addAll
    net.minecraft.world.entity.Entity.collectCollidersIgnoringWorldBorder
    net.minecraft.world.entity.Entity.collideBoundingBox
    net.minecraft.world.entity.Entity.collide
    net.minecraft.world.entity.Entity.move
    net.minecraft.world.entity.LivingEntity.handleRelativeFrictionAndCalculateMovement
    net.minecraft.world.entity.LivingEntity.travelInAir
    net.minecraft.world.entity.LivingEntity.travel
#6 2 (2.5%)
    net.minecraft.network.syncher.SynchedEntityData.getItem
    net.minecraft.network.syncher.SynchedEntityData.get
    net.minecraft.world.entity.Entity.getPose
    net.minecraft.world.entity.Entity.hasPose
    net.minecraft.world.entity.Entity.isVisuallySwimming
    net.minecraft.world.entity.LivingEntity.isVisuallySwimming
    net.minecraft.world.entity.LivingEntity.updateSwimAmount
    net.minecraft.world.entity.LivingEntity.tick
    net.minecraft.world.entity.Mob.tick
    net.minecraft.server.level.ServerLevel.tickNonPassenger
    net.minecraft.server.level.ServerLevel$$Lambda.0x00000000433026b8.accept
    net.minecraft.world.level.Level.guardEntityTick
#7 2 (2.5%)
    net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase.getCollisionShape
    net.minecraft.world.phys.shapes.EntityCollisionContext.getCollisionShape
    net.minecraft.world.level.BlockCollisions.computeNext
    com.google.common.collect.AbstractIterator.tryToComputeNext
    com.google.common.collect.AbstractIterator.hasNext
    com.google.common.collect.ImmutableCollection$Builder.addAll
    com.google.common.collect.ImmutableList$Builder.addAll
    net.minecraft.world.entity.Entity.collectCollidersIgnoringWorldBorder
    net.minecraft.world.entity.Entity.collideBoundingBox
    net.minecraft.world.entity.Entity.collide
    net.minecraft.world.entity.Entity.move
    net.minecraft.world.entity.LivingEntity.handleRelativeFrictionAndCalculateMovement
#8 2 (2.5%)
    java.util.concurrent.CompletableFuture.reportJoin
    java.util.concurrent.CompletableFuture.getNow
    net.minecraft.server.level.ChunkHolder.getTickingChunk
    net.minecraft.server.level.ChunkMap.lambda$forEachBlockTickingChunk$0
    net.minecraft.server.level.ChunkMap$$Lambda.0x0000000043295590.accept
    net.minecraft.server.level.DistanceManager.forEachEntityTickingChunk
    net.minecraft.server.level.ChunkMap.forEachBlockTickingChunk
    net.minecraft.server.level.ServerChunkCache.tickChunks
    net.minecraft.server.level.ServerChunkCache.tickChunks
    net.minecraft.server.level.ServerChunkCache.tick
    net.minecraft.server.level.ServerLevel.tick
    net.minecraft.server.MinecraftServer.tickChildren
#9 1 (1.2%)
    net.minecraft.world.level.Level.getChunk
    net.minecraft.world.level.LevelReader.getChunk
    net.minecraft.world.level.Level.getChunk
    net.minecraft.world.level.Level.getChunkAt
    net.minecraft.world.level.Level.getFluidState
    net.minecraft.world.level.BlockGetter.lambda$clip$0
    net.minecraft.world.level.BlockGetter$$Lambda.0x0000000043312000.apply
    net.minecraft.world.level.BlockGetter.traverseBlocks
    net.minecraft.world.level.BlockGetter.clip
    net.minecraft.world.entity.LivingEntity.hasLineOfSight
    net.minecraft.world.entity.LivingEntity.hasLineOfSight
    net.minecraft.world.entity.ai.sensing.Sensing.hasLineOfSight
#10 1 (1.2%)
    net.minecraft.world.entity.ai.util.RandomPos$$Lambda.0x00000000433953d0.applyAsDouble
    net.minecraft.world.entity.ai.util.RandomPos.generateRandomPos
    net.minecraft.world.entity.ai.util.RandomPos.generateRandomPos
    net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway
    dev.elsewhere.kingdom.ai.RoutGoal.tick
    net.minecraft.world.entity.ai.goal.WrappedGoal.tick
    net.minecraft.world.entity.ai.goal.GoalSelector.tickRunningGoals
    net.minecraft.world.entity.ai.goal.GoalSelector.tick
    net.minecraft.world.entity.Mob.serverAiStep
    net.minecraft.world.entity.LivingEntity.aiStep
    net.minecraft.world.entity.Mob.aiStep
    net.minecraft.world.entity.monster.Monster.aiStep
#11 1 (1.2%)
    it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.get
    net.minecraft.server.level.ChunkMap.getUpdatingChunkIfPresent
    net.minecraft.server.level.ChunkMap$DistanceManager.getChunk
    net.minecraft.server.level.LoadingChunkTracker.getLevel
    net.minecraft.server.level.ChunkTracker.getComputedLevel
    net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint.checkEdge
    net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint.checkEdge
    net.minecraft.server.level.ChunkTracker.update
    net.minecraft.server.level.LoadingChunkTracker$$Lambda.0x00000000431cc338.update
    net.minecraft.world.level.TicketStorage.removeTicketIf
    net.minecraft.world.level.TicketStorage.purgeStaleTickets
    net.minecraft.server.level.ServerChunkCache.tick
#12 1 (1.2%)
    net.minecraft.world.level.pathfinder.Path.getNodeCount
    net.minecraft.world.entity.ai.navigation.PathNavigation.shouldTargetNextNodeInDirection
    net.minecraft.world.entity.ai.navigation.PathNavigation.followThePath
    net.minecraft.world.entity.ai.navigation.PathNavigation.tick
    net.minecraft.world.entity.Mob.serverAiStep
    net.minecraft.world.entity.LivingEntity.aiStep
    net.minecraft.world.entity.Mob.aiStep
    net.minecraft.world.entity.monster.Monster.aiStep
    net.minecraft.world.entity.LivingEntity.tick
    net.minecraft.world.entity.Mob.tick
    net.minecraft.server.level.ServerLevel.tickNonPassenger
    net.minecraft.server.level.ServerLevel$$Lambda.0x00000000433026b8.accept
#13 1 (1.2%)
    net.minecraft.world.entity.ai.goal.GoalSelector.goalCanBeReplacedForAllFlags
    net.minecraft.world.entity.ai.goal.GoalSelector.tick
    net.minecraft.world.entity.Mob.serverAiStep
    net.minecraft.world.entity.LivingEntity.aiStep
    net.minecraft.world.entity.Mob.aiStep
    net.minecraft.world.entity.monster.Monster.aiStep
    net.minecraft.world.entity.LivingEntity.tick
    net.minecraft.world.entity.Mob.tick
    net.minecraft.server.level.ServerLevel.tickNonPassenger
    net.minecraft.server.level.ServerLevel$$Lambda.0x00000000433026b8.accept
    net.minecraft.world.level.Level.guardEntityTick
    net.minecraft.server.level.ServerLevel.lambda$tick$0
#14 1 (1.2%)
    net.minecraft.server.level.ServerChunkCache.getChunk
    net.minecraft.world.level.Level.getChunk
    net.minecraft.world.level.LevelReader.getChunk
    net.minecraft.world.level.Level.getChunk
    net.minecraft.world.level.Level.getChunkAt
    net.minecraft.world.level.Level.setBlock
    net.minecraft.world.level.LevelWriter.setBlock
    dev.elsewhere.rivals.mirewick.dev.SelfTest.clearBox
    dev.elsewhere.rivals.mirewick.dev.SelfTest.lambda$bench$0
    dev.elsewhere.rivals.mirewick.dev.SelfTest$$Lambda.0x00000000432b4f18.run
    dev.elsewhere.rivals.mirewick.dev.SelfTest.endTick
    dev.elsewhere.rivals.mirewick.DevEntry$$Lambda.0x0000000042e5f878.onEndTick
#15 1 (1.2%)
    it.unimi.dsi.fastutil.longs.LongOpenHashSet.contains
    net.minecraft.server.level.ChunkMap$DistanceManager.isChunkToRemove
    net.minecraft.server.level.LoadingChunkTracker.getLevel
    net.minecraft.server.level.ChunkTracker.getComputedLevel
    net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint.checkEdge
    net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint.checkNeighbor
    net.minecraft.server.level.ChunkTracker.checkNeighborsAfterUpdate
    net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint.runUpdates
    net.minecraft.server.level.LoadingChunkTracker.runDistanceUpdates
    net.minecraft.server.level.DistanceManager.runAllUpdates
    net.minecraft.server.level.ServerChunkCache.runDistanceManagerUpdates
    net.minecraft.server.level.ServerChunkCache$MainThreadExecutor.pollTask
```
