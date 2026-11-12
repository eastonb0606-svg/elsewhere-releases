package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom_court.dev.SelfTest.at;
import static dev.elsewhere.kingdom_court.dev.SelfTest.check;
import static dev.elsewhere.kingdom_court.dev.SelfTest.clean;
import static dev.elsewhere.kingdom_court.dev.SelfTest.count;
import static dev.elsewhere.kingdom_court.dev.SelfTest.fp;
import static dev.elsewhere.kingdom_court.dev.SelfTest.info;
import static dev.elsewhere.kingdom_court.dev.SelfTest.kill;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.dev.TestFactions;
import dev.elsewhere.kingdom_court.CourtApi;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtRealms;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.kingdom_court.world.CourtSite;
import dev.elsewhere.kingdom_court.world.CourtWorld;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * 2.1 multi-faction court checks: a second faction's realm placement (one per faction, never overlapping), per-faction
 * court state, rulers named/sized/dropping from the faction file, succession and the fall per faction (independent of
 * Aldermark), a monarchy with a Hird-oath ruler bonus (king + 3 Lords) and an oligarchy (5 Magnates, a weekly Chair, no
 * King, seat refills, the fall when all five are dead).
 */
public final class SelfTestFactions {
	private SelfTestFactions() {}

	static final Identifier A = Factions.ALDERMARK, M = TestFactions.MARCHES;
	static final Identifier H = CourtLifeIds.id("test_hird"), O = CourtLifeIds.id("test_oligarchy");

	static final class CourtLifeIds {
		static Identifier id(String p) {
			return Identifier.fromNamespaceAndPath("elsewhere", p);
		}
	}

	static FactionDef.RulerDef rd(String name, double health, double cap, String drop) {
		return new FactionDef.RulerDef(name, Optional.empty(), "", health, cap > 0 ? Optional.of(cap) : Optional.empty(),
			List.of(new FactionDef.Drop(Identifier.withDefaultNamespace(drop), 1, 1, 1.0F)));
	}

	static FactionDef variant(String name, Map<String, FactionDef.RulerDef> rulers, FactionDef.Society society) {
		FactionDef b = TestFactions.def();
		return new FactionDef(name, Optional.empty(), b.banner(), b.tabard(), Optional.empty(), true, List.of(), List.of(), Map.of(), rulers, List.of(), Optional.empty(), false, society, FactionDef.Economy.NONE, FactionDef.Army.DEFAULT);
	}

	static CourtRuler spawn(ServerLevel l, CourtState st, Ruler r, int i) {
		return CourtLife.spawnRuler(l, st, r, at(-30 + i * 8, 70), null);
	}

	static CourtRuler live(ServerLevel l, CourtState st, Ruler r) {
		UUID id = st.rulerIds.get(r);
		return id != null && l.getEntity(id) instanceof CourtRuler c ? c : null;
	}

