package dev.elsewhere.verdigris_ironpeak.s3.groups;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ARMIES.md 6.1 rule 2 and the 6.2 "Behaviour" column (s-spawngroups, 3.0.0): herds, flocks, packs and pairs.
 * <p>
 * A natural spawn pack (one {@code NaturalSpawner} pick: same type, same level, same game tick, within 24 blocks) becomes
 * one group: the first member is the leader, the rest follow it. The leader runs its own AI and pathfinds; followers only
 * steer to a formation slot next to it ({@link FollowLeader3}, a 10-tick decision, pathfinding only when more than 16
 * blocks away, at most once a second). Membership is kept in entity tags, so groups survive saving and chunk reloads.
 * <p>
 * Everything is event-driven: {@code ENTITY_LOAD} forms and restores groups, {@code AFTER_DAMAGE} alerts the group
 * (hostile packs hunt the attacker, herds flee from it; at most once per 20 ticks per group, one box query),
 * {@code AFTER_DEATH} hands the group to a follower. Nothing scans or polls per tick.
 * Kill switch: {@link SpawnGroups3#ON} ({@value SpawnGroups3#KEY}).
 */
public final class Herds3 {
	private Herds3() {}

	/** How followers line up next to the leader. */
	public enum Shape { RING, FILE, COLUMN2, PAIR }

	/** What the group does when one member is hurt. */
	public enum Alert { HUNT, FLEE, NONE }

	/**
	 * One grouped creature. {@code max}: group size cap; {@code spacing}: slot distance in blocks; {@code speed}: steering
	 * speed modifier; {@code steer}: false for mobs whose movement is not goal-driven (the group still forms and alerts);
	 * {@code grow}: the leader is drawn 15% larger (the tusked sow, the bosun, the bell-yak, the white stag ...);
	 * {@code choir}: followers hold their slot and take no target until the group is alerted;
	 * {@code horn}: the leader sounds a horn when the group is alerted, and the followers are shaken when it dies;
	 * {@code guard}: members keep to a 10-block post around where they spawned (static sentries).
	 */
	public record Rule(String id, int max, Shape shape, double spacing, double speed, boolean steer, boolean grow, Alert alert,
		boolean choir, boolean horn, boolean guard) {}

	public static final String LEAD_TAG = "elsewhere_herd_lead";
	/** Follower tag prefix: {@code elsewhere_herd:<leader uuid>:<slot index>}. */
	public static final String FOLLOW_PREFIX = "elsewhere_herd:";
	public static final Identifier LEADER_SCALE = Identifier.fromNamespaceAndPath("elsewhere", "verdigris_ironpeak_herd_leader");
	static final double LEADER_GROWTH = 0.15;
	static final int PACK_RADIUS = 24;
	static final int ALERT_RADIUS = 24;

	private static final Map<String, Rule> RULES = new HashMap<>();
	private static Map<EntityType<?>, Rule> byType;

	/** Instances already set up this load (ENTITY_LOAD fires once per instance; the self-test may also call join). */
	private static final Set<Mob> JOINED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Mob, UUID> LEADER_OF = new WeakHashMap<>();
	private static final Map<Mob, Integer> SLOT_OF = new WeakHashMap<>();
	/** The open pack per type: members loaded in the same tick join it. */
	private static final Map<EntityType<?>, Pack> OPEN = new IdentityHashMap<>();
	/** Dead leaders and who took over (bounded). */
	private static final Map<UUID, UUID> SUCCESSOR = bounded(512);
	private static final Map<UUID, Boolean> DEAD = bounded(512);
	/** Leader uuid -> game time of its last alert (bounded); and choirs already broken. */
	private static final Map<UUID, Long> LAST_ALERT = bounded(512);
	private static final Map<UUID, Boolean> BROKEN = bounded(512);

	private record Pack(ServerLevel level, long time, Vec3 pos, UUID leader, int[] size) {}

	private static <K, V> Map<K, V> bounded(final int max) {
		return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, false) {
			@Override
			protected boolean removeEldestEntry(final Map.Entry<K, V> e) {
				return size() > max;
			}
		});
	}

	static void rules(final List<Rule> rules) {
		for (Rule r : rules) RULES.put(r.id(), r);
	}

	private static Rule rule(final Entity e) {
		if (byType == null) {
			Map<EntityType<?>, Rule> m = new IdentityHashMap<>();
			for (Rule r : RULES.values()) {
				BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath("elsewhere", r.id())).ifPresent(t -> m.put(t, r));
			}
			byType = m;
		}
		return byType.get(e.getType());
	}

	public static Rule ruleFor(final String id) {
		return RULES.get(id);
	}

	static void init() {
		if (!SpawnGroups3.ON) {
			// switched off: undo what an earlier 3.0 session left on saved mobs (leader size, group tags)
			ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
				if (e instanceof Mob m && rule(m) != null) strip(m);
			});
			return;
		}
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				Rule r = rule(m);
				if (r != null) join(m, level, r);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0 && victim instanceof Mob m && victim.level() instanceof ServerLevel level) {
				Rule r = rule(m);
				if (r != null) alert(m, level, r, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (dead instanceof Mob m && m.entityTags().contains(LEAD_TAG) && rule(m) != null) leaderDied(m);
		});
	}

	// ------------------------------------------------------------------ forming groups

	/** Sets a freshly loaded group mob up: restores its saved role, or puts it in the pack of this tick. Idempotent. */
	public static void join(final Mob m, final ServerLevel level, final Rule r) {
		if (!JOINED.add(m)) return;
		if (m instanceof TamableAnimal t && t.isTame()) return; // a tamed companion follows its owner, not a herd
		if (m.isPersistenceRequired() && !hasRole(m) && m.hasCustomName()) return; // a named pet stays itself
		boolean leads = m.entityTags().contains(LEAD_TAG);
		UUID leader = null;
		int slot = 0;
		for (String tag : m.entityTags()) {
			if (tag.startsWith(FOLLOW_PREFIX)) {
				String[] p = tag.substring(FOLLOW_PREFIX.length()).split(":");
				try {
					leader = UUID.fromString(p[0]);
					slot = p.length > 1 ? Integer.parseInt(p[1]) : 1;
				} catch (IllegalArgumentException ex) {
					leader = null;
				}
			}
		}
		if (!leads && leader == null) {
			long now = level.getGameTime();
			Pack p = OPEN.get(m.getType());
			if (p != null && p.level() == level && p.time() == now && p.size()[0] < r.max()
				&& p.pos().distanceToSqr(m.position()) < PACK_RADIUS * PACK_RADIUS && !p.leader().equals(m.getUUID())) {
				leader = p.leader();
				slot = p.size()[0]++;
				m.addTag(FOLLOW_PREFIX + leader + ":" + slot);
			} else {
				leads = true;
				m.addTag(LEAD_TAG);
				OPEN.put(m.getType(), new Pack(level, now, m.position(), m.getUUID(), new int[] {1}));
			}
		}
		if (leads) {
			if (r.grow()) grow(m, true);
		} else {
			LEADER_OF.put(m, leader);
			SLOT_OF.put(m, slot);
			if (r.steer()) addFollowGoal(m, r);
		}
		if (r.guard() && m instanceof PathfinderMob pm && !pm.hasHome()) pm.setHomeTo(m.blockPosition(), 10);
	}

	private static boolean hasRole(final Mob m) {
		if (m.entityTags().contains(LEAD_TAG)) return true;
		for (String tag : m.entityTags()) if (tag.startsWith(FOLLOW_PREFIX)) return true;
		return false;
	}

	/** The follow goal sits just above the mob's own wander goal, so attacks, panics and floating still win. */
	static void addFollowGoal(final Mob m, final Rule r) {
		int stroll = Integer.MAX_VALUE;
		for (WrappedGoal w : m.getGoalSelector().getAvailableGoals()) {
			if (w.getGoal() instanceof FollowLeader3) return;
			String n = w.getGoal().getClass().getSimpleName();
			if (n.contains("Stroll") || n.contains("Wander") || n.contains("RandomSwimming") || n.contains("RandomFloat") || n.contains("RandomFly")) {
				stroll = Math.min(stroll, w.getPriority());
			}
		}
		int prio = stroll == Integer.MAX_VALUE ? 6 : Math.max(1, stroll - 1);
		m.getGoalSelector().addGoal(prio, new FollowLeader3(m, r));
	}

	static void grow(final Mob m, final boolean on) {
		AttributeInstance a = m.getAttribute(Attributes.SCALE);
		if (a == null) return;
		if (on && !a.hasModifier(LEADER_SCALE)) {
			a.addPermanentModifier(new AttributeModifier(LEADER_SCALE, LEADER_GROWTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		} else if (!on) {
			a.removeModifier(LEADER_SCALE);
		}
	}

	static void strip(final Mob m) {
		grow(m, false);
		m.removeTag(LEAD_TAG);
		for (String tag : List.copyOf(m.entityTags())) if (tag.startsWith(FOLLOW_PREFIX)) m.removeTag(tag);
	}

	// ------------------------------------------------------------------ leader lookup and succession

	public static boolean isLeader(final Mob m) {
		return m.entityTags().contains(LEAD_TAG);
	}

	/** Leader uuid of a follower (null for a leader or a loner). */
	public static UUID leaderId(final Mob m) {
		return LEADER_OF.get(m);
	}

	public static int slot(final Mob m) {
		Integer s = SLOT_OF.get(m);
		return s == null ? 0 : s;
	}

	/** The group id: the leader's own uuid for a leader, its leader's for a follower. */
	static UUID groupId(final Mob m) {
		UUID l = LEADER_OF.get(m);
		return l != null ? l : isLeader(m) ? m.getUUID() : null;
	}

	/**
	 * The follower's live leader, or null (leader unloaded, or this follower just took over). One uuid lookup in the
	 * level's entity map; a dead leader is replaced by the first follower that asks, the others re-point to it.
	 */
	public static Mob leaderOf(final Mob m) {
		UUID id = LEADER_OF.get(m);
		if (id == null || !(m.level() instanceof ServerLevel level)) return null;
		Entity e = level.getEntity(id);
		if (e instanceof Mob l && l.isAlive() && !l.isRemoved()) return l;
		boolean dead = DEAD.containsKey(id) || (e instanceof LivingEntity le && !le.isAlive());
		if (!dead) return null; // unloaded or in another dimension: wander on your own for now
		UUID next = SUCCESSOR.putIfAbsent(id, m.getUUID());
		if (next == null || next.equals(m.getUUID())) {
			promote(m, id);
			return null;
		}
		repoint(m, id, next);
		Entity n = level.getEntity(next);
		return n instanceof Mob nl && nl.isAlive() ? nl : null;
	}

	private static void promote(final Mob m, final UUID old) {
		for (String tag : List.copyOf(m.entityTags())) if (tag.startsWith(FOLLOW_PREFIX)) m.removeTag(tag);
		m.addTag(LEAD_TAG);
		LEADER_OF.remove(m);
		SLOT_OF.remove(m);
		m.getGoalSelector().removeAllGoals(g -> g instanceof FollowLeader3);
		Rule r = rule(m);
		if (r != null && r.grow()) grow(m, true);
		Boolean broken = BROKEN.get(old);
		if (broken != null) BROKEN.put(m.getUUID(), broken);
	}

	private static void repoint(final Mob m, final UUID old, final UUID next) {
		int s = slot(m);
		m.removeTag(FOLLOW_PREFIX + old + ":" + s);
		m.addTag(FOLLOW_PREFIX + next + ":" + s);
		LEADER_OF.put(m, next);
	}

	static void leaderDied(final Mob leader) {
		DEAD.put(leader.getUUID(), Boolean.TRUE);
		Rule r = rule(leader);
		if (r == null || !r.horn() || !(leader.level() instanceof ServerLevel level)) return;
		// raiding-party morale: the followers are shaken for 10 s when their leader falls
		UUID id = leader.getUUID();
		for (Mob f : level.getEntitiesOfClass(Mob.class, leader.getBoundingBox().inflate(ALERT_RADIUS), x -> id.equals(LEADER_OF.get(x)))) {
			f.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
		}
	}

	// ------------------------------------------------------------------ alerts

	/** Choir followers sing (take no target) until the group has been alerted once. */
	public static boolean singing(final Mob m) {
		UUID g = LEADER_OF.get(m);
		if (g == null) return false;
		Rule r = rule(m);
		return r != null && r.choir() && !BROKEN.containsKey(g);
	}

	static void alert(final Mob victim, final ServerLevel level, final Rule r, final DamageSource source) {
		if (r.alert() == Alert.NONE || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
		if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
		UUID g = groupId(victim);
		if (g == null) return;
		if (attacker instanceof Mob am && rule(am) == r && g.equals(groupId(am))) return; // friendly fire inside one group
		long now = level.getGameTime();
		Long last = LAST_ALERT.get(g);
		BROKEN.put(g, Boolean.TRUE);
		if (last != null && now - last < 20) return;
		LAST_ALERT.put(g, now);
		AABB box = victim.getBoundingBox().inflate(ALERT_RADIUS);
		List<Mob> group = level.getEntitiesOfClass(Mob.class, box, x -> x != victim && x.isAlive() && g.equals(groupId(x)));
		for (Mob x : group) {
			x.setLastHurtByMob(attacker);
			if (r.alert() == Alert.HUNT && x.getType().getCategory() == MobCategory.MONSTER) x.setTarget(attacker);
		}
		if (r.horn()) {
			Mob lead = isLeader(victim) ? victim : group.stream().filter(Herds3::isLeader).findFirst().orElse(null);
			if (lead != null) level.playSound(null, lead.getX(), lead.getY(), lead.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 2.5F, 1.25F);
		}
	}

	// ------------------------------------------------------------------ formation slots

	/** Where follower {@code slot} stands relative to its leader. */
	static Vec3 slotPos(final Mob leader, final Rule r, final int slot) {
		Vec3 at = leader.position();
		float yaw = leader.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // leader's facing
		double s = r.spacing();
		return switch (r.shape()) {
			case PAIR -> at.add(fz * s, 0, -fx * s);
			case FILE -> at.add(-fx * s * slot, 0, -fz * s * slot);
			case COLUMN2 -> {
				int row = (slot + 1) / 2;
				double side = (slot % 2 == 1 ? 1 : -1) * s * 0.6;
				yield at.add(-fx * s * row + fz * side, 0, -fz * s * row - fx * side);
			}
			case RING -> {
				double a = slot * 2.39996 + yaw; // golden angle: an even, loose herd
				double rad = s * (1 + slot / 4.0);
				yield at.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
			}
		};
	}

	/** Follower goal: steer to the formation slot. Decides every 10 ticks; never runs while the mob has a target. */
	public static final class FollowLeader3 extends Goal {
		private final Mob mob;
		private final Rule rule;
		private Mob leader;
		private int ticks;
		private int pathAt;

		FollowLeader3(final Mob mob, final Rule rule) {
			this.mob = mob;
			this.rule = rule;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		/**
		 * The 10-tick decision gate, staggered by entity id. A mob evaluates {@code canUse} only on every other tick
		 * (Mob.serverAiStep), so the gate counts those calls: one decision in five calls.
		 */
		static boolean decides(final Mob m) {
			return Math.floorMod(m.tickCount / 2 + m.getId(), 5) == 0;
		}

		@Override
		public boolean canUse() {
			if (rule.choir() && mob.getTarget() != null && singing(mob)) mob.setTarget(null); // the choir sings on
			if (!decides(mob) || mob.getTarget() != null || mob.isPassenger() || mob.isLeashed()) return false;
			leader = leaderOf(mob);
			if (leader == null) return false;
			double slack = rule.choir() || rule.shape() == Shape.PAIR ? 2.0 : rule.spacing() + 2.0;
			return mob.distanceToSqr(slotPos(leader, rule, slot(mob))) > slack * slack;
		}

		@Override
		public boolean canContinueToUse() {
			if (leader == null || !leader.isAlive() || mob.getTarget() != null || ticks > 120) return false;
			return mob.distanceToSqr(slotPos(leader, rule, slot(mob))) > 1.5 * 1.5;
		}

		@Override
		public void start() {
			ticks = 0;
			pathAt = -100;
			steer();
		}

		@Override
		public void tick() {
			if (++ticks % 5 == 0) steer(); // goals tick every other server tick: a new aim every 10 ticks
		}

		@Override
		public void stop() {
			leader = null;
		}

		private void steer() {
			if (leader == null) return;
			Vec3 s = slotPos(leader, rule, slot(mob));
			double d2 = mob.distanceToSqr(s);
			double speed = rule.speed() * (d2 > 144 ? 1.3 : 1.0);
			if (d2 > 16 * 16) {
				if (ticks - pathAt >= 10) { // far behind: one path at most once a second
					pathAt = ticks;
					mob.getNavigation().moveTo(s.x, s.y, s.z, speed);
				}
			} else {
				mob.getNavigation().stop();
				mob.getMoveControl().setWantedPosition(s.x, s.y, s.z, speed);
			}
		}
	}
}
