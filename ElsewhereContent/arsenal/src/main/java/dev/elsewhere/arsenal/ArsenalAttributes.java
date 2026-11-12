package dev.elsewhere.arsenal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * The 9 attributes of ARSENAL.md §2, registered in BuiltInRegistries.ATTRIBUTE under {@code elsewhere:} and added to
 * the player. Shared ids: if another module registered one first, its holder is reused (registry-collision rule).
 */
public final class ArsenalAttributes {
	private ArsenalAttributes() {}

	public static final List<Holder<Attribute>> ALL = new ArrayList<>();
	public static Holder<Attribute> MAX_GLIMMER, GLIMMER_REGEN, SPELL_POWER, SPELL_COST, PROJECTILE_PIERCE, MINION_SLOTS, MINION_DAMAGE, DODGE_CHANCE,
		KILL_HEAL;

	public static void init() {
		if (MAX_GLIMMER != null) return;
		MAX_GLIMMER = reg("max_glimmer", ArsenalStats.MAX_GLIMMER_BASE, ArsenalStats.MAX_GLIMMER_MIN, ArsenalStats.MAX_GLIMMER_MAX);
		GLIMMER_REGEN = reg("glimmer_regen", ArsenalStats.GLIMMER_REGEN);
		SPELL_POWER = reg("spell_power", ArsenalStats.SPELL_POWER);
		SPELL_COST = reg("spell_cost", ArsenalStats.SPELL_COST);
		PROJECTILE_PIERCE = reg("projectile_pierce", ArsenalStats.PROJECTILE_PIERCE);
		MINION_SLOTS = reg("minion_slots", ArsenalStats.MINION_SLOTS);
		MINION_DAMAGE = reg("minion_damage", ArsenalStats.MINION_DAMAGE);
		DODGE_CHANCE = reg("dodge_chance", ArsenalStats.DODGE_CHANCE);
		KILL_HEAL = reg("kill_heal", ArsenalStats.KILL_HEAL);
		FabricDefaultAttributeRegistry.MODIFY.register(ctx -> ctx.modify(EntityTypes.PLAYER, (type, builder) -> {
			for (Holder<Attribute> h : ALL) builder.add(h);
		}));
	}

	private static Holder<Attribute> reg(String name, double[] v) {
		return reg(name, v[0], v[1], v[2]);
	}

	private static Holder<Attribute> reg(String name, double base, double min, double max) {
		Identifier id = ArsenalContent.id(name);
		Optional<Holder.Reference<Attribute>> existing = BuiltInRegistries.ATTRIBUTE.get(id);
		Holder<Attribute> h = existing.isPresent() ? existing.get()
			: Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id,
				new RangedAttribute("attribute.name.elsewhere." + name, base, min, max).setSyncable(true));
		ALL.add(h);
		return h;
	}

	/** Attribute value that never throws (falls back to the attribute's default if the entity lacks it). */
	public static double value(LivingEntity e, Holder<Attribute> h) {
		AttributeInstance inst = e.getAttribute(h);
		return inst != null ? inst.getValue() : h.value().getDefaultValue();
	}
}