	static void add() {
		// ---- a second realm per world: one per faction, never overlapping, in Aldermark's wedge ----------------------------------------------------------
		STEPS.add((srv, l) -> {
			TestFactions.register();
			// Aldermark first and the test faction present; any other realm must be a kingdom shipped as a data file (the rival kingdoms in the
			// merged jar: azmar, hrafnholt, mirewick), never a leftover code-defined test faction.
			List<Identifier> realms = FactionDefs.realmFactions();
			boolean othersShipped = realms.stream().filter(f -> !f.equals(A) && !f.equals(M))
				.allMatch(f -> srv.getResourceManager().getResource(Identifier.fromNamespaceAndPath(f.getNamespace(), "kingdom_faction/" + f.getPath() + ".json")).isPresent());
			check("test faction registered (Aldermark's file untouched): both have a realm, beside only the installed kingdoms' own files", !realms.isEmpty() && realms.get(0).equals(A) && realms.contains(M) && othersShipped, realms);
			var reg = srv.registryAccess();
			ChunkGenerator gen = reg.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow().generator();
			NoiseBasedChunkGenerator nbg = (NoiseBasedChunkGenerator) gen;
			var noises = reg.lookupOrThrow(Registries.NOISE);
			long[] seeds = {1L, 42L, 1337L, 2026L, 987654321L, -5551212L, 7L, 123456789012L, -99L, 31337L, 8675309L, 2718281828L};
			BlockPos[] spawns = {BlockPos.ZERO, new BlockPos(200, 0, -300), new BlockPos(-640, 0, 480)};
			int both = 0, bad = 0, same2 = 0;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < seeds.length; i++) {
				BlockPos spawn = spawns[i % spawns.length];
				RandomState rs = RandomState.create(noises, seeds[i], nbg.generatorSettings().value());
				BlockPos a = CourtSite.choose(seeds[i], spawn, gen, rs, l, cp -> false);
				BlockPos a2 = CourtSite.choose(seeds[i], spawn, gen, rs, l, cp -> false, A, List.of());
				BlockPos m = CourtSite.chooseNear(seeds[i], spawn, gen, rs, l, cp -> false, M, a == null ? List.of() : List.of(a));
				BlockPos m2 = CourtSite.chooseNear(seeds[i], spawn, gen, rs, l, cp -> false, M, a == null ? List.of() : List.of(a));
				if (a != null && a.equals(a2)) same2++;
				if (a == null || m == null) {
					sb.append(" s").append(seeds[i]).append("=none");
					continue;
				}
				both++;
				double da = Math.hypot(a.getX() - spawn.getX(), a.getZ() - spawn.getZ()), dm = Math.hypot(m.getX() - spawn.getX(), m.getZ() - spawn.getZ());
				double bearing = Math.atan2(a.getZ() - spawn.getZ(), a.getX() - spawn.getX()), ang = Math.atan2(m.getZ() - spawn.getZ(), m.getX() - spawn.getX());
				boolean inWedge = Math.abs(Math.atan2(Math.sin(ang - bearing), Math.cos(ang - bearing))) <= Math.PI / 4 + 0.01;
				boolean apart = Math.hypot(a.getX() - m.getX(), a.getZ() - m.getZ()) >= CourtSite.MIN_APART;
				if (dm < 1500 || dm > 5000 || da < 1200 || da > 2500 || !inWedge || !apart || !m.equals(m2)) bad++;
				sb.append(String.format(" s%d=%.0f/%.0f", seeds[i], da, dm));
			}
			info("two realms per world, distance from spawn Aldermark/test faction:" + sb);
			check("each world with room gets one realm per faction, Aldermark 1,200-2,500 and the test faction 1,500-5,000 from spawn in Aldermark's 90-degree wedge, 1,800+ blocks apart (never overlapping), deterministic", bad == 0 && both >= 8, both + " worlds, " + bad + " bad");
			check("Aldermark's choice is the 2.0 choice (adding a faction does not move it)", same2 >= 9, same2);
			// placement: exactly one structure chunk per faction in the whole checked window, different chunks
			BlockPos sa = new BlockPos(1500, SelfTest.y, -900), sm = new BlockPos(-1800, SelfTest.y, 700);
			CourtWorld.setSite(l.getSeed(), A, sa);
			CourtWorld.setSite(l.getSeed(), M, sm);
			var state = l.getChunkSource().getGeneratorState();
			var pa = new CourtWorld.Placement(A);
			var pm = new CourtWorld.Placement(M);
			int ha = 0, hm = 0;
			for (int cx = -190; cx < 185; cx++) for (int cz = -190; cz < 185; cz++) {
				if (pa.isStructureChunk(state, cx, cz)) ha++;
				if (pm.isStructureChunk(state, cx, cz)) hm++;
			}
			check("placement: exactly one Aldermark realm and one test-faction realm in the checked window", ha == 1 && hm == 1 && pm.isStructureChunk(state, sm.getX() >> 4, sm.getZ() >> 4) && !pa.isStructureChunk(state, sm.getX() >> 4, sm.getZ() >> 4), ha + " / " + hm);
			CourtWorld.setSite(l.getSeed(), A, null);
			CourtWorld.setSite(l.getSeed(), M, null);
			JsonObject none = new JsonObject(), withF = new JsonObject();
			withF.addProperty("faction", "elsewhere:test_marches");
			var d0 = CourtWorld.Placement.CODEC.codec().parse(JsonOps.INSTANCE, none);
			var d1 = CourtWorld.Placement.CODEC.codec().parse(JsonOps.INSTANCE, withF);
			check("structure_set placement JSON: no faction field = Aldermark (the 2.0 structure set is unchanged), \"faction\" selects another realm",
				d0.result().isPresent() && d0.result().get().faction().equals(A) && d1.result().isPresent() && d1.result().get().faction().equals(M), null);
			return 1;
		});
		// ---- the second faction's court: names, health, drops, succession, the fall (independent of Aldermark) -----------------
		STEPS.add((srv, l) -> {
			clean(l);
			CourtState stA = CourtState.get(srv), stM = CourtState.get(srv, M);
			boolean aFallen = stA.fallen;
			int aDead = stA.dead.size();
			Map<Ruler, List<ItemStack>> aDrops = new java.util.EnumMap<>(Ruler.class);
			aDrops.putAll(CourtLife.LAST_DROPS);
			check("each faction has its own court state (Aldermark's is the 2.0 one; the test faction's is separate, faction recorded)", stA != stM && stM.faction.equals(M) && stA.faction.equals(A) && CourtState.peek(srv, M) == stM
				&& CourtState.peek(srv, Identifier.fromNamespaceAndPath("elsewhere", "nobody")) == null, null);
			stM.resetForTests();
			check("the test faction has six leader slots (King and 5 Lords) from its file", stM.slots().size() == 6 && stM.slots().get(0) == Ruler.KING && stM.lords().size() == 5 && !stM.oligarchy(), stM.slots());
			CourtRuler king = spawn(l, stM, Ruler.KING, 0), marshal = spawn(l, stM, Ruler.MARSHAL, 1), chancellor = spawn(l, stM, Ruler.CHANCELLOR, 2);
			spawn(l, stM, Ruler.CASTELLAN, 3);
			spawn(l, stM, Ruler.MASTER_AT_ARMS, 4);
			spawn(l, stM, Ruler.HUNTSMAN, 5);
			check("rulers take name and faction from the faction file (Warden-King Test, test faction)", king != null && M.equals(king.factionId()) && king.getCustomName() != null
				&& king.getCustomName().getString().equals("Warden-King Test") && marshal.getCustomName().getString().equals("Marshal Test"), king == null ? null : king.getCustomName());
			check("health caps from the file: King 280 + 12 per living Lord = 340 clamped to the 300 human cap, Marshal 150, Chancellor 80", king.getMaxHealth() == 300F && marshal.getMaxHealth() == 150F && chancellor.getMaxHealth() == 80F,
				king.getMaxHealth() + " " + marshal.getMaxHealth() + " " + chancellor.getMaxHealth());
			check("Aldermark's own Ruler constants are unchanged (300 / 190 / 90)", Ruler.KING.health == 300 && Ruler.MARSHAL.health == 190 && Ruler.CHANCELLOR.health == 90, null);
			FakePlayer killer = fp(l, "court-factions");
			dev.elsewhere.kingdom.KingdomAggression.set(l, killer.getUUID(), M, 0F);
			kill(l, king, killer);
			List<ItemStack> d = CourtLife.LAST_DROPS_BY.get(M + "|king");
			check("the King drops what his file says (1 diamond, 4-8 gold ingots) and nothing of Aldermark's", d != null && count(d, Items.DIAMOND) == 1 && count(d, Items.GOLD_INGOT) >= 4 && count(d, Items.GOLD_INGOT) <= 8
				&& CourtLife.LAST_DROPS.equals(aDrops), d);
			check("succession in the test faction: the Marshal is crowned, it is the Marshal who wears the crown", stM.crowned == Ruler.MARSHAL && marshal.crowned && !stM.fallen && stM.dead.size() == 1, stM.crowned);
			check("kill adds aggression to the test faction's ledger only (10 for the King; Aldermark's unchanged)", Math.abs(KingdomApi.aggression(killer, M) - 10F) < 0.01F, KingdomApi.aggression(killer, M));
			check("the ruler's death marks that faction's ruler as killed (lifts a Standing cap), not Aldermark's flag alone", KingdomState.get(srv.overworld()).isRulerKilled(M), null);
			check("Aldermark is unaffected (same fall state and dead count as before)", stA.fallen == aFallen && stA.dead.size() == aDead, null);
			// all five Lords die: the test faction falls, alone
			boolean[] firedKingdom = {false};
			List<Identifier> fell = new ArrayList<>();
			CourtEvents.KINGDOM_FALLEN.register(s -> firedKingdom[0] = true);
			CourtEvents.FACTION_FALLEN.register((s, f) -> fell.add(f));
			for (Ruler r : stM.lords()) {
				CourtRuler c = live(l, stM, r);
				if (c != null) kill(l, c, killer);
			}
			check("all six dead: the test faction has fallen (state, API, kingdom flag); Aldermark's KINGDOM_FALLEN did not fire again", stM.fallen && CourtApi.kingdomFallen(srv, M) && KingdomApi.isFactionFallen(srv, M)
				&& stM.dead.size() == 6 && !firedKingdom[0] && fell.equals(List.of(M)), fell);
			check("its soldiers' faction is no longer hostile to players and its remnant faction exists", !Factions.get(M).hostileToPlayers() && Factions.exists(dev.elsewhere.kingdom_court.CourtContent.remnantOf(M)), null);
			CourtRealms rt = CourtRealms.load(roundTripNbt(srv));
			CourtState back = rt.state(M, false);
			check("the test faction's court is saved in its own container and survives a save/load (faction, dead set, fallen)", back != null && back.faction.equals(M) && back.fallen && back.dead.size() == 6 && back.crowned == stM.crowned, null);
			check("an old Aldermark court file (no faction field) loads as Aldermark", CourtState.load(new CompoundTag()).faction.equals(A), null);
			killer.discard();
			clean(l);
			return 2;
		});
		// ---- a monarchy with 3 Lords and the Hird-oath (+15% damage per living champion) ---------------------------------------------
		STEPS.add((srv, l) -> {
			Map<String, FactionDef.RulerDef> rulers = new LinkedHashMap<>();
			rulers.put("king", rd("High Jarl Test", 250, 250, "netherite_scrap"));
			rulers.put("marshal", rd("Hersir Test", 140, 0, "iron_ingot"));
			rulers.put("castellan", rd("Skald Test", 140, 0, "gold_ingot"));
			rulers.put("huntsman", rd("Huntmaster Test", 120, 0, "emerald"));
			FactionDefs.putCodeDefined(H, variant("Test Hird", rulers, new FactionDef.Society(FactionDef.Government.MONARCHY, new FactionDef.RulerBonus("damage_per_champion", 0.15),
				List.of("king", "marshal", "castellan", "huntsman"), FactionDef.StandingRule.DEFAULT, FactionDef.Serfs.DEFAULT, 7, 7)));
			CourtState st = CourtState.get(srv, H);
			st.resetForTests();
			check("leader roles come from data: a ruler and 3 Lords (4 slots), no Chancellor or Master-at-Arms", st.slots().equals(List.of(Ruler.KING, Ruler.MARSHAL, Ruler.CASTELLAN, Ruler.HUNTSMAN)) && st.lords().size() == 3, st.slots());
			CourtRuler jarl = spawn(l, st, Ruler.KING, 0);
			for (int i = 0; i < 3; i++) spawn(l, st, st.lords().get(i), i + 1);
			var mod = jarl.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(CourtRuler.GATING_DAMAGE);
			check("Hird-oath: the Jarl gets +15% damage per living champion (3 -> +45%) and keeps his base health", mod != null && Math.abs(mod.amount() - 0.45) < 1e-6 && jarl.getMaxHealth() == 250F, mod == null ? "none" : mod.amount() + " " + jarl.getMaxHealth());
			FakePlayer killer = fp(l, "court-factions-2");
			kill(l, live(l, st, Ruler.MARSHAL), killer);
			var mod2 = jarl.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(CourtRuler.GATING_DAMAGE);
			check("a champion dies: +30%", mod2 != null && Math.abs(mod2.amount() - 0.30) < 1e-6 && !st.fallen, mod2 == null ? "none" : mod2.amount());
			kill(l, jarl, killer);
			check("the Jarl dies: the highest living Lord (Castellan) is crowned", st.crowned == Ruler.CASTELLAN && live(l, st, Ruler.CASTELLAN).crowned, st.crowned);
			kill(l, live(l, st, Ruler.CASTELLAN), killer);
			kill(l, live(l, st, Ruler.HUNTSMAN), killer);
			check("all four dead: this faction has fallen (4 leaders, not 6); the test faction and Aldermark are unaffected", st.fallen && CourtState.isFallen(srv, H) && st.dead.size() == 4, st.dead);
			killer.discard();
			clean(l);
			return 2;
		});
		// ---- an oligarchy: 5 Magnates, no King, a weekly Chair, seats refilled by new families -----------------------------------------------
		STEPS.add((srv, l) -> {
			Map<String, FactionDef.RulerDef> rulers = new LinkedHashMap<>();
			String[] keys = {"marshal", "castellan", "master_at_arms", "huntsman", "chancellor"};
			for (String k : keys) rulers.put(k, rd("Magnate " + k, 100, 200, "gold_nugget"));
			FactionDefs.putCodeDefined(O, variant("Test Republic", rulers, new FactionDef.Society(FactionDef.Government.OLIGARCHY, new FactionDef.RulerBonus("hp_per_other_magnate", 12.0),
				List.of(), FactionDef.StandingRule.DEFAULT, FactionDef.Serfs.DEFAULT, 7, 7)));
			CourtState st = CourtState.get(srv, O);
			st.resetForTests();
			check("oligarchy: 5 Magnates, no King slot, government from data", st.oligarchy() && st.slots().size() == 5 && !st.slots().contains(Ruler.KING) && st.lords().size() == 5, st.slots());
			CourtLife.council(l, st);
			Ruler chair = st.crowned;
			check("the Council elects a Chair from the living Magnates (nobody is crowned)", st.slots().contains(chair) && chair != Ruler.KING, chair);
			List<CourtRuler> ms = new ArrayList<>();
			for (int i = 0; i < 5; i++) ms.add(spawn(l, st, st.slots().get(i), i));
			CourtRuler chairE = live(l, st, chair);
			boolean noCrown = ms.stream().noneMatch(m -> m.crowned);
			boolean others100 = ms.stream().filter(m -> m != chairE).allMatch(m -> m.getMaxHealth() == 100F);
			check("the Chair holds the ruler bonus (+12 HP per other living Magnate: 100 + 48 = 148); the others have base health; nobody wears a crown", chairE.getMaxHealth() == 148F && others100 && noCrown, chairE.getMaxHealth() + " " + others100 + " " + noCrown);
			FakePlayer killer = fp(l, "court-factions-3");
			kill(l, chairE, killer);
			Ruler newChair = st.crowned;
			CourtRuler nc = live(l, st, newChair);
			check("the Chair dies: no succession crown, the Chair is vacant for a day (decision 5: the Council elects a new Chair after 1 day)", st.crowned == chair && st.chairVacantUntil >= 0 && !st.isHolder(st.crowned) && !st.fallen && ms.stream().noneMatch(m -> m.crowned),
				st.crowned + " " + st.chairVacantUntil);
			long day = l.getDefaultClockTime() / 24000L;
			check("the dead seat is recorded with its day", st.deadSince.containsKey(chair) && !st.alive(chair), null);
			CourtLife.council(l, st);
			check("before 7 days the seat stays empty", !st.alive(chair) && st.refills == 0, null);
			st.deadSince.put(chair, day - 8);
			CourtLife.council(l, st);
			check("after 7 in-game days a new family buys the seat (alive again, refill counted)", st.alive(chair) && st.refills == 1 && !st.dead.contains(chair), st.refills);
			CourtRuler fresh = spawn(l, st, chair, 6);
			for (Ruler r : st.slots()) {
				CourtRuler c = live(l, st, r);
				if (c != null && !st.dead.contains(r)) kill(l, c, killer);
			}
			check("all five seats dead with no refill in time: the Council breaks and the faction falls", fresh != null && st.fallen && CourtState.isFallen(srv, O), st.dead.size());
			killer.discard();
			clean(l);
			return 2;
		});
	}

	static CompoundTag roundTripNbt(MinecraftServer srv) {
		return CourtRealms.get(srv).save();
	}
}
