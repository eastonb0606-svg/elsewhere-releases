package dev.elsewhere.kingdom_court.api;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.ruler.CourtRulers;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * Leader role to custom boss entity. A faction's leader slots are the keys of its {@code rulers} map ({@code king, marshal,
 * castellan, master_at_arms, huntsman, chancellor}; the faction file's {@code society.leaders} picks the ones it uses, in rank order).
 * Register (in your mod's init, after your EntityType exists) the entity type that plays a slot; the court then spawns it at the
 * slot's post, tracks its death (drops from the file's {@code drops}, succession or Chair, seat refill, the fall, the brain-boss
 * event) and applies the ruler bonus to its attributes. A slot with no registration keeps Aldermark's built-in {@code CourtRuler}.
 */
public final class Leaders {
	private Leaders() {}

	/** Entity-tag prefix the court puts on every leader it spawns: {@code kingdom_court_leader:<faction>|<slot>}. */
	public static final String TAG = "kingdom_court_leader:";
	static final Identifier GATING = Identifier.fromNamespaceAndPath("elsewhere", "court_gating_damage");

	static final Map<String, EntityType<? extends Mob>> TYPES = new ConcurrentHashMap<>();

	public record Ref(Identifier faction, String slot) {}

	/** Optional interface for your boss entity: the court calls these on the server thread. */
	public interface LeaderBoss {
		/** Called right after the court created and positioned the entity, before it is added to the level. */
		default void onCourtSpawn(Identifier faction, String slot, @Nullable BlockPos home) {}

		/** The holder of the ruler bonus changed: this leader is now (or no longer) the monarch / the Chair (First Lantern). */
		default void onHolder(boolean holder) {}

		/** This boss is a Hrafnholt Risen champion (a promoted Hird-Captain): use only {@code movesAllowed} (2) of the seat boss's signature moves. */
		default void onRisen(int movesAllowed) {}
	}

	/** Registers the boss entity type that plays {@code slot} of {@code faction}. Call once, at mod init. */
	public static void register(Identifier faction, String slot, EntityType<? extends Mob> type) {
		TYPES.put(faction + "|" + slot, type);
	}

	public static @Nullable EntityType<? extends Mob> type(Identifier faction, String slot) {
		return TYPES.get(faction + "|" + slot);
	}

	public static boolean hasCustom(Identifier faction, String slot) {
		return TYPES.containsKey(faction + "|" + slot);
	}

	/** The faction and slot a court-spawned leader entity plays, or null. */
	public static @Nullable Ref of(Entity e) {
		for (String t : e.entityTags()) {
			if (!t.startsWith(TAG)) continue;
			String body = t.substring(TAG.length());
			int i = body.indexOf('|');
			Identifier f = i < 0 ? null : Identifier.tryParse(body.substring(0, i));
			if (f != null) return new Ref(f, body.substring(i + 1));
		}
		return null;
	}

	public static void tag(Entity e, Identifier faction, String slot) {
		e.addTag(TAG + faction + "|" + slot);
	}

	/**
	 * The faction's ruler bonus for the holder (the monarch or the Chair): applies max health and a damage multiplier to any
	 * mob (custom bosses included), exactly like Aldermark's rulers. Called by the court on every status change; safe to call again.
	 */
	public static void applyGating(Mob m, CourtState st, Ruler slot) {
		boolean holder = st.isHolder(slot);
		AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (hp == null) return;
		if (dmg != null) dmg.removeModifier(GATING);
		double base = CourtRulers.baseHealth(st.faction, slot);
		double want = base, dmgMul = 0;
		if (holder) {
			FactionDef def = st.def();
			var bonus = def == null ? FactionDef.RulerBonus.ALDERMARK : def.society().bonus();
			int n = st.livingLords() - (st.oligarchy() && st.alive(slot) ? 1 : 0);
			switch (bonus.type()) {
				case "hp_per_lord" -> {
					want = base + bonus.amount() * CourtRulers.hpScale(st.faction) * n;
					dmgMul = 0.04 * n;
				}
				case "hp_per_other_magnate" -> want = base + bonus.amount() * n;
				case "damage_per_champion" -> dmgMul = bonus.amount() * n;
				default -> {
				}
			}
			want = Math.min(want, Math.max(CourtRulers.healthCap(st.faction, slot), base));
		}
		if (hp.getBaseValue() != want) {
			hp.setBaseValue(want);
			if (m.getHealth() > m.getMaxHealth()) m.setHealth(m.getMaxHealth());
		}
		if (dmg != null && dmgMul > 0) dmg.addTransientModifier(new AttributeModifier(GATING, dmgMul, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		HpCap.clamp(m);
		if (m instanceof LeaderBoss lb) lb.onHolder(holder);
	}

	/** Test helper: is this living entity a court-tracked leader? */
	public static boolean isLeader(LivingEntity e) {
		return of(e) != null;
	}
}
