package dev.elsewhere.gear3.s3.reliquary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.slots.MinionSlotWeights;
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

/**
 * Echo Reliquary (GEAR.md A5, Summoner, Elite, G5; s-reliquary): a reliquary of the Mute Order of the Hush's Silent
 * Monasteries. Its 1 s call (10 Glimmer, like every staff) brings an Echo Knight: 2 minion slots, 60 HP, armor 16,
 * 10 damage every 1.2 s; its blows ignore 40% of the target's armor (on top of the minion rule) and it is silent: no
 * sound, no vibrations (sculk sensors, shriekers, Wardens and the Hush's noise bus never hear it or its blows).
 * Sources: Auris 30% (loot MODIFY on {@code entities/auris}, catalogue) and the Mute Archivist ({@link ReliquaryTrade}).
 * Kill switch: {@code "echo_reliquary": false} in config/elsewhere-overhaul3.json or -Delsewhere.overhaul3.echo_reliquary=false
 * (the call and the trade stop; the item stays registered so worlds keep it), or exclude the gear3 module.
 */
public final class EchoReliquary {
	private EchoReliquary() {}

	public static final String ITEM_ID = "echo_reliquary";
	public static final String ENTITY_ID = "echo_knight";

	// ---- GEAR.md A5 row: "2 slots: 60 HP, armor 16, 10 dmg / 1.2 s | hits ignore 40% armor; silent"
	public static final int SLOTS = 2;
	public static final double HP = 60, ARMOR = 16, DAMAGE = 10;
	public static final int ATTACK_TICKS = 24;
	/** Extra armor ignored by its blows, multiplied with the summoner's minion rule (GearStats.MINION_ARMOR_FACTOR). */
	public static final double ARMOR_IGNORE = 0.40;
	/** Not in the row: a heavy knight's walk (Rootling 0.30, Brassback 0.25), its reach and its footing. */
	public static final double SPEED = 0.28, REACH = 2.4, KNOCKBACK_RESISTANCE = 0.6;

	// ---- Mute Archivist offer (the Silent Monastery trader; Hushward Bell is 24 + a Cantor's Bell Shard, this is G5)
	public static final int TRADE_RESONANT_SHARDS = 32, TRADE_BELL_SHARDS = 1, TRADE_USES = 1, TRADE_XP = 30;
	public static final float TRADE_PRICE_MULT = 0.05F;

	/** Kill switch {@code echo_reliquary} (default on); read once at class load, nothing polls. */
	public static final boolean ON = on("echo_reliquary");

	public static EntityType<EchoKnight> ECHO_KNIGHT;
	private static boolean done;

	public static Identifier id(String path) {
		return Gear3Content.id(path);
	}

	/** Called once from Gear3Mechanics.init(): entity type, attributes, 2-slot weight, trade hook. */
	@SuppressWarnings("unchecked")
	public static void init() {
		if (done) return;
		done = true;
		Identifier rid = id(ENTITY_ID);
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(rid)) {
			ECHO_KNIGHT = (EntityType<EchoKnight>) BuiltInRegistries.ENTITY_TYPE.getValue(rid);
		} else {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, rid);
			ECHO_KNIGHT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(EchoKnight::new, MobCategory.MISC).sized(0.7F, 2.2F).eyeHeight(1.95F).clientTrackingRange(10).noSave().noLootTable().build(key));
			FabricDefaultAttributeRegistry.register(ECHO_KNIGHT, EchoKnight.createAttributes());
		}
		MinionSlotWeights.register(ECHO_KNIGHT, SLOTS);
		MinionSlotWeights.init();
		ReliquaryTrade.init();
	}

	static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (Files.isRegularFile(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception e) {
			// a missing or malformed file keeps the default
		}
		return true;
	}
}
