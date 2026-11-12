package dev.elsewhere.rivals.mirewick.dev;

import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import dev.elsewhere.rivals.mirewick.rewards.MirewickRewards;
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
 * 3.0 (s-bossdrops): every Mirewick Magnate drops XP and its drops on a player kill outside the court (the release suite found 0 XP on
 * every kill and empty drop lists for Ames, Thwaite and Tolliver); a court leader still drops its table exactly once; a stale leader rolls
 * it here; the GEAR.md gear3 lines (and the Chair line) roll at their rates; the kill switch brings 2.x back. Runs after the main suite
 * (the court is reset there), so the court events it fires touch no later check.
 */
final class SelfTestRewards {
	private SelfTestRewards() {}

	static final int Y = SelfTest.Y, ROW = 150;

	record Kill(MagnateBoss boss, String path, double x, int xp, int vanillaXp) {}

	static final List<Kill> KILLS = new ArrayList<>();
	static FakePlayer P;
	static MagnateBoss LEADER, STALE, OFF;
	static int rollsBefore;

	static void check(String n, boolean ok, Object d) { SelfTest.check(n, ok, d); }

	static void ground(ServerLevel l, int x0, int z0, int x1, int z1) {
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
		SelfTest.clearBox(l, x0, z0, x1, z1);
	}

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

	static void kill(ServerLevel l, MagnateBoss b) {
		b.setLastHurtByPlayer(P, 100);
		b.setHealth(0F);
		b.die(l.damageSources().playerAttack(P));
	}

