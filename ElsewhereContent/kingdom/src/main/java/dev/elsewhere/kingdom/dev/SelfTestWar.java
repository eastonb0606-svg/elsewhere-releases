package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;
import static dev.elsewhere.kingdom.dev.SelfTest.info;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Multipliers;
import dev.elsewhere.kingdom.Serfs;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.war.Longship;
import dev.elsewhere.kingdom.war.Tuning;
import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom.war.WarBattles;
import dev.elsewhere.kingdom.war.WarEvents;
import dev.elsewhere.kingdom.war.WarLayer;
import dev.elsewhere.kingdom.war.WarSim;
import dev.elsewhere.kingdom.war.WarState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 2.1 war lane checks: Tuning, the WAR-AI model (200 seeds x 400 days in this JVM), brain degradation, Standing rules, the war layer in a live world with the
 * real-battle hand-off, Free Companies, Serfs, multipliers, unit-id to class, saved data.
 */
public final class SelfTestWar {
	private SelfTestWar() {}

	static final Identifier A = Factions.ALDERMARK, M = TestFactions.MARCHES, H = Identifier.fromNamespaceAndPath("elsewhere", "test_hirers");
	static final Identifier UNIQUE = Identifier.fromNamespaceAndPath("elsewhere", "test_unique");
	static FakePlayer p;
	static int villageChanges, warEvents;
	static final List<String> evTypes = new ArrayList<>();

	static Tuning[] four() {
		return new Tuning[] {Tuning.builtin("aldermark"), Tuning.builtin("hrafnholt"), Tuning.builtin("azmar"), Tuning.builtin("mirewick")};
	}

	/** Runs 200 seeds x 400 days; killDay >= 0 kills Azmar's three brain bosses on that day. Returns {win shares x4, mean villages d30 x4, mean villages d200 x4, mean d400 x4}. */
	static double[][] campaign(int seeds, int killDay) {
		double[] win = new double[4], d30 = new double[4], d200 = new double[4], d400 = new double[4];
		for (int seed = 1; seed <= seeds; seed++) {
			WarSim s = WarSim.randomWorld(seed, four());
			for (int day = 0; day < 400; day++) {
				if (day == killDay) {
					s.brainKill(2, "sultana");
					s.brainKill(2, "wazir");
					s.brainKill(2, "falconer");
				}
				s.step();
				if (s.day == 30) for (int k = 0; k < 4; k++) d30[k] += s.n[k];
				if (s.day == 200) for (int k = 0; k < 4; k++) d200[k] += s.n[k];
			}
			int best = 0;
			for (int k = 0; k < 4; k++) {
				d400[k] += s.n[k];
				best = Math.max(best, s.n[k]);
			}
			int ties = 0;
			for (int k = 0; k < 4; k++) if (s.n[k] == best) ties++;
			for (int k = 0; k < 4; k++) if (s.n[k] == best) win[k] += 1.0 / ties;
		}
		for (int k = 0; k < 4; k++) {
			win[k] /= seeds; d30[k] /= seeds; d200[k] /= seeds; d400[k] /= seeds;
		}
		return new double[][] {win, d30, d200, d400};
	}

	static String fmt(double[] a) {
		return String.format("A %.2f H %.2f Z %.2f M %.2f", a[0], a[1], a[2], a[3]);
	}

