package dev.elsewhere.rivals.hrafnholt.dev;

import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.boss.Hrafn;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
import dev.elsewhere.rivals.hrafnholt.rewards.HrafnholtRewards;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * 3.0 (s-bossdrops): every Hrafnholt boss, the six seats and the five Risen champions, drops XP and its drops on a player kill outside
 * the court (the release suite found 0 XP on every kill); a court leader still drops its table exactly once; a stale leader rolls it here;
 * the GEAR.md gear3 lines roll at their rates; the kill switch brings 2.x back.
 */
final class SelfTestRewards {
	private SelfTestRewards() {}

	static final double Y = SelfTestBosses.Y;
	static final int ROW = 440;

	record Kill(HrafnholtBoss boss, String path, double x, int xp, int vanillaXp, int want) {}

	static final List<Kill> KILLS = new ArrayList<>();
	static FakePlayer P;
	static HrafnholtBoss LEADER, STALE, OFF;
	static int rollsBefore;

	static void check(String n, boolean ok, Object d) { SelfTest.check(n, ok, d); }

	static int orbs(ServerLevel l, AABB box) {
		int sum = 0;
		for (ExperienceOrb o : l.getEntitiesOfClass(ExperienceOrb.class, box)) sum += o.getValue();
		return sum;
	}