	static void add() {
		// the five Magnates, untracked (a hand-summoned boss, the release suite's direct spawn), killed by a player
		SelfTest.STEPS.add((srv, l) -> {
			SelfTest.clean(l);
			ground(l, -75, ROW - 14, 75, ROW + 14);
			MirewickRewards.setEnabledForTest(true);
			P = SelfTest.fp(l, "rewards-hunter");
			P.snapTo(0.5, Y, ROW - 30.5, 0F, 0F);
			KILLS.clear();
			int i = 0;
			for (Magnate m : Magnate.values()) {
				double x = -60 + 30 * i++;
				MagnateBoss b = MagnateBoss.spawn(l, m, SelfTest.at(x, ROW));
				if (b == null) { check("rewards: " + m.key + " spawns", false, null); continue; }
				KILLS.add(new Kill(b, BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath(), x + 0.5, MirewickRewards.xp(b, b.chair), b.getExperienceReward(l, P)));
				kill(l, b);
			}
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (Kill k : KILLS) {
				AABB box = around(k.x(), ROW + 0.5);
				int xp = orbs(l, box);
				List<String> got = items(l, box);
				check("rewards: " + k.path() + " always drops XP on a player kill (" + MirewickRewards.LORD_XP + "; HumanBoss had 0)",
					!k.boss().isAlive() && xp > 0 && k.xp() == MirewickRewards.LORD_XP && k.vanillaXp() == MirewickRewards.LORD_XP, xp + " in orbs, xp() " + k.xp() + ", vanilla " + k.vanillaXp());
				check("rewards: " + k.path() + " drops its faction table once outside the court (Seal, Oathkeeper's Seal, Scrip)",
					count(got, "mirewick_seal_" + k.boss().m.key) == 1 && count(got, "oathkeepers_seal") == 1 && count(got, "mirewick_scrip") >= 1, got);
			}
			SelfTest.clean(l);
			return 0;
		});
		// a court leader: the court rolls its table, this class rolls nothing (no double drop); XP still drops. Then a stale leader.
		SelfTest.STEPS.add((srv, l) -> {
			ground(l, -30, 166, 30, 194);
			CourtState st = CourtState.get(srv, MirewickContent.FACTION);
			st.resetForTests();
			rollsBefore = MirewickRewards.fallbackRolls;
			var m = CourtLife.spawnLeader(l, st, Ruler.MARSHAL, SelfTest.at(-15, 180), null);
			LEADER = m instanceof MagnateBoss b ? b : null;
			check("rewards: a court-seated Fen-Marshal is rolled by the court, not here", LEADER != null && MirewickRewards.courtRolls(l, LEADER) && MirewickRewards.fallbackDrops(l, LEADER, P).isEmpty(), m);
			STALE = MagnateBoss.spawn(l, Magnate.THWAITE, SelfTest.at(15, 180));
			if (STALE != null) Leaders.tag(STALE, MirewickContent.FACTION, "marshal");
			check("rewards: a tagged Magnate the court does not seat is not the court's to roll", STALE != null && !MirewickRewards.courtRolls(l, STALE), null);
			if (LEADER != null) kill(l, LEADER);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(-14.5, 180.5);
			List<String> got = items(l, box);
			check("rewards: a court leader's death drops its Seal exactly once (court roll, no second roll here) and XP",
				LEADER != null && count(got, "mirewick_seal_thwaite") == 1 && orbs(l, box) > 0 && MirewickRewards.fallbackRolls == rollsBefore, got + " xp " + orbs(l, box));
			if (STALE != null) kill(l, STALE);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(15.5, 180.5);
			List<String> got = items(l, box);
			check("rewards: a stale leader (seat already dead) still drops its table once and XP", count(got, "mirewick_seal_thwaite") == 1 && orbs(l, box) > 0
				&& MirewickRewards.fallbackRolls == rollsBefore + 1, got + " xp " + orbs(l, box));
			CourtState.get(srv, MirewickContent.FACTION).resetForTests();
			SelfTest.clean(l);
			return 0;
		});
		// GEAR.md A5 lines and their rates; the Chair-holder pays the ruler's XP
		SelfTest.STEPS.add((srv, l) -> {
			check("rewards: GEAR.md lines: Chair-holder Lanternfang Estoc 50%, Wildfowler Fowler's Bandolier 30%, Lord Almoner Almoner's Chalice 50%",
				MirewickRewards.CHAIR.size() == 1 && MirewickRewards.CHAIR.get(0).chance() == 0.5F && MirewickRewards.EXTRAS.get(Magnate.TOLLIVER).get(0).chance() == 0.3F
					&& MirewickRewards.EXTRAS.get(Magnate.AMES).get(0).chance() == 0.5F && MirewickRewards.lines(Magnate.SORREL, false).isEmpty() && MirewickRewards.lines(Magnate.SORREL, true).size() == 1, null);
			RandomSource rnd = RandomSource.create(13);
			int bando = 0, estoc = 0, n = 4000;
			for (int i = 0; i < n; i++) {
				for (var e : MirewickRewards.rollExtraLines(MirewickRewards.lines(Magnate.TOLLIVER, true), rnd)) {
					if (e.item().equals("mirewick_fowlers_bandolier")) bando++;
					if (e.item().equals("mirewick_lanternfang_estoc")) estoc++;
				}
			}
			check("rewards: Bandolier about 30% per Wildfowler kill and the Estoc about 50% per Chair-holder kill (4000 kills)", bando > 1110 && bando < 1290 && estoc > 1880 && estoc < 2120, bando + " / " + estoc);
			MagnateBoss probe = Magnate.SORREL.type.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			check("rewards: the Chair-holder pays the ruler's XP (" + MirewickRewards.RULER_XP + "), a Magnate " + MirewickRewards.LORD_XP,
				probe != null && MirewickRewards.xp(probe, true) == MirewickRewards.RULER_XP && MirewickRewards.xp(probe, false) == MirewickRewards.LORD_XP, null);
			if (probe != null) probe.discard();
			int missing = 0;
			for (var e : MirewickRewards.lines(Magnate.TOLLIVER, true)) if (MirewickRewards.item(e.item()) == null) missing++;
			for (var e : MirewickRewards.lines(Magnate.AMES, false)) if (MirewickRewards.item(e.item()) == null) missing++;
			if (BuiltInRegistries.ITEM.containsKey(MirewickContent.id("mirewick_lanternfang_estoc"))) check("rewards: every GEAR.md line resolves to a gear3 item", missing == 0, missing);
			else SelfTest.info("rewards: gear3 is not on this classpath; its lines are skipped by id (merged jar resolves them)");
			return 0;
		});
		// kill switch rival_boss_rewards=false: 2.x (no XP, nothing outside the court)
		SelfTest.STEPS.add((srv, l) -> {
			ground(l, -12, -192, 12, -168);
			MirewickRewards.setEnabledForTest(false);
			OFF = MagnateBoss.spawn(l, Magnate.AMES, SelfTest.at(0, -180));
			check("rewards: switch off gives the 2.x XP (none)", OFF != null && MirewickRewards.xp(OFF, false) == 0 && OFF.getExperienceReward(l, P) == 0, null);
			if (OFF != null) kill(l, OFF);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(0.5, -179.5);
			check("rewards: switch off drops no XP and rolls nothing here (2.x)", orbs(l, box) == 0 && count(items(l, box), "mirewick_seal_ames") == 0, items(l, box));
			MirewickRewards.setEnabledForTest(null);
			SelfTest.clean(l);
			return 0;
		});
	}
}
