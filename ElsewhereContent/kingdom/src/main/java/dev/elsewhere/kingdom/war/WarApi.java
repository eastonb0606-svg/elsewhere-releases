package dev.elsewhere.kingdom.war;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Public API of the war layer (WAR-AI.md). Server side. The villages lane registers its villages here and listens to {@link WarEvents#VILLAGE_CHANGED}; the court lane
 * registers castles and fires {@link #brainBossKilled}; everyone can read the effective brain numbers.
 */
public final class WarApi {
	private WarApi() {}

	// ---- brains -------------------------------------------------------------------------------------------------------

	/**
	 * A brain boss died: starts the 7-day empty seat, 30-day degrade and 90-day relearning ramp (WAR-AI section 4) for that kingdom, and applies the one-off effects.
	 * {@code role} is matched by keyword: sultana, wazir, falconer (Azmar), jarl, hersir (Hrafnholt), hawk, dove, miser or almoner (a Mirewick Magnate by leaning),
	 * king (Aldermark: sues for peace, no new wars for 7 days). A ruler slot name or title containing the word works too; pass whatever identifies the seat. Unknown roles are ignored.
	 * @return false when the kingdom is not in the war model or the role is not a brain role.
	 */
	public static boolean brainBossKilled(MinecraftServer server, Identifier faction, String role) {
		WarSim s = WarLayer.sim(server);
		if (s == null) return false;
		int k = WarLayer.ids.indexOf(faction);
		if (k < 0 || WarSim.category(roleText(faction, role)).isEmpty()) return false;
		s.brainKill(k, roleText(faction, role));
		WarState.get(server.overworld()).setDirty();
		return true;
	}

	/**
	 * The court fires a succession event (RIVALS-DECISIONS 10). {@code kind}: {@code ruler_succession} (Lord rules as Regent or strongest champion becomes Jarl: no brain change),
	 * {@code risen} (Hrafnholt: a Hird-Captain promoted into a champion seat, once per seat after 10 days: restores that seat's brain fields at half strength), {@code heir}
	 * (Mirewick: the vote returns; at most 2 heirs per family, the court counts them), {@code vacant_forever} (nothing to restore). Azmar brain seats never refill. True when the model changed.
	 */
	public static boolean successionEvent(MinecraftServer server, Identifier faction, String seat, String kind) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		if (k < 0) return false;
		boolean r = s.succession(k, roleText(faction, seat), kind);
		if (r) WarState.get(server.overworld()).setDirty();
		return r;
	}

	/** The role string plus the matching ruler slot's name and title (so {@code "marshal"} resolves to "Hersir" when the faction file says so). */
	static String roleText(Identifier faction, String role) {
		var d = Factions.def(faction);
		String t = role;
		if (d != null && d.rulers().containsKey(role)) t += " " + d.rulers().get(role).name() + " " + d.rulers().get(role).title();
		return t;
	}

	/** The brain numbers in force today for {@code faction}: the tuned values with any boss-death degradation applied. A copy. Null when the kingdom is not in the model. */
	public static @Nullable Tuning effectiveBrain(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		if (k < 0) return null;
		if (s.eff == null) return null;
		return s.eff[k].clone();
	}

	/** One effective brain field by its {@link Tuning#KEYS} name, or NaN. Note: the effective values are refreshed at each daily step. */
	public static double brainField(MinecraftServer server, Identifier faction, String key) {
		Tuning t = effectiveBrain(server, faction);
		return t == null ? Double.NaN : t.get(key);
	}

	/** True while any brain boss of {@code faction} is dead or its degrade or relearning period is running. */
	public static boolean brainDegraded(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		if (k < 0) return false;
		for (WarSim.Kill x : s.kills) if (x.k == k && s.day - x.day <= WarSim.SEAT_DAYS + WarSim.DEGRADE_DAYS + WarSim.PROB_DAYS) return true;
		return false;
	}

	// ---- the world the war is fought over ------------------------------------------------------------------------------

	/**
	 * Registers (or updates) a village. {@code owner} is the faction that holds it now (null = free). For a village already known the position and contestable flag update
	 * and the owner changes only when {@code forceOwner} is true. {@code contestable} false (neutral-ground villages) keeps it out of every candidate list.
	 */
	public static void registerVillage(MinecraftServer server, String key, BlockPos pos, @Nullable Identifier owner, boolean contestable, boolean forceOwner) {
		WarState st = WarState.get(server.overworld());
		int i = WarLayer.villageIndex(st, key);
		WarState.VRec r;
		if (i < 0) {
			r = new WarState.VRec();
			r.key = key;
			r.owner = owner == null ? "" : owner.toString();
			st.data.villages.add(r);
			i = st.data.villages.size() - 1;
			forceOwner = false;
		} else r = st.data.villages.get(i);
		r.x = pos.getX();
		r.z = pos.getZ();
		r.contestable = contestable;
		if (forceOwner) r.owner = owner == null ? "" : owner.toString();
		st.setDirty();
		WarSim s = WarLayer.sim != null ? WarLayer.sim : WarLayer.sim(server);
		if (s != null) {
			if (i >= s.nv) {
				s.resize(i + 1);
				s.fg[i] = 8 + Math.abs(key.hashCode() % 22);
				s.owner[i] = owner == null ? -1 : WarLayer.ids.indexOf(owner);
				s.prev[i] = -1;
			} else if (forceOwner) {
				s.owner[i] = owner == null ? -1 : WarLayer.ids.indexOf(owner);
			}
			s.vx[i] = pos.getX();
			s.vz[i] = pos.getZ();
			s.contestable[i] = contestable;
			WarLayer.geoDirty = true;
			if (s.eff != null) {
				for (int k = 0; k < s.K; k++) s.dirty[k] = true;
			}
		}
	}

	public static void registerVillage(MinecraftServer server, String key, BlockPos pos, @Nullable Identifier owner) {
		registerVillage(server, key, pos, owner, true, false);
	}

	/** Forgets a village (destroyed or removed). */
	public static void removeVillage(MinecraftServer server, String key) {
		registerVillage(server, key, BlockPos.ZERO, null, false, true);
	}

	/** The current owner of a registered village (null = free or unknown). */
	public static @Nullable Identifier villageOwner(MinecraftServer server, String key) {
		WarState st = WarState.get(server.overworld());
		int i = WarLayer.villageIndex(st, key);
		if (i < 0) return null;
		WarSim s = WarLayer.sim;
		if (s != null && i < s.nv && s.eff != null) return s.owner[i] < 0 || s.owner[i] >= WarLayer.ids.size() ? null : WarLayer.ids.get(s.owner[i]);
		String o = st.data.villages.get(i).owner;
		return o.isEmpty() ? null : Identifier.tryParse(o);
	}

	/** Villages currently held by a faction. */
	public static int villagesOf(MinecraftServer server, Identifier faction) {
		int c = 0;
		for (WarState.VRec r : WarState.get(server.overworld()).data.villages) {
			Identifier o = villageOwner(server, r.key);
			if (r.contestable && faction.equals(o)) c++;
		}
		return c;
	}

	/** Records a faction's castle position (used for supply lines and reach). Call when the realm is placed. */
	public static void setCastle(MinecraftServer server, Identifier faction, BlockPos pos) {
		WarState st = WarState.get(server.overworld());
		st.data.castles.put(faction.toString(), new double[] {pos.getX(), pos.getZ()});
		st.setDirty();
		WarSim s = WarLayer.sim;
		int k = WarLayer.ids.indexOf(faction);
		if (s != null && k >= 0 && k < s.K) {
			s.castle[k] = new double[] {pos.getX(), pos.getZ()};
			WarLayer.geoDirty = true;
			if (s.eff != null) for (int j = 0; j < s.K; j++) s.dirty[j] = true;
		}
	}

	// ---- reading and nudging the model ----------------------------------------------------------------------------------

	/** The faction's treasury in the model's abstract coin (caravans raided, Counting-House robbed: add a negative delta). */
	public static double treasury(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		return k < 0 ? 0 : s.money[k];
	}

	public static void addTreasury(MinecraftServer server, Identifier faction, double delta) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		if (k < 0) return;
		s.money[k] = Math.max(0, s.money[k] + delta);
		WarState.get(server.overworld()).setDirty();
	}

	/** Abstract army strength: heads, veterancy (0..vetCap) and hired heads. */
	public static double heads(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		return k < 0 ? 0 : s.H[k];
	}

	/** As {@link #veterancy} but never builds the model: 0 when it is not running (field AI calls this every decision). */
	public static double veterancyOrZero(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim;
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		return k < 0 || k >= s.K ? 0 : s.vet[k];
	}

	public static double veterancy(MinecraftServer server, Identifier faction) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		return k < 0 ? 0 : s.vet[k];
	}

	/** Real soldiers were killed by the player near a battle or raid: removes abstract heads (never below 10 percent of the cap). */
	public static void killHeads(MinecraftServer server, Identifier faction, double heads) {
		WarSim s = WarLayer.sim(server);
		int k = s == null ? -1 : WarLayer.ids.indexOf(faction);
		if (k < 0) return;
		s.H[k] = Math.max(0.1 * s.cap(k, s.n[k]), s.H[k] - heads);
		WarState.get(server.overworld()).setDirty();
	}

	/** Relation as the model sees it: 0 peace, 1 truce, 2 war, 3 allied. */
	public static int relation(MinecraftServer server, Identifier a, Identifier b) {
		WarSim s = WarLayer.sim(server);
		int ia = WarLayer.ids.indexOf(a), ib = WarLayer.ids.indexOf(b);
		if (s == null || ia < 0 || ib < 0 || ia == ib) return 0;
		if (s.isAllied(ia, ib)) return 3;
		if (s.war[ia][ib] > 0) return 2;
		if (s.truce[ia][ib] > 0) return 1;
		return 0;
	}

	/** Tension 0..100 between two kingdoms (70 or more starts wars). */
	public static double tension(MinecraftServer server, Identifier a, Identifier b) {
		WarSim s = WarLayer.sim(server);
		int ia = WarLayer.ids.indexOf(a), ib = WarLayer.ids.indexOf(b);
		return s == null || ia < 0 || ib < 0 ? 0 : s.t[ia][ib];
	}

	/** The kingdom a coalition currently targets (holding 40 percent or more of the villages), or null. */
	public static @Nullable Identifier coalitionTarget(MinecraftServer server) {
		WarSim s = WarLayer.sim(server);
		return s == null || s.coalitionTarget < 0 || s.coalitionTarget >= WarLayer.ids.size() ? null : WarLayer.ids.get(s.coalitionTarget);
	}

	/** Declares war now (3 days, then a 5-day truce). No effect on a truce, an alliance or a war already running. Also mirrors it into {@link Factions} stances. */
	public static void declareWar(MinecraftServer server, Identifier a, Identifier b) {
		WarSim s = WarLayer.sim(server);
		int ia = WarLayer.ids.indexOf(a), ib = WarLayer.ids.indexOf(b);
		if (s == null || ia < 0 || ib < 0) return;
		s.startWar(ia, ib);
		WarState.get(server.overworld()).setDirty();
	}

	/** A faction's villages count, kingdoms in the model, and the kingdom log (newest last) for commands and debugging. */
	public static java.util.List<String> log(MinecraftServer server) {
		return java.util.List.copyOf(WarState.get(server.overworld()).data.log);
	}

	/** Runs {@code days} war days now (tests and {@code /kingdom war step}). */
	public static void stepDays(ServerLevel overworld, int days) {
		for (int i = 0; i < days; i++) WarLayer.step(overworld);
	}

	/** Replaces the built-in real-battle hand-off (the villages lane may run sieges itself). Return true from {@link BattleHandler#handle} to take an assault over; you must then call {@link #reportAssault}. */
	public static void setBattleHandler(@Nullable BattleHandler h) {
		WarBattles.custom = h;
	}

	/** An outside owner of real battles (see {@link #setBattleHandler}). {@code attackerHeads}/{@code defenderHeads} are the abstract strengths in heads of the combatants. */
	public interface BattleHandler {
		boolean handle(ServerLevel level, String villageKey, BlockPos pos, Identifier attacker, @Nullable Identifier defender, double attackerHeads, double defenderHeads, int assaultId);
	}

	/** Finishes a hand-off assault: {@code attackerLostShare} and {@code defenderLostShare} (0..1) are the shares of the combatants lost, {@code attackerWon} the result. */
	public static void reportAssault(MinecraftServer server, int assaultId, boolean attackerWon, double attackerLostShare, double defenderLostShare, boolean playerInvolved) {
		WarBattles.reportCustom(server, assaultId, attackerWon, attackerLostShare, defenderLostShare, playerInvolved);
	}

	/** True when the faction takes part in the war model. */
	public static boolean participates(MinecraftServer server, Identifier faction) {
		return WarLayer.participants(server).contains(faction) && !KingdomState.get(server.overworld()).isFallen(faction);
	}
}
