package dev.elsewhere.gear3.s3.pennant;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Mechanics;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.gear3.setbonus.b.MarshalCommand;
import dev.elsewhere.minion.MinionManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Herdcaller's Pennant (GEAR.md A5, Summoner / Swarm, G2, a standard): "minions within 12 regen 1 HP/s and +10% speed for 30 s".
 * Standards (GEAR.md A5 tool families) are planted auras: 1 per player, 30 s, 60 s cooldown, minion bonus x D(Summoner).
 * <ul>
 * <li>Use the pennant on the ground (or in the air while standing: at your feet) and a {@link HerdcallerStandard} is planted there:
 * a 3-block banner pole with a green swallow-tailed flag. It needs a block with a solid top under it and room for its pole
 * (nothing floats, nothing is buried). The pennant itself is kept; it goes on a 60 s cooldown.</li>
 * <li>For 30 s your own minions (the summoner minion framework, {@code MinionManager.minions}) within {@link #BASE_RADIUS} blocks
 * of the standard heal {@link #HEAL_PER_SECOND} HP each second and move {@link #SPEED_BONUS} faster (one transient
 * {@code movement_speed} / {@code flying_speed} modifier per minion, removed when it leaves the radius or the standard ends). Both
 * are multiplied by the owner's Summoner Calling D(M) = 0.90 + 0.25 M, read when the standard is planted.</li>
 * <li>One standard per player: planting again (after the cooldown) takes the old one down. The standard ends early when its owner
 * leaves the level, dies or logs out, when the block under it is broken, or when its chunk unloads.</li>
 * <li>Lag: nothing ticks per tick. While a standard stands, one timer (the shared {@link Gear3Scheduler} min-heap) fires once a
 * second and walks the owner's minion list (at most 4); no world scan. The entity is never saved (noSave), so a crash or restart
 * leaves nothing behind; the speed modifiers are transient and minions are never saved either.</li>
 * <li>Visuals are client-side: the flag waves in the renderer, a ring of motes marks the radius once when it is planted, and a
 * mote rises from the flag every second (particles beyond 32 blocks are culled by the particle engine).</li>
 * </ul>
 * Radius: {@link #radiusFor} = 12 + every {@link #addRadiusBonus} hook, read each pulse (so putting a set on or off applies within a
 * second). The Marshal's Panoply set "Command" (+8, {@code MarshalCommand.standardRadiusBonus}) is built in; other bonuses register.
 * Merge entry: {@link #init()} from Gear3Content (before Gear3Items.init(): it puts the "pennant" item factory); client
 * {@code PennantClient.init()} from Gear3Client.
 * Kill switch: key {@code herdcaller_pennant} in {@code config/elsewhere-overhaul3.json} (or
 * {@code -Delsewhere.overhaul3.herdcaller_pennant=false}): off = the item and the standard stay registered (worlds keep loading),
 * but the pennant plants nothing.
 */
public final class Pennant {
	private Pennant() {}

	public static final String ID = "herdcaller_pennant";
	public static final String STANDARD_ID = "herdcaller_standard";
	/** Kill switch {@code herdcaller_pennant}, read once. */
	public static final boolean ENABLED = dev.elsewhere.arsenal.Overhaul3.on("herdcaller_pennant");

	// ---- GEAR.md numbers
	public static final double BASE_RADIUS = 12.0;
	public static final float HEAL_PER_SECOND = 1.0F;
	public static final double SPEED_BONUS = 0.10;
	public static final int PULSE_TICKS = 20, PULSES = 30, LIFETIME_TICKS = PULSE_TICKS * PULSES, COOLDOWN_TICKS = 1200;
	/** Blocks of free space the pole needs (the model is 2.94 blocks tall). */
	public static final int CLEARANCE = 3;
	public static final Identifier SPEED_MOD = Gear3Content.id("gear3/herdcaller_pennant.speed");

	public static EntityType<HerdcallerStandard> STANDARD;

	private static final Map<UUID, Standard> ACTIVE = new HashMap<>();
	private static final List<ToDoubleFunction<Player>> RADIUS_BONUSES = new CopyOnWriteArrayList<>(List.<ToDoubleFunction<Player>>of(Pennant::commandBonus));
	private static boolean done;

	@SuppressWarnings("unchecked")
	public static void init() {
		if (done) return;
		done = true;
		// the catalogue entry names mechanic "pennant"; registered here, before Gear3Items.init() builds the items
		Gear3Mechanics.FACTORIES.put("pennant", PennantItem::new);
		Identifier rid = Gear3Content.id(STANDARD_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) STANDARD = (EntityType<HerdcallerStandard>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
			STANDARD = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<HerdcallerStandard>of(HerdcallerStandard::new, MobCategory.MISC)
				.sized(0.6F, 2.95F).clientTrackingRange(8).noUpdateInterval().dontTrackDeltas().noSave().noSummon().noLootTable().fireImmune().build(key));
		}
		// the scheduler drops its queue on stop (Gear3Scheduler.init); drop the records with it
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> ACTIVE.clear());
	}

	// ---------------------------------------------------------------- radius

	public static Item item() {
		return BuiltInRegistries.ITEM.getValue(Gear3Content.id(ID));
	}

	/** Marshal's Panoply "Command": the standard's owner gets +8 x set power (s-setbonus-b, HOOKS-s-setbonus-b.md). */
	static double commandBonus(Player owner) {
		return MarshalCommand.SET == null ? 0.0 : MarshalCommand.standardRadiusBonus(owner);
	}

	/** Adds a radius bonus (blocks) for a player's standard, read at each pulse. */
	public static void addRadiusBonus(ToDoubleFunction<Player> bonus) {
		RADIUS_BONUSES.add(bonus);
	}

	public static void removeRadiusBonus(ToDoubleFunction<Player> bonus) {
		RADIUS_BONUSES.remove(bonus);
	}

	public static double radiusFor(Player p) {
		double r = BASE_RADIUS;
		for (ToDoubleFunction<Player> b : RADIUS_BONUSES) {
			try {
				r += Math.max(0.0, b.applyAsDouble(p));
			} catch (RuntimeException e) {
				// a broken hook never takes the standard down
			}
		}
		return r;
	}

	/** Bonus multiplier D(Summoner) = 0.90 + 0.25 M (1.0 when Callings are off). */
	public static float multiplier(Player p) {
		return Calling.damageMult(p, Calling.Path.SUMMONER);
	}

	// ---------------------------------------------------------------- planting

	/** Where a use on {@code clicked} plants the pole: in the clicked block if it is replaceable (grass, a thin snow layer), else beside the clicked face. */
	public static BlockPos target(Level level, BlockPos clicked, Direction face) {
		return level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
	}

	/** A block with a solid top under {@code base}, and {@link #CLEARANCE} blocks without collision from {@code base} up. */
	public static boolean canPlantAt(Level level, BlockPos base) {
		BlockPos below = base.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
		for (int i = 0; i < CLEARANCE; i++) {
			BlockPos p = base.above(i);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
		}
		return true;
	}

	/**
	 * Plants {@code owner}'s standard with its pole in {@code base}, facing {@code yaw}'s viewer. Takes the owner's previous standard
	 * down. Null when the spot is not valid or the entity cannot be added. Does not touch the item cooldown (the item does that).
	 */
	public static @Nullable Standard plant(ServerPlayer owner, ServerLevel level, BlockPos base, float yaw) {
		if (STANDARD == null || !canPlantAt(level, base)) return null;
		HerdcallerStandard e = STANDARD.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (e == null) return null;
		Standard old = ACTIVE.get(owner.getUUID());
		if (old != null) old.end(true);
		double r = radiusFor(owner);
		e.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, yaw, 0F);
		e.setup((float) r, level.getGameTime());
		if (!level.addFreshEntity(e)) return null;
		Standard s = new Standard(owner, level, e, base, multiplier(owner));
		ACTIVE.put(owner.getUUID(), s);
		level.playSound(null, base, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.6, e.getZ(), 10, 0.4, 0.7, 0.4, 0.0);
		s.pulse(false); // speed at once; the first heal comes after one second
		schedule(s);
		return s;
	}

	private static void schedule(Standard s) {
		Gear3Scheduler.after(s.level.getServer(), PULSE_TICKS, () -> second(s));
	}

	/** One timer firing (once a second). Counted, so the standard always ends after {@link #PULSES} firings, also in a synchronous self-test. */
	static void second(Standard s) {
		if (s.ended) return;
		s.pulses++;
		if (!s.valid()) {
			s.end(true);
			return;
		}
		s.pulse(true);
		if (s.pulses >= PULSES) s.end(true);
		else schedule(s);
	}

	/** The owner's live standard, or null. */
	public static @Nullable Standard active(UUID owner) {
		Standard s = ACTIVE.get(owner);
		return s == null || s.ended ? null : s;
	}

	/** The entity belongs to a live standard record (else it is an orphan and removes itself). */
	static boolean isLive(HerdcallerStandard e) {
		for (Standard s : ACTIVE.values()) if (s.entity == e && !s.ended) return true;
		return false;
	}

	public static int activeCount() {
		return ACTIVE.size();
	}

	/** Sets (amount > 0) or removes (0) the pennant speed modifier on movement_speed and flying_speed. */
	public static void speed(LivingEntity m, double amount) {
		for (Holder<Attribute> a : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
			AttributeInstance inst = m.getAttribute(a);
			if (inst == null) continue;
			AttributeModifier cur = inst.getModifier(SPEED_MOD);
			if (amount <= 0) {
				if (cur != null) inst.removeModifier(SPEED_MOD);
			} else if (cur == null || cur.amount() != amount) {
				inst.removeModifier(SPEED_MOD);
				inst.addTransientModifier(new AttributeModifier(SPEED_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	// ---------------------------------------------------------------- one planted standard

	public static final class Standard {
		public final ServerPlayer owner;
		public final ServerLevel level;
		public final HerdcallerStandard entity;
		public final BlockPos base;
		public final Vec3 pos;
		/** The aura radius at the last pulse (12 + bonuses, re-read each pulse). */
		double radius;
		/** D(Summoner) when planted. */
		public final float mult;
		int pulses;
		boolean ended;
		private final Set<LivingEntity> buffed = Collections.newSetFromMap(new IdentityHashMap<>());

		Standard(ServerPlayer owner, ServerLevel level, HerdcallerStandard entity, BlockPos base, float mult) {
			this.owner = owner;
			this.level = level;
			this.entity = entity;
			this.base = base;
			this.pos = entity.position();
			this.radius = entity.radius();
			this.mult = mult;
		}

		public boolean ended() {
			return ended;
		}

		public int pulses() {
			return pulses;
		}

		public double radius() {
			return radius;
		}

		/** Re-reads the radius (12 + bonuses) for the owner; the standard's synced radius follows for the client ring. */
		public double refreshRadius() {
			radius = radiusFor(owner);
			entity.setRadius((float) radius);
			return radius;
		}

		public double healPerSecond() {
			return HEAL_PER_SECOND * mult;
		}

		public double speedBonus() {
			return SPEED_BONUS * mult;
		}

		/** Still standing: entity live, owner live in this level, a solid top still under the pole. */
		boolean valid() {
			if (entity.isRemoved() || owner.isRemoved() || !owner.isAlive() || owner.level() != level) return false;
			BlockPos below = base.below();
			return level.isLoaded(below) && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
		}

		/** One pulse over the owner's minions (at most 4, from the minion framework's own list). */
		public int pulse(boolean heal) {
			if (ended) return 0;
			refreshRadius();
			return pulse(MinionManager.minions(owner), heal);
		}

		/** One pulse over {@code minions}: in range = speed on (+ heal if hurt); out of range = speed off. Returns how many are in range. */
		public int pulse(Iterable<? extends LivingEntity> minions, boolean heal) {
			if (ended) return 0;
			double r2 = radius * radius;
			int n = 0;
			for (LivingEntity m : minions) {
				boolean in = m.isAlive() && !m.isRemoved() && m.level() == level && m.position().distanceToSqr(pos) <= r2;
				if (in) {
					speed(m, speedBonus());
					buffed.add(m);
					if (heal && m.getHealth() < m.getMaxHealth()) {
						m.heal((float) healPerSecond());
						level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() * 0.7, m.getZ(), 2, 0.25, 0.25, 0.25, 0.0);
					}
					n++;
				} else if (buffed.remove(m)) speed(m, 0);
			}
			return n;
		}

		/** Takes the standard down: speed modifiers off every minion it buffed, the entity removed (with a puff when {@code effects}). */
		public void end(boolean effects) {
			if (ended) return;
			ended = true;
			ACTIVE.remove(owner.getUUID(), this);
			for (LivingEntity m : buffed) speed(m, 0);
			buffed.clear();
			if (!entity.isRemoved()) {
				if (effects) {
					level.playSound(null, base, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
					level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.6, entity.getZ(), 8, 0.3, 0.7, 0.3, 0.02);
				}
				entity.discard();
			}
		}
	}
}
