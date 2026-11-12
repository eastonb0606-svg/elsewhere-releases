package dev.elsewhere.gear3.s3.totems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;

/**
 * gear3 totems (GEAR.md: "totems are placed minions that don't move (1 slot each, max 2)"). Three placed minions on the
 * summoner module's minion API (MinionEntity / MinionManager: Glimmer cost, slot cap, upkeep timer, Summoner Calling
 * damage, owner protection, Herd focus):
 * <ul>
 * <li>{@code bramble_totem} (Greenhaze, Swarm, G2): every 2 s lashes up to 3 mobs within 5 blocks for 2 + Slowness I 1 s.
 *     Sold by the Budtender only (12 Haze Resin + 4 Giant Fern, 2 per Budtender). Gag: the haze pollen makes every
 *     4th lashed mob sneeze (sound + particles only).</li>
 * <li>{@code clockwork_sentry} (Verdigris, Sentry, G3): 40 HP, armor 8; a 3-damage bolt every second, range 16.</li>
 * <li>{@code azmar_sunglass_obelisk} (Azmar, Sentry, G4): 50 HP; a sunbeam on one target, 4 damage/s, 8/s after 3 s on
 *     the same target; daylight or bright light (12+) only, dormant in the dark.</li>
 * </ul>
 * Every totem works on its own per-entity timer (no global polling). Max 2 totems per player (the oldest crumbles),
 * 48-block leash (a totem whose owner walks off crumbles), sneak-use the item to take your totems of that kind down.
 * <p>Kill switch: {@code "gear3_totems": false} in {@code config/elsewhere-overhaul3.json} (or
 * {@code -Delsewhere.overhaul3.gear3_totems=false}): the items place nothing and the Budtender stops selling the
 * Bramble Totem. Registry ids stay.
 */
public final class Totems {
	private Totems() {}

	public static final String KEY = "gear3_totems";
	/** Read once at class load; not final only so the self-test can sweep it. */
	public static volatile boolean enabled = on(KEY);

	public static final String BRAMBLE_ID = "bramble_totem", SENTRY_ID = "clockwork_sentry", OBELISK_ID = "azmar_sunglass_obelisk";

	/** GEAR.md: at most 2 totems per player (each takes 1 normal minion slot as well). */
	public static final int MAX_TOTEMS = 2;
	/** A totem whose owner is further than this crumbles (checked on its 1 s upkeep tick). */
	public static final double LEASH = 48.0;
	/** Item cooldown after a placement (ticks). */
	public static final int PLACE_COOLDOWN = 40;

	public static EntityType<BrambleTotem> BRAMBLE;
	public static EntityType<ClockworkSentry> SENTRY;
	public static EntityType<SunglassObelisk> OBELISK;

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		BRAMBLE = reg(BRAMBLE_ID, BrambleTotem::new, 0.7F, 1.25F, 1.0F, TotemEntity.attributes(BrambleTotem.HP, BrambleTotem.ARMOR));
		SENTRY = reg(SENTRY_ID, ClockworkSentry::new, 0.8F, 1.1F, 0.9F, TotemEntity.attributes(ClockworkSentry.HP, ClockworkSentry.ARMOR));
		OBELISK = reg(OBELISK_ID, SunglassObelisk::new, 0.7F, 1.9F, (float) SunglassObelisk.LENS_Y, TotemEntity.attributes(SunglassObelisk.HP, SunglassObelisk.ARMOR));
		TotemTrades.init();
	}

	@SuppressWarnings("unchecked")
	private static <T extends TotemEntity> EntityType<T> reg(String id, EntityType.EntityFactory<T> f, float w, float h, float eye, AttributeSupplier.Builder attrs) {
		Identifier rid = Gear3Content.id(id);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
		EntityType<T> t = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<T>of(f, MobCategory.MISC).sized(w, h).eyeHeight(eye).clientTrackingRange(10).noSave().noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(t, attrs);
		return t;
	}

	/** The totem a catalogue item places (null for any other item). */
	public static EntityType<? extends TotemEntity> typeFor(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		if (id == null) return null;
		if (id.equals(Gear3Content.id(BRAMBLE_ID))) return BRAMBLE;
		if (id.equals(Gear3Content.id(SENTRY_ID))) return SENTRY;
		if (id.equals(Gear3Content.id(OBELISK_ID))) return OBELISK;
		return null;
	}

	// ---------------------------------------------------------------- kill switch (read once, at class load; nothing polls)

	private static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return true;
			JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			return !o.has(key) || o.get(key).getAsBoolean();
		} catch (Exception e) {
			return true; // a missing or malformed file keeps the default (on)
		}
	}
}
