package dev.elsewhere.kingdom.war;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The abstract war model of WAR-AI.md, ported 1:1 from {@code _tools/warsim.py} and generalised from four fixed kingdoms to any number
 * of kingdoms with their own {@link Tuning}. One {@link #step()} is one in-game day. It knows nothing about Minecraft: positions are plain
 * x/z doubles, kingdoms are indices. {@link WarLayer} owns the world-facing side (ids, villages, saved state, events, the hand-off to real soldiers).
 * All state is plain public data so it serialises with Gson; derived caches are {@code transient}.
 */
public final class WarSim {
	// world constants (WAR-AI section 2)
	public static final double CAP0 = 100, CAPV = 7, REGEN = 0.06, UPKEEP = 0.04, G_SHARE = 3.0, R_SHARE = 0.12, OPP_MIN = 0.4, OPP_MAX = 2.2;
	public static final double FAIL_LOSS = 0.53, SKIRMISH_P = 0.34, PATROL = 0.06, ALLY_SHARE = 0.6, WALL = 1.25, DECL_BASE = 0.25;
	public static final double HOP = 1000, SUPPLY_REACH = 1100, MARCH = 1500, INFLUENCE = 1100, COLD = 4000;
	public static final int LOCK_DAYS = 3, WORLD_FLIPS_PER_DAY = 2;
	public static final double COALITION = 0.40, BROKER_SHARE = 0.276;
	public static final int SEAT_DAYS = 7, DEGRADE_DAYS = 30, PROB_DAYS = 90;

	public enum EvType { DECLARE, WAR_END, SKIRMISH, ASSAULT_WON, ASSAULT_FAILED, ASSAULT_BROKEN_OFF, GARRISON_WITHDREW, CAPTURE, REVOLT, COALITION, ALLIANCE, HIRE, DESERT, BROKER, BRAIN_KILL, SEAT_REFILLED }

	/** One thing that happened today. {@code k} is the actor (kingdom index), {@code o} the other party (-1 = none/free), {@code v} the village (-1 = none). */
	public record Ev(EvType type, int day, int k, int o, int v, double odds, String text) {}

	/** One assault in flight: attacker {@code k} against village {@code v} (owner {@code o}, -1 = free) with committed strength {@code a0}. */
	public static final class Assault {
		public int k, v, o;
		public double a0, d0, atot;
		public String kind;
		public List<int[]> allyIdx = new ArrayList<>();
		public List<Double> allyPower = new ArrayList<>();
		public boolean playerInvolved;
		public int day;
	}

	/** How an assault ended: {@code fa}/{@code fd} are the shares of the attacking and defending strength lost. */
	public record Outcome(boolean win, boolean retreat, boolean defRetreat, double fa, double fd) {}

	/** What the layer provides: events out, and the chance to turn an assault into a real fight near a player. */
	public interface Sink {
		void event(Ev e);

		/** True when the layer takes the assault over (real soldiers); it must later call {@link WarSim#finish}. */
		default boolean handoff(Assault a) {
			return false;
		}

		/** True when the layer takes a patrol clash between kingdoms {@code a} and {@code b} over (a real skirmish); it must later call {@link WarSim#finishSkirmish}. */
		default boolean skirmish(int a, int b, double powerA, double powerB) {
			return false;
		}
	}

	public static final class QItem {
		public int exec, k, a;
		public String kind, akind;
		public double frac;
	}

	public static final class Kill {
		public int k;
		public String role;
		public int day;
		/** RIVALS-DECISIONS 10: Azmar's brain seats, Hrafnholt champions and Mirewick votes stay empty until the court fires risen/heir (Azmar never). */
		public boolean permanent;
		/** 0 none, 0.5 restored at half strength (Hrafnholt risen). */
		public double restored;
	}

	// ---- saved state ----
	public int K, nv, day;
	public long seed;
	public double[] H, vet, money, Hm;
	public int[] lastFree;
	public double[][] castle;
	public double[] vx, vz, unrest, fort, fg;
	public int[] owner, prev, lock, since;
	public boolean[] contestable, pending;
	public double[][] t;
	public int[][] war, truce, ally;
	public boolean[][] warflip;
	public List<QItem> queue = new ArrayList<>();
	public List<Kill> kills = new ArrayList<>();
	public int[] declLock;
	public int coalPrev = -1, coalitionTarget = -1;

	// ---- derived ----
	public transient Tuning[] br, eff;
	transient double[] scent, fortDecay;
	transient boolean[] supplyCut, isolated;
	transient double[][] dV, dC;
	transient int[][] nHop, nSup, nMarch, nInf, cHop, cSup, cMarch, cInf;
	transient boolean[][] cold, adj;
	transient boolean[] border, dirty;
	transient int[][] own, reachL;
	transient Set<Integer>[] conn;
	public transient int[] n;
	transient int free, flipsToday;
	transient Random rng = new Random(1);
	public transient Sink sink;
	public transient String[] names = new String[0];
	public transient int[] statAtt, statWin;

	public WarSim() {}

	@SuppressWarnings("unchecked")
	public WarSim(int kingdoms, long seed) {
		this.K = kingdoms;
		this.seed = seed;
		H = new double[K]; vet = new double[K]; money = new double[K]; Hm = new double[K];
		lastFree = new int[K];
		java.util.Arrays.fill(lastFree, -99);
		declLock = new int[K];
		castle = new double[K][];
		t = new double[K][K];
		war = new int[K][K]; truce = new int[K][K]; ally = new int[K][K];
		for (int[] r : ally) java.util.Arrays.fill(r, -99);
		warflip = new boolean[K][K];
		resize(0);
	}

	/** Allocates the per-village arrays for {@code count} villages (existing data is kept). */
	public void resize(int count) {
		vx = grow(vx, count); vz = grow(vz, count); unrest = grow(unrest, count); fort = grow(fort, count); fg = grow(fg, count);
		owner = growI(owner, count, -1); prev = growI(prev, count, -1); lock = growI(lock, count, 0); since = growI(since, count, 0);
		contestable = growB(contestable, count, true); pending = growB(pending, count, false);
		nv = count;
	}

	private static double[] grow(double[] a, int n) {
		double[] r = new double[n];
		if (a != null) System.arraycopy(a, 0, r, 0, Math.min(a.length, n));
		return r;
	}

	private static int[] growI(int[] a, int n, int fill) {
		int[] r = new int[n];
		java.util.Arrays.fill(r, fill);
		if (a != null) System.arraycopy(a, 0, r, 0, Math.min(a.length, n));
		return r;
	}

	private static boolean[] growB(boolean[] a, int n, boolean fill) {
		boolean[] r = new boolean[n];
		java.util.Arrays.fill(r, fill);
		if (a != null) System.arraycopy(a, 0, r, 0, Math.min(a.length, n));
		return r;
	}

	/** Tuning must be set (and geometry rebuilt) after construction or loading. */
	@SuppressWarnings("unchecked")
	public void init(Tuning[] tunings) {
		this.br = tunings;
		this.eff = new Tuning[K];
		for (int k = 0; k < K; k++) eff[k] = tunings[k].clone();
		scent = new double[K]; fortDecay = new double[K];
		supplyCut = new boolean[K]; isolated = new boolean[K];
		cold = new boolean[K][K]; adj = new boolean[K][K];
		dirty = new boolean[K];
		java.util.Arrays.fill(dirty, true);
		own = new int[K][0]; reachL = new int[K][0];
		conn = new Set[K];
		for (int k = 0; k < K; k++) conn[k] = new HashSet<>();
		n = new int[K];
		statAtt = new int[K]; statWin = new int[K];
		rebuildGeometry();
	}

	static double dist(double ax, double az, double bx, double bz) {
		return Math.hypot(ax - bx, az - bz);
	}

	/** Recomputes distances and neighbour lists (call after villages or castles change). */
	public void rebuildGeometry() {
		dV = new double[nv][nv];
		dC = new double[K][nv];
		nHop = new int[nv][]; nSup = new int[nv][]; nMarch = new int[nv][]; nInf = new int[nv][];
		for (int i = 0; i < nv; i++) {
			for (int j = 0; j < nv; j++) dV[i][j] = dist(vx[i], vz[i], vx[j], vz[j]);
			nHop[i] = within(dV[i], i, HOP); nSup[i] = within(dV[i], i, SUPPLY_REACH); nMarch[i] = within(dV[i], i, MARCH); nInf[i] = within(dV[i], i, INFLUENCE);
		}
		cHop = new int[K][]; cSup = new int[K][]; cMarch = new int[K][]; cInf = new int[K][];
		for (int k = 0; k < K; k++) {
			for (int j = 0; j < nv; j++) dC[k][j] = castle[k] == null ? Double.MAX_VALUE : dist(castle[k][0], castle[k][1], vx[j], vz[j]);
			cHop[k] = within(dC[k], -1, HOP); cSup[k] = within(dC[k], -1, SUPPLY_REACH); cMarch[k] = within(dC[k], -1, MARCH); cInf[k] = within(dC[k], -1, INFLUENCE);
		}
		for (int i = 0; i < K; i++) for (int j = 0; j < K; j++) {
			cold[i][j] = castle[i] != null && castle[j] != null && dist(castle[i][0], castle[i][1], castle[j][0], castle[j][1]) > COLD;
		}
		java.util.Arrays.fill(dirty, true);
		border = new boolean[nv];
		recount();
	}

	private static int[] within(double[] d, int self, double r) {
		int c = 0;
		for (int j = 0; j < d.length; j++) if (j != self && d[j] <= r) c++;
		int[] o = new int[c];
		c = 0;
		for (int j = 0; j < d.length; j++) if (j != self && d[j] <= r) o[c++] = j;
		return o;
	}

	// ------------------------------------------------------------------------------------------------ helpers
	void ev(EvType type, int k, int o, int v, double odds, String text) {
		if (sink != null) sink.event(new Ev(type, day, k, o, v, odds, text));
	}

	public double cap(int k, int count) {
		return br[k].size * (CAP0 + CAPV * count);
	}

	public double q(int k) {
		return br[k].hp * br[k].dmg;
	}

	public double power(int k) {
		return H[k] * q(k) * (1 + vet[k]) + Hm[k];
	}

	public void recount() {
		java.util.Arrays.fill(n, 0);
		free = 0;
		for (int v = 0; v < nv; v++) {
			if (!contestable[v]) continue;
			if (owner[v] >= 0) n[owner[v]]++;
			else free++;
		}
	}

	int totalContestable() {
		int c = 0;
		for (int v = 0; v < nv; v++) if (contestable[v]) c++;
		return Math.max(1, c);
	}

	void refresh(int k) {
		if (!dirty[k]) return;
		dirty[k] = false;
		List<Integer> ownL = new ArrayList<>();
		boolean[] isOwn = new boolean[nv];
		for (int v = 0; v < nv; v++) if (owner[v] == k) {
			ownL.add(v);
			isOwn[v] = true;
		}
		own[k] = ownL.stream().mapToInt(Integer::intValue).toArray();
		Set<Integer> cn = new HashSet<>();
		List<Integer> frontier = new ArrayList<>();
		for (int v : cHop[k]) if (isOwn[v]) {
			cn.add(v);
			frontier.add(v);
		}
		while (!frontier.isEmpty()) {
			List<Integer> nxt = new ArrayList<>();
			for (int v : frontier) for (int u : nHop[v]) if (isOwn[u] && cn.add(u)) nxt.add(u);
			frontier = nxt;
		}
		conn[k] = cn;
		Set<Integer> reach = new HashSet<>();
		if (br[k].supply) {
			for (int v : cSup[k]) reach.add(v);
			for (int v : cn) for (int u : nSup[v]) reach.add(u);
		} else {
			for (int v : cMarch[k]) reach.add(v);
			for (int v : own[k]) for (int u : nMarch[v]) reach.add(u);
		}
		reach.removeIf(v -> owner[v] == k);
		int[] ra = reach.stream().mapToInt(Integer::intValue).sorted().toArray();
		reachL[k] = ra;
	}

	void refreshGeo() {
		for (boolean[] r : adj) java.util.Arrays.fill(r, false);
		for (int v = 0; v < nv; v++) {
			int o = owner[v];
			boolean bord = false;
			if (o >= 0) {
				for (int u : nInf[v]) {
					int p = owner[u];
					if (p >= 0 && p != o) {
						adj[o][p] = true;
						bord = true;
					}
				}
				for (int k = 0; k < K; k++) if (k != o && contains(cInf[k], v)) {
					adj[o][k] = true;
					bord = true;
				}
			}
			border[v] = bord;
		}
		for (int k = 0; k < K; k++) for (int v : cInf[k]) {
			int o = owner[v];
			if (o >= 0 && o != k) adj[k][o] = true;
		}
	}

	private static boolean contains(int[] a, int x) {
		for (int y : a) if (y == x) return true;
		return false;
	}

	int warsActive(int k) {
		int c = 0;
		for (int e = 0; e < K; e++) if (war[k][e] > 0) c++;
		return c;
	}

	public double defence(int v) {
		int o = owner[v];
		if (o < 0) return fg[v];
		refresh(o);
		double d = power(o) * (G_SHARE / (n[o] + 2) + R_SHARE * eff[o].react) * WALL * (1 + fort[v]);
		if (!conn[o].contains(v) || supplyCut[o]) d *= 0.75;
		int w = warsActive(o);
		if (w > 1) d /= (1 + 0.3 * (w - 1));
		return d;
	}

	public boolean isAllied(int a, int b) {
		return ally[a][b] >= day;
	}

	List<Integer> alliesFor(int k, int o) {
		List<Integer> out = new ArrayList<>();
		if (o < 0) return out;
		for (int j = 0; j < K; j++) {
			if (j == k || j == o || !isAllied(k, j)) continue;
			if (war[j][o] > 0 || coalitionTarget == o) out.add(j);
		}
		return out;
	}

	int randInt(int lo, int hi) {
		return lo + rng.nextInt(hi - lo + 1);
	}

	// ------------------------------------------------------------------------------------------------ brain
	static String category(String role) {
		String r = role.toLowerCase(java.util.Locale.ROOT);
		if (r.contains("sultana")) return "sultana";
		if (r.contains("wazir") || r.contains("vizier")) return "wazir";
		if (r.contains("falcon")) return "falconer";
		if (r.contains("jarl")) return "jarl";
		if (r.contains("hersir")) return "hersir";
		if (r.contains("hawk")) return "hawk";
		if (r.contains("miser") || r.contains("almoner")) return "miser";
		if (r.contains("dove")) return "dove";
		if (r.contains("king")) return "king";
		return "";
	}

	/** A brain boss died on {@code day}: starts the section 4 timers and applies the one-off effects. */
	public void brainKill(int k, String role) {
		String cat = category(role);
		if (cat.isEmpty()) return;
		for (Kill x : kills) if (x.k == k && category(x.role).equals(cat) && (x.permanent || day - x.day < SEAT_DAYS)) return; // already dead
		Kill kl = new Kill();
		kl.k = k; kl.role = cat; kl.day = day;
		kl.permanent = !cat.equals("king");
		kills.add(kl);
		switch (cat) {
			case "sultana" -> vet[k] *= 0.10;
			case "wazir" -> {
				for (int v = 0; v < nv; v++) if (owner[v] == k) unrest[v] = Math.max(unrest[v], 0.3);
			}
			case "king" -> declLock[k] = Math.max(declLock[k], day + SEAT_DAYS);
			default -> { }
		}
		ev(EvType.BRAIN_KILL, k, -1, -1, 0, cat);
	}

	/** 0 = not affected, 1 = seat empty, 2 = degraded, 3 = relearning (with w in {@link #ramp}) . */
	double ramp;

	Kill cur;

	int phase(int d, int killDay, boolean ramped) {
		if (cur != null && cur.permanent) return 1;
		int dt = d - killDay;
		if (dt < SEAT_DAYS) return 1;
		if (dt < SEAT_DAYS + DEGRADE_DAYS) return 2;
		if (ramped && dt < SEAT_DAYS + DEGRADE_DAYS + PROB_DAYS) {
			ramp = 1.0 - (dt - SEAT_DAYS - DEGRADE_DAYS) / (double) PROB_DAYS;
			return 3;
		}
		return 0;
	}

	void updateBrain() {
		for (int k = 0; k < K; k++) {
			eff[k] = br[k].clone();
			scent[k] = 0; fortDecay[k] = 0; supplyCut[k] = false; isolated[k] = false;
		}
		kills.removeIf(x -> !x.permanent && day - x.day > SEAT_DAYS + DEGRADE_DAYS + PROB_DAYS + 1);
		for (Kill x : kills) {
			Tuning z = eff[x.k], base = br[x.k];
			int k = x.k;
			cur = x;
			switch (x.role) {
				case "sultana" -> {
					int ph = phase(day, x.day, true);
					if (ph == 1) {
						z.minOdds = 0.9; z.retreat = 0.2; z.defRetreat = 0; z.fortRate = 0; z.scout = 1; z.commit = 0.5; z.leaderCap = 1.0; z.react = 0.1;
						fortDecay[k] = 0.5;
					} else if (ph == 2) {
						z.minOdds = 1.2; z.retreat = 0.30; z.defRetreat = 0.15; z.fortRate = 0.004; z.scout = 2; z.commit = 0.45; z.leaderCap = 1.0; z.react = 0.25;
						fortDecay[k] = 0.04;
					} else if (ph == 3) {
						double w = ramp;
						z.minOdds = lerp(base.minOdds, 1.2, w); z.retreat = lerp(base.retreat, 0.30, w); z.react = lerp(1.0, 0.25, w);
						z.commit = lerp(base.commit, 0.45, w); z.fortRate = lerp(base.fortRate, 0.004, w);
						fortDecay[k] = 0.04 * w;
					}
				}
				case "wazir" -> {
					int ph = phase(day, x.day, true);
					if (ph == 1) {
						z.timing = false; z.broker = false; z.delayMin = 2; z.delayMax = 3; z.incVillage = z.incVillage * 0.6; z.integ = 12;
						supplyCut[k] = true; isolated[k] = true; scent[k] = 40.0;
					} else if (ph == 2) {
						z.timing = false; z.broker = false; z.delayMin = 1; z.delayMax = 2; z.incVillage = z.incVillage * 0.8; z.integ = 8;
						isolated[k] = true; scent[k] = 20.0;
					} else if (ph == 3) {
						double w = ramp;
						z.timing = base.timing && w < 0.5; z.broker = base.broker && w < 0.5;
						scent[k] = 20.0 * w; isolated[k] = w > 0.5;
					}
				}
				case "falconer" -> {
					int ph = phase(day, x.day, true);
					if (ph == 1) z.noise = 0.80;
					else if (ph == 2) z.noise = 0.50;
					else if (ph == 3) z.noise = lerp(base.noise, 0.50, ramp);
				}
				case "jarl" -> {
					int ph = phase(day, x.day, false);
					if (ph == 1 || ph == 2) z.warFreq = lerp(Math.min(z.warFreq, 1.0), base.warFreq, x.restored);
				}
				case "hersir" -> {
					int ph = phase(day, x.day, false);
					if (ph == 1 || ph == 2) {
						z.pursuit = lerp(0, base.pursuit, x.restored);
						z.retreat = lerp(Math.max(z.retreat, 0.55), base.retreat, x.restored);
					}
				}
				case "hawk" -> {
					if (phase(day, x.day, false) == 1) z.warFreq = Math.max(0.1, z.warFreq - 0.1);
				}
				case "dove" -> {
					if (phase(day, x.day, false) == 1) z.warFreq += 0.1;
				}
				case "miser" -> {
					if (phase(day, x.day, false) == 1) {
						z.miser = Math.max(0, z.miser - 0.05);
						z.hireReserve *= 0.8;
					}
				}
				default -> { }
			}
		}
	}

	/** The court fired a succession event for a seat (RIVALS-DECISIONS 10). risen: restore Hrafnholt's seat at half strength; heir: the Mirewick vote returns; others change nothing here. */
	public boolean succession(int k, String role, String kind) {
		String cat = category(role);
		for (java.util.Iterator<Kill> it = kills.iterator(); it.hasNext();) {
			Kill x = it.next();
			if (x.k != k || !x.role.equals(cat)) continue;
			if (kind.equals("risen") && (cat.equals("jarl") || cat.equals("hersir")) && x.restored == 0) {
				x.restored = 0.5;
				return true;
			}
			if (kind.equals("heir") && (cat.equals("hawk") || cat.equals("dove") || cat.equals("miser"))) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	/** Tests: recompute the effective brain for the current day. */
	public void updateBrainForTest() {
		updateBrain();
	}

	public boolean isolatedForTest(int k) {
		return isolated[k];
	}

	static double lerp(double ok, double bad, double w) {
		return ok + (bad - ok) * w;
	}

	// ------------------------------------------------------------------------------------------------ economy
	void economy() {
		for (int k = 0; k < K; k++) {
			Tuning b = eff[k];
			refresh(k);
			int cnt = own[k].length;
			double inc = b.incBase + b.incVillage * cnt;
			inc *= Math.max(0.1, 1.0 + rng.nextGaussian() * b.incNoise);
			money[k] += inc - UPKEEP * H[k];
			if (br[k].fortRate > 0 && br[k].fortBorderOnly) {
				int bc = 0;
				for (int v : own[k]) if (border[v]) bc++;
				money[k] -= 0.4 * bc;
			}
			double cap = cap(k, cnt);
			if (H[k] < cap && money[k] > 0) {
				double r = Math.min(Math.min(cap - H[k], REGEN * cap), money[k] / b.headCost);
				vet[k] *= H[k] / (H[k] + r);
				H[k] += r;
				money[k] -= r * b.headCost;
			} else if (H[k] > cap) H[k] -= 0.05 * (H[k] - cap);
			if (br[k].vetDecay != 0) vet[k] *= 1 - br[k].vetDecay;
			vet[k] = Math.min(vet[k], br[k].vetCap);
			money[k] = Math.min(money[k], 4000.0);
		}
		for (int k = 0; k < K; k++) {
			Tuning m = eff[k];
			if (br[k].hireMax <= 0) continue;
			boolean atWar = warsActive(k) > 0 || day - lastFree[k] <= 3;
			boolean rich = money[k] > m.hireRetain * m.hireReserve;
			if ((atWar || rich) && money[k] > m.hireReserve && Hm[k] < m.hireMax) {
				double want = Math.min(m.hireMax, (money[k] - m.hireReserve) / (m.hireCost + m.hirePay * 6));
				if (Hm[k] < want) {
					double add = Math.min(want - Hm[k], 8.0);
					Hm[k] += add;
					money[k] -= add * m.hireCost;
					if (Hm[k] - add < 1 && Hm[k] >= 8) ev(EvType.HIRE, k, -1, -1, Hm[k], "hire");
				}
			} else if (!atWar) Hm[k] *= 0.97;
			money[k] -= m.hirePay * Hm[k];
			if (money[k] < 0 && Hm[k] > 0.5) {
				double gone = Hm[k] * m.desert;
				Hm[k] -= gone;
				int tgt = -1;
				for (int e = 0; e < K; e++) if (e != k && war[k][e] > 0) tgt = e;
				if (tgt >= 0 && rng.nextDouble() < 0.4) {
					H[tgt] += gone * 0.5;
					ev(EvType.DESERT, k, tgt, -1, gone, "to_enemy");
				} else ev(EvType.DESERT, k, -1, -1, gone, "walked");
				money[k] = 0.0;
			}
			if (money[k] < 0) money[k] = 0.0;
		}
	}

	// ------------------------------------------------------------------------------------------------ villages
	void villageUpkeep() {
		int flips = 0;
		for (int v = 0; v < nv; v++) {
			int o = owner[v];
			if (o < 0 || !contestable[v]) continue;
			Tuning b = br[o];
			if (unrest[v] > 0) {
				unrest[v] = Math.max(0.0, unrest[v] - 1.0 / eff[o].integ);
				double gar = H[o] * G_SHARE / (n[o] + 2);
				double thin = Math.min(2.0, Math.max(0.5, 14.0 / Math.max(gar, 1.0)));
				double p = unrest[v] * 0.10 * thin / (1 + fort[v]);
				if (flips < 2 && rng.nextDouble() < p) {
					revolt(v);
					flips++;
					continue;
				}
			}
			if (fortDecay[o] != 0) fort[v] = Math.max(0.0, fort[v] - fortDecay[o]);
			double fr = eff[o].fortRate;
			if (fr > 0 && (border[v] || !b.fortBorderOnly)) fort[v] = Math.min(b.fortCap, fort[v] + fr);
		}
	}

	void revolt(int v) {
		int o = owner[v], back = prev[v];
		int nw = (back >= 0 && back != o && rng.nextDouble() < 0.5 && !isAllied(o, back)) ? back : -1;
		changeOwner(v, nw, true);
		ev(EvType.REVOLT, o, nw, v, 0, "revolt");
	}

	/** Changes a village's owner with the model's side effects (lock, fort reset, conquest unrest). */
	public void changeOwner(int v, int nw, boolean rebel) {
		int old = owner[v];
		owner[v] = nw;
		prev[v] = old;
		lock[v] = day + LOCK_DAYS;
		since[v] = day;
		fort[v] = 0.0;
		if (old >= 0) dirty[old] = true;
		if (nw >= 0) dirty[nw] = true;
		if (nw < 0) {
			fg[v] = 8 + rng.nextDouble() * 22;
			unrest[v] = 0.0;
		} else {
			refresh(nw);
			boolean sup = conn[nw].contains(v) || contains(cHop[nw], v);
			if (!sup) for (int u : nHop[v]) if (owner[u] == nw) {
				sup = true;
				break;
			}
			unrest[v] = br[nw].unrest + (sup ? 0.0 : 0.35);
		}
		recount();
		if (!rebel) flipsToday++;
		if (!rebel) ev(EvType.CAPTURE, nw, old, v, 0, "capture");
	}

	// ------------------------------------------------------------------------------------------------ diplomacy
	int leader() {
		int L = 0;
		for (int i = 1; i < K; i++) if (n[i] > n[L]) L = i;
		return L;
	}

	void diplomacy() {
		int d = day;
		refreshGeo();
		for (int a = 0; a < K; a++) for (int b = a + 1; b < K; b++) {
			if (cold[a][b]) continue;
			double tt = t[a][b];
			if (truce[a][b] > 0) tt -= 2;
			else if (war[a][b] > 0) { /* unchanged */ }
			else if (adj[a][b] || adj[b][a]) {
				tt += 3;
				if (rng.nextDouble() < 0.2) tt += 5;
			} else tt -= 1;
			double sc = Math.max(scent[a], scent[b]);
			if (sc != 0 && war[a][b] == 0 && truce[a][b] == 0 && (adj[a][b] || adj[b][a])) tt += sc;
			t[a][b] = t[b][a] = Math.min(100.0, Math.max(0.0, tt));
		}
		int L = leader();
		double share = n[L] / (double) totalContestable();
		coalitionTarget = -1;
		for (int a = 0; a < K; a++) for (int b = a + 1; b < K; b++) {
			boolean common = false;
			for (int c = 0; c < K; c++) if (c != a && c != b && war[a][c] > 0 && war[b][c] > 0) common = true;
			if (common && war[a][b] == 0 && !(isolated[a] || isolated[b])) ally[a][b] = ally[b][a] = d + 3;
		}
		int broker = -1;
		for (int k = 0; k < K; k++) if (eff[k].broker && k != L) {
			broker = k;
			break;
		}
		if (share >= COALITION) {
			coalitionTarget = L;
			for (int a = 0; a < K; a++) for (int b = a + 1; b < K; b++) if (a != L && b != L) setAlly(a, b);
		} else if (share >= BROKER_SHARE && broker >= 0) {
			coalitionTarget = L;
			for (int x = 0; x < K; x++) if (x != L && x != broker) setAlly(broker, x);
			if (coalPrev != L) ev(EvType.BROKER, broker, L, -1, n[L], "broker");
		}
		if (coalitionTarget >= 0 && coalitionTarget != coalPrev && share >= COALITION) ev(EvType.COALITION, L, -1, -1, n[L], "coalition");
		coalPrev = coalitionTarget;
		if (coalitionTarget >= 0) for (int x = 0; x < K; x++) {
			if (x != L && !cold[x][L] && truce[x][L] == 0 && war[x][L] == 0) t[x][L] = t[L][x] = Math.max(t[x][L], 70.0);
		}
		for (int a = 0; a < K; a++) for (int b = a + 1; b < K; b++) {
			if ((adj[a][b] || adj[b][a]) && t[a][b] >= 40 && truce[a][b] == 0 && !isAllied(a, b) && rng.nextDouble() < SKIRMISH_P) skirmish(a, b);
		}
		List<QItem> rest = new ArrayList<>();
		for (QItem it : new ArrayList<>(queue)) {
			if (it.exec > d) {
				rest.add(it);
				continue;
			}
			if (it.kind.equals("declare")) startWar(it.k, it.a);
			else runAttack(it.k, it.a, it.frac, it.akind);
		}
		queue = rest;
		for (int k = 0; k < K; k++) {
			Tuning b = eff[k];
			if (declLock[k] > d) continue;
			for (int e = 0; e < K; e++) {
				if (e == k || t[k][e] < 70 || war[k][e] > 0 || truce[k][e] > 0 || isAllied(k, e)) continue;
				boolean queued = false;
				for (QItem it : queue) if (it.kind.equals("declare") && it.k == k && it.a == e) queued = true;
				if (queued) continue;
				double p;
				if (b.timing) {
					if (n[k] / (double) totalContestable() >= b.leaderCap) continue;
					if (!timingOk(k, e)) continue;
					p = b.timingP;
				} else {
					p = DECL_BASE * b.warFreq * opportunity(k, e);
					if (coalitionTarget == e) p = Math.max(p, 0.3);
				}
				if (rng.nextDouble() < p) {
					int dl = b.delayMax > 0 ? randInt(b.delayMin, b.delayMax) : 0;
					if (br[k].delayMax > 0 && dl == 0) dl = 1;
					if (dl == 0) startWar(k, e);
					else {
						QItem it = new QItem();
						it.exec = d + dl; it.kind = "declare"; it.k = k; it.a = e;
						queue.add(it);
					}
				}
			}
		}
		for (int a = 0; a < K; a++) for (int b = a + 1; b < K; b++) {
			if (war[a][b] > 0) {
				war[a][b]--;
				war[b][a] = war[a][b];
				if (war[a][b] == 0) {
					truce[a][b] = truce[b][a] = 5;
					t[a][b] = t[b][a] = 30.0;
					warflip[a][b] = warflip[b][a] = false;
					ev(EvType.WAR_END, a, b, -1, 0, "truce");
				}
			} else if (truce[a][b] > 0) {
				truce[a][b]--;
				truce[b][a] = truce[a][b];
			}
		}
		for (int a = 0; a < K; a++) for (int b = 0; b < K; b++) {
			if (a != b && isAllied(a, b) && war[a][b] > 0) {
				war[a][b] = war[b][a] = 0;
				truce[a][b] = truce[b][a] = 5;
			}
		}
	}

	void skirmish(int a, int b) {
		double pa = PATROL * power(a), pb = PATROL * power(b);
		int[][] pr = {{a, b}, {b, a}};
		double[][] pw = {{pa, pb}, {pb, pa}};
		for (int i = 0; i < 2; i++) {
			Tuning bx = eff[pr[i][0]];
			double px = pw[i][0], py = pw[i][1];
			if (px <= 0 || py <= 0) return;
			double est = (px / py) * Math.exp(rng.nextGaussian() * bx.noise);
			if (est < bx.skirmOdds) return;
		}
		if (sink != null && sink.skirmish(a, b, pa, pb)) return;
		double ra = pa * (0.8 + 0.4 * rng.nextDouble()), rb = pb * (0.8 + 0.4 * rng.nextDouble());
		int win = ra >= rb ? a : b, lose = ra >= rb ? b : a;
		finishSkirmish(win, lose, Math.max(ra, rb) / Math.max(Math.min(ra, rb), 1e-9), false);
	}

	/** Applies a patrol clash result (also called by the layer after a real skirmish). A real fight with a player in it grants the winner no veterancy. */
	public void finishSkirmish(int win, int lose, double ratio, boolean playerInvolved) {
		double lw = Math.min(0.3, 0.10 / ratio), ll = Math.min(0.6, 0.15 * ratio + 0.05);
		lose(win, lw * PATROL);
		lose(lose, ll * PATROL);
		double gain = br[win].vetGain;
		if (gain != 0 && !playerInvolved) vet[win] = Math.min(br[win].vetCap, vet[win] + gain * 0.15 * (1 - lw));
		t[win][lose] = t[lose][win] = Math.min(100.0, t[win][lose] + 5);
		ev(EvType.SKIRMISH, win, lose, -1, ratio, "skirmish");
	}

	void setAlly(int a, int b) {
		if (isolated[a] || isolated[b]) return;
		if (war[a][b] > 0) return;
		boolean already = isAllied(a, b);
		ally[a][b] = ally[b][a] = day + 3;
		t[a][b] = t[b][a] = Math.min(t[a][b], 30.0);
		if (!already) ev(EvType.ALLIANCE, a, b, -1, 0, "alliance");
	}

	double opportunity(int k, int e) {
		refresh(k);
		double sum = 0;
		int c = 0;
		for (int v : reachL[k]) if (owner[v] == e) {
			sum += defence(v);
			c++;
		}
		if (c == 0) return 0.5;
		double est = (sum / c) * Math.exp(rng.nextGaussian() * eff[k].noise);
		double r = eff[k].commit * power(k) / est;
		return Math.min(OPP_MAX, Math.max(OPP_MIN, r));
	}

	boolean timingOk(int k, int e) {
		double estRatio = (H[e] / cap(e, n[e])) * Math.exp(rng.nextGaussian() * eff[k].noise);
		boolean busy = false;
		for (int x = 0; x < K; x++) if (x != k && x != e && war[e][x] > 0) busy = true;
		boolean coal = coalitionTarget == e;
		boolean healthy = H[k] >= 0.8 * cap(k, n[k]);
		return healthy && (busy || coal || estRatio < 0.8);
	}

	/** Starts a war now (3 days) unless a truce, alliance or war already exists. */
	public void startWar(int k, int e) {
		if (war[k][e] > 0 || truce[k][e] > 0 || isAllied(k, e)) return;
		war[k][e] = war[e][k] = 3;
		warflip[k][e] = warflip[e][k] = false;
		ev(EvType.DECLARE, k, e, -1, 0, "declare");
	}

	// ------------------------------------------------------------------------------------------------ military
	void military() {
		List<Integer> order = new ArrayList<>();
		for (int k = 0; k < K; k++) order.add(k);
		java.util.Collections.shuffle(order, rng);
		for (int k : order) {
			Tuning b = eff[k];
			if (flipsToday >= WORLD_FLIPS_PER_DAY) break;
			if (b.timing && n[k] / (double) totalContestable() >= b.leaderCap) continue;
			refresh(k);
			if (H[k] < 0.35 * cap(k, n[k])) continue;
			double ramp = day >= b.opening ? 1.0 : 0.15;
			if (free > 0 && rng.nextDouble() < b.freeRate * ramp) {
				List<Integer> cands = new ArrayList<>();
				for (int v : reachL[k]) if (owner[v] == -1 && contestable[v] && !pending[v] && lock[v] <= day) cands.add(v);
				plan(k, cands, "free");
			}
			if (rng.nextDouble() < b.attackRate) {
				List<Integer> enemies = new ArrayList<>();
				for (int e = 0; e < K; e++) if (war[k][e] > 0) enemies.add(e);
				if (!enemies.isEmpty()) {
					List<Integer> cands = new ArrayList<>();
					for (int v : reachL[k]) if (owner[v] >= 0 && enemies.contains(owner[v]) && contestable[v] && !pending[v] && lock[v] <= day && !warflip[k][owner[v]]) cands.add(v);
					if (coalitionTarget >= 0 && enemies.contains(coalitionTarget) && rng.nextDouble() < 0.5) {
						List<Integer> c2 = new ArrayList<>();
						for (int v : cands) if (owner[v] == coalitionTarget) c2.add(v);
						if (!c2.isEmpty()) cands = c2;
					}
					plan(k, cands, "war");
				}
			}
		}
	}

	void plan(int k, List<Integer> cands, String kind) {
		if (cands.isEmpty()) return;
		Tuning b = eff[k];
		double pw = power(k);
		double maxfrac = kind.equals("war") ? b.commit : Math.min(b.commit, 0.35);
		if (br[k].disorg > 0) {
			if (rng.nextDouble() < br[k].disorg) maxfrac *= 0.5;
			if (kind.equals("war") && rng.nextDouble() < br[k].miser) return;
		}
		List<Integer> pool = cands;
		if (cands.size() > b.scout) {
			pool = new ArrayList<>(cands);
			java.util.Collections.shuffle(pool, rng);
			pool = pool.subList(0, b.scout);
		}
		double bestRatio = -1, bestEst = 0, bestA = 0;
		int bestV = -1;
		for (int v : pool) {
			int o = owner[v];
			double D = defence(v);
			double est = D * Math.exp(rng.nextGaussian() * b.noise);
			double A = maxfrac * pw;
			if (kind.equals("war")) for (int j : alliesFor(k, o)) A += ALLY_SHARE * eff[j].commit * power(j);
			double ratio = A / est;
			if (bestV < 0 || ratio > bestRatio) {
				bestRatio = ratio; bestV = v; bestEst = est; bestA = A;
			}
		}
		if (bestV < 0 || bestRatio < b.minOdds) return;
		double frac = maxfrac;
		if (b.timing) frac = kind.equals("free") ? Math.min(maxfrac, b.minOdds * 1.12 * bestEst / pw) : maxfrac * Math.min(1.0, b.minOdds * 1.12 * bestEst / bestA);
		if (kind.equals("free") && br[k].hireMax > 0) lastFree[k] = day;
		int dl = b.delayMax > 0 ? randInt(b.delayMin, b.delayMax) : 0;
		if (dl > 0) {
			QItem it = new QItem();
			it.exec = day + dl; it.kind = "attack"; it.k = k; it.a = bestV; it.frac = frac; it.akind = kind;
			queue.add(it);
		} else runAttack(k, bestV, frac, kind);
	}

	void runAttack(int k, int v, double frac, String kind) {
		if (flipsToday >= WORLD_FLIPS_PER_DAY || v < 0 || v >= nv) return;
		int o = owner[v];
		if (o == k || lock[v] > day || pending[v]) return;
		if (kind.equals("free") && o != -1) return;
		if (kind.equals("war") && (o < 0 || war[k][o] == 0 || warflip[k][o])) return;
		if (H[k] < 0.35 * cap(k, n[k])) return;
		assault(k, v, frac * power(k), kind);
	}

	void lose(int k, double frac) {
		frac = Math.min(0.95, Math.max(0.0, frac));
		H[k] *= 1 - frac;
		Hm[k] *= 1 - frac;
		vet[k] *= 1 - 0.5 * frac;
	}

	/** Starts an assault: the sink may take it over (a real fight); otherwise it is rolled now. */
	public void assault(int k, int v, double a0, String kind) {
		Assault a = new Assault();
		a.k = k; a.v = v; a.o = owner[v]; a.a0 = a0; a.kind = kind; a.day = day;
		a.d0 = defence(v);
		double atot = a0;
		if (kind.equals("war")) for (int j : alliesFor(k, a.o)) {
			double aj = ALLY_SHARE * eff[j].commit * power(j);
			a.allyIdx.add(new int[] {j});
			a.allyPower.add(aj);
			atot += aj;
		}
		a.atot = atot;
		if (sink != null && sink.handoff(a)) {
			pending[v] = true;
			return;
		}
		finish(a, dice(a));
	}

	/** The four-round abstract fight of WAR-AI section 2. */
	public Outcome dice(Assault as) {
		int o = as.o;
		Tuning bk = eff[as.k];
		double a = as.atot, d = as.d0;
		boolean retreat = false, defRet = false;
		for (int r = 0; r < 4; r++) {
			double la = Math.min(0.6, 0.15 * d / a + randInt(1, 6) * 0.01);
			double ld = Math.min(0.6, 0.15 * a / d + randInt(1, 6) * 0.01);
			a *= 1 - la;
			d *= 1 - ld;
			if (d <= 0.25 * as.d0) break;
			if (r < 3 && a / d < bk.retreat) {
				retreat = true;
				break;
			}
			if (o >= 0 && r < 3 && d / a < eff[o].defRetreat) {
				defRet = true;
				break;
			}
		}
		return classify(as, a, d, retreat, defRet);
	}

	/** Turns remaining attacker and defender strength into an {@link Outcome} (also used for real fights that end by timeout). */
	public Outcome classify(Assault as, double a, double d, boolean retreat, boolean defRet) {
		double fa = 1 - a / as.atot, fd = 1 - d / Math.max(as.d0, 1e-9);
		boolean win;
		if (retreat) {
			win = false;
			fa = Math.min(1.0, fa + 0.05);
		} else if (defRet) {
			win = true;
			fd = Math.min(fd, 0.15);
		} else {
			win = a > d;
			if (!win) fa = Math.max(fa, FAIL_LOSS);
		}
		Tuning bk = eff[as.k];
		if (win && bk.pursuit != 0 && !defRet) fd = Math.min(0.9, fd + bk.pursuit);
		if (!win && as.o >= 0 && br[as.o].pursuit != 0) fa = Math.min(0.9, fa + br[as.o].pursuit);
		return new Outcome(win, retreat, defRet, Math.max(0, fa), Math.max(0, fd));
	}

	/** Applies an assault's result: losses, veterancy, ownership. Called by {@link #assault} or later by the layer for a real fight. */
	public void finish(Assault as, Outcome oc) {
		int k = as.k, v = as.v, o = as.o;
		pending[v] = false;
		double fa = oc.fa(), fd = oc.fd();
		boolean win = oc.win(), retreat = oc.retreat();
		double pk = Math.max(power(k), 1.0);
		lose(k, fa * as.a0 / pk);
		for (int i = 0; i < as.allyIdx.size(); i++) {
			int j = as.allyIdx.get(i)[0];
			lose(j, fa * as.allyPower.get(i) / Math.max(power(j), 1.0));
		}
		if (o >= 0) lose(o, fd * (G_SHARE / (n[o] + 2) + R_SHARE));
		double share = as.a0 / pk, capv = br[k].vetCap;
		if (!as.playerInvolved) {
			if (win) vet[k] = Math.min(capv, vet[k] + br[k].vetGain * share * (1 - fa) * 4);
			else if (retreat) vet[k] = Math.min(capv, vet[k] + 0.5 * br[k].vetGain * share * (1 - fa) * 4);
		}
		if (o >= 0 && !win) vet[o] = Math.min(br[o].vetCap, vet[o] + 0.5 * br[o].vetGain * (1 - fd) * 0.4);
		statAtt[k]++;
		if (win) statWin[k]++;
		double odds = as.atot / Math.max(as.d0, 1e-9);
		if (win) {
			if (o >= 0) {
				t[k][o] = t[o][k] = Math.min(100.0, t[k][o] + 15);
				warflip[k][o] = warflip[o][k] = true;
				if (br[o].hireMax > 0) money[o] *= 0.92;
			}
			ev(oc.defRetreat() ? EvType.GARRISON_WITHDREW : EvType.ASSAULT_WON, k, o, v, odds, as.kind);
			changeOwner(v, k, false);
		} else ev(retreat ? EvType.ASSAULT_BROKEN_OFF : EvType.ASSAULT_FAILED, k, o, v, odds, as.kind);
	}

	// ------------------------------------------------------------------------------------------------ main step
	/** Runs one in-game day. */
	public void step() {
		rng = new Random(seed * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L + 12345);
		flipsToday = 0;
		updateBrain();
		for (int k = 0; k < K; k++) refresh(k);
		economy();
		villageUpkeep();
		diplomacy();
		military();
		day++;
		recount();
	}

	/** Villages per kingdom today. */
	public int[] counts() {
		return n.clone();
	}

	// ------------------------------------------------------------------------------------------------ test worlds (port of the sim's placement)
	/** A random 4-kingdom world as in WAR-AI section 7 (castles in a 1200-2500 ring, at least 1800 apart, each with a neighbour within 3000). */
	public static WarSim randomWorld(long worldSeed, Tuning[] tunings) {
		Random r = new Random(worldSeed * 7919 + 17);
		int K = tunings.length;
		double[][] c;
		while (true) {
			c = new double[K][];
			for (int i = 0; i < K; i++) {
				double rad = 1200 + r.nextDouble() * 1300, ang = r.nextDouble() * 2 * Math.PI;
				c[i] = new double[] {rad * Math.cos(ang), rad * Math.sin(ang)};
			}
			boolean ok = true;
			for (int i = 0; i < K; i++) {
				boolean near = false;
				for (int j = 0; j < K; j++) {
					if (i == j) continue;
					double dd = dist(c[i][0], c[i][1], c[j][0], c[j][1]);
					if (dd < 1800) ok = false;
					if (dd <= 3000) near = true;
				}
				if (!near) ok = false;
			}
			if (ok) break;
		}
		List<double[]> pts = new ArrayList<>();
		List<List<Integer>> home = new ArrayList<>();
		for (int k = 0; k < K; k++) {
			home.add(new ArrayList<>());
			int tries = 0;
			while (home.get(k).size() < 6 && tries < 400) {
				tries++;
				double a = r.nextDouble() * 2 * Math.PI, rr = 260 + r.nextDouble() * 640;
				double[] p = {c[k][0] + rr * Math.cos(a), c[k][1] + rr * Math.sin(a)};
				if (freeSpot(p, pts, c)) {
					home.get(k).add(pts.size());
					pts.add(p);
				}
			}
		}
		int target = 32 + r.nextInt(15);
		int tries = 0;
		List<int[]> pairs = new ArrayList<>();
		for (int i = 0; i < K; i++) for (int j = i + 1; j < K; j++) if (dist(c[i][0], c[i][1], c[j][0], c[j][1]) <= 3600) pairs.add(new int[] {i, j});
		while (pts.size() < target && tries < 3000) {
			tries++;
			double[] p;
			if (!pairs.isEmpty() && r.nextDouble() < 0.6) {
				int[] pr = pairs.get(r.nextInt(pairs.size()));
				double mx = (c[pr[0]][0] + c[pr[1]][0]) / 2, mz = (c[pr[0]][1] + c[pr[1]][1]) / 2;
				p = new double[] {mx + r.nextGaussian() * 520, mz + r.nextGaussian() * 520};
			} else {
				int i = r.nextInt(K);
				double a = r.nextDouble() * 2 * Math.PI, rr = 700 + r.nextDouble() * 800;
				p = new double[] {c[i][0] + rr * Math.cos(a), c[i][1] + rr * Math.sin(a)};
			}
			if (freeSpot(p, pts, c)) pts.add(p);
		}
		WarSim s = new WarSim(K, worldSeed);
		s.castle = c;
		s.resize(pts.size());
		for (int i = 0; i < pts.size(); i++) {
			s.vx[i] = pts.get(i)[0];
			s.vz[i] = pts.get(i)[1];
			s.fg[i] = 8 + r.nextDouble() * 22;
		}
		for (int k = 0; k < K; k++) for (int v : home.get(k)) s.owner[v] = k;
		s.init(tunings);
		for (int k = 0; k < K; k++) {
			s.H[k] = s.cap(k, 6) * 0.9;
			s.money[k] = 60.0;
		}
		s.recount();
		return s;
	}

	private static boolean freeSpot(double[] p, List<double[]> pts, double[][] c) {
		for (double[] q : pts) if (dist(p[0], p[1], q[0], q[1]) < 230) return false;
		for (double[] cc : c) if (dist(p[0], p[1], cc[0], cc[1]) < 250) return false;
		return true;
	}
}
