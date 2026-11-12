package dev.elsewhere.gear3.minion;

import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Weighted minion slots for gear3 minions (GEAR.md A5: the Elite "2 slots" War-Yak and Echo Reliquary; any other
 * weight works too, e.g. 1/3 for a moth of a 3-moth slot). The summoner module counts every minion as 1 slot; a type
 * given a weight here counts that many instead. Default 1 = the summoner's own rule, unchanged.
 * <p>How: {@code MinionManager.enforceSlots(owner)} (run by the summoner after every summon and on its existing 10-tick
 * slot check) is replaced, through {@code mixin.minion.MinionManagerSlotsMixin}, by {@link #enforce} for owners who
 * have a weighted minion out ({@link #handles}: one set lookup; nothing new polls). {@link #enforce} drops the oldest
 * minions until the weighted total fits {@code MinionApi.maxSlots(owner)} (4-minion hard cap, Summoner Calling cap,
 * slot gear and server config all included there).
 * <p>Use: {@code MinionSlots.setWeight(MY_TYPE, 2)} once at init; before summoning check {@link #fits}.
 * Other gear3 code may read {@link #weight} (e.g. Elderbark "Greater Bond" buffs minions of weight >= 2).
 */
public final class MinionSlots {
	private MinionSlots() {}

	private static final Map<EntityType<?>, Double> WEIGHTS = new HashMap<>();
	/** Owners with at least one minion whose weight is not 1 (added on the minion's load; dropped by {@link #enforce}). */
	private static final Set<UUID> WEIGHTED_OWNERS = new HashSet<>();
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof MinionEntity m && weight(m) != 1.0) {
				UUID u = m.getOwnerUUID();
				if (u != null) WEIGHTED_OWNERS.add(u);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> WEIGHTED_OWNERS.clear());
	}

	/** Slots one minion of {@code type} takes (> 0). */
	public static void setWeight(EntityType<?> type, double slots) {
		if (type == null || !(slots > 0)) throw new IllegalArgumentException("minion slot weight must be > 0");
		if (slots == 1.0) WEIGHTS.remove(type);
		else WEIGHTS.put(type, slots);
	}

	public static double weight(EntityType<?> type) {
		return WEIGHTS.getOrDefault(type, 1.0);
	}

	public static double weight(Entity minion) {
		return weight(minion.getType());
	}

	/** Weighted slots in use by the owner's live minions. */
	public static double used(ServerPlayer owner) {
		double s = 0;
		for (MinionEntity m : MinionManager.minions(owner)) s += weight(m);
		return s;
	}

	/** True if a minion of {@code type} can be out at all with the owner's slots (older minions are replaced as usual). */
	public static boolean fits(ServerPlayer owner, EntityType<?> type) {
		return MinionApi.maxSlots(owner) + 1.0E-6 >= weight(type);
	}

	/** Call right before summoning a minion of {@code type} (the weighted rule then also covers that summon's own slot check). */
	public static void track(ServerPlayer owner, EntityType<?> type) {
		if (weight(type) != 1.0) WEIGHTED_OWNERS.add(owner.getUUID());
	}

	/** The weighted rule runs for this owner (a weighted minion is or was out). */
	public static boolean handles(ServerPlayer owner) {
		return !WEIGHTED_OWNERS.isEmpty() && WEIGHTED_OWNERS.contains(owner.getUUID());
	}

	/**
	 * How many of the oldest minions must go so the rest fit in {@code max} slots ({@code weightsOldestFirst} in summon
	 * order). Pure; the self-test checks it.
	 */
	public static int evictions(List<Double> weightsOldestFirst, int max) {
		double total = 0;
		for (double w : weightsOldestFirst) total += w;
		int n = 0;
		while (total > max + 1.0E-6 && n < weightsOldestFirst.size()) total -= weightsOldestFirst.get(n++);
		return n;
	}

	/** Drops the owner's oldest minions (no refund, like the summoner's own rule) until the weighted total fits. */
	public static void enforce(ServerPlayer owner) {
		List<MinionEntity> l = MinionManager.minions(owner); // live, oldest first (a copy)
		List<Double> w = new java.util.ArrayList<>(l.size());
		for (MinionEntity m : l) w.add(weight(m));
		int n = evictions(w, MinionApi.maxSlots(owner));
		for (int i = 0; i < n; i++) MinionManager.dismiss(l.get(i));
		boolean weighted = false;
		for (int i = n; i < l.size(); i++) if (w.get(i) != 1.0) weighted = true;
		if (!weighted) WEIGHTED_OWNERS.remove(owner.getUUID()); // back to the summoner's own count rule
	}
}
