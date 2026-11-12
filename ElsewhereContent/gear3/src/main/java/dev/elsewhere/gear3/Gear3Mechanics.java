package dev.elsewhere.gear3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.item.Item;

/**
 * Unique mechanics. A catalogue entry names its mechanic ({@code "mechanic": "key"}); {@link #FACTORIES} maps the key to
 * the Item class (for use / tool behaviour), and {@link #init()} registers the event hooks. Rule: event-driven or
 * cooldown-based only, never a per-tick scan.
 */
public final class Gear3Mechanics {
	private Gear3Mechanics() {}

	public static final Map<String, Function<Item.Properties, Item>> FACTORIES = new HashMap<>();

	static {
		FACTORIES.put("chisel", ChiselItem::new);
		FACTORIES.put("amber_grimoire", SpellItems.AmberGrimoire::new);
		FACTORIES.put("chalice", SpellItems.AlmonersChalice::new);
		FACTORIES.put("ka_sigil", SpellItems.KaSigil::new);
		FACTORIES.put("glimmer_lantern", SpellItems.GlimmerLantern::new);
		FACTORIES.put("blinkstone", SpellItems.Blinkstone::new);
		FACTORIES.put("emberfall", SpellItems.EmberfallRod::new);
		FACTORIES.put("frostwake", SpellItems.FrostwakeScepter::new);
		FACTORIES.put("orrery", SpellItems.StarfallOrrery::new);
		FACTORIES.put("mead_horn", MeadHornItem::new);
		FACTORIES.put("whip", dev.elsewhere.summoner.GaleRibbonItem::new);
		FACTORIES.put("crook", dev.elsewhere.summoner.HerdingCrookItem::new);
		FACTORIES.put("echo_arrow", dev.elsewhere.gear3.s3.echoarrow.EchoArrowItem::new); // s-echoarrow
	}

	public static void init() {
		Gear3Scheduler.init();
	}

	public static Function<Item.Properties, Item> factory(String key) {
		if (key == null || key.isEmpty()) return Item::new;
		Function<Item.Properties, Item> f = FACTORIES.get(key);
		if (f == null) throw new IllegalStateException("catalogue mechanic '" + key + "' has no registered item class");
		return f;
	}
}
