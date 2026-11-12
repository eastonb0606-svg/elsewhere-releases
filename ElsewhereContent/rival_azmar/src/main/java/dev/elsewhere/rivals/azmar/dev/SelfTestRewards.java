package dev.elsewhere.rivals.azmar.dev;

import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.rewards.AzmarRewards;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * 3.0 (s-bossdrops): every Azmar boss drops XP and its drops on a player kill outside the court (the release suite found 0 XP and an empty
 * drop list for the Sultana); a court leader still drops its table exactly once; a stale leader rolls it here; the GEAR.md gear3 lines
 * roll at their rates; the kill switch brings 2.x back.
 */
final class SelfTestRewards {
	private SelfTestRewards() {}

	static final int ROW = 60;

	record Kill(AzmarBoss boss, String path, String seal, double x, int xp, int vanillaXp, int want) {}

	static final List<Kill> KILLS = new ArrayList<>();
	static FakePlayer P;
	static AzmarBoss LEADER, STALE, OFF;
	static int rollsBefore;

	static void check(String n, boolean ok, Object d) { SelfTest.check(n, ok, d); }

	static int y() { return SelfTest.y; }

	static void load(ServerLevel l, int x0, int z0, int x1, int z1) {
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
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
		return new AABB(x - 12, y() - 4, z - 12, x + 12, y() + 10, z + 12);
	}

	static void kill(ServerLevel l, AzmarBoss b) {
		b.setLastHurtByPlayer(P, 100);
		b.setHealth(0F);
		b.die(l.damageSources().playerAttack(P));
	}

	static <T extends AzmarBoss> T spawn(ServerLevel l, EntityType<T> type, double x, double z) {
		return SelfTestBosses.spawn(l, type, x, z);
	}

