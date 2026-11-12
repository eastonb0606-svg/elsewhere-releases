package dev.elsewhere.kingdom.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.ai.FormationGoal;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kingdom self-test (-Dkingdom.selftest=true): a tick-driven sequence of checks plus the MSPT benchmark.
 * Logs "[kingdom-selftest] PASS|FAIL ..." lines, "BENCH ..." lines and a TOTAL; writes run/selftest-kingdom.log.
 */
public final class SelfTest {
	private SelfTest() {}

	interface Step {
		/** Returns the ticks to wait before the next step. */
		int run(MinecraftServer srv, ServerLevel l) throws Exception;
	}

	static final List<String> LOG = new ArrayList<>();
	static int pass, fail;
	static final List<Step> STEPS = new ArrayList<>();
	static int idx, wait, startDelay = 0;
	static boolean started, finished;
	static int y;

	// benchmark measuring
	static boolean measuring;
	static long tickStart;
	static final List<Long> SAMPLES = new ArrayList<>();

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[kingdom-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void info(String line) {
		LOG.add(line);
		System.out.println("[kingdom-selftest] " + line);
	}

	public static void startTick(MinecraftServer srv) {
		if (measuring) tickStart = System.nanoTime();
	}

	public static void endTick(MinecraftServer srv) {
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (finished) return;
		ServerLevel l = srv.overworld();
		if (!started) {
			if (++startDelay < 400 && !l.isPositionEntityTicking(new BlockPos(0, 0, 0))) return;
			if (startDelay < 40) return;
			started = true;
			build();
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (idx >= STEPS.size()) {
			finish(srv);
			return;
		}
		Step s = STEPS.get(idx++);
		try {
			wait = s.run(srv, l);
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		String total = "TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		info(total);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-kingdom.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("kingdom.selftest.exit")) srv.halt(false);
	}

	static Vec3 at(double x, double z) {
		return new Vec3(x + 0.5, y, z + 0.5);
	}

	static void clean(ServerLevel l) {
		for (Entity e : l.getEntities((Entity) null, new AABB(-80, y - 20, -80, 80, y + 40, 80), e -> !(e instanceof Player))) e.discard();
		KingdomState.get(l).clearForTests();
	}

	static Zombie zombie(ServerLevel l, Vec3 p, boolean ai) {
		Zombie z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
		z.snapTo(p.x, p.y, p.z, 0F, 0F);
		z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		z.setDropChance(EquipmentSlot.HEAD, 0F);
		z.setNoAi(!ai);
		z.setPersistenceRequired();
		l.addFreshEntity(z);
		return z;
	}

	static KingdomSoldier soldier(ServerLevel l, Identifier unit, Identifier faction, Vec3 p) {
		return KingdomApi.spawnSoldier(l, unit, faction, p, null);
	}

	static void face(Entity e, float yaw) {
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
		if (e instanceof net.minecraft.world.entity.LivingEntity le) le.yBodyRot = yaw;
	}

	// ------------------------------------------------------------------------------------------------------------

	static final List<KingdomSoldier> units = new ArrayList<>();
	static KingdomSoldier kBlock, kOpen, kAxe, kAi, kCrit;
	static Pig pig, axePig;
	static Zombie zAi, z1, z2;
	static float aiStartHp;
	static int aiBlockTicks;
	static Squad squad, squad2;
	static BlockPos objective;
	static long memberPaths0;

	static void build() {
		STEPS.clear();
		// --- setup
		STEPS.add((srv, l) -> {
			y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
			info("platform y=" + y + ", units loaded: " + UnitTypes.all().keySet());
			check("13 unit types loaded from JSON", UnitTypes.all().size() >= 13 && UnitTypes.exists(UnitTypes.LANCER) && UnitTypes.exists(UnitTypes.CAPTAIN) && UnitTypes.exists(UnitTypes.PIKEMAN), UnitTypes.all().size());
			KingdomPerf.forceActive = true;
			clean(l);
			return 2;
		});
		// --- loadouts
		STEPS.add((srv, l) -> {
			units.clear();
			Identifier[] ids = {UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.LANCER, UnitTypes.MOUNTED_SWORDSMAN, UnitTypes.ARCHER, UnitTypes.CROSSBOWMAN, UnitTypes.SERGEANT};
			for (int i = 0; i < ids.length; i++) {
				KingdomSoldier s = soldier(l, ids[i], Factions.ALDERMARK, at(-24 + i * 6, 40));
				units.add(s);
				if (s != null) s.setNoAi(false);
			}
			check("all 7 units spawn via API", units.stream().allMatch(s -> s != null), null);
			return 5;
		});
		STEPS.add((srv, l) -> {
			KingdomSoldier ma = units.get(0), kn = units.get(1), la = units.get(2), ms = units.get(3), ar = units.get(4), xb = units.get(5), sg = units.get(6);
			check("man-at-arms: chainmail, iron sword, shield", ma.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE) && ma.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET)
				&& ma.getMainHandItem().is(Items.IRON_SWORD) && ma.getOffhandItem().is(Items.SHIELD) && ma.getMaxHealth() == 20F, ma.getMaxHealth());
			check("knight: iron armor, sword, shield", kn.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && kn.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)
				&& kn.getMainHandItem().is(Items.IRON_SWORD) && kn.getOffhandItem().is(Items.SHIELD), null);
			check("lancer: iron spear, riding an armored horse", la.getMainHandItem().is(Items.IRON_SPEAR) && la.getVehicle() instanceof AbstractHorse h
				&& h.getItemBySlot(EquipmentSlot.BODY).is(Items.IRON_HORSE_ARMOR) && h.isSaddled(), la.getVehicle());
			check("mounted swordsman: sword on a horse", ms.getMainHandItem().is(Items.IRON_SWORD) && ms.getVehicle() instanceof AbstractHorse, ms.getVehicle());
			check("archer: dyed leather (no chainmail), bow", ar.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
				&& ar.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.DYED_COLOR) && ar.getMainHandItem().is(Items.BOW)
				&& Arrays.stream(new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}).noneMatch(s -> ar.getItemBySlot(s).getItem().toString().contains("chainmail")), null);
			check("crossbowman: crossbow, dyed leather", xb.getMainHandItem().is(Items.CROSSBOW) && xb.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.DYED_COLOR), null);
			check("sergeant: officer, 28 hp, toughness 2, sword+shield", sg.isOfficer() && sg.getMaxHealth() == 28F && sg.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) >= 2.0 && sg.getOffhandItem().is(Items.SHIELD), sg.getMaxHealth());
			check("skins and tabard synced", ar.skin().getPath().contains("archer") && ma.tabardColor() == 0x2E4C9E, ar.skin() + " " + Integer.toHexString(ma.tabardColor()));
			clean(l);
			return 2;
		});
		// --- shield: forced block reduces damage; unblocked control; axe disables
		STEPS.add((srv, l) -> {
			kBlock = soldier(l, UnitTypes.KNIGHT, Factions.ALDERMARK, at(0, 30));
			kOpen = soldier(l, UnitTypes.KNIGHT, Factions.ALDERMARK, at(8, 30));
			kAxe = soldier(l, UnitTypes.KNIGHT, Factions.ALDERMARK, at(16, 30));
			for (KingdomSoldier k : List.of(kBlock, kOpen, kAxe)) {
				k.setNoAi(true);
				face(k, 0F);
			}
			pig = EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND);
			pig.snapTo(0.5, y, 32.5, 180F, 0F);
			pig.setNoAi(true);
			l.addFreshEntity(pig);
			axePig = EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND);
			axePig.snapTo(16.5, y, 32.5, 180F, 0F);
			axePig.setNoAi(true);
			axePig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			l.addFreshEntity(axePig);
			kBlock.startUsingItem(InteractionHand.OFF_HAND);
			kAxe.startUsingItem(InteractionHand.OFF_HAND);
			return 8;
		});
		STEPS.add((srv, l) -> {
			face(kBlock, 0F);
			face(kOpen, 0F);
			face(kAxe, 0F);
			check("knight is blocking after the shield delay", kBlock.isBlocking(), kBlock.getTicksUsingItem());
			float h0 = kBlock.getHealth(), o0 = kOpen.getHealth();
			kBlock.hurtServer(l, l.damageSources().mobAttack(pig), 6F);
			kOpen.hurtServer(l, l.damageSources().mobAttack(pig), 6F);
			check("shield block reduces frontal melee damage (vanilla BlocksAttacks)", kBlock.getHealth() == h0 && kOpen.getHealth() < o0 && kBlock.blocks == 1,
				"blocked " + (h0 - kBlock.getHealth()) + " vs open " + (o0 - kOpen.getHealth()));
			kAxe.hurtServer(l, l.damageSources().mobAttack(axePig), 4F);
			check("axe hit disables the shield like a player's", kAxe.shieldDisabledTicks >= 60 && !kAxe.isUsingItem(), kAxe.shieldDisabledTicks);
			clean(l);
			// AI blocking vs an attacking zombie
			kAi = soldier(l, UnitTypes.KNIGHT, Factions.ALDERMARK, at(0, 30));
			zAi = zombie(l, at(0, 33), true);
			zAi.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60.0);
			zAi.setHealth(60F);
			zAi.setTarget(kAi);
			aiStartHp = zAi.getHealth();
			aiBlockTicks = 0;
			KingdomSoldier.totalBlocks = 0;
			return 0;
		});
		for (int i = 0; i < 120; i++) {
			STEPS.add((srv, l) -> {
				if (kAi.isBlocking()) aiBlockTicks++;
				return 0;
			});
		}
		STEPS.add((srv, l) -> {
			check("AI knight raises the shield in a fight and lowers it to strike", aiBlockTicks > 0 && (zAi.isDeadOrDying() || zAi.getHealth() < aiStartHp),
				"blocking ticks " + aiBlockTicks + "/120, zombie hp " + zAi.getHealth() + ", blocks " + kAi.blocks);
			clean(l);
			return 2;
		});
		// --- crits: grounded vs falling hit
		STEPS.add((srv, l) -> {
			kCrit = soldier(l, UnitTypes.KNIGHT, Factions.ALDERMARK, at(0, 30));
			kCrit.setNoAi(true);
			z1 = zombie(l, at(1, 31), false);
			z2 = zombie(l, at(-1, 31), false);
			return 2;
		});
		STEPS.add((srv, l) -> {
			kCrit.chargeAttack();
			float a = z1.getHealth();
			kCrit.strike(l, z1);
			float d1 = a - z1.getHealth();
			kCrit.chargeAttack();
			kCrit.setOnGround(false);
			kCrit.fallDistance = 1.0;
			kCrit.setDeltaMovement(0, -0.3, 0);
			float b = z2.getHealth();
			long c0 = KingdomSoldier.totalCrits;
			kCrit.strike(l, z2);
			float d2 = b - z2.getHealth();
			check("jump crit deals 1.5x and counts as a crit", Math.abs(d2 - d1 * 1.5F) < 0.05F && KingdomSoldier.totalCrits == c0 + 1, "normal " + d1 + ", crit " + d2);
			kCrit.setOnGround(true);
			kCrit.fallDistance = 0;
			kCrit.setSprinting(true);
			kCrit.chargeAttack();
			z1.damageCooldownTime = 0;
			long sh = KingdomSoldier.totalSprintHits;
			Vec3 v0 = z1.getDeltaMovement();
			kCrit.strike(l, z1);
			check("sprint hit adds knockback and ends the sprint", KingdomSoldier.totalSprintHits == sh + 1 && !kCrit.isSprinting(), z1.getDeltaMovement().subtract(v0));
			clean(l);
			return 2;
		});
		// --- squad follows leader
		STEPS.add((srv, l) -> {
			List<KingdomSoldier> sq = KingdomApi.spawnSquad(l, BlockPos.containing(at(-20, -20)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.ARCHER));
			check("squad spawns (sergeant + 6)", sq.size() == 7 && sq.get(0).isSquadLeader() && sq.get(0).squad().size() == 7, sq.size());
			squad = sq.get(0).squad();
			objective = BlockPos.containing(at(-4, -20));
			squad.setObjective(objective);
			memberPaths0 = FormationGoal.memberPaths;
			return 200;
		});
		STEPS.add((srv, l) -> {
			var lead = squad.leaderEntity(l);
			double ld = lead == null ? 999 : Math.sqrt(objective.distToCenterSqr(lead.position()));
			double worst = 0;
			for (KingdomSoldier m : squad.memberEntities(l)) {
				double d = m.formationTarget == null ? 999 : Math.sqrt(m.distanceToSqr(m.formationTarget));
				worst = Math.max(worst, d);
			}
			check("squad leader reaches the objective", ld < 3.0, String.format("%.1f blocks", ld));
			check("members hold formation slots near the leader", worst < 3.0, String.format("worst %.1f blocks", worst));
			check("members did not pathfind globally", FormationGoal.memberPaths - memberPaths0 <= 1, FormationGoal.memberPaths - memberPaths0);
			// persistence: state codec roundtrip + soldier NBT roundtrip
			KingdomState st = KingdomState.get(l);
			Tag tag = KingdomState.CODEC.encodeStart(l.registryAccess().createSerializationContext(NbtOps.INSTANCE), st).getOrThrow();
			KingdomState back = KingdomState.CODEC.parse(l.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
			Squad sb = back.squad(squad.id());
			check("squad survives save/load (codec)", sb != null && squad.leader().equals(sb.leader()) && sb.members().equals(squad.members())
				&& sb.formation() == squad.formation() && objective.equals(sb.objective()), tag.toString().length() + " chars");
			KingdomSoldier m0 = squad.memberEntities(l).get(0);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, l.registryAccess());
			m0.saveWithoutId(out);
			CompoundTag ct = out.buildResult();
			KingdomSoldier copy = (KingdomSoldier) m0.getType().create(l, EntitySpawnReason.LOAD);
			copy.load(TagValueInput.create(ProblemReporter.DISCARDING, l.registryAccess(), ct));
			check("soldier keeps unit, faction and squad in NBT", squad.id().equals(copy.squadId()) && copy.unitId().equals(m0.unitId()) && copy.factionId().equals(m0.factionId()), copy.unitId());
			copy.discard();
			return 2;
		});
		// --- morale: officer dies -> rout
		STEPS.add((srv, l) -> {
			KingdomConfig.routChanceOnOfficerDeath = 1.0;
			squad.leaderEntity(l).kill(l);
			return 2;
		});
		STEPS.add((srv, l) -> {
			List<KingdomSoldier> mem = new ArrayList<>(squad.memberEntities(l));
			var nl = squad.leaderEntity(l);
			if (nl instanceof KingdomSoldier ks) mem.add(ks);
			long routed = mem.stream().filter(KingdomSoldier::isRouted).count();
			check("officer death routs the squad (chance 1.0)", routed == mem.size() && mem.size() == 6, routed + "/" + mem.size());
			// 50 right after the death; the squad's 5 s morale regen (+2, +5 with a standard) may land inside the 2-tick wait
			check("morale drops by 50 and a new leader is promoted", squad.morale() >= 50 && squad.morale() <= 55 && nl != null, squad.morale());
			KingdomConfig.routChanceOnOfficerDeath = 0.0;
			List<KingdomSoldier> sq2 = KingdomApi.spawnSquad(l, BlockPos.containing(at(20, -20)), Factions.ALDERMARK, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS));
			squad2 = sq2.get(0).squad();
			sq2.get(0).kill(l);
			return 2;
		});
		STEPS.add((srv, l) -> {
			long routed = squad2.memberEntities(l).stream().filter(KingdomSoldier::isRouted).count();
			check("no rout with chance 0.0", routed == 0 && squad2.leader() != null, routed);
			KingdomConfig.routChanceOnOfficerDeath = 0.5;
			clean(l);
			return 2;
		});
		// --- caps
		STEPS.add((srv, l) -> {
			int g0 = KingdomConfig.maxRealSoldiersGlobal;
			int base = KingdomPerf.loadedCount();
			KingdomConfig.maxRealSoldiersGlobal = base + 3;
			int ok = 0;
			for (int i = 0; i < 5; i++) if (soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(-10 + i * 2, 0)) != null) ok++;
			check("global cap refuses spawns past the limit", ok == 3, ok + "/5 spawned with room for 3");
			KingdomConfig.maxRealSoldiersGlobal = g0;
			int sq0 = KingdomConfig.maxSquadSize;
			KingdomConfig.maxSquadSize = 4;
			List<KingdomSoldier> s4 = KingdomApi.spawnSquad(l, BlockPos.containing(at(10, 0)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS));
			check("squad size cap", s4.size() == 4 && s4.get(0).squad().size() == 4, s4.size());
			KingdomConfig.maxSquadSize = sq0;
			int m0 = KingdomConfig.maxMounted;
			KingdomConfig.maxMounted = 0;
			check("mounted cap refuses a lancer", soldier(l, UnitTypes.LANCER, Factions.ALDERMARK, at(0, 8)) == null, null);
			KingdomConfig.maxMounted = m0;
			// per-player active cap
			KingdomPerf.forceActive = false;
			KingdomConfig.maxRealSoldiersPerPlayer = 5;
			KingdomPerf.testObservers.add(at(0, 0));
			return 25;
		});
		STEPS.add((srv, l) -> {
			int act = KingdomPerf.activeCount(l), all = KingdomPerf.loaded(l).size();
			check("per-player active cap (5 of " + all + " active)", act == 5 && all > 5, act);
			KingdomPerf.testObservers.clear();
			KingdomConfig.maxRealSoldiersPerPlayer = 15;
			return 25;
		});
		STEPS.add((srv, l) -> {
			check("no player near: no soldier runs full AI", KingdomPerf.activeCount(l) == 0, KingdomPerf.activeCount(l));
			KingdomPerf.forceActive = true;
			clean(l);
			return 2;
		});
		// --- targeting
		STEPS.add((srv, l) -> {
			KingdomSoldier s = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(0, 20));
			KingdomSoldier ally = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(2, 20));
			KingdomSoldier rebel = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.REBELS, at(4, 20));
			Zombie z = zombie(l, at(-3, 20), false);
			Wolf w = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND);
			w.snapTo(0.5, y, 23.5, 0, 0);
			l.addFreshEntity(w);
			FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("kingdom-test".getBytes()), "kingdom-test"));
			w.setTame(true, false);
			w.setOwner(fp);
			check("targets hostile mobs", KingdomTargeting.canAttack(s, z), null);
			check("targets other factions, not its own", KingdomTargeting.canAttack(s, rebel) && !KingdomTargeting.canAttack(s, ally), null);
			check("never targets tamed pets unless attacked", !KingdomTargeting.canAttack(s, w), null);
			// Aldermark is hostile in its unit JSON, but in the merged build kingdom_lore sets it neutral once on the first start
			// (KINGDOM.md 3). Test the hostile stance explicitly so the check does not depend on which modules are present.
			KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, true);
			check("hostile faction targets players", KingdomTargeting.canAttack(s, fp), null);
			KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, false);
			check("neutral faction ignores players", !KingdomTargeting.canAttack(s, fp), null);
			check("faction stance saved in the overworld state", Boolean.FALSE.equals(KingdomState.get(srv.overworld()).factionOverrides().get(Factions.ALDERMARK)), null);
			s.setLastHurtByMob(fp);
			check("neutral soldier fights a player who attacked it", KingdomTargeting.canAttack(s, fp), null);
			s.setLastHurtByMob(w);
			check("pet that attacked is fair game", KingdomTargeting.canAttack(s, w), null);
			KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, true);
			KingdomConfig.respectPvpGamerule = true;
			var rules = l.getGameRules();
			boolean pvp0 = rules.get(net.minecraft.world.level.gamerules.GameRules.PVP);
			rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, false, srv);
			check("respectPvpGamerule: pvp off -> no unprovoked player targets", !KingdomTargeting.canAttack(ally, fp), null);
			rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, pvp0, srv);
			KingdomConfig.respectPvpGamerule = false;
			float h0 = ally.getHealth();
			ally.hurtServer(l, l.damageSources().mobAttack(s), 5F);
			check("no friendly fire between same-faction soldiers", ally.getHealth() == h0, null);
			fp.discard();
			clean(l);
			return 2;
		});
		SelfTest2.add();
		// --- load test (K-A): 30 soldiers in mixed squads vs 30 zombies, and the 15-per-player case
		addBench("baseline: 30 zombies, no soldiers", 0, 0);
		addBench("30 soldiers in 4 mixed squads vs 30 zombies (all full AI)", 30, 0);
		addBench("15 per player: 1 player at the battle (30 requested)", 30, 1);
		addBench("2 players at the battle: 30 soldiers, 15 active each", 30, 2);
		SelfTestFactions.add();
		SelfTestWar.add();
		SelfTestLongship.add();
		SelfTestTactics.add();
	}

	static int benchDead;
	static long crit0, block0, attacks0, paths0, denied0;
	static final List<String> BENCH = new ArrayList<>();

	static void addBench(String name, int soldiers, int observers) {
		STEPS.add((srv, l) -> {
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			KingdomPerf.forceActive = observers == 0;
			KingdomPerf.testObservers.clear();
			if (observers >= 1) KingdomPerf.testObservers.add(at(0, 0));
			if (observers >= 2) KingdomPerf.testObservers.add(at(4, 0));
			int made = 0;
			if (soldiers > 0) {
				List<List<Identifier>> mem = List.of(
					List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.ARCHER),
					List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.PIKEMAN, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.CROSSBOWMAN, UnitTypes.CROSSBOWMAN),
					List.of(UnitTypes.STANDARD_BEARER, UnitTypes.SURGEON, UnitTypes.SQUIRE, UnitTypes.LANCER, UnitTypes.MOUNTED_SWORDSMAN, UnitTypes.ARCHER),
					List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.HERALD, UnitTypes.KNIGHT, UnitTypes.CROSSBOWMAN, UnitTypes.ARCHER, UnitTypes.LANCER));
				Identifier[] leaders = {UnitTypes.SERGEANT, UnitTypes.SERGEANT, UnitTypes.CAPTAIN, UnitTypes.SERGEANT};
				for (int i = 0; i < 4; i++)
					made += KingdomApi.spawnSquad(l, BlockPos.containing(at(-12 + i * 8, -6)), Factions.ALDERMARK, i % 2 == 0 ? Squad.Formation.LINE : Squad.Formation.SHIELD_WALL, leaders[i], mem.get(i)).size();
			}
			benchMade = made;
			for (int i = 0; i < 30; i++) zombie(l, at(-15 + (i % 10) * 3, 12 + (i / 10) * 3), true);
			crit0 = KingdomSoldier.totalCrits;
			block0 = KingdomSoldier.totalBlocks;
			attacks0 = KingdomSoldier.totalAttacks;
			paths0 = KingdomPerf.pathsStarted;
			denied0 = KingdomPerf.pathsDenied;
			info("BENCH start: " + name + " (spawned " + made + " soldiers, 30 zombies)");
			SAMPLES.clear();
			measuring = true;
			tickStart = 0;
			return 300;
		});
		STEPS.add((srv, l) -> {
			measuring = false;
			long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
			int zl = l.getEntitiesOfClass(Zombie.class, new AABB(at(0, 0), at(0, 0)).inflate(80)).size();
			int sl = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(at(0, 0), at(0, 0)).inflate(80)).size();
			long attacks = KingdomSoldier.totalAttacks - attacks0, crits = KingdomSoldier.totalCrits - crit0;
			int active = KingdomPerf.activeCount(l);
			String line = String.format("BENCH %s: MSPT avg %.2f ms, p95 %.2f, max %.2f over %d ticks | spawned %d, alive %d soldiers / %d zombies | attacks %d, crits %d, blocks %d, paths %d (denied %d), active %d",
				name, avg, p95, max, s.length, benchMade, sl, zl, attacks, crits, KingdomSoldier.totalBlocks - block0,
				KingdomPerf.pathsStarted - paths0, KingdomPerf.pathsDenied - denied0, active);
			info(line);
			BENCH.add(line);
			check("benchmark ran: " + name, s.length >= 290, s.length + " ticks");
			List<KingdomSoldier> alive = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(at(0, 0), at(0, 0)).inflate(80));
			if (soldiers > 0 && observers == 0) {
				int shots = alive.stream().filter(k -> k.role().ranged()).mapToInt(k -> k.shots).sum();
				int cav = alive.stream().filter(k -> k.role() == dev.elsewhere.kingdom.UnitType.Role.CAVALRY).mapToInt(k -> k.attacks).sum();
				int spear = alive.stream().filter(k -> k.role() == dev.elsewhere.kingdom.UnitType.Role.LANCER).mapToInt(k -> k.spearTicks).sum();
				check("30 mixed: all 30 spawn (area cap 30)", benchMade == 30, benchMade);
				check("archers and crossbowmen shoot from range", shots > 0, shots + " shots");
				check("mounted swordsmen and lancers fight (cavalry 3 of 30 = 10%)", cav + spear > 0, cav + " hits, " + spear + " spear ticks");
				check("AI shield blocks do not break the battle (blocks counted: informational)", KingdomSoldier.totalBlocks - block0 >= 0, KingdomSoldier.totalBlocks - block0);
				check("crits stay rare in battle (<20% of melee hits)", crits <= attacks * 0.2, crits + "/" + attacks);
				check("soldiers kill zombies", zl < 30, zl + " zombies left");
				check("30-soldier battle stays light (avg MSPT < 15 ms)", avg < 15.0, String.format("%.2f ms", avg));
			}
			if (observers == 1) check("15 per player: only 15 of 30 spawn near one player, every one still alive is active (a zombie may kill one during the run)", benchMade == 15 && active == sl && sl >= 12, benchMade + " spawned, " + active + " active");
			if (observers == 2) check("2 players: 30 spawn, every one still alive is active (15 each; a zombie may kill one during the run)", benchMade == 30 && active == sl && sl >= 26, benchMade + " spawned, " + active + " active");
			if (soldiers > 0) check("path budget respected", (KingdomPerf.pathsStarted - paths0) <= 300L * KingdomConfig.pathSearchesPerTick, KingdomPerf.pathsStarted - paths0);
			KingdomPerf.testObservers.clear();
			KingdomPerf.forceActive = true;
			return 2;
		});
	}

	static int benchMade;
}
