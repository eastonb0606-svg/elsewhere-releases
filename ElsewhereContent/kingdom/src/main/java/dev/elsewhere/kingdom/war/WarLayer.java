package dev.elsewhere.kingdom.war;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Standing;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The world-facing side of the abstract war layer: builds the {@link WarSim} from the loaded factions, keeps it saved in {@link WarState}, runs one step per in-game day
 * (overworld game time / 24000), turns sim events into {@link WarEvents}, and hands assaults and skirmishes within 96 blocks of a player to {@link WarBattles}.
 * Only kingdoms with a faction file and a realm take part. With fewer than two the layer does nothing and writes nothing (a 2.0 world with only Aldermark is untouched).
 */
public final class WarLayer {
	private WarLayer() {}

	static WarSim sim;
	static List<Identifier> ids = List.of();
	static boolean geoDirty;
	static MinecraftServer server;
	/** Tests: a hook that sees every raw sim event. */
	public static java.util.function.Consumer<WarSim.Ev> testTap;
	public static int stepsRun;

	public static void init() {
		WarBattles.hook();
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level == level.getServer().overworld()) tick(level);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> Longship.onStart(srv.overworld()));
		ServerLifecycleEvents.SERVER_STOPPING.register(Longship::onServerStopping);
		Longship.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
			Longship.pending = false;
			sim = null;
			ids = List.of();
			server = null;
			WarBattles.reset();
		});
	}

	/** Tests: drop the live model so it is rebuilt from the (cleared) registry. */
	public static void forget() {
		sim = null;
		ids = List.of();
		geoDirty = false;
		stepsRun = 0;
	}

	/** The kingdoms in the war model, in registration order. */
	public static List<Identifier> participants(MinecraftServer srv) {
		KingdomState ks = KingdomState.get(srv.overworld());
		List<Identifier> out = new ArrayList<>();
		for (Factions.Faction f : Factions.all()) {
			if (f.parent() != null || f.def() == null || !f.def().realm() || ks.isFallen(f.id) || f.id.equals(FreeCompanies.FACTION)) continue;
			out.add(f.id);
		}
		return out;
	}

	/** The live model (built on demand), or null when fewer than two kingdoms exist. */
	public static @Nullable WarSim sim(MinecraftServer srv) {
		server = srv;
		List<Identifier> now = participants(srv);
		if (now.size() < 2) return null;
		WarState st = WarState.get(srv.overworld());
		if (sim == null) {
			String js = st.takePendingSim();
			if (js != null) {
				try {
					sim = WarState.GSON.fromJson(js, WarSim.class);
				} catch (RuntimeException e) {
					KingdomContent.LOG.warn("war layer: saved model unreadable, starting fresh", e);
					sim = null;
				}
			}
			if (sim != null) ids = st.data.ids.stream().map(Identifier::tryParse).toList();
		}
		boolean same = sim != null && ids.equals(now);
		if (!same) {
			sim = remap(srv, st, now);
			ids = List.copyOf(now);
			st.data.ids = now.stream().map(Identifier::toString).toList();
			st.setDirty();
		}
		bind(srv, st);
		return sim;
	}

	static void bind(MinecraftServer srv, WarState st) {
		Tuning[] tn = new Tuning[ids.size()];
		for (int i = 0; i < tn.length; i++) tn[i] = Tuning.of(ids.get(i), Factions.def(ids.get(i)));
		if (sim.eff == null || sim.K != tn.length) sim.init(tn);
		else sim.br = tn;
		st.sim = sim;
		sim.sink = new Sink(srv);
	}

	static WarSim remap(MinecraftServer srv, WarState st, List<Identifier> now) {
		WarSim old = sim;
		List<Identifier> oldIds = ids;
		WarSim n = new WarSim(now.size(), srv.overworld().getSeed());
		int vc = st.data.villages.size();
		n.castle = new double[now.size()][];
		n.resize(vc);
		int[] map = new int[oldIds.size()];
		for (int i = 0; i < oldIds.size(); i++) map[i] = now.indexOf(oldIds.get(i));
		java.util.Random vr = new java.util.Random(srv.overworld().getSeed() ^ 0x5DEECE66DL);
		for (int v = 0; v < vc; v++) {
			WarState.VRec r = st.data.villages.get(v);
			n.vx[v] = r.x; n.vz[v] = r.z; n.contestable[v] = r.contestable;
			n.owner[v] = r.owner.isEmpty() ? -1 : now.indexOf(Identifier.tryParse(r.owner));
			n.fg[v] = 8 + vr.nextDouble() * 22;
		}
		if (old != null) {
			n.day = old.day;
			int oc = Math.min(vc, old.nv);
			System.arraycopy(old.unrest, 0, n.unrest, 0, oc);
			System.arraycopy(old.fort, 0, n.fort, 0, oc);
			System.arraycopy(old.fg, 0, n.fg, 0, oc);
			System.arraycopy(old.lock, 0, n.lock, 0, oc);
			System.arraycopy(old.since, 0, n.since, 0, oc);
			for (int v = 0; v < oc; v++) {
				n.owner[v] = old.owner[v] >= 0 && old.owner[v] < map.length ? map[old.owner[v]] : -1;
				n.prev[v] = old.prev[v] >= 0 && old.prev[v] < map.length ? map[old.prev[v]] : -1;
			}
			for (int a = 0; a < map.length; a++) {
				if (map[a] < 0) continue;
				int na = map[a];
				n.H[na] = old.H[a]; n.vet[na] = old.vet[a]; n.money[na] = old.money[a]; n.Hm[na] = old.Hm[a];
				n.lastFree[na] = old.lastFree[a]; n.declLock[na] = old.declLock[a]; n.castle[na] = old.castle[a];
				for (int b = 0; b < map.length; b++) {
					if (map[b] < 0) continue;
					int nb = map[b];
					n.t[na][nb] = old.t[a][b]; n.war[na][nb] = old.war[a][b]; n.truce[na][nb] = old.truce[a][b];
					n.ally[na][nb] = old.ally[a][b]; n.warflip[na][nb] = old.warflip[a][b];
				}
			}
			for (WarSim.QItem q : old.queue) {
				if (q.k < 0 || q.k >= map.length || map[q.k] < 0) continue;
				if (q.kind.equals("declare") && (q.a >= map.length || map[q.a] < 0)) continue;
				q.k = map[q.k];
				if (q.kind.equals("declare")) q.a = map[q.a];
				n.queue.add(q);
			}
			for (WarSim.Kill k : old.kills) if (k.k < map.length && map[k.k] >= 0) {
				k.k = map[k.k];
				n.kills.add(k);
			}
		}
		for (int k = 0; k < now.size(); k++) {
			boolean existed = false;
			for (int m : map) if (m == k) existed = true;
			if (!existed) {
				n.money[k] = 60;
				n.lastFree[k] = -99;
				for (int j = 0; j < now.size(); j++) {
					n.ally[k][j] = -99;
					n.ally[j][k] = -99;
				}
			}
		}
		for (int k = 0; k < now.size(); k++) {
			double[] c = st.data.castles.get(now.get(k).toString());
			if (c != null) n.castle[k] = c;
		}
		Tuning[] tn = new Tuning[now.size()];
		for (int i = 0; i < tn.length; i++) tn[i] = Tuning.of(now.get(i), Factions.def(now.get(i)));
		n.br = tn;
		for (int k = 0; k < now.size(); k++) {
			boolean existed = false;
			for (int m : map) if (m == k) existed = true;
			if (!existed) {
				int cnt = 0;
				for (int v = 0; v < vc; v++) if (n.owner[v] == k) cnt++;
				n.H[k] = tn[k].size * (WarSim.CAP0 + WarSim.CAPV * Math.max(cnt, 6)) * 0.9;
			}
		}
		return n;
	}

	// ---- village registry ------------------------------------------------------------------------------------------

	static int villageIndex(WarState st, String key) {
		for (int i = 0; i < st.data.villages.size(); i++) if (st.data.villages.get(i).key.equals(key)) return i;
		return -1;
	}

	// ---- daily tick -------------------------------------------------------------------------------------------------

	static void tick(ServerLevel overworld) {
		long t = overworld.getGameTime();
		WarBattles.tick(overworld, t);
		if (Longship.sailing()) Longship.tickVoyages(overworld);
		if (Longship.pending && t % 100 == 11) Longship.expire(overworld);
		if (t % 100 != 7) return;
		MinecraftServer srv = overworld.getServer();
		WarState st = WarState.get(overworld);
		long day = t / Standing.DAY;
		if (st.data.lastDay < 0) {
			if (participants(srv).size() < 2) return;
			st.data.lastDay = day;
			st.setDirty();
			return;
		}
		if (day <= st.data.lastDay) return;
		int steps = (int) Math.min(5, day - st.data.lastDay);
		st.data.lastDay = day;
		st.setDirty();
		Standing.driftAll(overworld);
		FreeCompanies.dailyTick(overworld, steps);
		WarSim s = sim(srv);
		if (s == null) return;
		for (int i = 0; i < steps; i++) step(overworld);
	}

	/** Runs one war day now (also the test and {@code /kingdom war step} entry point). */
	public static void step(ServerLevel overworld) {
		WarSim s = sim(overworld.getServer());
		if (s == null) return;
		syncGeometry(s);
		s.step();
		stepsRun++;
		WarState.get(overworld).setDirty();
		FreeCompanies.syncFromSim(overworld, s, ids);
	}

	/** Rebuilds the model's distance tables when villages or castles were added since the last step. */
	static void syncGeometry(WarSim s) {
		if (geoDirty) {
			s.rebuildGeometry();
			geoDirty = false;
		}
	}

	// ---- events -------------------------------------------------------------------------------------------------------

	static final class Sink implements WarSim.Sink {
		final MinecraftServer srv;

		Sink(MinecraftServer srv) {
			this.srv = srv;
		}

		@Override
		public void event(WarSim.Ev e) {
			if (testTap != null) testTap.accept(e);
			WarState st = WarState.get(srv.overworld());
			ServerLevel lv = srv.overworld();
			Identifier f = e.k() >= 0 && e.k() < ids.size() ? ids.get(e.k()) : null;
			Identifier o = e.o() >= 0 && e.o() < ids.size() ? ids.get(e.o()) : null;
			String vk = e.v() >= 0 && e.v() < st.data.villages.size() ? st.data.villages.get(e.v()).key : null;
			BlockPos bp = e.v() >= 0 && e.v() < sim.nv ? BlockPos.containing(sim.vx[e.v()], 0, sim.vz[e.v()]) : null;
			if (f == null) return;
			WarEvents.Type ty = switch (e.type()) {
				case DECLARE -> WarEvents.Type.DECLARED;
				case WAR_END -> WarEvents.Type.TRUCE;
				case SKIRMISH -> WarEvents.Type.SKIRMISH;
				case ASSAULT_WON -> WarEvents.Type.ASSAULT_WON;
				case ASSAULT_FAILED -> WarEvents.Type.ASSAULT_FAILED;
				case ASSAULT_BROKEN_OFF -> WarEvents.Type.BROKE_OFF;
				case GARRISON_WITHDREW -> WarEvents.Type.GARRISON_WITHDREW;
				case CAPTURE -> WarEvents.Type.VILLAGE_CAPTURED;
				case REVOLT -> WarEvents.Type.VILLAGE_REVOLTED;
				case COALITION -> WarEvents.Type.COALITION;
				case ALLIANCE -> WarEvents.Type.ALLIANCE;
				case BROKER -> WarEvents.Type.BROKERED;
				case HIRE -> WarEvents.Type.HIRED;
				case DESERT -> WarEvents.Type.DESERTED;
				case BRAIN_KILL -> WarEvents.Type.BRAIN_KILLED;
				case SEAT_REFILLED -> WarEvents.Type.BRAIN_KILLED;
			};
			String text = "day " + e.day() + ": " + ty + " " + f.getPath() + (o != null ? " / " + o.getPath() : "") + (vk != null ? " @" + vk : "") + (e.odds() > 0 ? String.format(" (%.2f)", e.odds()) : "");
			st.data.log.add(text);
			while (st.data.log.size() > 60) st.data.log.remove(0);
			st.setDirty();
			WarEvents.WAR_EVENT.invoker().onWarEvent(lv, new WarEvents.WarEvent(ty, e.day(), f, o, vk, bp, e.odds(), text));
			if (e.type() == WarSim.EvType.CAPTURE || e.type() == WarSim.EvType.REVOLT) {
				if (e.v() >= 0 && e.v() < st.data.villages.size()) st.data.villages.get(e.v()).owner = (e.type() == WarSim.EvType.CAPTURE ? f : o) == null ? "" : (e.type() == WarSim.EvType.CAPTURE ? f : o).toString();
				Identifier newOwner = e.type() == WarSim.EvType.CAPTURE ? f : o;
				Identifier oldOwner = e.type() == WarSim.EvType.CAPTURE ? o : f;
				if (vk != null) WarEvents.VILLAGE_CHANGED.invoker().onVillageChanged(lv, vk, bp, newOwner, oldOwner, e.type() == WarSim.EvType.CAPTURE ? "conquest" : "revolt");
			}
		}

		@Override
		public boolean handoff(WarSim.Assault a) {
			return WarBattles.tryAssault(srv, sim, ids, a);
		}

		@Override
		public boolean skirmish(int a, int b, double pa, double pb) {
			return WarBattles.trySkirmish(srv, sim, ids, a, b, pa, pb);
		}
	}
}