	static List<String> items(ServerLevel l, AABB box) {
		List<String> out = new ArrayList<>();
		for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, box)) out.add(ie.getItem().getCount() + "x" + BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath());
		return out;
	}

	static int count(List<String> items, String path) {
		int n = 0;
		for (String s : items) if (s.endsWith("x" + path)) n++;
		return n;
	}

	static AABB around(double x, double z) {
		return new AABB(x - 12, Y - 4, z - 12, x + 12, Y + 10, z + 12);
	}

	static void kill(ServerLevel l, HrafnholtBoss b) {
		b.setLastHurtByPlayer(P, 100);
		b.setHealth(0F);
		b.die(l.damageSources().playerAttack(P));
	}

	static void add() {
		// every boss type, untracked (a hand-summoned boss, the release suite's direct spawn), killed by a player
		SelfTest.STEPS.add((srv, l) -> {
			SelfTestBosses.clean(l);
			SelfTestBosses.flat(l, -178, ROW - 14, 178, ROW + 14);
			HrafnholtRewards.setEnabledForTest(true);
			P = SelfTest.fp(l, "rewards-hunter");
			P.snapTo(0.5, Y, ROW - 30.5, 0F, 0F);
			KILLS.clear();
			int i = 0;
			for (Hrafn h : Hrafn.values()) for (boolean risen : new boolean[] {false, true}) {
				if (risen && h.risenType == null) continue;
				double x = -165 + 30 * i++;
				HrafnholtBoss b = SelfTestBosses.boss(l, h, risen, x, ROW);
				int want = h == Hrafn.JARL && !risen ? HrafnholtRewards.RULER_XP : HrafnholtRewards.LORD_XP;
				KILLS.add(new Kill(b, BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath(), x + 0.5, HrafnholtRewards.xp(b), b.getExperienceReward(l, P), want));
				kill(l, b);
			}
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (Kill k : KILLS) {
				AABB box = around(k.x(), ROW + 0.5);
				int xp = orbs(l, box);
				List<String> got = items(l, box);
				check("rewards: " + k.path() + " always drops XP on a player kill (" + k.want() + "; HumanBoss had 0)",
					!k.boss().isAlive() && xp > 0 && k.xp() == k.want() && k.vanillaXp() == k.want(), xp + " in orbs, xp() " + k.xp() + ", vanilla " + k.vanillaXp());
				check("rewards: " + k.path() + " drops its faction table once outside the court (Seal, Oathkeeper's Seal, Hacksilver)",
					count(got, "hrafnholt_seal_" + k.boss().h.key) == 1 && count(got, "oathkeepers_seal") == 1 && count(got, "hacksilver") >= 1, got);
			}
			SelfTestBosses.clean(l);
			return 0;
		});
		// a court leader: the court rolls its table, this class rolls nothing (no double drop); XP still drops
		SelfTest.STEPS.add((srv, l) -> {
			SelfTestBosses.flat(l, -20, ROW + 50, 20, ROW + 80);
			CourtState st = CourtState.get(srv, HrafnholtContent.FACTION);
			st.resetForTests();
			rollsBefore = HrafnholtRewards.fallbackRolls;
			var m = CourtLife.spawnLeader(l, st, Ruler.CASTELLAN, SelfTestBosses.at(-10, ROW + 65), null);
			LEADER = m instanceof HrafnholtBoss b ? b : null;
			check("rewards: a court-seated Hallwarden is rolled by the court, not here", LEADER != null && HrafnholtRewards.courtRolls(l, LEADER) && HrafnholtRewards.fallbackDrops(l, LEADER, P).isEmpty(), m);
			// a stale leader: tagged for the castellan seat, but the court seats another entity there
			STALE = SelfTestBosses.boss(l, Hrafn.HALLWARDEN, false, 10, ROW + 65);
			Leaders.tag(STALE, HrafnholtContent.FACTION, "castellan");
			check("rewards: a tagged boss the court does not seat is not the court's to roll", !HrafnholtRewards.courtRolls(l, STALE), null);
			if (LEADER != null) kill(l, LEADER);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(-9.5, ROW + 65.5);
			List<String> got = items(l, box);
			check("rewards: a court leader's death drops its Seal exactly once (court roll, no second roll here) and XP",
				LEADER != null && count(got, "hrafnholt_seal_hallwarden") == 1 && orbs(l, box) > 0 && HrafnholtRewards.fallbackRolls == rollsBefore, got + " xp " + orbs(l, box));
			kill(l, STALE);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(10.5, ROW + 65.5);
			List<String> got = items(l, box);
			check("rewards: a stale leader (seat already dead) still drops its table once and XP", count(got, "hrafnholt_seal_hallwarden") == 1 && orbs(l, box) > 0
				&& HrafnholtRewards.fallbackRolls == rollsBefore + 1, got + " xp " + orbs(l, box));
			CourtState.get(srv, HrafnholtContent.FACTION).resetForTests();
			SelfTestBosses.clean(l);
			return 0;
		});
		// GEAR.md A5 lines and their rates
		SelfTest.STEPS.add((srv, l) -> {
			check("rewards: GEAR.md lines: Hallwarden Hearthguard x3 50% + Skjaldborg 30%, Gudrun Ulfhednar mask/breeches 50%, Jarl Huginn's Bite 30%, Skald Runeseax 50%",
				HrafnholtRewards.EXTRAS.get(Hrafn.HALLWARDEN).size() == 4 && HrafnholtRewards.EXTRAS.get(Hrafn.HALLWARDEN).get(3).chance() == 0.3F
					&& HrafnholtRewards.EXTRAS.get(Hrafn.CHIEF).size() == 2 && HrafnholtRewards.EXTRAS.get(Hrafn.JARL).get(0).chance() == 0.3F
					&& HrafnholtRewards.EXTRAS.get(Hrafn.SKALD).get(0).chance() == 0.5F && !HrafnholtRewards.EXTRAS.containsKey(Hrafn.HUNTMASTER) && !HrafnholtRewards.EXTRAS.containsKey(Hrafn.HERSIR), HrafnholtRewards.EXTRAS);
			RandomSource rnd = RandomSource.create(7);
			int helm = 0, shield = 0, n = 4000;
			for (int i = 0; i < n; i++) for (var e : HrafnholtRewards.rollExtraLines(Hrafn.HALLWARDEN, rnd)) {
				if (e.item().equals("hrafnholt_hearthguard_helm")) helm++;
				if (e.item().equals("hrafnholt_skjaldborg_shield")) shield++;
			}
			check("rewards: Hearthguard helm about 50% and Skjaldborg about 30% per Hallwarden kill (4000 kills)", helm > 1880 && helm < 2120 && shield > 1110 && shield < 1290, helm + " / " + shield);
			int missing = 0;
			for (var list : HrafnholtRewards.EXTRAS.values()) for (var e : list) if (HrafnholtRewards.item(e.item()) == null) missing++;
			if (BuiltInRegistries.ITEM.containsKey(HrafnholtContent.id("hrafnholt_hearthguard_helm"))) check("rewards: every GEAR.md line resolves to a gear3 item", missing == 0, missing);
			else SelfTest.info("rewards: gear3 is not on this classpath; its lines are skipped by id (merged jar resolves them)");
			return 0;
		});
		// kill switch rival_boss_rewards=false: no XP (2.x); the old untracked roll in onDefeated still drops the table once
		SelfTest.STEPS.add((srv, l) -> {
			SelfTestBosses.flat(l, -20, ROW + 90, 20, ROW + 110);
			HrafnholtRewards.setEnabledForTest(false);
			OFF = SelfTestBosses.boss(l, Hrafn.SKALD, false, 0, ROW + 100);
			check("rewards: switch off gives the 2.x XP (none)", HrafnholtRewards.xp(OFF) == 0 && OFF.getExperienceReward(l, P) == 0, null);
			kill(l, OFF);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(0.5, ROW + 100.5);
			List<String> got = items(l, box);
			check("rewards: switch off keeps the 2.x untracked roll, once (no XP)", count(got, "hrafnholt_seal_skald") == 1 && orbs(l, box) == 0, got);
			HrafnholtRewards.setEnabledForTest(null);
			SelfTestBosses.clean(l);
			return 0;
		});
	}

}
