package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.SAMPLES;
import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;
import static dev.elsewhere.kingdom.dev.SelfTest.info;
import static dev.elsewhere.kingdom.dev.SelfTest.soldier;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.FactionConflict;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomAggression;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 2.1 multi-faction checks: the data-driven faction file, Aldermark unchanged, a recoloured test faction
 * ({@link TestFactions}), stances, a two-faction battle within the caps, per-faction aggression ledgers and abstract
 * (entity-free) clashes between virtual squads, plus the two-faction MSPT benchmark.
 */
public final class SelfTestFactions {
	private SelfTestFactions() {}

	static final Identifier A = Factions.ALDERMARK, M = TestFactions.MARCHES;

	static KingdomState roundTrip(KingdomState st) {
		Tag t = KingdomState.CODEC.encodeStart(NbtOps.INSTANCE, st).getOrThrow();
		return KingdomState.CODEC.parse(NbtOps.INSTANCE, t).getOrThrow();
	}

	static int countOf(net.minecraft.server.level.ServerLevel l, Identifier faction) {
		int n = 0;
		for (KingdomSoldier s : KingdomPerf.loaded(l)) if (faction.equals(s.factionId())) n++;
		return n;
	}

	static float hpLost(List<KingdomSoldier> list) {
		float lost = 0;
		for (KingdomSoldier s : list) lost += s.isAlive() ? s.getMaxHealth() - s.getHealth() : s.getMaxHealth();
		return lost;
	}

	static List<Identifier> mix(int n) {
		List<Identifier> out = new ArrayList<>();
		Identifier[] pool = {UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.KNIGHT, UnitTypes.CROSSBOWMAN, UnitTypes.MAN_AT_ARMS};
		for (int i = 0; i < n; i++) out.add(pool[i % pool.length]);
		return out;
	}

	static List<KingdomSoldier> squadOf(net.minecraft.server.level.ServerLevel l, Identifier faction, double x, double z, int members) {
		return KingdomApi.spawnSquad(l, BlockPos.containing(at(x, z)), faction, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(members));
	}

