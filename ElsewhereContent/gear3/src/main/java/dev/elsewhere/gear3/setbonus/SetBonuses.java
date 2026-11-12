package dev.elsewhere.gear3.setbonus;

import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.Overhaul3;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionTuning;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The gear3 set and pair bonus registry and its event hooks (API: ElsewhereContent/_swarm/gear3/SETBONUS-API.md).
 *
 * <p>Lag rule: nothing here ticks or scans. Which bonuses a player has is worked out once per armor change (Fabric
 * EQUIPMENT_CHANGE), on join and on respawn, and kept as one bit mask per player. Every hook first checks that some player
 * has a bonus at all ({@link #anyone()}), so with nobody wearing a set each hook costs one map-size check.
 *
 * <p>Hooks, all server side: {@link #DAMAGE} (multiply a hit's damage before armor, LivingEntity.hurtServer head),
 * {@link #AFTER_HIT} (a hit that landed), {@link #SPELL_COST} (Glimmer cost of spells and Glimmer items),
 * {@link #GLIMMER_REGEN} (Glimmer regen per second), {@link #STANCE} (a player started or stopped sneaking or using an item).
 * Spell damage and casts use arsenal's own {@code Spells.DAMAGE_MULT} and {@code Spells.ON_CAST}.
 */
public final class SetBonuses {
	private SetBonuses() {}

	/** Master kill switch {@code set_bonuses}; each bonus also has {@code setbonus_<id>}. */
	public static final boolean MASTER = Overhaul3.on("set_bonuses");
	/** Ascendant sets ("set bonus II", Ascension Shard): every magnitude from {@link #power} is multiplied by this. */
	public static float TIER2_SCALE = 1.5F;
	/** True for an Ascendant piece; the Ascension Shard lane sets it (default: none are). */
	public static Predicate<ItemStack> ascendant = s -> false;

	public static final List<SetBonus> ALL = new ArrayList<>();
	private static final Map<String, SetBonus> BY_ID = new LinkedHashMap<>();
	private static final Map<UUID, Long> MASK = new HashMap<>();
	private static final Set<EquipmentSlot> USED_SLOTS = EnumSet.noneOf(EquipmentSlot.class);

	@FunctionalInterface
	public interface DamageHook {
		/** Returns the new damage; return {@code amount} unchanged when the hit is not yours. */
		float apply(LivingEntity victim, DamageSource source, float amount);
	}

	@FunctionalInterface
	public interface HitHook {
		void hit(LivingEntity victim, DamageSource source, float damageTaken);
	}

	@FunctionalInterface
	public interface PlayerMult {
		float mult(ServerPlayer p);
	}

	public static final List<DamageHook> DAMAGE = new ArrayList<>();
	public static final List<HitHook> AFTER_HIT = new ArrayList<>();
	public static final List<PlayerMult> SPELL_COST = new ArrayList<>();
	public static final List<PlayerMult> GLIMMER_REGEN = new ArrayList<>();
	public static final List<Consumer<ServerPlayer>> STANCE = new ArrayList<>();

	// ---------------------------------------------------------------- registry

	public static SetBonus register(SetBonus b) {
		if (BY_ID.containsKey(b.id)) throw new IllegalStateException("set bonus registered twice: " + b.id);
		if (ALL.size() >= 63) throw new IllegalStateException("too many set bonuses (63 max)");
		if (b.slots.isEmpty()) throw new IllegalStateException("set bonus " + b.id + " has no pieces");
		b.bit = ALL.size();
		b.enabled = MASTER && Overhaul3.on(b.killKey());
		ALL.add(b);
		BY_ID.put(b.id, b);
		USED_SLOTS.addAll(b.slots);
		return b;
	}

	public static SetBonus get(String id) {
		return BY_ID.get(id);
	}

	/** Self-test: switch one bonus on or off (as its kill switch would) and re-read every online player. */
	public static void setEnabledForTest(SetBonus b, boolean on, Iterable<ServerPlayer> players) {
		b.enabled = on;
		for (ServerPlayer p : players) recompute(p);
	}

	// ---------------------------------------------------------------- queries

	/** Pieces of {@code b} worn now (read from the equipment; tooltips use it). */
	public static int worn(Player p, SetBonus b) {
		int n = 0;
		for (int i = 0; i < b.slots.size(); i++) if (b.fills(i, p.getItemBySlot(b.slots.get(i)))) n++;
		return n;
	}

	/** Every piece of {@code b} is worn (read from the equipment). */
	public static boolean wears(Player p, SetBonus b) {
		for (int i = 0; i < b.slots.size(); i++) if (!b.fills(i, p.getItemBySlot(b.slots.get(i)))) return false;
		return true;
	}

	/** The bonus is on: switched on and the set is complete (server: the cached mask; client: the equipment). */
	public static boolean active(Player p, SetBonus b) {
		if (!b.enabled) return false;
		if (p instanceof ServerPlayer) {
			Long m = MASK.get(p.getUUID());
			return m != null && (m & (1L << b.bit)) != 0;
		}
		return wears(p, b);
	}

	/** H(a, b) for a pair bonus (live from the Callings), 1 for a single-path set. */
	public static float hybrid(Player p, SetBonus b) {
		return b.isHybrid() ? Calling.hybrid(p, b.hybridA, b.hybridB) : 1F;
	}

	/** 2 when every piece is Ascendant ("set bonus II"), 1 when the set is worn, 0 otherwise. */
	public static int tier(Player p, SetBonus b) {
		if (!wears(p, b)) return 0;
		for (EquipmentSlot s : b.slots) if (!ascendant.test(p.getItemBySlot(s))) return 1;
		return 2;
	}

	/**
	 * The bonus's strength right now: 0 when off, else H (pair) or 1 (set), times {@link #TIER2_SCALE} for an Ascendant
	 * set. Multiply each of the bonus's magnitudes by this (e.g. +10% damage becomes {@code 1 + 0.10 * power}).
	 */
	public static float power(Player p, SetBonus b) {
		if (!active(p, b)) return 0F;
		float h = hybrid(p, b);
		return tier(p, b) >= 2 ? h * TIER2_SCALE : h;
	}

	/** Some player has some bonus on (cheap early-out for every hook). */
	public static boolean anyone() {
		return !MASK.isEmpty();
	}

	/** The bit mask of active bonuses for a server player (0 = none). */
	public static long mask(ServerPlayer p) {
		Long m = MASK.get(p.getUUID());
		return m == null ? 0L : m;
	}

	// ---------------------------------------------------------------- state changes (events only)

	/** Re-reads which bonuses are complete (equipment change, join, respawn) and tells the listeners of the ones that changed. */
	public static void recompute(ServerPlayer p) {
		long now = 0L;
		for (SetBonus b : ALL) if (b.enabled && wears(p, b)) now |= 1L << b.bit;
		UUID u = p.getUUID();
		Long old = MASK.get(u);
		long was = old == null ? 0L : old;
		if (now == 0L) MASK.remove(u);
		else MASK.put(u, now);
		long diff = was ^ now;
		if (diff == 0L) return;
		for (SetBonus b : ALL) {
			long bit = 1L << b.bit;
			if ((diff & bit) == 0L) continue;
			boolean on = (now & bit) != 0L;
			for (SetBonus.Listener l : b.listeners) l.changed(p, on);
		}
	}

	/** Forgets a player (disconnect, or the old body on respawn): every active bonus's listeners hear "off" first. */
	static void drop(ServerPlayer p) {
		Long old = MASK.remove(p.getUUID());
		if (old == null) return;
		for (SetBonus b : ALL) if ((old & (1L << b.bit)) != 0L) for (SetBonus.Listener l : b.listeners) l.changed(p, false);
	}

	// ---------------------------------------------------------------- hook entry points (called from gear3 mixins and events)

	/** LivingEntity.hurtServer head (SetBonusMixins.Hurt). */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (MASK.isEmpty() || DAMAGE.isEmpty() || amount <= 0F || victim.level().isClientSide()) return amount;
		for (DamageHook h : DAMAGE) amount = h.apply(victim, source, amount);
		return amount;
	}

	/** Glimmer.cost(Player, int, boolean) return (SetBonusMixins.Cost): spells and Glimmer items; the rounding and floor stay arsenal's. */
	public static float spellCost(Player p, float cost) {
		if (MASK.isEmpty() || SPELL_COST.isEmpty() || !(p instanceof ServerPlayer sp) || !MASK.containsKey(sp.getUUID())) return cost;
		float m = 1F;
		for (PlayerMult f : SPELL_COST) m *= f.mult(sp);
		if (m == 1F) return cost;
		float r = Math.round(cost * m / ArsenalStats.COST_ROUNDING) * ArsenalStats.COST_ROUNDING;
		return Math.max(ArsenalStats.COST_MIN, Math.round(r * 10F) / 10F);
	}

	/** Glimmer.regenPerSecond return (SetBonusMixins.Regen). */
	public static float regen(ServerPlayer p, float perSecond) {
		if (MASK.isEmpty() || GLIMMER_REGEN.isEmpty() || !MASK.containsKey(p.getUUID())) return perSecond;
		float m = 1F;
		for (PlayerMult f : GLIMMER_REGEN) m *= f.mult(p);
		return perSecond * m;
	}

	/** A server player started or stopped sneaking, or started or stopped using an item (SetBonusMixins.Sneak / Use). */
	public static void stanceChanged(ServerPlayer p) {
		if (MASK.isEmpty() || STANCE.isEmpty() || !MASK.containsKey(p.getUUID())) return;
		for (Consumer<ServerPlayer> c : STANCE) c.accept(p);
	}

	// ---------------------------------------------------------------- shared helpers

	/**
	 * Refreshes the owner's minions' battle timers to at least {@code strength} x their full timer (1 = full), resets their
	 * idle count and heals each by {@code heal}. Returns how many minions were refreshed.
	 */
	public static int refreshMinions(ServerPlayer owner, float strength, float heal) {
		int n = 0;
		float s = Math.max(0F, Math.min(1F, strength));
		for (MinionEntity m : MinionManager.minions(owner)) {
			int want = Math.round(MinionTuning.fervorTicks(owner, m) * s);
			if (m.fervorTicks < want) m.fervorTicks = want;
			m.idleTicks = 0;
			if (heal > 0F && m.isAlive() && m.getHealth() < m.getMaxHealth()) m.heal(heal);
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- init

	private static boolean hooked;

	/** Registers the events (idempotent). Bonuses register themselves before or after; order does not matter. */
	public static void init() {
		if (hooked) return;
		hooked = true;
		ServerEntityEvents.EQUIPMENT_CHANGE.register((e, slot, from, to) -> {
			if (e instanceof ServerPlayer p && USED_SLOTS.contains(slot)) recompute(p);
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> recompute(h.player));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> drop(h.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			drop(oldP);
			recompute(newP);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (MASK.isEmpty() || AFTER_HIT.isEmpty() || blocked || base <= 0F || victim.level().isClientSide()) return;
			for (HitHook h : AFTER_HIT) h.hit(victim, source, taken);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> MASK.clear());
	}
}
