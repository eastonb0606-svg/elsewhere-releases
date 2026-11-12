package dev.elsewhere.kingdom.armies.townsfolk;

import dev.elsewhere.kingdom.war.WarApi;
import dev.elsewhere.kingdom.war.WarState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The towns that have townsfolk: every realm capital (the castle site the court module records with {@code WarApi.setCastle}) and
 * every village a kingdom holds (registered with the war layer by the villages module), read from the kingdom module's own
 * {@link WarState}, so no other module needs a change. Other lanes and the self-test can add towns with {@link #register}. Refreshed
 * only when the manager evaluates (a player crossed a chunk border, an hour changed), never per tick.
 */
public final class Towns {
	private Towns() {}

	public static final int CAPITAL_RADIUS = 56, TOWN_RADIUS = 36;

	/** A town at runtime: identity, its live townsfolk and per-hour caches (none of it saved; {@link TownsfolkState} is the save). */
	public static final class Town {
		public final String key;
		public Identifier faction;
		public final boolean capital;
		public BlockPos center;
		public final int radius;
		final boolean explicit;
		public final List<Townsfolk> live = new ArrayList<>();
		@Nullable StreetGraph graph;
		/** Anchor cache for the current hour: where id to position. */
		final Map<String, BlockPos> anchors = new HashMap<>();

		Town(String key, Identifier faction, boolean capital, BlockPos center, boolean explicit) {
			this.key = key;
			this.faction = faction;
			this.capital = capital;
			this.center = center;
			this.radius = capital ? CAPITAL_RADIUS : TOWN_RADIUS;
			this.explicit = explicit;
		}

		public int maxResidents() {
			return TownsfolkRoles.residents(faction, capital);
		}

		@Override
		public String toString() {
			return key + " (" + faction.getPath() + (capital ? " capital" : " town") + " at " + center.getX() + ", " + center.getZ() + ")";
		}
	}

	private static final Map<String, Town> TOWNS = new LinkedHashMap<>();

	public static Collection<Town> all() {
		return TOWNS.values();
	}

	public static @Nullable Town get(String key) {
		return key == null || key.isEmpty() ? null : TOWNS.get(key);
	}

	/**
	 * Registers (or moves) a town explicitly, e.g. a layout lane's market town or a test town. {@code center} y may be 0: the street
	 * graph takes heights from the world. Explicit towns are kept until {@link #unregister}.
	 */
	public static Town register(String key, Identifier faction, boolean capital, BlockPos center) {
		Town t = TOWNS.get(key);
		if (t == null || t.capital != capital) {
			if (t != null) TownsfolkManager.foldTown(t, "moved");
			t = new Town(key, faction, capital, center, true);
			TOWNS.put(key, t);
		} else {
			t.faction = faction;
			t.center = center;
		}
		return t;
	}

	public static void unregister(String key) {
		Town t = TOWNS.remove(key);
		if (t != null) TownsfolkManager.foldTown(t, "unregistered");
	}

	/** Re-reads the capitals and kingdom-held villages from the war layer's saved state. Cheap: a few dozen records. */
	static void refresh(ServerLevel lv) {
		WarState ws = WarState.get(lv);
		Map<String, Town> seen = new HashMap<>();
		for (var e : ws.data.castles.entrySet()) {
			Identifier f = Identifier.tryParse(e.getKey());
			double[] c = e.getValue();
			if (f == null || c == null || c.length < 2 || TownsfolkRoles.of(f) == null) continue;
			BlockPos p = BlockPos.containing(c[0], lv.getSeaLevel(), c[1]);
			seen.put("capital:" + f, derived(lv, "capital:" + f, f, true, p));
		}
		for (WarState.VRec r : ws.data.villages) {
			if (r.key.isEmpty() || (r.x == 0 && r.z == 0 && !r.contestable)) continue; // removed villages are zeroed
			Identifier owner = WarApi.villageOwner(lv.getServer(), r.key);
			if (owner == null || TownsfolkRoles.of(owner) == null) continue; // free villages keep their vanilla villagers only
			String key = "village:" + r.key;
			seen.put(key, derived(lv, key, owner, false, BlockPos.containing(r.x, lv.getSeaLevel(), r.z)));
		}
		for (var it = TOWNS.values().iterator(); it.hasNext();) {
			Town t = it.next();
			if (t.explicit || seen.containsKey(t.key)) continue;
			TownsfolkManager.foldTown(t, "gone");
			it.remove();
		}
	}

	private static Town derived(ServerLevel lv, String key, Identifier faction, boolean capital, BlockPos center) {
		Town t = TOWNS.get(key);
		if (t != null && t.explicit) return t;
		if (t == null) {
			t = new Town(key, faction, capital, center, false);
			TOWNS.put(key, t);
		} else if (!t.faction.equals(faction)) {
			// the village changed hands: its people of the old kingdom go indoors, the new kingdom's come out
			TownsfolkManager.foldTown(t, "new owner");
			t.faction = faction;
		}
		if (t.center.getX() != center.getX() || t.center.getZ() != center.getZ()) {
			t.center = center;
			t.graph = null;
		}
		return t;
	}

	/** The town of {@code faction} whose area contains {@code p} (within its radius plus {@code margin}), nearest first; null if none. */
	public static @Nullable Town near(BlockPos p, @Nullable Identifier faction, int margin) {
		Town best = null;
		double bd = Double.MAX_VALUE;
		for (Town t : TOWNS.values()) {
			if (faction != null && !t.faction.equals(faction)) continue;
			double dx = t.center.getX() - p.getX(), dz = t.center.getZ() - p.getZ(), d = dx * dx + dz * dz;
			double r = t.radius + margin;
			if (d <= r * r && d < bd) {
				bd = d;
				best = t;
			}
		}
		return best;
	}

	/** Server stop / tests: forget the runtime towns (the saved counts and graphs stay in {@link TownsfolkState}). */
	static void reset() {
		TOWNS.clear();
	}
}
