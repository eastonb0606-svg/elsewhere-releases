package dev.elsewhere.arsenal;

import dev.elsewhere.arsenal.combat.AttributeHooks;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.ArsenalEntities;
import dev.elsewhere.arsenal.trinket.Trinkets;
import net.minecraft.resources.Identifier;

/** Arsenal module entry point (ARSENAL.md): Glimmer, the new attributes, spells, caster gear, trinkets. */
public final class ArsenalContent {
	private ArsenalContent() {}

	public static final String NS = "elsewhere";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	public static void init() {
		ArsenalAttributes.init();
		ArsenalEffects.init();
		ArsenalEntities.init();
		ArsenalItems.init();
		Glimmer.init();
		dev.elsewhere.arsenal.spell.Calling.init();
		Trinkets.init();
		AttributeHooks.init();
		dev.elsewhere.arsenal.spell.GearV2.init();
		ArsenalLoot.init();
		dev.elsewhere.arsenal.spell.GlimmerMotes.init();
		dev.elsewhere.arsenal.spell.RepeaterItem.init();
		dev.elsewhere.arsenal.compat.TestMeterKills.init(); // s-compat-testmeter: Elsewhere bosses score as bosses in Test Meter
	}
}
