package dev.elsewhere.gear3.s3.ascension;

import dev.elsewhere.gear3.Gear3Content;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Where Ascension Shards come from (GEAR.md: "T5/T6 bosses always 1; kingdom rulers 1"). One shard per player kill of:
 * <ul>
 * <li>a T5/T6 boss: Auris, Lithara (Greenhaze Hush), Ossyra, Aphelion (Nether/End bosses); it lands at the killer's feet
 * (Ossyra's and Aphelion's arenas hang over the void), glowing and long-lived like other boss loot;</li>
 * <li>a kingdom ruler: the Aldermark King ({@code elsewhere:court_king}), any court leader registered in the King slot
 * (entity tag {@code kingdom_court_leader:<faction>|king}: the Sultana of Azmar, the High Jarl of Hrafnholt), and Mirewick's
 * Chair (Mirewick has no King: the Magnate who holds the Chair when they die). It lands where the ruler fell.</li>
 * </ul>
 * Court Lords (G3 tier) and T4 bosses give none. Code, not loot tables: the rival rulers have no loot tables and the court
 * classes are not a compile dependency of gear3 (the Chair is read by reflection from the public {@code chair} field, or the
 * private {@code chairAtDeath} the Magnate captures before its court clears the flag). The hook runs in an early AFTER_DEATH
 * phase, before the court's own handler, so the Chair is still set. Needs a player kill and the mobLoot gamerule.
 */
public final class AscensionSources {
	private AscensionSources() {}

	public static final Set<String> BOSSES = Set.of("elsewhere:auris", "elsewhere:lithara", "elsewhere:ossyra", "elsewhere:aphelion");
	public static final String ALDERMARK_KING = "elsewhere:court_king";
	public static final String LEADER_TAG = "kingdom_court_leader:";
	public static final Identifier PHASE = Gear3Content.id("ascension_early");
	/** Self-test only: an entity tag that counts as a ruler while the self-test runs (null otherwise). */
	static volatile String testTag;

	private static final Map<Class<?>, Field[]> CHAIR_FIELDS = new ConcurrentHashMap<>();
	private static boolean hooked;

	static void init() {
		if (hooked) return;
		hooked = true;
		ServerLivingEntityEvents.AFTER_DEATH.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DEATH.register(PHASE, AscensionSources::onDeath);
	}

	/** Pure drop rule: shards for a kill of this entity type id with these entity tags ({@code chair}: it held Mirewick's Chair). */
	public static int shards(String typeId, Collection<String> tags, boolean chair) {
		if (BOSSES.contains(typeId) || ALDERMARK_KING.equals(typeId)) return 1;
		String t0 = testTag;
		for (String t : tags) {
			if (t.startsWith(LEADER_TAG) && (t.endsWith("|king") || chair)) return 1;
			if (t0 != null && t0.equals(t)) return 1;
		}
		return 0;
	}

	static boolean killedByPlayer(LivingEntity e, DamageSource src) {
		return src.getEntity() instanceof Player || e.getLastHurtByPlayer() != null;
	}

	private static void onDeath(LivingEntity e, DamageSource src) {
		if (!Ascension.ENABLED || !(e.level() instanceof ServerLevel sl)) return;
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		Set<String> tags = e.entityTags();
		boolean boss = BOSSES.contains(id);
		if (!boss && !ALDERMARK_KING.equals(id) && !hasLeaderOrTestTag(tags)) return; // the common case: one set lookup, no allocation
		if (shards(id, tags, !boss && heldChair(e)) <= 0 || !killedByPlayer(e, src) || !sl.getGameRules().get(GameRules.MOB_DROPS)) return;
		Item shard = Ascension.shard();
		if (shard == null) return;
		Vec3 at = e.position();
		if (boss) {
			Player killer = src.getEntity() instanceof Player p ? p : e.getLastHurtByPlayer();
			if (killer != null && killer.level() == sl && !killer.isSpectator()) at = killer.position();
		}
		ItemEntity ie = new ItemEntity(sl, at.x, at.y + 0.5, at.z, new ItemStack(shard));
		ie.setDefaultPickUpDelay();
		ie.setExtendedLifetime();
		ie.setGlowingTag(true);
		sl.addFreshEntity(ie);
	}

	private static boolean hasLeaderOrTestTag(Set<String> tags) {
		if (tags.isEmpty()) return false;
		String t0 = testTag;
		for (String t : tags) if (t.startsWith(LEADER_TAG) || (t0 != null && t0.equals(t))) return true;
		return false;
	}

	/** Mirewick's Chair: the Magnate's public {@code chair} flag, or {@code chairAtDeath} (set before its court clears the flag). */
	static boolean heldChair(LivingEntity e) {
		Field[] fs = CHAIR_FIELDS.computeIfAbsent(e.getClass(), AscensionSources::chairFields);
		for (Field f : fs) {
			try {
				if (f.getBoolean(e)) return true;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// not readable: treat as not the Chair
			}
		}
		return false;
	}

	private static Field[] chairFields(Class<?> c) {
		java.util.List<Field> out = new java.util.ArrayList<>(2);
		for (String name : new String[] {"chair", "chairAtDeath"}) {
			for (Class<?> k = c; k != null && k != LivingEntity.class; k = k.getSuperclass()) {
				try {
					Field f = k.getDeclaredField(name);
					if (f.getType() != boolean.class) break;
					f.setAccessible(true);
					out.add(f);
					break;
				} catch (NoSuchFieldException ignored) {
					// try the superclass
				} catch (RuntimeException ignored) {
					break;
				}
			}
		}
		return out.toArray(new Field[0]);
	}
}