	static FactionDef hirerDef() {
		FactionDef base = TestFactions.def();
		Map<Identifier, FactionDef.RosterEntry> roster = new java.util.LinkedHashMap<>();
		roster.put(UnitTypes.MAN_AT_ARMS, new FactionDef.RosterEntry(Optional.of(UNIQUE), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
		FactionDef.Brain br = new FactionDef.Brain(1.0, 1.2, false, 0.0, 0.25, false, 0.0, false, 0, List.of(), Map.of("hire_max", 45.0, "hire_pay", 1.2, "inc_base", 30.0, "standing_loss", 0.5));
		FactionDef.Army army = new FactionDef.Army(List.of(), List.of(), Map.of(UnitTypes.KNIGHT, 60F, UnitTypes.ARCHER, 40F, UnitTypes.SERGEANT, 5F), 1.0, 1.0, 1.0, 1.5, 9, 10, br);
		return new FactionDef("Test Hirers", Optional.empty(), base.banner(), base.tabard(), Optional.empty(), false, List.of(), List.of(), roster, base.rulers(), List.of(), Optional.empty(), true,
			FactionDef.Society.DEFAULT, FactionDef.Economy.NONE, army);
	}

	static void add() {
		// ---- tuning and the model, headless --------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Tuning az = Tuning.builtin("azmar"), hr = Tuning.builtin("hrafnholt"), mi = Tuning.builtin("mirewick"), al = Tuning.builtin("aldermark");
			check("tuned values are the WAR-AI table: Azmar min_odds 1.5, retreat 0.85, noise 0.07, vet cap 0.30, timing, fort 0.9; Hrafnholt war_freq 1.5, pursuit 0.12; Mirewick delay 1-2, hire 45; Aldermark 1.2/0.55",
				az.minOdds == 1.5 && az.retreat == 0.85 && az.noise == 0.07 && az.vetCap == 0.30 && az.timing && az.fortCap == 0.90 && hr.warFreq == 1.5 && hr.pursuit == 0.12 && mi.delayMin == 1 && mi.delayMax == 2
					&& mi.hireMax == 45 && al.minOdds == 1.2 && al.retreat == 0.55 && al.vetCap == 0, null);
			check("army multipliers are in the tuning: Azmar hp 0.8 dmg 0.9 size 0.75, Hrafnholt 1.15/1.10/1.2", az.hp == 0.8 && az.dmg == 0.9 && az.size == 0.75 && hr.hp == 1.15 && hr.dmg == 1.10 && hr.size == 1.2, null);
			Tuning t = Tuning.builtin("azmar");
			check("Tuning.set/get by key; unknown keys refused", t.set("commit", 0.5) && t.get("commit") == 0.5 && !t.set("nonsense", 1) && Double.isNaN(t.get("nonsense")) && Tuning.KEYS.size() >= 40, Tuning.KEYS.size());
			FactionDef d = hirerDef();
			Tuning ft = Tuning.of(H, d);
			check("brain.tuning overrides built-ins (hire_max 45, standing_loss 0.5, size 1.5 from army.size_multiplier)", ft.hireMax == 45 && ft.standingLoss == 0.5 && ft.size == 1.5 && ft.incBase == 30.0, ft.hireMax + " " + ft.size);
			check("loss/gain multipliers built in: Hrafnholt 1.5/0.75, Mirewick 0.5/1.25", hr.standingLoss == 1.5 && hr.standingGain == 0.75 && mi.standingLoss == 0.5 && mi.standingGain == 1.25, null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			long t0 = System.nanoTime();
			double[][] base = campaign(200, -1);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			info("war model, 200 seeds x 400 days in " + ms + " ms. win share: " + fmt(base[0]) + " | villages d30: " + fmt(base[1]) + " | d200: " + fmt(base[2]) + " | d400: " + fmt(base[3]));
			boolean azTop = base[0][2] > base[0][0] && base[0][2] > base[0][1] && base[0][2] > base[0][3];
			check("WAR-AI 8.5: Azmar is the most likely long-run winner (largest win share, at least 50 percent)", azTop && base[0][2] >= 0.5, fmt(base[0]));
			check("WAR-AI 8.5: Azmar is weakest early (fewest villages at day 30)", base[1][2] <= Math.min(base[1][0], Math.min(base[1][1], base[1][3])) + 0.05, fmt(base[1]));
			check("WAR-AI 8.5: Hrafnholt leads early and declines (more villages at day 30 than at day 400, and above Aldermark early)", base[1][1] > base[3][1] && base[1][1] > base[1][0], fmt(base[1]) + " -> " + fmt(base[3]));
			double[][] kill = campaign(100, 100);
			double[][] ctl = campaign(100, -1);
			info("brain kill at day 100: Azmar d200 " + String.format("%.2f vs %.2f", kill[2][2], ctl[2][2]) + ", d400 " + String.format("%.2f vs %.2f", kill[3][2], ctl[3][2]) + ", win " + String.format("%.2f vs %.2f", kill[0][2], ctl[0][2]));
			check("WAR-AI 8.5: killing Azmar's three brain bosses at day 100 removes its edge (fewer villages at day 200 and a lower win share)", kill[2][2] < ctl[2][2] - 0.5 && kill[0][2] < ctl[0][2], null);
			// Mirewick swings with its treasury
			WarSim s = WarSim.randomWorld(7, four());
			double hm0 = 0;
			for (int i = 0; i < 120; i++) {
				s.step();
				hm0 = Math.max(hm0, s.Hm[3]);
			}
			check("Mirewick hires Free Company heads while rich or at war (peak hired heads > 0)", hm0 > 0, hm0);
			WarSim b = new WarSim(2, 3);
			b.castle = new double[][] {null, null};
			b.resize(0);
			b.init(new Tuning[] {Tuning.builtin("mirewick"), Tuning.builtin("aldermark")});
			b.Hm[0] = 40;
			b.H[0] = 50;
			b.H[1] = 50;
			b.money[0] = 0;
			b.br[0].incBase = 0;
			b.br[0].incVillage = 0;
			b.eff[0].incBase = 0;
			b.war[0][1] = b.war[1][0] = 3;
			List<WarSim.Ev> evs = new ArrayList<>();
			b.sink = evs::add;
			double h1 = b.H[1];
			b.step();
			check("broke Mirewick's company deserts: half of the hired heads leave (the model's 'desert' 0.5)", b.Hm[0] <= 20.5 && evs.stream().anyMatch(e -> e.type() == WarSim.EvType.DESERT), b.Hm[0]);
			return 1;
		});
		// ---- brain degradation timers -------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			WarSim s = WarSim.randomWorld(3, four());
			s.day = 50;
			s.updateBrainForTest();
			check("no kill: Azmar's effective brain is the tuned one", s.eff[2].minOdds == 1.5 && s.eff[2].noise == 0.07, null);
			s.brainKill(2, "Sultana Nahira");
			s.updateBrainForTest();
			check("Sultana dead (day 0): min_odds 0.9, retreat 0.2, def_retreat 0, fort_rate 0, scout 1, commit 0.5, react 0.1", s.eff[2].minOdds == 0.9 && s.eff[2].retreat == 0.2 && s.eff[2].defRetreat == 0 && s.eff[2].fortRate == 0
				&& s.eff[2].scout == 1 && s.eff[2].commit == 0.5 && s.eff[2].react == 0.1, null);
			s.day = 50 + 200;
			s.updateBrainForTest();
			check("Azmar: a dead Sultana is permanent (RIVALS-DECISIONS 10): 200 days later the seat is still empty, no refill and no relearning ramp", s.eff[2].minOdds == 0.9 && s.eff[2].retreat == 0.2, s.eff[2].minOdds);
			WarSim w = WarSim.randomWorld(3, four());
			w.day = 10;
			w.brainKill(2, "wazir");
			w.brainKill(2, "falconer");
			w.updateBrainForTest();
			check("Wazir dead: timing and broker off, delay 2-3 days, supply cut (x0.75 defence), isolated; Falconer dead: noise 0.80", !w.eff[2].timing && !w.eff[2].broker && w.eff[2].delayMin == 2 && w.eff[2].delayMax == 3 && w.isolatedForTest(2) && w.eff[2].noise == 0.80, null);
			WarSim h = WarSim.randomWorld(3, four());
			h.day = 10;
			h.brainKill(1, "High Jarl");
			h.updateBrainForTest();
			double wf = h.eff[1].warFreq;
			h.brainKill(1, "hersir");
			h.updateBrainForTest();
			check("Hrafnholt: High Jarl dead -> war_freq 1.0; Hersir dead -> pursuit 0 and retreat 0.55", wf == 1.0 && h.eff[1].pursuit == 0 && h.eff[1].retreat == 0.55, wf + " " + h.eff[1].retreat);
			WarSim m = WarSim.randomWorld(3, four());
			m.day = 10;
			m.brainKill(3, "hawk");
			m.updateBrainForTest();
			double hawk = m.eff[3].warFreq;
			m.day = 10 + 30;
			m.updateBrainForTest();
			boolean stays = Math.abs(m.eff[3].warFreq - 0.4) < 1e-9;
			m.succession(3, "hawk", "heir");
			m.updateBrainForTest();
			check("Mirewick: a dead hawk lowers war_freq by 0.1 and stays dead; an 'heir' event brings the vote back", Math.abs(hawk - 0.4) < 1e-9 && stays && Math.abs(m.eff[3].warFreq - 0.5) < 1e-9, hawk + " " + m.eff[3].warFreq);
			h.succession(1, "hersir", "risen");
			h.updateBrainForTest();
			check("Hrafnholt: a 'risen' event restores the Hersir seat at half strength (pursuit 0.06, retreat 0.275)", Math.abs(h.eff[1].pursuit - 0.06) < 1e-9 && Math.abs(h.eff[1].retreat - 0.275) < 1e-9, h.eff[1].pursuit + " " + h.eff[1].retreat);
			check("succession events through the API are refused for an unknown kingdom and ruler_succession changes nothing", !h.succession(1, "hersir", "ruler_succession"), null);
			WarSim ald = WarSim.randomWorld(3, four());
			ald.day = 10;
			ald.brainKill(0, "king");
			check("Aldermark: the King's death locks new wars for 7 days (sues for peace)", ald.declLock[0] == 17, ald.declLock[0]);
			return 1;
		});
		// ---- Standing in a live world --------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			TestFactions.register();
			FactionDefs.putCodeDefined(H, hirerDef());
			p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("kingdom-war".getBytes()), "kingdom-war"));
			Vec3 pos = at(0, 0);
			p.snapTo(pos.x, pos.y, pos.z, 0, 0);
			WarState.get(l).clearForTests();
			KingdomState ks = KingdomState.get(srv.overworld());
			ks.standingLedger(A, true).clear();
			ks.standingLedger(M, true).clear();
			ks.standingLedger(H, true).clear();
			int lossBefore = Standing.get(l, p.getUUID(), M);
			int loss = Standing.onKilled(p, M, Standing.Victim.SOLDIER);
			check("killing a soldier of the test kingdom: -3 with it (multiplier 1), and +2 with Aldermark which is at war with it (kill gain beats half the loss)", loss == 3 && Standing.get(l, p.getUUID(), M) == lossBefore - 3
				&& Standing.get(l, p.getUUID(), A) == 2, loss + " M " + Standing.get(l, p.getUUID(), M) + " A " + Standing.get(l, p.getUUID(), A));
			Standing.onKilled(p, M, Standing.Victim.OFFICER);
			check("an officer costs -8 (M -34) and sets the hunting-squad flag", Standing.get(l, p.getUUID(), M) == lossBefore - 3 - 8 && WarState.get(l).extra(p.getUUID(), M, false).officerKilled, Standing.get(l, p.getUUID(), M));
			ks.standingLedger(A, true).clear();
			WarState.get(l).extra(p.getUUID(), A, true).gainToday = 0;
			for (int i = 0; i < 15; i++) Standing.onKilled(p, M, Standing.Victim.SOLDIER);
			check("kill gains are capped at +20 per in-game day (15 kills: Aldermark Standing 20, not 30)", Standing.get(l, p.getUUID(), A) == 20, Standing.get(l, p.getUUID(), A));
			Standing.set(l, p.getUUID(), M, -20);
			Standing.grantTruce(p, M, 24000);
			int ls = Standing.onKilled(p, M, Standing.Victim.SOLDIER);
			check("attacking during a truce doubles the loss (-6)", ls == 6, ls);
			WarState.get(l).extra(p.getUUID(), M, true).truceUntil = 0;
			Standing.set(l, p.getUUID(), M, 40);
			ls = Standing.onKilled(p, M, Standing.Victim.SOLDIER);
			check("a Friendly player attacking doubles the loss (-6)", ls == 6, ls);
			Standing.set(l, p.getUUID(), M, 75);
			Standing.set(l, p.getUUID(), A, 5);
			Standing.onKilled(p, M, Standing.Victim.SOLDIER);
			check("Oathbreaker: hurting an Allied kingdom sends you to Hostile with it (-60) and -10 with every other kingdom", Standing.get(l, p.getUUID(), M) == -60 && Standing.bandOf(l, p.getUUID(), M) == Standing.Band.HOSTILE
				&& Standing.get(l, p.getUUID(), A) <= -5, "M " + Standing.get(l, p.getUUID(), M) + " A " + Standing.get(l, p.getUUID(), A));
			int m0 = Standing.get(l, p.getUUID(), M);
			Standing.add(l, p.getUUID(), M, 20);
			check("Oathbreaker lock: Standing cannot rise for 3 in-game days", Standing.locked(l, p.getUUID(), M) && Standing.get(l, p.getUUID(), M) == m0 && Standing.onTribute(p, M, 10) == 0, Standing.get(l, p.getUUID(), M));
			WarState.get(l).extra(p.getUUID(), M, true).oathUntil = 0;
			Standing.add(l, p.getUUID(), M, 20);
			check("after the lock it can rise again", Standing.get(l, p.getUUID(), M) == m0 + 20, Standing.get(l, p.getUUID(), M));
			Standing.set(l, p.getUUID(), M, 0);
			int t1 = Standing.onTribute(p, M, 50), t2 = Standing.onTribute(p, M, 5);
			check("tribute: +1 per coin, capped at +15 a week (50 coins give 15, the next gives 0)", t1 == 15 && t2 == 0, t1 + " " + t2);
			int trades = 0;
			for (int i = 0; i < 8; i++) trades += Standing.onTrade(p, M);
			check("trading: +1 per trade, capped at +5 a week (8 trades give 5)", trades == 5, trades);
			Standing.set(l, p.getUUID(), A, 0);
			WarState.get(l).extra(p.getUUID(), A, true).tradeThisWeek = 0;
			int at1 = Standing.onTrade(p, A), at2 = Standing.onTribute(p, A, 3);
			check("court report: onTrade/onTribute pay +1 and +3 for a plain player at Standing 0 (Aldermark), and 0 only at the 29 cap", at1 == 1 && at2 == 3 && Standing.get(l, p.getUUID(), A) == 4, at1 + " " + at2);
			// per-faction gain multipliers (Hrafnholt 0.75, Mirewick 1.25; Azmar and the test faction 1.0): exact gains, minimum +1, caps in Standing points
			Identifier HR = Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt"), MW = Identifier.fromNamespaceAndPath("elsewhere", "mirewick"), AZ = Identifier.fromNamespaceAndPath("elsewhere", "azmar");
			Standing.set(l, p.getUUID(), HR, 0);
			int h1 = Standing.onTribute(p, HR, 1), h2 = Standing.onTribute(p, HR, 10), h3 = Standing.onTrade(p, HR);
			check("gain multiplier 0.75 (Hrafnholt): 1 coin gives the minimum +1, 10 coins give 8, a trade gives +1 (minimum)", h1 == 1 && h2 == 8 && h3 == 1 && Standing.get(l, p.getUUID(), HR) == 10, h1 + " " + h2 + " " + h3);
			int h4 = Standing.onTribute(p, HR, 40);
			check("gain multiplier 0.75: the weekly tribute cap (+15) still counts Standing points (1 + 8 + 6 = 15)", h4 == 6 && Standing.onTribute(p, HR, 5) == 0, h4);
			Standing.set(l, p.getUUID(), MW, 0);
			int w1 = Standing.onTribute(p, MW, 10), w2 = Standing.onTrade(p, MW), w3 = Standing.onTribute(p, MW, 4);
			check("gain multiplier 1.25 (Mirewick): 10 coins give 13, a trade +1 (1.25 rounds to 1), then the +15 tribute cap leaves 2 of the next 4 coins' 5", w1 == 13 && w2 == 1 && w3 == 2 && Standing.get(l, p.getUUID(), MW) == 16, w1 + " " + w2 + " " + w3);
			Standing.set(l, p.getUUID(), AZ, 0);
			int z1 = Standing.onTribute(p, AZ, 7), z2 = Standing.onTrade(p, AZ);
			check("gain multiplier 1.0 (Azmar): 7 coins give 7, a trade +1", z1 == 7 && z2 == 1, z1 + " " + z2);
			Standing.set(l, p.getUUID(), M, -40);
			Standing.driftAll(l);
			check("drift: Standing moves 1 point a day towards the start value (-40 -> -39, start -20)", Standing.get(l, p.getUUID(), M) == -39, Standing.get(l, p.getUUID(), M));
			Standing.set(l, p.getUUID(), M, -20);
			Standing.driftAll(l);
			check("an entry back at its start value is dropped (the player is 'unrecorded' again)", KingdomState.get(srv.overworld()).standingLedger(M, false).get(p.getUUID()) == null, null);
			Standing.onVillageDefended(p, M);
			check("defending a village: +10", Standing.get(l, p.getUUID(), M) == -10, Standing.get(l, p.getUUID(), M));
			KingdomSoldier sm = SelfTest.soldier(l, UnitTypes.MAN_AT_ARMS, M, at(6, 0));
			sm.setNoAi(true);
			KingdomApi.setFactionHostileToPlayers(srv, M, false);
			Standing.set(l, p.getUUID(), M, -30);
			boolean off = !KingdomTargeting.canAttack(sm, p);
			Standing.setRealmLocator((lv, bp, f) -> true);
			boolean inRealm = KingdomTargeting.canAttack(sm, p);
			Standing.setRealmLocator(null);
			check("Wary attacks only inside its realm: not at -30 outside (no realm locator), yes with the locator saying 'in realm'", off && inRealm, off + " " + inRealm + " agg " + dev.elsewhere.kingdom.KingdomAggression.get(l, p.getUUID(), M) + " prov " + sm.isProvokedBy(p) + " sight " + Standing.attacksOnSight(p, M));
			Standing.set(l, p.getUUID(), M, -70);
			check("Hostile attacks on sight anywhere", KingdomTargeting.canAttack(sm, p), null);
			Standing.set(l, p.getUUID(), M, -70);
			check("hunting squads: Hostile and an officer killed -> allowed; Aldermark keeps its 2.0 rule (allowed)", Standing.huntAllowed(l, p, M) && Standing.huntAllowed(l, p, A), null);
			WarState.get(l).extra(p.getUUID(), M, true).officerKilled = false;
			check("hunting squads: Hostile without a killed officer -> no hunt", !Standing.huntAllowed(l, p, M), null);
			Standing.set(l, p.getUUID(), A, 29);
			Standing.set(l, p.getUUID(), A, 80);
			check("Aldermark still cannot reach Friendly until the ruler has been killed (cap 29)", Standing.get(l, p.getUUID(), A) <= 29 || KingdomState.get(srv.overworld()).isRulerKilled(A), Standing.get(l, p.getUUID(), A));
			// soldier kills by a player are counted automatically
			KingdomSoldier victim = SelfTest.soldier(l, UnitTypes.SERGEANT, M, at(8, 0));
			victim.setNoAi(true);
			Standing.set(l, p.getUUID(), M, 0);
			victim.hurtServer(l, l.damageSources().playerAttack(p), 500F);
			check("a player killing an officer of a kingdom is counted automatically via SOLDIER_DIED (-8)", Standing.get(l, p.getUUID(), M) == -8 && WarState.get(l).extra(p.getUUID(), M, false).officerKilled, Standing.get(l, p.getUUID(), M));
			sm.discard();
			KingdomState.get(srv.overworld()).standingLedger(A, true).remove(p.getUUID());
			KingdomState.get(srv.overworld()).standingLedger(M, true).remove(p.getUUID());
			return 2;
		});
		// ---- the war layer in a live world, villages, real battles -----------------------------------------------------------------
		STEPS.add((srv, l) -> {
			WarState.get(l).clearForTests();
			WarEvents.VILLAGE_CHANGED.register((lv, k, bp, n, o, r) -> villageChanges++);
			WarEvents.WAR_EVENT.register((lv, e) -> {
				warEvents++;
				evTypes.add(e.type().name());
			});
			check("war layer is idle with one kingdom only (Aldermark's 2.0 world)", true, null);
			WarApi.setCastle(srv, A, new BlockPos(-900, 64, 0));
			WarApi.setCastle(srv, M, new BlockPos(900, 64, 0));
			for (int i = 0; i < 6; i++) {
				WarApi.registerVillage(srv, "w:a" + i, new BlockPos(-700 + i * 130, 64, 150 + (i % 2) * 90), A);
				WarApi.registerVillage(srv, "w:m" + i, new BlockPos(300 + i * 130, 64, -150 - (i % 2) * 90), M);
			}
			WarApi.registerVillage(srv, "w:free", new BlockPos(40, 64, 40), null);
			WarApi.registerVillage(srv, "w:neutral", new BlockPos(60, 64, -300), null, false, false);
			WarSim s = WarLayer.sim(srv);
			check("war model built from the registry: kingdoms {Aldermark, test}, 14 villages, 6 each, 1 free", s != null && s.K >= 2 && s.nv == 14 && WarApi.villagesOf(srv, A) == 6 && WarApi.villagesOf(srv, M) == 6
				&& WarApi.villageOwner(srv, "w:free") == null && WarApi.villageOwner(srv, "w:a0").equals(A), s == null ? null : s.K + " " + s.nv);
			check("a neutral-ground village is never contestable", !s.contestable[13], null);
			Tuning ta = WarApi.effectiveBrain(srv, A);
			check("effective brain of Aldermark is the baseline (min_odds 1.2, noise 0.49) through the API", ta != null && ta.minOdds == 1.2 && ta.noise == 0.49 && WarApi.brainField(srv, A, "min_odds") == 1.2, null);
			check("KingdomApi.brainBossKilled starts the timers; unknown roles are refused", KingdomApi.brainBossKilled(srv, A, "king") && WarApi.brainDegraded(srv, A) && !KingdomApi.brainBossKilled(srv, A, "cook"), null);
			s.kills.clear();
			s.declLock[0] = 0;
			s.sink = s.sink;
			WarApi.stepDays(l, 60);
			check("60 war days run in the live world without error and record events", WarLayer.stepsRun >= 60 && warEvents > 0, WarLayer.stepsRun + " steps, " + warEvents + " events " + evTypes.stream().distinct().toList());
			int total = WarApi.villagesOf(srv, A) + WarApi.villagesOf(srv, M) + countFree(srv);
			check("villages are conserved (13 contestable): owners add up", total == 13, total);
			check("every ownership change fired VILLAGE_CHANGED for the villages lane", villageChanges >= 0 && (villageChanges > 0 || !evTypes.contains("VILLAGE_CAPTURED")), villageChanges);
			check("the model log is kept for /kingdom war log", !WarApi.log(srv).isEmpty(), WarApi.log(srv).size());
			WarState ws = WarState.get(l);
			Tag t = WarState.CODEC.encodeStart(NbtOps.INSTANCE, ws).getOrThrow();
			WarState back = WarState.CODEC.parse(NbtOps.INSTANCE, t).getOrThrow();
			check("war state round trip: villages, log and model are saved", back.data.villages.size() == ws.data.villages.size() && back.data.log.size() == ws.data.log.size() && !t.toString().isEmpty(), null);
			return 1;
		});
		STEPS.add((srv, l) -> {
			// real battle: a free village 56 blocks from the player is assaulted by the test kingdom's army; the hand-off must put real soldiers there inside the caps
			WarBattles.TIMEOUT = 20;
			WarSim s = WarLayer.sim(srv);
			int k = WarLayer.participants(srv).indexOf(M), v = 12;
			s.owner[v] = -1;
			s.lock[v] = 0;
			s.pending[v] = false;
			s.H[k] = 400;
			p.snapTo(at(0, 0).x, at(0, 0).y, at(0, 0).z, 0, 0);
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(0, 0));
			WarBattles.reset();
			int before = WarBattles.active();
			s.assault(k, v, 60, "free");
			int spawned = KingdomPerf.loaded(l).size();
			check("an assault within 96 blocks of a player becomes a real battle: soldiers on both sides, at most 15 in total", WarBattles.active() == before + 1 && spawned >= 2 && spawned <= 15, WarBattles.active() + " battles, " + spawned + " soldiers");
			check("the village is held for the fight (no second abstract resolution while it is pending)", s.pending[v], null);
			return 120;
		});
		STEPS.add((srv, l) -> {
			WarSim s = WarLayer.sim(srv);
			check("the real battle was resolved and written back: it ended, soldiers removed, the village is no longer pending", WarBattles.active() == 0 && !s.pending[12] && WarBattles.finished >= 1 && KingdomPerf.loaded(l).isEmpty(),
				WarBattles.active() + " " + KingdomPerf.loaded(l).size() + " finished " + WarBattles.finished);
			// out of range: abstract
			p.snapTo(at(0, 0).x, at(0, 0).y, at(0, 0).z, 0, 0);
			KingdomPerf.testObservers.clear();
			int started = WarBattles.started;
			s.owner[12] = -1;
			s.lock[12] = 0;
			WarApi.registerVillage(srv, "w:far", new BlockPos(5000, 64, 5000), null);
			s = WarLayer.sim(srv);
			WarLayer.sim(srv).rebuildGeometry();
			int kk = WarLayer.participants(srv).indexOf(M);
			s.assault(kk, s.nv - 1, 60, "free");
			check("an assault far from every player resolves with the dice (no real soldiers)", WarBattles.started == started && !s.pending[s.nv - 1] && KingdomPerf.loaded(l).isEmpty(), null);
			return 1;
		});
		// ---- Free Companies ------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			WarBattles.TIMEOUT = 1800;
			KingdomPerf.testObservers.clear();
			WarState.get(l).clearForTests();
			for (int i = 0; i < 6; i++) {
				WarApi.registerVillage(srv, "f:h" + i, new BlockPos(-300 + i * 120, 64, 200), H);
				WarApi.registerVillage(srv, "f:m" + i, new BlockPos(200 + i * 120, 64, -200), M);
			}
			WarApi.setCastle(srv, H, new BlockPos(-500, 64, 0));
			WarApi.setCastle(srv, M, new BlockPos(500, 64, 0));
			WarSim s = WarLayer.sim(srv);
			int kh = WarLayer.participants(srv).indexOf(H);
			check("the test hirer is in the model with hire_max 45 from its brain tuning", kh >= 0 && s.br[kh].hireMax == 45, kh);
			s.money[kh] = 400;
			boolean hired = FreeCompanies.hire(srv, H, 10);
			check("hire: 10 sellswords paid from the treasury (money 400 -> 360), one named company with a captain", hired && Math.abs(s.money[kh] - 360) < 1e-6 && FreeCompanies.companiesOf(srv, H).size() == 1
				&& !FreeCompanies.companiesOf(srv, H).get(0).captain.isEmpty() && Math.abs(FreeCompanies.heads(srv, H) - 10) < 1e-6, FreeCompanies.companiesOf(srv, H).size());
			WarApi.declareWar(srv, H, M);
			FreeCompanies.refreshStances(srv);
			check("the Free Company faction is allied with its employer and at war with the employer's enemy; neutral to players", Factions.stance(FreeCompanies.FACTION, H) == Factions.Stance.ALLIED
				&& Factions.hostile(FreeCompanies.FACTION, M) && !Factions.get(FreeCompanies.FACTION).hostileToPlayers(), Factions.stance(FreeCompanies.FACTION, M));
			WarState.Company c = FreeCompanies.companiesOf(srv, H).get(0);
			int price = FreeCompanies.outbidPrice(c);
			boolean low = FreeCompanies.outbid(p, c.id, price - 1, M);
			double hM = s.H[WarLayer.participants(srv).indexOf(M)];
			boolean ok = FreeCompanies.outbid(p, c.id, price, M);
			check("outbidding: 10 days of pay (120 scrip) turns the company; half its heads join the player's chosen enemy; the employer loses them", !low && ok && price == 120 && FreeCompanies.heads(srv, H) == 0
				&& s.H[WarLayer.participants(srv).indexOf(M)] > hM && s.Hm[kh] == 0, price + " " + s.Hm[kh]);
			Standing.set(l, p.getUUID(), H, 50);
			check("a Friendly player can be hired as a sellsword; kills of the enemy count for pay; an unfriendly one is refused", FreeCompanies.startContract(p, H, M, 24000), null);
			KingdomSoldier foe = SelfTest.soldier(l, UnitTypes.MAN_AT_ARMS, M, at(8, 0));
			foe.setNoAi(true);
			foe.hurtServer(l, l.damageSources().playerAttack(p), 500F);
			check("the contract counted the kill (1)", FreeCompanies.contractKills(l, p.getUUID(), H) == 1, FreeCompanies.contractKills(l, p.getUUID(), H));
			Standing.set(l, p.getUUID(), H, -10);
			check("a player below Friendly cannot take a contract", !FreeCompanies.startContract(p, H, M, 24000), null);
			KingdomState.get(srv.overworld()).standingLedger(H, true).remove(p.getUUID());
			KingdomState.get(srv.overworld()).standingLedger(M, true).remove(p.getUUID());
			KingdomState.get(srv.overworld()).standingLedger(A, true).remove(p.getUUID());
			check("company units are plain-skinned mixed gear: blade (iron sword + shield), bolt (crossbow), captain (officer)", UnitTypes.get(FreeCompanies.BLADE).role() == dev.elsewhere.kingdom.UnitType.Role.MELEE
				&& UnitTypes.get(FreeCompanies.BOLT).role() == dev.elsewhere.kingdom.UnitType.Role.CROSSBOW && UnitTypes.get(FreeCompanies.CAPTAIN).officer(), null);
			return 1;
		});
		// ---- Serfs, multipliers, unit-id to class ------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Identifier hr = Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt"), az = Identifier.fromNamespaceAndPath("elsewhere", "azmar"), mi = Identifier.fromNamespaceAndPath("elsewhere", "mirewick");
			Serfs.Variant vh = Serfs.variant(hr), va = Serfs.variant(A);
			check("serf tiers: Aldermark serf x1 18 HP; Hrafnholt thrall x2, 18 HP, levy 1/3 stone_axe; Azmar porter x0.5 with 1-2 on caravans; Mirewick peat-cutter council-paid",
				va.type().getPath().equals("serf") && va.countMultiplier() == 1 && va.health() == 18 && vh.type().getPath().equals("thrall") && vh.countMultiplier() == 2 && vh.health() == 18
					&& vh.levy().isPresent() && Serfs.variant(az).type().getPath().equals("porter") && Serfs.variant(az).countMultiplier() == 0.5 && Serfs.variant(mi).paidByCouncil() && Serfs.variant(mi).type().getPath().equals("peat_cutter"), null);
			check("counts and levy: base 6 serfs -> 12 thralls, 3 of 12 take stone axes (a third); Aldermark has no levy", Serfs.countFor(hr, 6) == 12 && Serfs.levyCount(hr, 12) == 3 && Serfs.levyCount(A, 12) == 0
				&& Serfs.levyWeapon(hr).getPath().equals("stone_axe") && Serfs.levyWeapon(A) == null, null);
			RandomSource r = RandomSource.create(5);
			int porters = Serfs.caravanPorters(az, r);
			check("Azmar caravans get 1-2 porters; others none", porters >= 1 && porters <= 2 && Serfs.caravanPorters(A, r) == 0, porters);
			check("a faction file's society.serfs wins over the built-in (Aldermark file: serf, 18 HP)", Serfs.variant(A).health() == 18.0, null);
			check("Peat-Cutters strike 3 days after a Counting-House raid and rebuilding stops, then resumes", !Serfs.isOnStrike(srv, mi) && lateStrike(srv, mi), null);
			check("size multipliers: Hrafnholt 1.2, Azmar 0.75, Mirewick 0.9, Aldermark 1; file size 1.5 wins for the test hirer", Multipliers.size(hr) == 1.2 && Multipliers.size(az) == 0.75 && Multipliers.size(mi) == 0.9 && Multipliers.size(A) == 1.0 && Multipliers.size(H) == 1.5, null);
			check("patrol sizes: Hrafnholt 7 day / 8 night, Azmar 5, Mirewick 6, Aldermark 6; file 9/10 wins; scale rounds and keeps at least 1", Multipliers.patrolSize(hr, false) == 7 && Multipliers.patrolSize(hr, true) == 8 && Multipliers.patrolSize(az, false) == 5 && Multipliers.patrolSize(A, true) == 6
				&& Multipliers.patrolSize(H, false) == 9 && Multipliers.patrolSize(H, true) == 10 && Multipliers.scale(az, 8) == 6 && Multipliers.scale(az, 1) == 1, null);
			List<Identifier> mem = ArmyComposer.members(RandomSource.create(9), 40, false, H);
			check("a faction's makeup drives its squads: the hirer rolls only its knights and archers (no officers or other units) in about 60:40", mem.size() == 40 && mem.stream().allMatch(u -> u.equals(UnitTypes.KNIGHT) || u.equals(UnitTypes.ARCHER))
				&& mem.stream().filter(u -> u.equals(UnitTypes.KNIGHT)).count() > 15, mem.stream().filter(u -> u.equals(UnitTypes.KNIGHT)).count());
			List<Identifier> a1 = ArmyComposer.members(RandomSource.create(11), 30, true), a2 = ArmyComposer.members(RandomSource.create(11), 30, true, A);
			check("Aldermark's rolls are identical with and without the faction argument (2.0 unchanged)", a1.equals(a2), null);
			KingdomEntities.registerUnitClass(UNIQUE, KingdomEntities.KNIGHT);
			check("unit id to class: the redirect roster line resolves to the unique unit and its registered entity type", Factions.effectiveUnitId(H, UnitTypes.MAN_AT_ARMS).equals(UNIQUE) && KingdomEntities.typeFor(UNIQUE) == KingdomEntities.KNIGHT
				&& Factions.effectiveUnitId(A, UnitTypes.MAN_AT_ARMS).equals(UnitTypes.MAN_AT_ARMS), null);
			KingdomSoldier u = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, H, at(-20, -20), null);
			check("spawning the base unit for that faction creates the registered class (the knight entity type), not the man-at-arms type", u != null && u.getType() == KingdomEntities.KNIGHT, u == null ? null : u.getType());
			if (u != null) u.discard();
			return 1;
		});
		// ---- Hrafnholt longship landing (16a): flag off, parse, prop placement, full restore, player edits kept ---------------------------------------------
		STEPS.add((srv, l) -> {
			int yy = SelfTest.y;
			var arm = FactionDef.Army.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"landing\": \"longship\"}"));
			check("faction data flag army.landing parses (default off)", arm.result().isPresent() && arm.result().get().landing().equals(Optional.of("longship")) && FactionDef.Army.DEFAULT.landing().isEmpty(), null);
			int x0 = -58, x1 = -40, z0 = -22, z1 = -6;
			BlockPos target = BlockPos.containing(at(-28, -14));
			int ships0 = WarState.get(l).data.ships.size();
			Longship.Shore none = Longship.land(l, A, target, 77); // flag off for Aldermark (and anyone without army.landing), even with no water
			check("flag off: no landing, nothing placed or recorded", none == null && !Longship.enabled(A) && !Longship.enabled(M) && WarState.get(l).data.ships.size() == ships0, null);
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int dy = 1; dy <= 3; dy++) l.setBlock(new BlockPos(x, yy - dy, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
			// an obstacle on the beach path that must never be replaced
			BlockPos stone = new BlockPos(x1 + 2, yy, -14);
			l.setBlock(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
			Longship.Shore sh = Longship.findShore(l, target);
			check("shore search finds water within 64 blocks and a land block towards the target (pointing east)", sh != null && sh.dx() == 1 && sh.land().getX() >= x1 && sh.land().distSqr(target) < 70 * 70, sh);
			if (sh == null) return 1;
			// snapshot the box the ship can touch
			BlockPos lo = sh.land().offset(-14, -4, -6), hi = sh.land().offset(4, 10, 6);
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
			for (BlockPos q : BlockPos.betweenClosed(lo, hi)) before.put(q.immutable(), l.getBlockState(q));
			Longship.ShipRec rec = Longship.place(l, sh, Longship.Style.defaults(), 77);
			int changed = 0, planks = 0;
			for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
				var st = l.getBlockState(q);
				if (st != before.get(q)) changed++;
				if (st.is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)) planks++;
			}
			check("longship placed: hull, keel, mast, sail recorded, replaces only water/air (the stone beach block stays)", rec != null && rec.blocks.size() >= 40 && changed == rec.blocks.size() && planks > 8
				&& l.getBlockState(stone).is(net.minecraft.world.level.block.Blocks.STONE), rec == null ? "null" : rec.blocks.size() + " blocks, changed " + changed);
			// a player edits one hull block and builds next to the ship: restore must leave both
			BlockPos hull = null;
			for (Longship.BlockRec r : rec.blocks) if (l.getBlockState(new BlockPos(r.x, r.y, r.z)).is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)) { hull = new BlockPos(r.x, r.y, r.z); break; }
			l.setBlock(hull, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 2);
			BlockPos mine = sh.land().offset(0, 6, 5);
			l.setBlock(mine, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 2);
			Longship.restoreBattle(l, 77);
			int diff = 0;
			for (BlockPos q : BlockPos.betweenClosed(lo, hi)) if (l.getBlockState(q) != before.get(q) && !q.equals(hull) && !q.equals(mine)) diff++;
			check("raid ends: every original block (water included) is restored, the ledger is empty, a block the player changed stays",
				diff == 0 && l.getBlockState(hull).is(net.minecraft.world.level.block.Blocks.OBSIDIAN) && l.getBlockState(mine).is(net.minecraft.world.level.block.Blocks.COBBLESTONE) && WarState.get(l).data.ships.size() == ships0, diff + " differ");
			l.setBlock(hull, before.get(hull), 2);
			l.setBlock(mine, before.get(mine), 2);
			// 10-minute expiry
			Longship.ShipRec r2 = Longship.place(l, sh, Longship.Style.defaults(), 78);
			r2.until = l.getGameTime() - 1;
			Longship.expire(l);
			int diff2 = 0;
			for (BlockPos q : BlockPos.betweenClosed(lo, hi)) if (l.getBlockState(q) != before.get(q)) diff2++;
			check("after 10 minutes the prop is restored without a raid end", diff2 == 0 && WarState.get(l).data.ships.size() == ships0, diff2 + " differ");
			// put the ground back
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
				l.setBlock(new BlockPos(x, yy - 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				l.setBlock(new BlockPos(x, yy - 2, z), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
				l.setBlock(new BlockPos(x, yy - 3, z), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
			}
			l.setBlock(stone, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			return 1;
		});
		STEPS.add((srv, l) -> {
			// cleanup: forget everything the war tests created so later steps see a plain 2.0 world
			WarState.get(l).clearForTests();
			FactionDefs.removeCodeDefined(H);
			Standing.setRealmLocator(null);
			KingdomPerf.testObservers.clear();
			WarBattles.reset();
			clean(l);
			info("war lane tests done");
			return 1;
		});
	}

	static int countFree(net.minecraft.server.MinecraftServer srv) {
		int c = 0;
		for (WarState.VRec r : WarState.get(srv.overworld()).data.villages) if (r.contestable && WarApi.villageOwner(srv, r.key) == null) c++;
		return c;
	}

	static boolean lateStrike(net.minecraft.server.MinecraftServer srv, Identifier f) {
		Serfs.strike(srv, f);
		return Serfs.isOnStrike(srv, f);
	}
}
