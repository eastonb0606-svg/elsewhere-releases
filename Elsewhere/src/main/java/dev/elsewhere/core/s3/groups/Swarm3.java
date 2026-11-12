package dev.elsewhere.core.s3.groups;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * ARMIES.md 6.1 rule 3, swarm-as-one (s-spawngroups, 3.0.0): a tiny swarming mob spawns as ONE entity that holds the pooled
 * health of the whole swarm, and the client draws its members (4x fewer entities for the same or a bigger swarm).
 * <p>
 * Server: when a naturally spawned swarm mob loads for the first time ({@code finalizeSpawn} ran, no swarm tag yet), it rolls
 * its member count and gets three permanent attribute modifiers: max health = members x the per-member health, a wider
 * hitbox (SCALE; the renderer cancels it for the members themselves) and, where the rule says so, damage for the swarm.
 * The members still alive are {@code ceil(health / per-member health)}, so the client needs no extra synced data (max health,
 * its base and health are synced already). Event-driven: one ENTITY_LOAD hook. Kill switch: {@link SpawnGroups3#ON}.
 */
public final class Swarm3 {
	private Swarm3() {}

	/**
	 * One swarm creature: members rolled in {@code [min, max]}; {@code healthShare} = one member's health as a share of the
	 * entity's base max health; {@code hitbox} = SCALE added per swarm; {@code damagePer} = damage share per member (0: none).
	 */
	public record Rule(String id, int min, int max, double healthShare, double hitbox, double damagePer) {}

	public static final String TAG = "elsewhere_swarm";
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("elsewhere", "core_swarm_health");
	public static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("elsewhere", "core_swarm_hitbox");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("elsewhere", "core_swarm_damage");

	private static final Map<String, Rule> RULES = new java.util.HashMap<>();
	private static Map<EntityType<?>, Rule> byType;
	private static final Set<Mob> SEEN = Collections.newSetFromMap(new WeakHashMap<>());

	static void rules(final List<Rule> rules) {
		for (Rule r : rules) RULES.put(r.id(), r);
	}

	public static Rule ruleFor(final String id) {
		return RULES.get(id);
	}

	static Rule rule(final Entity e) {
		if (byType == null) {
			Map<EntityType<?>, Rule> m = new IdentityHashMap<>();
			for (Rule r : RULES.values()) {
				BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath("elsewhere", r.id())).ifPresent(t -> m.put(t, r));
			}
			byType = m;
		}
		return byType.get(e.getType());
	}

	static void init() {
		if (RULES.isEmpty()) return;
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mob m && rule(m) != null) {
				if (SpawnGroups3.ON) form(m, rule(m));
				else strip(m);
			}
		});
	}

	/** Makes a freshly spawned mob a swarm (once; the tag and the modifiers are saved with it). */
	public static void form(final Mob m, final Rule r) {
		if (!SEEN.add(m) || m.entityTags().contains(TAG) || !Herds3.spawnedNaturally(m)) return;
		int n = r.min() + m.getRandom().nextInt(r.max() - r.min() + 1);
		AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return;
		m.addTag(TAG);
		hp.removeModifier(HEALTH_ID);
		hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, n * r.healthShare() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		AttributeInstance sc = m.getAttribute(Attributes.SCALE);
		if (sc != null && r.hitbox() > 0) {
			sc.removeModifier(SCALE_ID);
			sc.addPermanentModifier(new AttributeModifier(SCALE_ID, r.hitbox(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmg != null && r.damagePer() > 0) {
			dmg.removeModifier(DAMAGE_ID);
			dmg.addPermanentModifier(new AttributeModifier(DAMAGE_ID, n * r.damagePer() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		m.setHealth(m.getMaxHealth());
	}

	/** Kill switch off: a saved swarm goes back to one plain 2.1 mob. */
	static void strip(final Mob m) {
		if (!m.entityTags().contains(TAG)) return;
		m.removeTag(TAG);
		AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH), sc = m.getAttribute(Attributes.SCALE), dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (hp != null) hp.removeModifier(HEALTH_ID);
		if (sc != null) sc.removeModifier(SCALE_ID);
		if (dmg != null) dmg.removeModifier(DAMAGE_ID);
		if (m.getHealth() > m.getMaxHealth()) m.setHealth(m.getMaxHealth());
	}

	/** One member's health: base max health x the rule's share (the base comes from the entity type, or a slime's size). */
	public static double memberHealth(final LivingEntity e, final double share) {
		AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
		return Math.max(0.5, (hp == null ? e.getMaxHealth() : hp.getBaseValue()) * share);
	}

	/** A swarm carries the pooled-health modifier; works on both sides (attribute modifiers are synced to clients). */
	public static boolean isSwarm(final LivingEntity e) {
		AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
		return hp != null && hp.hasModifier(HEALTH_ID);
	}

	/** Members the swarm was born with (1 for a plain 2.1 mob or a boss add). */
	public static int members(final LivingEntity e, final double share) {
		if (!isSwarm(e)) return 1;
		return Math.max(1, (int) Math.round(e.getMaxHealth() / memberHealth(e, share)));
	}

	/** Members still alive: health / one member's health, rounded up, at least 1 while the entity lives. */
	public static int alive(final LivingEntity e, final double share) {
		if (!isSwarm(e)) return 1;
		return Math.max(1, Math.min(members(e, share), (int) Math.ceil(e.getHealth() / memberHealth(e, share) - 1e-4)));
	}

	/** How many 2.1 mobs this swarm stands for (for effects that used to stack once per mob); 1 for a plain mob. */
	public static float weight(final LivingEntity e) {
		Rule r = rule(e);
		if (r == null || !e.entityTags().contains(TAG)) return 1F;
		return Math.max(1F, alive(e, r.healthShare()) * (float) r.healthShare());
	}

	/** Self-test: forms a real swarm next to the herd test site, checks health, hitbox, members alive and the off-switch strip. */
	static void selfTest(final ServerLevel level, final BlockPos at, final List<Entity> spawned, final BiConsumer<Boolean, String> check, final String n) {
		for (Rule r : RULES.values()) {
			String w = n + "swarm-as-one " + r.id() + ": ";
			try {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("elsewhere", r.id()));
				if (!(type.create(level, EntitySpawnReason.NATURAL) instanceof Mob m)) {
					check.accept(false, w + "cannot create");
					continue;
				}
				m.setPos(at.getX() + 0.5, at.getY() + 6, at.getZ() + 6.5);
				m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
				m.setNoGravity(true);
				m.setNoAi(true);
				float baseWidth = m.getBbWidth();
				level.addFreshEntity(m);
				spawned.add(m);
				form(m, r);
				int mem = members(m, r.healthShare());
				double per = memberHealth(m, r.healthShare());
				boolean healthOk = m.entityTags().contains(TAG) && mem >= r.min() && mem <= r.max() && Math.abs(m.getMaxHealth() - mem * per) < 0.01
					&& Math.abs(m.getHealth() - m.getMaxHealth()) < 0.01;
				check.accept(healthOk, w + mem + " members, pooled " + m.getMaxHealth() + " HP (" + per + " each)");
				check.accept(r.hitbox() <= 0 || m.getBbWidth() > baseWidth * 1.2F, w + "the swarm's hitbox is wider (" + baseWidth + " -> " + m.getBbWidth() + ")");
				m.setHealth((float) (per * 1.5));
				check.accept(alive(m, r.healthShare()) == 2 && weight(m) > 0, w + "members alive follow health (1.5 members' health -> 2 drawn)");
				strip(m);
				check.accept(!m.entityTags().contains(TAG) && !isSwarm(m) && members(m, r.healthShare()) == 1 && m.getHealth() <= m.getMaxHealth(),
					w + "switched off, a saved swarm is one plain 2.1 mob again");
			} catch (Throwable t) {
				check.accept(false, w + "threw " + t);
			}
		}
	}
}
