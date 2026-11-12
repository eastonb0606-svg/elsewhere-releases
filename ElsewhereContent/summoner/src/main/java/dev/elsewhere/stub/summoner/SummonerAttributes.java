package dev.elsewhere.stub.summoner;

import dev.elsewhere.summoner.GearStats;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.jspecify.annotations.Nullable;

/**
 * STUB: arsenal owns all 9 ARSENAL.md attributes. This registers the two the minion framework reads
 * ({@code elsewhere:minion_slots}, {@code elsewhere:minion_damage}) only if nobody did yet, so the dev module works
 * alone; {@code PlayerAttributesMixin} attaches them to players. At merge, drop this class and the mixin.
 */
public final class SummonerAttributes {
	private SummonerAttributes() {}

	public static final Identifier MINION_SLOTS_ID = Identifier.fromNamespaceAndPath("elsewhere", "minion_slots");
	public static final Identifier MINION_DAMAGE_ID = Identifier.fromNamespaceAndPath("elsewhere", "minion_damage");
	/** Read by the summoner pieces' own-damage con (spell_power -20%); registered here too so init order never matters. */
	public static final Identifier SPELL_POWER_ID = Identifier.fromNamespaceAndPath("elsewhere", "spell_power");
	/** Bell of the Flock +6 (rev 4); same parameters as arsenal's (base 20, 0-100). */
	public static final Identifier MAX_GLIMMER_ID = Identifier.fromNamespaceAndPath("elsewhere", "max_glimmer");
	private static @Nullable Holder<Attribute> spellPower, maxGlimmer;

	private static @Nullable Holder<Attribute> slots, damage;

	public static void init() {
		slots = getOrRegister(MINION_SLOTS_ID, new RangedAttribute("attribute.name.elsewhere.minion_slots", GearStats.BASE_MINION_SLOTS, 0.0, GearStats.HARD_SLOT_CAP).setSyncable(true));
		damage = getOrRegister(MINION_DAMAGE_ID, new RangedAttribute("attribute.name.elsewhere.minion_damage", 1.0, 0.0, 4.0).setSyncable(true));
		spellPower = getOrRegister(SPELL_POWER_ID, new RangedAttribute("attribute.name.elsewhere.spell_power", 1.0, 0.0, 4.0).setSyncable(true));
		maxGlimmer = getOrRegister(MAX_GLIMMER_ID, new RangedAttribute("attribute.name.elsewhere.max_glimmer", 20.0, 0.0, 100.0).setSyncable(true));
	}

	private static Holder<Attribute> getOrRegister(Identifier id, Attribute attr) {
		var existing = BuiltInRegistries.ATTRIBUTE.get(id);
		if (existing.isPresent()) return existing.get();
		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id, attr);
	}

	/** The holder by id lookup (works whether this stub or arsenal registered it). */
	public static @Nullable Holder<Attribute> slots() {
		if (slots == null) slots = BuiltInRegistries.ATTRIBUTE.get(MINION_SLOTS_ID).map(h -> (Holder<Attribute>) h).orElse(null);
		return slots;
	}

	public static @Nullable Holder<Attribute> spellPower() {
		if (spellPower == null) spellPower = BuiltInRegistries.ATTRIBUTE.get(SPELL_POWER_ID).map(h -> (Holder<Attribute>) h).orElse(null);
		return spellPower;
	}

	public static @Nullable Holder<Attribute> maxGlimmer() {
		if (maxGlimmer == null) maxGlimmer = BuiltInRegistries.ATTRIBUTE.get(MAX_GLIMMER_ID).map(h -> (Holder<Attribute>) h).orElse(null);
		return maxGlimmer;
	}

	public static @Nullable Holder<Attribute> damage() {
		if (damage == null) damage = BuiltInRegistries.ATTRIBUTE.get(MINION_DAMAGE_ID).map(h -> (Holder<Attribute>) h).orElse(null);
		return damage;
	}
}