	static void add() {
		// the four bosses, untracked (a hand-summoned boss, the release suite's direct spawn), killed by a player
		SelfTest.STEPS.add((srv, l) -> {
			SelfTest.clean(l);
			load(l, -70, ROW - 20, 70, ROW + 20);
			AzmarRewards.setEnabledForTest(true);
			P = SelfTestBosses.player(l, 0, ROW - 30);
			KILLS.clear();
			List<EntityType<? extends AzmarBoss>> types = List.of(AzmarBosses.SULTANA, AzmarBosses.AMIR, AzmarBosses.WAZIR, AzmarBosses.FALCONER);
			String[] seals = {"azmar_seal_sultana", "azmar_seal_amir", "azmar_seal_wazir", "azmar_seal_falconer"};
			for (int i = 0; i < types.size(); i++) {
				double x = -45 + 30 * i;
				AzmarBoss b = spawn(l, types.get(i), x, ROW);
				int want = i == 0 ? AzmarRewards.RULER_XP : AzmarRewards.LORD_XP;
				KILLS.add(new Kill(b, BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()).getPath(), seals[i], x + 0.5, AzmarRewards.xp(b), b.getExperienceReward(l, P), want));
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
				check("rewards: " + k.path() + " drops its faction table once outside the court (Seal, Oathkeeper's Seal, Dinars)",
					count(got, k.seal()) == 1 && count(got, "oathkeepers_seal") == 1 && count(got, "azmar_dinar") >= 1, got);
			}
			SelfTest.clean(l);
			return 0;
		});
		// a court leader: the court rolls its table, this class rolls nothing (no double drop); XP still drops. Then a stale leader.
		SelfTest.STEPS.add((srv, l) -> {
			load(l, -30, -60, 30, -20);
			CourtState st = CourtState.get(srv, AzmarContent.FACTION);
			st.resetForTests();
			rollsBefore = AzmarRewards.fallbackRolls;
			var m = CourtLife.spawnLeader(l, st, Ruler.CASTELLAN, SelfTest.at(-15, -40), null);
			LEADER = m instanceof AzmarBoss b ? b : null;
			check("rewards: a court-seated Grand Wazir is rolled by the court, not here", LEADER != null && AzmarRewards.courtRolls(l, LEADER) && AzmarRewards.fallbackDrops(l, LEADER, P).isEmpty(), m);
			STALE = spawn(l, AzmarBosses.WAZIR, 15, -40);
			Leaders.tag(STALE, AzmarContent.FACTION, "castellan");
			check("rewards: a tagged boss the court does not seat is not the court's to roll", !AzmarRewards.courtRolls(l, STALE), null);
			if (LEADER != null) kill(l, LEADER);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(-14.5, -39.5);
			List<String> got = items(l, box);
			check("rewards: a court leader's death drops its Seal exactly once (court roll, no second roll here) and XP",
				LEADER != null && count(got, "azmar_seal_wazir") == 1 && orbs(l, box) > 0 && AzmarRewards.fallbackRolls == rollsBefore, got + " xp " + orbs(l, box));
			kill(l, STALE);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(15.5, -39.5);
			List<String> got = items(l, box);
			check("rewards: a stale leader (seat already dead) still drops its table once and XP", count(got, "azmar_seal_wazir") == 1 && orbs(l, box) > 0
				&& AzmarRewards.fallbackRolls == rollsBefore + 1, got + " xp " + orbs(l, box));
			CourtState.get(srv, AzmarContent.FACTION).resetForTests();
			SelfTest.clean(l);
			return 0;
		});
		// GEAR.md A5 lines and their rates
		SelfTest.STEPS.add((srv, l) -> {
			check("rewards: GEAR.md lines: Sultana Ka Sigil 30%, Amir Sunscourge 50%, Royal Falconer Falconer's Kilt 50%, Wazir none",
				AzmarRewards.SULTANA.get(0).chance() == 0.3F && AzmarRewards.AMIR.get(0).chance() == 0.5F && AzmarRewards.FALCONER.get(0).chance() == 0.5F
					&& AzmarRewards.SULTANA.size() == 1 && AzmarRewards.AMIR.size() == 1 && AzmarRewards.FALCONER.size() == 1, null);
			RandomSource rnd = RandomSource.create(11);
			int sigil = 0, kilt = 0, n = 4000;
			for (int i = 0; i < n; i++) {
				sigil += AzmarRewards.rollExtraLines(AzmarRewards.SULTANA, rnd).size();
				kilt += AzmarRewards.rollExtraLines(AzmarRewards.FALCONER, rnd).size();
			}
			check("rewards: Ka Sigil about 30% per Sultana kill and the Kilt about 50% per Falconer kill (4000 kills)", sigil > 1110 && sigil < 1290 && kilt > 1880 && kilt < 2120, sigil + " / " + kilt);
			int missing = 0;
			for (var list : List.of(AzmarRewards.SULTANA, AzmarRewards.AMIR, AzmarRewards.FALCONER)) for (var e : list) if (AzmarRewards.item(e.item()) == null) missing++;
			if (BuiltInRegistries.ITEM.containsKey(AzmarContent.id("azmar_ka_sigil"))) check("rewards: every GEAR.md line resolves to a gear3 item", missing == 0, missing);
			else SelfTest.info("rewards: gear3 is not on this classpath; its lines are skipped by id (merged jar resolves them)");
			return 0;
		});
		// kill switch rival_boss_rewards=false: 2.x (no XP, nothing outside the court)
		SelfTest.STEPS.add((srv, l) -> {
			load(l, -20, -80, 20, -60);
			AzmarRewards.setEnabledForTest(false);
			OFF = spawn(l, AzmarBosses.AMIR, 0, -70);
			check("rewards: switch off gives the 2.x XP (none)", AzmarRewards.xp(OFF) == 0 && OFF.getExperienceReward(l, P) == 0, null);
			kill(l, OFF);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			AABB box = around(0.5, -69.5);
			check("rewards: switch off drops no XP and rolls nothing here (2.x)", orbs(l, box) == 0 && count(items(l, box), "azmar_seal_amir") == 0, items(l, box));
			AzmarRewards.setEnabledForTest(null);
			SelfTest.clean(l);
			return 0;
		});
	}
}
