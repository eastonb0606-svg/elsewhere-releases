package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The three diplomacy NPCs and what they offer. Entity types {@code elsewhere:kingdom_lawspeaker}, {@code kingdom_royal_envoy} and
 * {@code kingdom_clerk} (one {@link DiplomatNpc} class each, unit ids of the same name so a faction file's {@code roster} can reskin them).
 * The court spawns one per faction at its castle gate once the realm is built ({@link #bind} says which role a faction has; Hrafnholt: Lawspeaker,
 * Azmar: Envoy, Mirewick: Clerk). Players talk to them with right-click and the {@code /diplomacy} command.
 * <ul>
 * <li>all: Standing readout, tribute (+1 Standing per coin of the faction's currency, cap +15 a week), the shop ({@link Shops}), duel challenge ({@link Duel});</li>
 * <li>Envoy: Azmar's tribute offer ({@link #offerAvailable}, {@link #acceptOffer}): a strong player (one who has killed a Lord of it) is paid Dinars to stay out of its wars;</li>
 * <li>Clerk: the exchange ({@link Shops#exchange}), council bribes ({@link Oligarchy#bribe}), votes readout and sellsword contracts ({@link #signContract}).</li>
 * </ul>
 */
public final class Diplomacy {
	private Diplomacy() {}

	public enum Role {
		LAWSPEAKER("kingdom_lawspeaker"), ENVOY("kingdom_royal_envoy"), CLERK("kingdom_clerk"),
		/** 2.1 trade NPCs (RIVALS-DECISIONS 12): Hrafnholt's Smith (the stall that is open every day), Azmar's Market Inspector, Mirewick's Auctioneer and the Azmar caravan's master. */
		SMITH("kingdom_smith"), INSPECTOR("kingdom_market_inspector"), AUCTIONEER("kingdom_auctioneer"), CARAVAN_MASTER("kingdom_caravan_master");

		public final String path;
		public @Nullable EntityType<? extends DiplomatNpc> type;

		Role(String path) {
			this.path = path;
		}

		public Identifier unitId() {
			return CourtContent.id(path);
		}

		/** Treasury points for Azmar's Letters of Credit: deposit at one, draw at any other. */
		public boolean treasury() {
			return this == ENVOY || this == INSPECTOR || this == CARAVAN_MASTER;
		}
	}

	static final Map<String, Role> BINDINGS = new ConcurrentHashMap<>(Map.of(
		"elsewhere:hrafnholt", Role.LAWSPEAKER, "elsewhere:azmar", Role.ENVOY, "elsewhere:mirewick", Role.CLERK));

	/** The trade NPCs a faction gets besides its diplomat (2.1): Hrafnholt's Smith, Azmar's Market Inspector, Mirewick's Auctioneer. */
	static final Map<String, java.util.List<Role>> EXTRA = new ConcurrentHashMap<>(Map.of(
		"elsewhere:hrafnholt", java.util.List.of(Role.SMITH), "elsewhere:azmar", java.util.List.of(Role.INSPECTOR), "elsewhere:mirewick", java.util.List.of(Role.AUCTIONEER)));

	/** The Magnate each Mirewick trader serves ("faction|role" to slot key): the Counting-House Clerk is Marrow's, the Auctioneer Ames'. A miser's trader haggles 15 points harder. */
	static final Map<String, String> PATRONS = new ConcurrentHashMap<>(Map.of(
		"elsewhere:mirewick|kingdom_clerk", "master_at_arms", "elsewhere:mirewick|kingdom_auctioneer", "chancellor"));

	public static void bindExtra(Identifier faction, Role role) {
		EXTRA.compute(faction.toString(), (k, v) -> {
			java.util.List<Role> l = new java.util.ArrayList<>(v == null ? java.util.List.of() : v);
			if (!l.contains(role)) l.add(role);
			return l;
		});
	}

	/** Primary diplomat first, then the trade NPCs; each is spawned once at the castle gate. */
	public static java.util.List<Role> rolesOf(Identifier faction) {
		java.util.List<Role> out = new java.util.ArrayList<>();
		Role main = roleOf(faction);
		if (main != null) out.add(main);
		for (Role r : EXTRA.getOrDefault(faction.toString(), java.util.List.of())) if (!out.contains(r)) out.add(r);
		return out;
	}

	public static void setPatron(Identifier faction, Role role, @Nullable String slot) {
		if (slot == null) PATRONS.remove(faction + "|" + role.path);
		else PATRONS.put(faction + "|" + role.path, slot);
	}

	public static @Nullable String patronOf(Identifier faction, Role role) {
		return PATRONS.get(faction + "|" + role.path);
	}

	/** Chooses which diplomacy NPC a faction gets at its castle (null removes it). */
	public static void bind(Identifier faction, @Nullable Role role) {
		if (role == null) BINDINGS.remove(faction.toString());
		else BINDINGS.put(faction.toString(), role);
	}

	public static @Nullable Role roleOf(Identifier faction) {
		return BINDINGS.get(faction.toString());
	}

	public static @Nullable EntityType<? extends DiplomatNpc> type(Role r) {
		return r.type;
	}

	/** The nearest diplomacy NPC within {@code radius} blocks of the player. */
	public static @Nullable DiplomatNpc npcNear(ServerPlayer p, double radius) {
		DiplomatNpc best = null;
		for (DiplomatNpc n : p.level().getEntitiesOfClass(DiplomatNpc.class, new AABB(p.blockPosition()).inflate(radius)))
			if (best == null || n.distanceToSqr(p) < best.distanceToSqr(p)) best = n;
		return best;
	}

	/** One line: the player's Standing band and number with the faction. */
	public static Component readout(ServerPlayer p, Identifier faction) {
		ServerLevel level = (ServerLevel) p.level();
		int v = Standing.get(level, p.getUUID(), faction);
		return Component.literal("Standing with " + dev.elsewhere.kingdom.Factions.get(faction).displayName().getString() + ": " + v + " (" + Standing.band(v).name().toLowerCase() + ")");
	}

	// ---- Azmar's tribute offer --------------------------------------------------------------------------------------------

	public static final int OFFER_DINARS = 32;
	public static final float OFFER_AGGRESSION = 6.0F;

	static long week(ServerLevel l) {
		return l.getDefaultClockTime() / 24000L / 7;
	}

	/** The pact: a paid-off player is left out of the faction's wars until the end of the week (the war lane's hunting squads read {@link #hasPact}). */
	public static boolean hasPact(ServerLevel level, Identifier faction, java.util.UUID player) {
		CourtState st = CourtState.peek(level.getServer(), faction);
		return st != null && st.ledger.getOrDefault("pact|" + player, -1L) == week(level);
	}

	/** A strong player (has hurt the faction enough: aggression 6, a Lord's worth) who is not already its friend is offered Dinars to keep out of its wars. */
	public static boolean offerAvailable(ServerPlayer p, Identifier faction) {
		ServerLevel level = (ServerLevel) p.level();
		if (CourtState.isFallen(level.getServer(), faction) || hasPact(level, faction, p.getUUID())) return false;
		return KingdomApi.aggression(p, faction) >= OFFER_AGGRESSION && Standing.get(level, p.getUUID(), faction) < 30;
	}

	/** Accepts the offer: {@link #OFFER_DINARS} of the faction's currency now, a pact until the week ends, and Standing is lifted to Neutral at least. */
	public static boolean acceptOffer(ServerPlayer p, Identifier faction) {
		if (!offerAvailable(p, faction)) return false;
		ServerLevel level = (ServerLevel) p.level();
		CourtState st = CourtState.get(level, faction);
		st.ledger.put("pact|" + p.getUUID(), week(level));
		st.touch();
		Shops.give(p, faction, OFFER_DINARS);
		if (Standing.get(level, p.getUUID(), faction) < 0) Standing.set(level, p.getUUID(), faction, 0);
		return true;
	}

	// ---- Mirewick's sellsword contracts (the war lane's FreeCompanies keeps the kills) --------------------------------------------

	public static final int CONTRACT_DAYS = 3, SCRIP_PER_KILL = 2;

	/** A Friendly-or-better player signs on with {@code employer} for {@link #CONTRACT_DAYS} days against {@code enemy}: Scrip per kill, collected with {@link #collectContract}. */
	public static boolean signContract(ServerPlayer p, Identifier employer, Identifier enemy) {
		return FreeCompanies.startContract(p, employer, enemy, CONTRACT_DAYS * 24000L);
	}

	/** Pays {@link #SCRIP_PER_KILL} of the employer's currency for each kill since the last collection and marks them paid; returns the amount. */
	public static int collectContract(ServerPlayer p, Identifier employer) {
		ServerLevel level = (ServerLevel) p.level();
		int kills = FreeCompanies.contractKills(level, p.getUUID(), employer);
		if (kills <= 0) return 0;
		Shops.give(p, employer, kills * SCRIP_PER_KILL);
		FreeCompanies.markPaid(level, p.getUUID(), employer);
		return kills * SCRIP_PER_KILL;
	}

	public static boolean isNpc(net.minecraft.world.entity.Entity e) {
		return e instanceof DiplomatNpc;
	}

	public static @Nullable Mob unused() {
		return null;
	}
}
