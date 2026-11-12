package dev.elsewhere.summoner;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Well Fed (Trail Biscuit on minions): +20% minion damage (applied in MinionApi), +4 armor. */
public final class SummonerEffects {
	private SummonerEffects() {}

	public static Holder<MobEffect> WELL_FED;
	/** Herded (Herding Crook mark): visual marker; the bonus and the minion focus come from dev.elsewhere.minion.Herding. */
	public static Holder<MobEffect> HERDED;

	static class Herded extends MobEffect {
		Herded() {
			super(MobEffectCategory.HARMFUL, 0xA77BF3);
		}
	}

	static class WellFed extends MobEffect {
		WellFed() {
			super(MobEffectCategory.BENEFICIAL, 0xE0A458);
		}
	}

	public static void init() {
		Identifier hid = SummonerContent.id("herded");
		HERDED = BuiltInRegistries.MOB_EFFECT.get(hid).map(h -> (Holder<MobEffect>) h).orElseGet(() -> Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, hid, new Herded()));
		Identifier id = SummonerContent.id("well_fed");
		var existing = BuiltInRegistries.MOB_EFFECT.get(id);
		if (existing.isPresent()) {
			WELL_FED = existing.get();
			return;
		}
		WELL_FED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id, new WellFed()
			.addAttributeModifier(Attributes.ARMOR, SummonerContent.id("effect.well_fed.armor"), GearStats.WELL_FED_ARMOR, AttributeModifier.Operation.ADD_VALUE));
	}
}