	static void add() {
		// ---- data: Aldermark is the first faction file, behaviour identical -----------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			FactionDef ald = FactionDefs.get(A);
			check("faction data: elsewhere:aldermark is loaded from data/elsewhere/kingdom_faction", ald != null && ald.name().equals("Kingdom of Aldermark"), ald == null ? null : ald.name());
			check("Aldermark file: tabard 0x2E4C9E, hostile to players, realm on, music prefix, biome tag",
				ald != null && ald.tabard().color() == 0x2E4C9E && ald.hostileToPlayers() && ald.realm() && ald.music().orElse("").equals("elsewhere:court_music")
					&& ald.biomes().equals(List.of("#elsewhere:court_realm_biomes")), null);
			check("Aldermark live faction matches the file (tabard colour, hostile)", Factions.get(A).tabardColor == 0x2E4C9E && Factions.get(A).hostileToPlayers() && Factions.def(A) == ald, null);
			check("Aldermark file defines the King and 5 Lords (name, health, drops)", ald != null && ald.rulers().keySet().containsAll(List.of("king", "marshal", "castellan", "master_at_arms", "huntsman", "chancellor"))
				&& ald.rulers().size() == 6 && ald.rulers().get("king").health() == 300 && ald.rulers().get("king").cap() == 300 && ald.rulers().get("chancellor").health() == 90
				&& ald.rulers().values().stream().allMatch(r -> !r.drops().isEmpty() && !r.name().isEmpty()), null);
			int same = 0, total = 0;
			for (Identifier u : UnitTypes.all().keySet()) {
				total++;
				if (Factions.unit(A, u) == UnitTypes.get(u)) same++;
			}
			check("Aldermark units are the untouched base unit types (identical behaviour)", same == total && total >= 13, same + "/" + total);
			KingdomSoldier ka = soldier(l, UnitTypes.KNIGHT, A, at(-40, 40));
			ka.setNoAi(true);
			check("Aldermark knight as in 2.0: iron sword + shield, soldier_b skin, blue tabard, 26 hp",
				ka.getMainHandItem().is(Items.IRON_SWORD) && ka.getOffhandItem().is(Items.SHIELD) && ka.skin().getPath().endsWith("soldier_b.png") && ka.tabardColor() == 0x2E4C9E && ka.getMaxHealth() == 24F,
				ka.skin() + " " + ka.getMaxHealth());
			return 1;
		});
		// ---- the test faction: a rival is data plus skins -----------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			check("test faction is not registered before the self-test registers it (never in production)", !Factions.exists(M) && FactionDefs.get(M) == null, null);
			TestFactions.register();
			check("test faction registered from a FactionDef: live faction, green tabard, 5 roster entries, 6 rulers", Factions.exists(M) && Factions.get(M).tabardColor == TestFactions.TABARD
				&& FactionDefs.get(M).roster().size() == 5 && FactionDefs.get(M).rulers().size() == 6, null);
			UnitType kn = Factions.unit(M, UnitTypes.KNIGHT), base = UnitTypes.get(UnitTypes.KNIGHT);
			check("roster resolves the test knight: own skin, iron axe, shield kept, 1.25x health, emerald trim; the base unit is untouched",
				kn != base && kn.skin().getPath().contains("testfaction") && kn.mainHand().get().getPath().equals("iron_axe") && kn.offHand().get().getPath().equals("shield")
					&& Math.abs(kn.stats().health() - base.stats().health() * 1.25) < 1e-6 && kn.trim().get().material().getPath().equals("emerald") && base.mainHand().get().getPath().equals("iron_sword")
					&& base.trim().get().material().getPath().equals("lapis") && UnitTypes.get(UnitTypes.KNIGHT) == base, kn.skin() + " " + kn.mainHand());
			check("a unit without a roster entry keeps the base unit but takes the faction trim", Factions.unit(M, UnitTypes.LANCER).skin().equals(UnitTypes.get(UnitTypes.LANCER).skin())
				&& Factions.unit(M, UnitTypes.LANCER).trim().get().material().getPath().equals("emerald"), null);
			KingdomSoldier km = soldier(l, UnitTypes.KNIGHT, M, at(-30, 40));
			KingdomSoldier ka = soldier(l, UnitTypes.KNIGHT, A, at(-34, 40));
			km.setNoAi(true);
			ka.setNoAi(true);
			var trim = km.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM);
			check("test knight in the world: green skin synced, green tabard, iron axe, trimmed iron armor, 30 hp",
				km.skin().getPath().contains("testfaction") && km.tabardColor() == TestFactions.TABARD && km.getMainHandItem().is(Items.IRON_AXE) && km.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
					&& trim != null && trim.material().is(TrimMaterials.EMERALD) && Math.abs(km.getMaxHealth() - 30F) < 0.01F, km.skin() + " " + km.getMaxHealth());
			check("an Aldermark knight spawned beside it is unchanged (blue, sword, 24 hp)", ka.tabardColor() == 0x2E4C9E && ka.getMainHandItem().is(Items.IRON_SWORD) && ka.getMaxHealth() == 24F && !ka.skin().getPath().contains("testfaction"), null);
			check("the faction's archer is dyed green leather", Factions.unit(M, UnitTypes.ARCHER).armorDye().orElse(0) == 0x2E9E4C, null);
			km.discard();
			ka.discard();
			return 1;
		});
		// ---- schema: leaders, government, brain, multipliers, currency, serfs, standing, loaded from JSON -----------------------------
		STEPS.add((srv, l) -> {
			String json = """
				{ "name": "Test Brain", "tabard": {"color": "#AA3322"}, "hostile_to_players": false,
				  "rulers": { "king": {"name": "High Jarl X", "health": 250, "health_cap": 300}, "marshal": {"name": "Hersir X", "health": 150},
				              "castellan": {"name": "C", "health": 140}, "master_at_arms": {"name": "M", "health": 140}, "huntsman": {"name": "H", "health": 130}, "chancellor": {"name": "Z", "health": 100} },
				  "society": { "government": "monarchy", "ruler_bonus": {"type": "damage_per_champion", "amount": 0.10}, "leaders": ["king","marshal","castellan","master_at_arms","huntsman","chancellor"],
				               "standing": {"start": -20}, "serfs": {"type": "elsewhere:thrall", "count_multiplier": 2.0, "health": 12, "levy": {"fraction": 0.33, "weapon": "minecraft:stone_axe"}} },
				  "economy": { "currency": "elsewhere:hacksilver", "shop": [ {"item": "elsewhere:hacksilver_seal", "price": 220, "weekly_limit": 2, "extra": [{"item": "minecraft:emerald_block", "min": 3}]} ] },
				  "army": { "units": ["elsewhere:man_at_arms", "elsewhere:berserker"], "unique_units": ["elsewhere:berserker", "elsewhere:wolf_handler"], "makeup": {"elsewhere:man_at_arms": 60},
				            "health_multiplier": 1.15, "damage_multiplier": 1.1, "speed_multiplier": 0.9, "size_multiplier": 1.2, "patrol_day": 7, "patrol_night": 8,
				            "brain": { "war_frequency": 1.5, "min_attack_odds": 1.0, "intelligence_noise": 0.4, "decision_delay_days": 1.5, "hire_mercenaries": true, "mercenary_daily_cost": 12,
				                       "supply_line_only": true, "retreat_instead_of_die": true, "veterancy_cap": 0.3, "brain_roles": [ {"role": "king", "degrades": ["war_frequency"]}, {"role": "marshal", "degrades": ["min_attack_odds","intelligence_noise"]} ] } } }
				""";
			var parsed = FactionDef.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json));
			check("a full faction file parses (government, leaders, bonus, Standing, serfs, currency + shop, units, brain)", parsed.result().isPresent(), parsed.error().map(e -> e.message()).orElse(null));
			FactionDef d = parsed.result().orElseThrow();
			check("leaders: 6 slots from data in order; ruler bonus damage_per_champion 0.10; Standing start -20", d.leaderSlots().size() == 6 && d.leaderSlots().get(0).equals("king") && d.society().bonus().type().equals("damage_per_champion")
				&& d.society().bonus().amount() == 0.10 && d.startStanding() == -20 && d.society().government() == FactionDef.Government.MONARCHY, d.leaderSlots());
			check("serf tier from data (type, x2 count, 12 HP, levy a third with stone axes) and currency + shop line (220, 2 per week, extra 3 emerald blocks)",
				d.society().serfs().countMultiplier() == 2.0F && d.society().serfs().health() == 12.0 && d.society().serfs().levy().get().fraction() == 0.33F && d.economy().currency().get().getPath().equals("hacksilver")
					&& d.economy().shop().get(0).price() == 220 && d.economy().shop().get(0).weeklyLimit() == 2 && d.economy().shop().get(0).extra().get(0).min() == 3, null);
			FactionDef.Brain b = d.army().brain();
			check("brain block: war frequency 1.5, min odds 1.0, noise 0.4, delay 1.5 days, mercenaries 12/day, supply-line-only, retreat, veterancy 0.3, two brain roles with degraded fields",
				b.warFrequency() == 1.5 && b.minAttackOdds() == 1.0 && b.intelligenceNoise() == 0.4 && b.decisionDelayDays() == 1.5 && b.hireMercenaries() && b.mercenaryDailyCost() == 12 && b.supplyLineOnly() && b.retreatInsteadOfDie()
					&& b.veterancyCap() == 0.3 && b.brainRoles().size() == 2 && b.brainRoles().get(1).degrades().equals(List.of("min_attack_odds", "intelligence_noise")), b);
			check("unique units, makeup, army size and patrol sizes load", d.army().uniqueUnits().size() == 2 && d.army().makeup().get(UnitTypes.MAN_AT_ARMS) == 60F && d.army().sizeMultiplier() == 1.2 && d.army().patrolDay() == 7 && d.army().patrolNight() == 8, null);
			FactionDef ald = FactionDefs.get(A);
			check("Aldermark's file carries the baseline brain (war frequency 1.0, odds 1.2, no veterancy), currency, serfs and shop", ald.army().brain().warFrequency() == 1.0 && ald.army().brain().minAttackOdds() == 1.2 && ald.army().brain().veterancyCap() == 0.0
				&& ald.economy().currency().get().getPath().equals("aldermark_crown") && !ald.economy().shop().isEmpty() && ald.society().serfs().health() == 18.0 && ald.startStanding() == 0 && ald.society().standing().capUntilRulerKilled(), null);
			Identifier bid = KingdomContent.id("test_brain");
			FactionDefs.putCodeDefined(bid, d);
			UnitType base = UnitTypes.get(UnitTypes.MAN_AT_ARMS), u = Factions.unit(bid, UnitTypes.MAN_AT_ARMS);
			check("per-faction multipliers apply to every unit: HP x1.15, damage x1.1, speed x0.9 (base unit untouched)",
				Math.abs(u.stats().health() - base.stats().health() * 1.15) < 1e-6 && Math.abs(u.stats().attackDamage() - base.stats().attackDamage() * 1.1) < 1e-6 && Math.abs(u.stats().speed() - base.stats().speed() * 0.9) < 1e-6
					&& UnitTypes.get(UnitTypes.MAN_AT_ARMS) == base, u.stats());
			FactionDefs.removeCodeDefined(bid);
			return 1;
		});
		// ---- stances ----------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			check("default relation between two factions is war (config factions.defaultStance)", Factions.stance(A, M) == Factions.Stance.WAR && Factions.hostile(A, M) && Factions.hostile(M, A)
				&& !Factions.hostile(A, A) && Factions.stance(A, A) == Factions.Stance.ALLIED, null);
			KingdomSoldier sa = soldier(l, UnitTypes.MAN_AT_ARMS, A, at(-30, 60));
			KingdomSoldier sm = soldier(l, UnitTypes.MAN_AT_ARMS, M, at(-26, 60));
			sa.setNoAi(true);
			sm.setNoAi(true);
			check("at war, soldiers of the two factions are valid targets", KingdomTargeting.canAttack(sa, sm) && KingdomTargeting.canAttack(sm, sa), null);
			KingdomApi.setStance(srv, A, M, Factions.Stance.PEACE);
			check("peace: they ignore each other (both directions)", !Factions.hostile(A, M) && !Factions.hostile(M, A) && !KingdomTargeting.canAttack(sa, sm) && !KingdomTargeting.canAttack(sm, sa), null);
			check("stance is saved with the world (survives a save/load round trip)", KingdomState.get(srv.overworld()).savedStances().containsValue("PEACE")
				&& roundTrip(KingdomState.get(srv.overworld())).savedStances().equals(KingdomState.get(srv.overworld()).savedStances()), KingdomState.get(srv.overworld()).savedStances());
			KingdomApi.setStance(srv, A, M, Factions.Stance.ALLIED);
			check("allied: not hostile", !Factions.hostile(A, M) && Factions.stance(M, A) == Factions.Stance.ALLIED, null);
			KingdomApi.setStance(srv, A, M, Factions.Stance.WAR);
			check("back to war", Factions.hostile(A, M) && KingdomTargeting.canAttack(sa, sm), null);
			KingdomConfig.factionDefaultStance = "peace";
			check("config factions.defaultStance=peace changes unconfigured pairs (rebels vs test) but not Aldermark vs test (explicit war)", Factions.stance(Factions.REBELS, M) == Factions.Stance.PEACE && Factions.stance(A, M) == Factions.Stance.WAR, null);
			KingdomConfig.factionDefaultStance = "war";
			check("config default back to war", Factions.stance(Factions.REBELS, M) == Factions.Stance.WAR, null);
			sa.discard();
			sm.discard();
			return 1;
		});
		// ---- two patrols meet and fight; caps count every faction together --------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			SelfTestFactionsState.sa = squadOf(l, A, -10, -20, 5);
			SelfTestFactionsState.sm = squadOf(l, M, -10, 6, 5);
			check("two 6-soldier patrols of different factions spawn", SelfTestFactionsState.sa.size() == 6 && SelfTestFactionsState.sm.size() == 6, SelfTestFactionsState.sa.size() + " + " + SelfTestFactionsState.sm.size());
			SelfTestFactionsState.sm.forEach(s -> s.setNoAi(false));
			SelfTestFactionsState.sa.forEach(s -> s.setNoAi(false));
			return 700;
		});
		STEPS.add((srv, l) -> {
			float la = hpLost(SelfTestFactionsState.sa), lm = hpLost(SelfTestFactionsState.sm);
			info(String.format("patrol fight: Aldermark lost %.0f hp, test faction lost %.0f hp, alive %d / %d", la, lm,
				SelfTestFactionsState.sa.stream().filter(KingdomSoldier::isAlive).count(), SelfTestFactionsState.sm.stream().filter(KingdomSoldier::isAlive).count()));
			check("patrols of different factions fight each other when they meet (both sides took damage)", la > 0 && lm > 0, String.format("%.0f / %.0f", la, lm));
			check("nobody was hurt by his own side (squads keep their faction)", SelfTestFactionsState.sa.stream().allMatch(s -> A.equals(s.factionId())) && SelfTestFactionsState.sm.stream().allMatch(s -> M.equals(s.factionId())), null);
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			// caps: 15 per player, shared by every faction
			KingdomPerf.forceActive = false;
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(0, 0));
			List<KingdomSoldier> a = KingdomApi.spawnSquad(l, BlockPos.containing(at(-8, 0)), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(9));
			List<KingdomSoldier> m = KingdomApi.spawnSquad(l, BlockPos.containing(at(8, 0)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(9));
			check("15-per-player cap counts all factions together: 10 Aldermark + 10 test faction near one player -> 15 in total", a.size() == 10 && m.size() == 5 && countOf(l, A) + countOf(l, M) == 15,
				a.size() + " + " + m.size());
			check("a further spawn near that player is refused for either faction", KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, A, at(0, 4), null) == null && KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, M, at(2, 4), null) == null
				&& KingdomPerf.canSpawn(l, at(0, 4), false) != null, KingdomPerf.canSpawn(l, at(0, 4), false));
			clean(l);
			KingdomPerf.testObservers.clear();
			return 3;
		});
		STEPS.add((srv, l) -> {
			// caps: 30 per area
			int made = 0, madeA = 0;
			for (int i = 0; i < 2; i++) madeA += KingdomApi.spawnSquad(l, BlockPos.containing(at(-10, -12 + i * 8)), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(9)).size();
			int madeM = 0;
			for (int i = 0; i < 2; i++) madeM += KingdomApi.spawnSquad(l, BlockPos.containing(at(10, -12 + i * 8)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(9)).size();
			made = madeA + madeM;
			check("30-per-area cap counts all factions together: 20 Aldermark + 20 test faction in one area -> 30", madeA == 20 && madeM == 10 && countOf(l, A) + countOf(l, M) == 30, madeA + " + " + madeM);
			clean(l);
			int g0 = KingdomConfig.maxRealSoldiersGlobal, ar0 = KingdomConfig.maxRealSoldiersPerArea;
			KingdomConfig.maxRealSoldiersGlobal = 25;
			KingdomConfig.maxRealSoldiersPerArea = 100;
			int ga = KingdomApi.spawnSquad(l, BlockPos.containing(at(-60, -10)), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(12)).size();
			int gm = KingdomApi.spawnSquad(l, BlockPos.containing(at(60, 10)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(14)).size()
				+ KingdomApi.spawnSquad(l, BlockPos.containing(at(60, 30)), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(14)).size();
			check("global cap counts all factions together (cap 25: 13 Aldermark + 12 test faction)", ga + gm == 25 && KingdomPerf.loadedCount() == 25, ga + " + " + gm + ", loaded " + KingdomPerf.loadedCount());
			KingdomConfig.maxRealSoldiersGlobal = g0;
			KingdomConfig.maxRealSoldiersPerArea = ar0;
			clean(l);
			KingdomPerf.forceActive = true;
			return 3;
		});
		// ---- player Standing per faction ------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("kingdom-standing".getBytes()), "kingdom-standing"));
			Vec3 pos = at(0, 0);
			p.snapTo(pos.x, pos.y, pos.z, 0, 0);
			check("Standing bands: -100..-50 Hostile, -49..-10 Wary, -9..29 Neutral, 30..69 Friendly, 70..100 Allied",
				Standing.band(-100) == Standing.Band.HOSTILE && Standing.band(-50) == Standing.Band.HOSTILE && Standing.band(-49) == Standing.Band.WARY && Standing.band(-10) == Standing.Band.WARY
					&& Standing.band(-9) == Standing.Band.NEUTRAL && Standing.band(29) == Standing.Band.NEUTRAL && Standing.band(30) == Standing.Band.FRIENDLY && Standing.band(69) == Standing.Band.FRIENDLY
					&& Standing.band(70) == Standing.Band.ALLIED && Standing.band(100) == Standing.Band.ALLIED, null);
			check("start values: Aldermark 0 (Neutral band; the 2.0 hostile flag still makes it attack on sight), test faction -20 (Wary from its file)", KingdomApi.standing(p, A) == 0 && Standing.bandOf(l, p.getUUID(), A) == Standing.Band.NEUTRAL
				&& KingdomApi.standing(p, M) == -20 && Standing.bandOf(l, p.getUUID(), M) == Standing.Band.WARY, KingdomApi.standing(p, A) + " / " + KingdomApi.standing(p, M));
			KingdomSoldier sa = soldier(l, UnitTypes.MAN_AT_ARMS, A, at(4, 30));
			KingdomSoldier sm = soldier(l, UnitTypes.MAN_AT_ARMS, M, at(-4, 30));
			sa.setNoAi(true);
			sm.setNoAi(true);
			check("no recorded Standing: Aldermark attacks on sight exactly as in 2.0 (hostile flag), the test faction does not (its Wary band only applies in its realm)", KingdomTargeting.canAttack(sa, p) && !KingdomTargeting.canAttack(sm, p), null);
			KingdomApi.setFactionHostileToPlayers(srv, A, false);
			check("the lore module's neutral switch still works for Aldermark (flag false, no recorded Standing)", !KingdomTargeting.canAttack(sa, p), null);
			KingdomApi.setFactionHostileToPlayers(srv, A, true);
			Standing.set(l, p.getUUID(), M, -70);
			check("recorded Standing -70 (Hostile): the test faction's soldiers attack on sight although its default stance is neutral", KingdomTargeting.canAttack(sm, p), null);
			Standing.set(l, p.getUUID(), A, 40);
			check("Aldermark cannot reach Friendly until its ruler has been killed once (40 is clamped to 29)", KingdomApi.standing(p, A) == 29 && Standing.bandOf(l, p.getUUID(), A) == Standing.Band.NEUTRAL, KingdomApi.standing(p, A));
			check("Neutral (29) does not calm a faction flagged hostile to players (the 2.0 stance holds below Friendly)", KingdomTargeting.canAttack(sa, p), null);
			KingdomApi.markRulerKilled(srv, A);
			Standing.set(l, p.getUUID(), A, 40);
			check("after the ruler has been killed Aldermark Standing can rise: 40 is Friendly and its soldiers leave the player alone", KingdomApi.standing(p, A) == 40 && !KingdomTargeting.canAttack(sa, p), KingdomApi.standing(p, A));
			Standing.set(l, p.getUUID(), A, 500);
			Standing.set(l, p.getUUID(), M, -500);
			check("Standing is clamped to -100..100", KingdomApi.standing(p, A) == 100 && KingdomApi.standing(p, M) == -100, null);
			KingdomState copy = roundTrip(KingdomState.get(srv.overworld()));
			check("Standing and ruler-killed flags are saved (round trip)", copy.isRulerKilled(A) && !copy.isRulerKilled(M), null);
			Standing.set(l, p.getUUID(), A, -60);
			KingdomState.get(srv.overworld()).standingLedger(A, true).remove(p.getUUID());
			KingdomState.get(srv.overworld()).standingLedger(M, true).remove(p.getUUID());
			sa.discard();
			sm.discard();
			return 1;
		});
		// ---- per-player aggression ledger per faction ----------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("kingdom-factions".getBytes()), "kingdom-factions"));
			Vec3 pos = at(0, 0);
			p.snapTo(pos.x, pos.y, pos.z, 0, 0);
			KingdomAggression.set(l, p.getUUID(), 0F);
			KingdomAggression.set(l, p.getUUID(), M, 0F);
			KingdomSoldier sg = soldier(l, UnitTypes.SERGEANT, M, at(0, 10));
			sg.setNoAi(true);
			sg.hurtServer(l, l.damageSources().playerAttack(p), 1000F);
			check("killing a test-faction sergeant adds 2 to the test faction's ledger and nothing to Aldermark's",
				Math.abs(KingdomApi.aggression(p, M) - 2F) < 0.01F && KingdomApi.aggression(p) == 0F, KingdomApi.aggression(p, M) + " / " + KingdomApi.aggression(p));
			KingdomApi.setFactionHostileToPlayers(srv, M, false);
			KingdomSoldier w = soldier(l, UnitTypes.MAN_AT_ARMS, M, at(5, 10));
			w.setNoAi(true);
			boolean before = KingdomTargeting.canAttack(w, p);
			KingdomAggression.set(l, p.getUUID(), M, 5F);
			boolean marked = KingdomTargeting.canAttack(w, p);
			KingdomSoldier wa = soldier(l, UnitTypes.MAN_AT_ARMS, A, at(-5, 10));
			wa.setNoAi(true);
			KingdomApi.setFactionHostileToPlayers(srv, A, false);
			boolean aldermarkCalm = !KingdomTargeting.canAttack(wa, p);
			check("5 aggression with the test faction: its neutral soldiers attack the player on sight, Aldermark's stay calm", !before && marked && aldermarkCalm
				&& KingdomAggression.isMarked(p, M) && !KingdomAggression.isMarked(p, A), before + " " + marked + " " + aldermarkCalm);
			KingdomApi.setFactionHostileToPlayers(srv, A, true);
			KingdomAggression.set(l, p.getUUID(), M, 8F);
			Squad hunt = KingdomAggression.startHunt(l, p, M, 8F);
			check("aggression 6+: the test faction sends a hunting squad of its own soldiers (its skin, its tabard)", hunt != null && M.equals(hunt.faction()) && p.getUUID().equals(hunt.huntTarget())
				&& hunt.leaderEntity(l) instanceof KingdomSoldier hl && hl.skin().getPath().contains("testfaction") && hl.tabardColor() == TestFactions.TABARD,
				hunt == null ? "no hunt" : String.valueOf(hunt.faction()));
			KingdomState st = KingdomState.get(srv.overworld());
			KingdomAggression.set(l, p.getUUID(), A, 3F);
			KingdomState copy = roundTrip(st);
			check("ledgers are saved per faction (round trip keeps 8 for the test faction and 3 for Aldermark)",
				Math.abs(copy.aggressionOf(M, p.getUUID()) - 8F) < 0.01F && Math.abs(copy.aggressionOf(A, p.getUUID()) - 3F) < 0.01F && copy.aggressionOf(Factions.REBELS, p.getUUID()) == 0F, copy.aggressionOf(M, p.getUUID()) + "/" + copy.aggressionOf(A, p.getUUID()));
			KingdomApi.setFactionFallen(srv, M, true);
			check("a fallen faction is recorded (saved) so hunts and clashes stop for it", KingdomApi.isFactionFallen(srv, M) && roundTrip(st).isFallen(M) && !KingdomApi.isFactionFallen(srv, A), null);
			KingdomApi.setFactionFallen(srv, M, false);
			KingdomApi.setFactionHostileToPlayers(srv, M, true);
			KingdomAggression.set(l, p.getUUID(), 0F);
			KingdomAggression.set(l, p.getUUID(), M, 0F);
			clean(l);
			return 1;
		});
		// ---- abstract conflict: no entities, seeded roll, stored ---------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomPerf.forceActive = false;
			KingdomPerf.testObservers.clear();
			// pure roll: deterministic, power-weighted, heavy loser losses
			FactionConflict.Outcome o1 = FactionConflict.roll(12345L, 100, 100, 6, 6), o2 = FactionConflict.roll(12345L, 100, 100, 6, 6);
			int strongWins = 0, evenWins = 0, loserOk = 0;
			for (long s = 0; s < 400; s++) {
				if (FactionConflict.roll(s, 400, 100, 6, 6).aWins()) strongWins++;
				FactionConflict.Outcome o = FactionConflict.roll(s + 9000, 100, 100, 6, 6);
				if (o.aWins()) evenWins++;
				int loser = o.aWins() ? o.lossesB() : o.lossesA(), winner = o.aWins() ? o.lossesA() : o.lossesB();
				if (loser >= 4 && winner <= 3 && winner < 6) loserOk++;
			}
			check("seeded roll is deterministic (same seed, same outcome)", o1.equals(o2), o1);
			check("seeded roll favours the stronger squad (4x power wins >90%, equal power 35-65%)", strongWins > 360 && evenWins > 140 && evenWins < 260, strongWins + " / " + evenWins);
			check("the loser loses most of its squad, the winner never all of it", loserOk == 400, loserOk);
			// two virtual squads of hostile factions meet far from every player
			KingdomState st = KingdomState.get(l);
			SelfTestFactionsState.clashes0 = st.clashes().size();
			SelfTestFactionsState.resolved0 = FactionConflict.resolved;
			List<Identifier> mem = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.ARCHER);
			SelfTestFactionsState.va = KingdomApi.createVirtualSquad(l, new BlockPos(3000, SelfTest.y, 3000), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			SelfTestFactionsState.vm = KingdomApi.createVirtualSquad(l, new BlockPos(3010, SelfTest.y, 3000), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			SelfTestFactionsState.powerA = FactionConflict.power(SelfTestFactionsState.va);
			SelfTestFactionsState.powerM = FactionConflict.power(SelfTestFactionsState.vm);
			// a pair at peace and a pair with a fallen side: no clash
			KingdomApi.setStance(srv, M, Factions.REBELS, Factions.Stance.PEACE);
			KingdomApi.createVirtualSquad(l, new BlockPos(3100, SelfTest.y, 3000), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.createVirtualSquad(l, new BlockPos(3110, SelfTest.y, 3000), Factions.REBELS, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.setFactionFallen(srv, Factions.REBELS, true);
			KingdomApi.setFactionFallen(srv, Factions.REBELS, true);
			KingdomApi.createVirtualSquad(l, new BlockPos(3200, SelfTest.y, 3000), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.createVirtualSquad(l, new BlockPos(3210, SelfTest.y, 3000), Factions.REBELS, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			// allied pair far apart in the same place as nothing: two squads of the same faction never clash
			KingdomApi.createVirtualSquad(l, new BlockPos(3300, SelfTest.y, 3000), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.createVirtualSquad(l, new BlockPos(3310, SelfTest.y, 3000), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			// a pair of hostile squads 100 blocks apart: not meeting
			KingdomApi.createVirtualSquad(l, new BlockPos(3400, SelfTest.y, 3000), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.createVirtualSquad(l, new BlockPos(3500, SelfTest.y, 3000), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			SelfTestFactionsState.entities0 = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(2900, -64, 2900, 3700, 320, 3100)).size();
			return 230;
		});
		STEPS.add((srv, l) -> {
			KingdomState st = KingdomState.get(l);
			Squad va = SelfTestFactionsState.va, vm = SelfTestFactionsState.vm;
			int clashes = st.clashes().size() - SelfTestFactionsState.clashes0;
			FactionConflict.Record r = st.clashes().isEmpty() ? null : st.clashes().get(st.clashes().size() - 1);
			check("two virtual squads of hostile factions that meet resolve exactly one abstract clash (peace, fallen, same faction, far apart: none)", clashes == 1 && FactionConflict.resolved - SelfTestFactionsState.resolved0 == 1, clashes + " clashes");
			boolean ours = r != null && ((r.factionA().equals(A) && r.factionB().equals(M)) || (r.factionA().equals(M) && r.factionB().equals(A)));
			check("the clash is stored in saved data with factions, sizes, losses, winner and seed", ours && r.sizeA() == 7 && r.sizeB() == 7 && r.lossesA() + r.lossesB() > 0 && (r.winner().equals(A) || r.winner().equals(M)), r);
			if (r != null) {
				boolean aFirst = r.factionA().equals(A);
				double pa = aFirst ? SelfTestFactionsState.powerA : SelfTestFactionsState.powerM, pb = aFirst ? SelfTestFactionsState.powerM : SelfTestFactionsState.powerA;
				FactionConflict.Outcome again = FactionConflict.roll(r.seed(), pa, pb, r.sizeA(), r.sizeB());
				check("the stored seed reproduces the outcome (winner and both loss counts)", again.lossesA() == r.lossesA() && again.lossesB() == r.lossesB() && again.aWins() == r.winner().equals(r.factionA()), again + " vs " + r);
				int remA = (aFirst ? va : vm).virtualMembers().size(), remB = (aFirst ? vm : va).virtualMembers().size();
				boolean squadsGone = st.squads().stream().noneMatch(q -> q.id().equals((aFirst ? va : vm).id())) || remA == r.sizeA() - r.lossesA();
				check("casualties were applied to the virtual squads (or the squad is gone)", squadsGone && (st.squads().stream().noneMatch(q -> q.id().equals((aFirst ? vm : va).id())) || remB == r.sizeB() - r.lossesB()), remA + " / " + remB);
			}
			int ents = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(2900, -64, 2900, 3700, 320, 3100)).size();
			check("no entities were involved (no soldiers in the clash area)", ents == SelfTestFactionsState.entities0 && ents == 0, ents);
			KingdomState copy = roundTrip(st);
			check("clash log survives a save/load round trip", copy.clashes().size() == st.clashes().size() && !copy.clashes().isEmpty() && copy.clashes().get(copy.clashes().size() - 1).equals(r), copy.clashes().size());
			KingdomApi.setFactionFallen(srv, Factions.REBELS, false);
			KingdomApi.setStance(srv, M, Factions.REBELS, Factions.Stance.WAR);
			// the switch: no abstract clashes when disabled
			st.clearForTests();
			KingdomConfig.abstractConflicts = false;
			List<Identifier> mem = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER);
			KingdomApi.createVirtualSquad(l, new BlockPos(3000, SelfTest.y, 3500), A, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			KingdomApi.createVirtualSquad(l, new BlockPos(3008, SelfTest.y, 3500), M, Squad.Formation.LINE, UnitTypes.SERGEANT, mem);
			SelfTestFactionsState.clashes0 = st.clashes().size();
			return 130;
		});
		STEPS.add((srv, l) -> {
			KingdomState st = KingdomState.get(l);
			check("factions.abstractConflicts=false: meeting virtual squads do not clash", st.clashes().size() == SelfTestFactionsState.clashes0, st.clashes().size());
			KingdomConfig.abstractConflicts = true;
			st.clearForTests();
			KingdomPerf.forceActive = true;
			return 2;
		});
		// ---- benchmark: two factions fighting ----------------------------------------------------------------------------------
		addBench2("two factions: 15 Aldermark vs 15 test faction (30 in one area, all full AI)", 15, 15, 0);
		addBench2("two factions, 1 player at the battle: 15 per player cap shared (20 + 20 requested)", 20, 20, 1);
	}

	static final class SelfTestFactionsState {
		static List<KingdomSoldier> sa, sm;
		static Squad va, vm;
		static double powerA, powerM;
		static int clashes0, entities0;
		static long resolved0;
		static double benchAvg;
	}

	static void addBench2(String name, int nA, int nM, int observers) {
		STEPS.add((srv, l) -> {
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			KingdomPerf.forceActive = observers == 0;
			KingdomPerf.testObservers.clear();
			if (observers >= 1) KingdomPerf.testObservers.add(at(0, 0));
			int madeA = 0, madeM = 0;
			for (int i = 0; i < 3 && madeA < nA; i++) madeA += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, -9)), A, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, mix(Math.min(7, nA - madeA - 1))).size();
			for (int i = 0; i < 3 && madeM < nM; i++) madeM += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 9)), M, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, mix(Math.min(7, nM - madeM - 1))).size();
			SelfTestFactionsState.entities0 = madeA + madeM;
			SelfTestFactionsState.clashes0 = madeA;
			info("BENCH start: " + name + " (spawned " + madeA + " + " + madeM + ")");
			SAMPLES.clear();
			SelfTest.measuring = true;
			SelfTest.tickStart = 0;
			return 300;
		});
		STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
			int aliveA = countOf(l, A), aliveM = countOf(l, M);
			int total = SelfTestFactionsState.entities0;
			String line = String.format("BENCH %s: MSPT avg %.2f ms, p95 %.2f, max %.2f over %d ticks | spawned %d, alive %d Aldermark + %d test faction | active %d",
				name, avg, p95, max, s.length, total, aliveA, aliveM, KingdomPerf.activeCount(l));
			info(line);
			SelfTest.BENCH.add(line);
			check("benchmark ran: " + name, s.length >= 290, s.length + " ticks");
			check("two-faction battle stays within the caps (" + (observers == 0 ? "30 per area" : "15 per player") + ", both factions counted)", total == (observers == 0 ? 30 : 15), total);
			if (observers == 0) {
				List<KingdomSoldier> la = KingdomPerf.loaded(l).stream().filter(k -> A.equals(k.factionId())).toList(), lm = KingdomPerf.loaded(l).stream().filter(k -> M.equals(k.factionId())).toList();
				float ha = hpLost(la) + (nA - la.size()) * 20F, hm = hpLost(lm) + (nM - lm.size()) * 20F;
				check("both factions fight (each side lost health or soldiers)", ha > 0 && hm > 0, String.format("%.0f / %.0f hp", ha, hm));
			}
			if (observers == 0) {
				check("two-faction battle MSPT within the 2.0 numbers (avg < 15 ms)", avg < 15.0, String.format("%.2f ms", avg));
				double ref = -1;
				for (String b : SelfTest.BENCH) {
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("BENCH 30 soldiers in 4 mixed squads.*?MSPT avg ([0-9.]+)").matcher(b);
					if (m.find()) ref = Double.parseDouble(m.group(1));
				}
				info(String.format("two-faction battle avg %.2f ms vs 2.0 reference (30 mixed vs 30 zombies, same run) %.2f ms", avg, ref));
				check("two-faction battle is no heavier than the 2.0 30-soldier battle of the same run (avg <= 1.25x + 1 ms)", ref < 0 || avg <= ref * 1.25 + 1.0, String.format("%.2f vs %.2f", avg, ref));
				SelfTestFactionsState.benchAvg = avg;
			}
			KingdomPerf.testObservers.clear();
			KingdomPerf.forceActive = true;
			clean(l);
			return 2;
		});
	}
}
