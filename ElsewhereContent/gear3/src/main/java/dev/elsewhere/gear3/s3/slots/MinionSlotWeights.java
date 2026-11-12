package dev.elsewhere.gear3.s3.slots;

import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Multi-slot minions for gear3 (GEAR.md A5 "2 slots": Echo Reliquary, War-Yak Horn). The summoner module counts minions,
 * one slot each; a heavy minion type registers its slot cost here and this class keeps the owner's total cost within
 * {@link MinionApi#maxSlots}, sending the oldest minions home first (the summoner's own rule, by weight).
 * <p>
 * Shared helper owned by s-reliquary (see ElsewhereContent/_swarm/gear3/SLOTS-API.md). Use:
 * {@code MinionSlotWeights.register(MY_TYPE, 2)} once at init; before a call, {@link #canCall}; right after
 * {@code MinionManager.summon(...)}, {@link #enforce}; and from the heavy minion's own once-a-second upkeep
 * ({@code minionSecond}), {@link #enforce} again so a lowered slot cap (gear swap, Calling settle, trinket moved) is
 * applied within a second. Event-driven otherwise: any other minion entering the world schedules one check for the next
 * tick ({@code ServerEntityEvents.ENTITY_LOAD}, cheap instanceof filter). Nothing scans or polls players.
 */
public final class MinionSlotWeights {
	private MinionSlotWeights() {}

	private static final Map<EntityType<?>, Integer> WEIGHTS = new HashMap<>();
	private static boolean hooked;
	/** Self-test: how many minions this class has sent home. */
	public static int dismissed;

	/** Registers a minion type that fills {@code slots} minion slots (1 = an ordinary minion). */
	public static void register(EntityType<?> type, int slots) {
		if (type != null) WEIGHTS.put(type, Math.max(1, slots));
	}

	public static int weight(EntityType<?> type) {
		return WEIGHTS.getOrDefault(type, 1);
	}

	public static int weight(Entity minion) {
		return weight(minion.getType());
	}

	/** Slots the owner's live minions fill, by weight. */
	public static int used(ServerPlayer owner) {
		int n = 0;
		for (MinionEntity m : MinionManager.minions(owner)) n += weight(m);
		return n;
	}

	/** True when the owner's slot cap could hold a minion of this type on its own (older minions make room when it is called). */
	public static boolean canCall(ServerPlayer owner, EntityType<?> type) {
		return MinionApi.maxSlots(owner) >= weight(type);
	}

	/**
	 * The rule as a pure function: minion weights oldest first and the slot limit; returns how many of the oldest go home
	 * so the rest fit (all of them when even the newest alone does not fit).
	 */
	public static int overflow(List<Integer> oldestFirst, int max) {
		int sum = 0;
		for (int w : oldestFirst) sum += w;
		int n = 0;
		while (sum > max && n < oldestFirst.size()) {
			sum -= oldestFirst.get(n);
			n++;
		}
		return n;
	}

	/** Sends the owner's oldest minions home until their weights fit the slot cap. Returns how many went. */
	public static int enforce(ServerPlayer owner) {
		List<MinionEntity> live = MinionManager.minions(owner); // oldest first (summon order)
		if (live.isEmpty()) return 0;
		List<Integer> w = new ArrayList<>(live.size());
		boolean heavy = false;
		for (MinionEntity m : live) {
			int x = weight(m);
			heavy |= x > 1;
			w.add(x);
		}
		if (!heavy) return 0; // one slot each: the summoner's own count rule is already exact
		int n = overflow(w, MinionApi.maxSlots(owner));
		for (int i = 0; i < n; i++) MinionManager.dismiss(live.get(i));
		if (n > 0) {
			dismissed += n;
			dev.elsewhere.summoner.SummonerPerks.updateHerdFocus(owner);
		}
		return n;
	}

	public static void init() {
		if (hooked) return;
		hooked = true;
		// a minion from any staff joins: one weight check next tick (it is registered with its owner right after it loads)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof MinionEntity m) || WEIGHTS.isEmpty()) return;
			Gear3Scheduler.after(level.getServer(), 1, () -> {
				ServerPlayer owner = m.ownerPlayer();
				if (owner != null) enforce(owner);
			});
		});
	}
}
