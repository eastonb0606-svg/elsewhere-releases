package dev.elsewhere.kingdom.war;

import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomEvents;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Multipliers;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hand-off from the abstract war model to real soldiers (WAR-AI section 6). An assault or patrol clash whose location is within {@link #RADIUS} blocks of a player, in
 * loaded chunks, is fought by real {@link KingdomSoldier}s: at most 15 soldiers across both sides for a village assault (8 attackers, 7 defenders) and 12 for a skirmish
 * (6 a side), always inside the 15 per player / 30 per area caps (the caps are enforced by {@link KingdomApi#spawnSquad}; a side that gets no room makes the fight fall back to the
 * dice). The rest of the committed strength waits as reserves: one soldier per 10 s per side steps in as casualties fall. The fight ends when one side is gone, after {@link #TIMEOUT}
 * ticks (90 s), when the attacker's remaining strength falls below its {@code retreat} threshold (Azmar 0.85 of the odds, Hrafnholt never) or when no player is near any more; the result is written
 * back into the model ({@link WarSim#finish}). A fight a player took part in grants the winner no veterancy. Survivors are removed at the end.
 */
public final class WarBattles {
	private WarBattles() {}

	public static final double RADIUS = 96;
	public static final int RESERVE_TICKS = 200, MAX_BATTLES = 2;
	/** Ticks before a real battle is called (90 s); tests shorten it. */
	public static int TIMEOUT = 1800;
	/** Village militia: the defenders of a free village. Neutral to players, at war with every kingdom (the default stance). */
	public static final Identifier MILITIA = KingdomContent.id("village_militia");

	static WarApi.@Nullable BattleHandler custom;
	static final List<Battle> BATTLES = new ArrayList<>();
	static final Map<Integer, WarSim.Assault> CUSTOM = new HashMap<>();
	static int nextId = 1;
	/** Tests: battles started and finished. */
	public static int started, finished, skirmishesStarted;
	static boolean hooked;

	static final class Battle {
		int id;
		boolean skirmish;
		WarSim.Assault assault;
		int ka = -1, kb = -1; // skirmish kingdoms
		Identifier fa, fd;
		final List<UUID> att = new ArrayList<>(), def = new ArrayList<>();
		int initA, initD, resA, resD, resA0, resD0;
		long start;
		boolean playerInvolved, done;
		final Set<UUID> killers = new HashSet<>(), attackerKillers = new HashSet<>();
		double retreat;
		BlockPos pos;
		String villageKey;
		/** Hrafnholt by sea (RIVALS-DECISIONS 18): the voyage bringing the attackers, null for a land approach. */
		Longship.@Nullable Voyage voyage;
		/** Set by a voyage that could not field its crew: the fight goes back to the dice. */
		boolean abort;
		double ha;
	}

	public static void reset() {
		Longship.resetVoyages();
		BATTLES.clear();
		CUSTOM.clear();
		nextId = 1;
	}

	public static int active() {
		return BATTLES.size();
	}

	static void hook() {
		if (hooked) return;
		hooked = true;
		if (!Factions.exists(MILITIA)) Factions.register(MILITIA, 0xA08060, false);
		KingdomEvents.SOLDIER_DIED.register((level, s, src) -> {
			for (Battle b : BATTLES) {
				boolean a = b.att.contains(s.getUUID()), d = b.def.contains(s.getUUID());
				if (!a && !d) continue;
				if (src.getEntity() instanceof ServerPlayer p) {
					b.playerInvolved = true;
					b.killers.add(p.getUUID());
					if (a) b.attackerKillers.add(p.getUUID());
				}
			}
		});
	}

	static @Nullable ServerPlayer nearPlayer(ServerLevel lv, double x, double z, double r) {
		ServerPlayer best = null;
		double bd = r * r;
		for (ServerPlayer p : lv.players()) {
			if (p.isSpectator()) continue;
			double dx = p.getX() - x, dz = p.getZ() - z, d = dx * dx + dz * dz;
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	static @Nullable BlockPos ground(ServerLevel lv, double x, double z) {
		BlockPos p = BlockPos.containing(x, 64, z);
		if (!lv.isLoaded(p)) return null;
		BlockPos g = lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
		if (!lv.getFluidState(g).isEmpty() || !lv.getFluidState(g.below()).isEmpty()) return null;
		return g;
	}

	static @Nullable Identifier factionOf(List<Identifier> ids, int k) {
		return k >= 0 && k < ids.size() ? ids.get(k) : null;
	}

	/** Spawns {@code n} soldiers of {@code faction} (a sergeant and n-1 members, some from hired companies) around {@code at}; returns the soldier UUIDs. */
	static List<UUID> spawnSide(ServerLevel lv, BlockPos at, Identifier faction, int n, double hiredShare, Squad.Formation f, @Nullable BlockPos objective) {
		List<UUID> out = new ArrayList<>();
		if (n <= 0) return out;
		var r = lv.getRandom();
		List<Identifier> members = ArmyComposer.members(r, Math.max(0, n - 1), false, faction == MILITIA ? Factions.ALDERMARK : faction);
		if (hiredShare > 0) {
			for (int i = 0; i < members.size(); i++) if (r.nextDouble() < hiredShare) members.set(i, FreeCompanies.skinned(r, r.nextBoolean() ? FreeCompanies.BLADE : FreeCompanies.BOLT));
		}
		Identifier fac = faction;
		List<KingdomSoldier> sq = KingdomApi.spawnSquad(lv, at, fac, f, UnitTypes.SERGEANT, members);
		for (KingdomSoldier s : sq) out.add(s.getUUID());
		if (!sq.isEmpty() && sq.get(0).squad() != null && objective != null) sq.get(0).squad().setObjective(objective);
		return out;
	}

	static int alive(ServerLevel lv, List<UUID> l) {
		int c = 0;
		for (var it = l.iterator(); it.hasNext();) {
			Entity e = lv.getEntity(it.next());
			if (e == null || !e.isAlive()) continue;
			c++;
		}
		return c;
	}

	// ---- assaults ---------------------------------------------------------------------------------------------------------

	static boolean tryAssault(MinecraftServer srv, WarSim sim, List<Identifier> ids, WarSim.Assault a) {
		hook();
		ServerLevel lv = srv.overworld();
		if (BATTLES.size() >= MAX_BATTLES || a.v < 0 || a.v >= sim.nv) return false;
		double x = sim.vx[a.v], z = sim.vz[a.v];
		if (!dev.elsewhere.kingdom.KingdomLod.observerNear(lv, x, z, RADIUS)) return false;
		BlockPos vp = ground(lv, x, z);
		if (vp == null) return false;
		Identifier atk = factionOf(ids, a.k), dfd = a.o >= 0 ? factionOf(ids, a.o) : MILITIA;
		if (atk == null || dfd == null) return false;
		WarState st = WarState.get(lv);
		String key = a.v < st.data.villages.size() ? st.data.villages.get(a.v).key : "?";
		double qa = sim.q(a.k) * (1 + sim.vet[a.k]);
		double ha = a.a0 / Math.max(0.1, qa);
		double hd = a.o >= 0 ? sim.H[a.o] * (WarSim.G_SHARE / (sim.n[a.o] + 2) + WarSim.R_SHARE * sim.eff[a.o].react) : sim.fg[a.v];
		int id = nextId++;
		if (custom != null && custom.handle(lv, key, vp, atk, a.o >= 0 ? dfd : null, ha, hd, id)) {
			CUSTOM.put(id, a);
			return true;
		}
		int ra = (int) Math.max(1, Math.min(8, Math.round(ha / 4))), rd = (int) Math.max(1, Math.min(7, Math.round(hd / 4)));
		Battle b = new Battle();
		b.id = id;
		b.assault = a;
		b.fa = atk;
		b.fd = dfd;
		b.pos = vp;
		b.villageKey = key;
		b.retreat = sim.eff[a.k].retreat;
		double hired = sim.Hm[a.k] > 0 ? Math.min(0.5, sim.Hm[a.k] / Math.max(1, sim.power(a.k))) : 0;
		// attackers approach from the side of their castle (or the east), defenders hold the village
		double[] cs = sim.castle[a.k];
		double ang = cs == null ? 0 : Math.atan2(cs[1] - z, cs[0] - x);
		BlockPos ap = ground(lv, x + Math.cos(ang) * 30, z + Math.sin(ang) * 30);
		if (ap == null) ap = vp;
		// Hrafnholt (flag army.landing = "longship"): one shore search; the raiders then come by a sailing longship (RIVALS-DECISIONS 18) that beaches
		// there (the 16a prop replaces it), or walk from their castle's side when no water route exists. No per-tick searches.
		Longship.Shore shore = Longship.enabled(atk) ? Longship.findShore(lv, vp) : null;
		b.ha = ha;
		b.def.addAll(spawnSide(lv, vp, dfd, rd, 0, Squad.Formation.SHIELD_WALL, null));
		if (b.def.isEmpty()) {
			cleanup(lv, b);
			return false;
		}
		b.initD = b.def.size();
		b.resD0 = b.resD = (int) Math.min(20, Math.max(0, Math.round(hd / 4) - b.initD));
		if (shore != null && !dev.elsewhere.kingdom.KingdomConfig.sailingLongships) {
			// kill switch war.sailingLongships off: the 2.1 landing (16a), raiders spawn on the beach beside a beached prop
			Longship.place(lv, shore, Longship.styleFor(lv, atk), id);
			BlockPos land = ground(lv, shore.land().getX() + shore.dx() * 3.0, shore.land().getZ() + shore.dz() * 3.0);
			if (land != null) ap = land;
			shore = null;
		}
		if (shore != null) {
			final BlockPos walk = ap;
			final int n = ra;
			final double hiredShare = hired;
			// the ship comes from the castle's side (or from the open water beyond the shore)
			double fx = cs != null ? cs[0] - shore.land().getX() : shore.land().getX() - vp.getX(), fz = cs != null ? cs[1] - shore.land().getZ() : shore.land().getZ() - vp.getZ();
			b.voyage = Longship.sail(lv, atk, vp, shore, fx, fz, id, new Longship.Crew() {
				@Override
				public List<KingdomSoldier> spawn(BlockPos at) {
					// the caps are counted where the raiders will fight (the village), not only out on the water where they appear
					int room = Math.min(n, dev.elsewhere.kingdom.KingdomPerf.room(lv, Vec3.atBottomCenterOf(vp)));
					if (room <= 0) return List.of();
					List<UUID> u = spawnSide(lv, at, atk, room, hiredShare, Squad.Formation.LINE, vp);
					List<KingdomSoldier> out = new ArrayList<>();
					for (UUID x : u) if (lv.getEntity(x) instanceof KingdomSoldier k) out.add(k);
					b.att.addAll(u);
					armed(b);
					return out;
				}

				@Override
				public void noRoute() {
					b.voyage = null;
					b.att.addAll(spawnSide(lv, walk, atk, n, hiredShare, Squad.Formation.LINE, vp));
					armed(b);
					b.start = lv.getGameTime();
				}

				@Override
				public void noCrew() {
					b.abort = true;
				}

				@Override
				public void landed() {
					b.start = lv.getGameTime(); // the 90 s fight starts on the beach
				}

				@Override
				public void lost() {
					b.abort = true;
				}
			});
		}
		if (b.voyage != null) {
			b.start = lv.getGameTime();
			BATTLES.add(b);
			started++;
			return true;
		}
		b.att.addAll(spawnSide(lv, ap, atk, ra, hired, Squad.Formation.LINE, vp));
		if (b.att.isEmpty()) {
			cleanup(lv, b);
			return false;
		}
		armed(b);
		b.start = lv.getGameTime();
		BATTLES.add(b);
		started++;
		return true;
	}

	/** Strength bookkeeping once the attackers are on the field (at once on land, when the crew boards for a voyage). */
	static void armed(Battle b) {
		if (b.att.isEmpty()) {
			b.abort = true;
			return;
		}
		b.initA = b.att.size();
		b.resA0 = b.resA = (int) Math.min(20, Math.max(0, Math.round(b.ha / 4) - b.initA));
	}

	static boolean trySkirmish(MinecraftServer srv, WarSim sim, List<Identifier> ids, int ka, int kb, double pa, double pb) {
		hook();
		ServerLevel lv = srv.overworld();
		if (BATTLES.size() >= MAX_BATTLES) return false;
		// the closest pair of villages of the two kingdoms: the patrols meet between them
		double bd = Double.MAX_VALUE, mx = 0, mz = 0;
		for (int i = 0; i < sim.nv; i++) {
			if (sim.owner[i] != ka) continue;
			for (int j = 0; j < sim.nv; j++) {
				if (sim.owner[j] != kb || sim.dV[i][j] >= bd) continue;
				bd = sim.dV[i][j];
				mx = (sim.vx[i] + sim.vx[j]) / 2;
				mz = (sim.vz[i] + sim.vz[j]) / 2;
			}
		}
		if (bd == Double.MAX_VALUE || !dev.elsewhere.kingdom.KingdomLod.observerNear(lv, mx, mz, RADIUS)) return false;
		BlockPos c1 = ground(lv, mx - 14, mz), c2 = ground(lv, mx + 14, mz);
		Identifier fa = factionOf(ids, ka), fb = factionOf(ids, kb);
		if (c1 == null || c2 == null || fa == null || fb == null) return false;
		Battle b = new Battle();
		b.id = nextId++;
		b.skirmish = true;
		b.ka = ka;
		b.kb = kb;
		b.fa = fa;
		b.fd = fb;
		b.pos = c1;
		b.retreat = 0;
		int na = Math.min(6, Multipliers.patrolSize(fa, false)), nb = Math.min(6, Multipliers.patrolSize(fb, false));
		b.att.addAll(spawnSide(lv, c1, fa, na, 0, Squad.Formation.LINE, c2));
		b.def.addAll(spawnSide(lv, c2, fb, nb, 0, Squad.Formation.LINE, c1));
		if (b.att.isEmpty() || b.def.isEmpty()) {
			cleanup(lv, b);
			return false;
		}
		b.initA = b.att.size();
		b.initD = b.def.size();
		b.start = lv.getGameTime();
		BATTLES.add(b);
		started++;
		skirmishesStarted++;
		return true;
	}

	// ---- ticking ------------------------------------------------------------------------------------------------------------

	static void tick(ServerLevel lv, long now) {
		if (BATTLES.isEmpty() || now % 20 != 3) return;
		WarSim sim = WarLayer.sim;
		for (Battle b : new ArrayList<>(BATTLES)) {
			if (sim == null) {
				cleanup(lv, b);
				BATTLES.remove(b);
				continue;
			}
			if (b.abort) {
				// the crew could not be fielded (caps) or its ship was lost before landing: the dice decide, as when a hand-off fails
				sim.finish(b.assault, sim.dice(b.assault));
				cleanup(lv, b);
				BATTLES.remove(b);
				continue;
			}
			int aa = alive(lv, b.att), da = alive(lv, b.def);
			if (b.voyage != null && !b.voyage.ashore()) {
				// at sea: no reserves and no clock yet; a crew shot down, beaten defenders or nobody watching any more still end it
				boolean watched = dev.elsewhere.kingdom.KingdomLod.observerNear(lv, b.pos.getX(), b.pos.getZ(), RADIUS + 32);
				if (b.voyage.phase == Longship.Phase.ROUTING) {
					if (!watched) {
						sim.finish(b.assault, sim.dice(b.assault));
						cleanup(lv, b);
						BATTLES.remove(b);
					}
					continue;
				}
				if (aa > 0 && da + b.resD > 0 && watched) continue;
				finish(lv, sim, b, aa, da, false);
				BATTLES.remove(b);
				continue;
			}
			long age = now - b.start;
			if (!b.skirmish && age % RESERVE_TICKS < 20 && age > 0) {
				if (b.resA > 0 && aa < b.initA) {
					List<UUID> n = spawnSide(lv, b.pos, b.fa, 1, 0, Squad.Formation.LINE, b.pos);
					if (!n.isEmpty()) {
						b.att.addAll(n);
						b.resA--;
					}
				}
				if (b.resD > 0 && da < b.initD) {
					List<UUID> n = spawnSide(lv, b.pos, b.fd, 1, 0, Squad.Formation.SHIELD_WALL, null);
					if (!n.isEmpty()) {
						b.def.addAll(n);
						b.resD--;
					}
				}
			}
			double aTot = aa + b.resA, dTot = da + b.resD;
			boolean retreat = false;
			if (!b.skirmish && age > 200 && b.retreat > 0 && dTot > 0 && aTot / Math.max(1, dTot) * (b.assault.atot / Math.max(1e-9, b.assault.d0)) / Math.max(1e-9, (double) (b.initA + b.resA0) / Math.max(1, b.initD + b.resD0)) < b.retreat) retreat = true;
			boolean over = aTot <= 0 || dTot <= 0 || age > TIMEOUT || retreat || !dev.elsewhere.kingdom.KingdomLod.observerNear(lv, b.pos.getX(), b.pos.getZ(), RADIUS + 32);
			if (!over) continue;
			finish(lv, sim, b, aa, da, retreat);
			BATTLES.remove(b);
		}
	}

	static void finish(ServerLevel lv, WarSim sim, Battle b, int aa, int da, boolean retreat) {
		finished++;
		double aTot = aa + b.resA, dTot = da + b.resD;
		if (b.skirmish) {
			double ia = Math.max(1, b.initA), ib = Math.max(1, b.initD);
			boolean aWins = aa / ia >= da / ib;
			int win = aWins ? b.ka : b.kb, lose = aWins ? b.kb : b.ka;
			double rw = aWins ? aa / ia : da / ib, rl = aWins ? da / ib : aa / ia;
			sim.finishSkirmish(win, lose, Math.max(1.0, rw / Math.max(rl, 0.1)), b.playerInvolved);
		} else {
			WarSim.Assault as = b.assault;
			double fracA = aTot / Math.max(1.0, b.initA + b.resA0), fracD = dTot / Math.max(1.0, b.initD + b.resD0);
			WarSim.Outcome oc = sim.classify(as, as.atot * fracA, as.d0 * fracD, retreat, false);
			as.playerInvolved = b.playerInvolved;
			for (UUID u : b.attackerKillers) {
				ServerPlayer p = lv.getServer().getPlayerList().getPlayer(u);
				if (p != null && b.assault.o >= 0) Standing.onVillageDefended(p, b.fd);
			}
			sim.finish(as, oc);
		}
		cleanup(lv, b);
		WarState.get(lv).setDirty();
	}

	static void cleanup(ServerLevel lv, Battle b) {
		Longship.cancel(lv, b.id);
		Longship.restoreBattle(lv, b.id);
		KingdomState ks = KingdomState.get(lv);
		Set<Squad> sq = new HashSet<>();
		for (List<UUID> l : List.of(b.att, b.def)) {
			for (UUID u : l) {
				Entity e = lv.getEntity(u);
				if (e instanceof KingdomSoldier s) {
					if (s.squad() != null) sq.add(s.squad());
					s.discard();
				}
			}
		}
		for (Squad q : sq) ks.disband(q, lv);
	}

	static void reportCustom(MinecraftServer srv, int id, boolean win, double fa, double fd, boolean player) {
		WarSim.Assault a = CUSTOM.remove(id);
		WarSim sim = WarLayer.sim;
		if (a == null || sim == null) return;
		a.playerInvolved = player;
		sim.finish(a, new WarSim.Outcome(win, false, false, Math.clamp(fa, 0, 1), Math.clamp(fd, 0, 1)));
		WarState.get(srv.overworld()).setDirty();
	}
}
