package dev.elsewhere.arsenal;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.alchemy.Potion;

/** Clarity (Clarity Draught) and Dazed (Glimmer Tonics), plus the two Clarity potions (brewing is data: recipe/brewing_*.json). */
public final class ArsenalEffects {
	private ArsenalEffects() {}

	public static Holder<MobEffect> CLARITY, DAZED;
	public static Holder<Potion> CLARITY_DRAUGHT, LONG_CLARITY_DRAUGHT;

	static class Effect extends MobEffect {
		Effect(MobEffectCategory c, int color) {
			super(c, color);
		}
	}

	public static void init() {
		CLARITY = effect("clarity", () -> new Effect(MobEffectCategory.BENEFICIAL, 0xA77BF3)
			.addAttributeModifier(ArsenalAttributes.SPELL_COST, ArsenalContent.id("effect.clarity.cost"), ArsenalStats.CLARITY_COST,
				AttributeModifier.Operation.ADD_VALUE)
			.addAttributeModifier(ArsenalAttributes.GLIMMER_REGEN, ArsenalContent.id("effect.clarity.regen"), ArsenalStats.CLARITY_REGEN,
				AttributeModifier.Operation.ADD_VALUE));
		DAZED = effect("dazed", () -> new Effect(MobEffectCategory.HARMFUL, 0x6B45B8));
		CLARITY_DRAUGHT = potion("clarity_draught", new Potion("clarity_draught", new MobEffectInstance(CLARITY, ArsenalStats.CLARITY_TICKS)));
		LONG_CLARITY_DRAUGHT = potion("long_clarity_draught",
			new Potion("clarity_draught", new MobEffectInstance(CLARITY, ArsenalStats.LONG_CLARITY_TICKS)));
	}

	private static Holder<MobEffect> effect(String name, java.util.function.Supplier<MobEffect> f) {
		Identifier id = ArsenalContent.id(name);
		var existing = BuiltInRegistries.MOB_EFFECT.get(id);
		if (existing.isPresent()) return existing.get();
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id, f.get());
	}

	private static Holder<Potion> potion(String name, Potion p) {
		Identifier id = ArsenalContent.id(name);
		var existing = BuiltInRegistries.POTION.get(id);
		if (existing.isPresent()) return existing.get();
		return Registry.registerForHolder(BuiltInRegistries.POTION, id, p);
	}
}
