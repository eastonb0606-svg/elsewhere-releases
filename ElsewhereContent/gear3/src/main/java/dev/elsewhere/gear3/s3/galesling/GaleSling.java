package dev.elsewhere.gear3.s3.galesling;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Gale Sling ({@code elsewhere:gale_sling}, GEAR.md A5 Marksman / Skirmisher, G2), 3.0 swarm item s-galesling.
 * <p>Hold use to whirl the sling overhead (0.5 s to full charge), release to loose the first ammo found (held stack first, then
 * the inventory, like a bow): cobblestone or gravel flies as a {@link SlingStone} for 4 damage at full charge (scaled by charge,
 * times the thrower's Marksman multiplier against non-players); a wind charge is flung as a vanilla wind charge, farther than by
 * hand. Source: the Windwright (Petalwind) trades it for 6 Zephyr Feathers + a String (as designed); the same is a crafting
 * recipe, and Breeze Temple vaults hold one (catalogue entry in tools/build_catalogue.py).
 * <p>Lag: nothing ticks besides the stone itself (a vanilla throwable); the trail and the launch gust are client-side particles
 * from the stone's own client tick; hits use one entity event. Kill switches: exclude the module {@code gear3} (the item);
 * {@code "gale_sling_trade": false} in {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.gale_sling_trade=false})
 * keeps the sling out of new Windwrights' trades, the only gear3 change to another module's content.
 */
public final class GaleSling {
	private GaleSling() {}

	public static final String ID = "gale_sling", STONE_ID = "gale_sling_stone", TRADE_KEY = "gale_sling_trade";
	// GEAR.md A5: "4 dmg, 0.5 s charge, cobblestone or gravel ammo; wind charge ammo launches a wind charge"
	public static final float DAMAGE = 4.0F;
	public static final int CHARGE_TICKS = 10;
	/** Below this charge (a 1-tick tap) nothing is loosed, like a bow's 0.1. */
	public static final float MIN_POWER = 0.1F;
	/** Launch speed in blocks per tick at full charge (bow 3.0, snowball 1.5, hand-thrown wind charge 1.5). */
	public static final float STONE_SPEED = 2.0F, WIND_SPEED = 2.25F, INACCURACY = 1.0F;
	public static final int RANGE = 15;
	// GEAR.md A5 source: "Windwright 6 Zephyr Feathers + string"
	public static final int TRADE_FEATHERS = 6, TRADE_USES = 4, TRADE_XP = 10;
	public static final float TRADE_PRICE_MULT = 0.05F;
	public static final Identifier STONE_MODEL = Gear3Content.id("gale_sling_stone"), GRAVEL_MODEL = Gear3Content.id("gale_sling_gravel");
	public static final Identifier ZEPHYR_FEATHER = Gear3Content.id("zephyr_feather");

	public static EntityType<SlingStone> STONE;
	public static SoundEvent WHIRL, LOOSE, HIT;

	private static boolean done;

	/** Entity type and sound events (one call from Gear3Content.init; idempotent). The item itself comes from the catalogue (mechanic "gale_sling"). */
	public static void init() {
		if (done) return;
		done = true;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Gear3Content.id(STONE_ID));
		STONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<SlingStone>of(SlingStone::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).noLootTable().build(key));
		WHIRL = sound("item.gale_sling.whirl");
		LOOSE = sound("item.gale_sling.loose");
		HIT = sound("entity.gale_sling_stone.hit");
	}

	private static SoundEvent sound(String path) {
		Identifier id = Gear3Content.id(path);
		if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) return BuiltInRegistries.SOUND_EVENT.getValue(id);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Charge 0..1 after {@code ticks} of whirling: the bow curve over 10 ticks instead of 20 (1.0 at 0.5 s). */
	public static float power(int ticks) {
		float f = ticks / (float) CHARGE_TICKS;
		f = (f * f + f * 2.0F) / 3.0F;
		return Math.min(f, 1.0F);
	}

	/** Stone damage before the Marksman multiplier: 4 at full charge, scaled by charge. */
	public static float stoneDamage(float power) {
		return DAMAGE * Math.max(0.0F, Math.min(1.0F, power));
	}

	// ---------------------------------------------------------------- Marksman (arsenal Calling, 3.0)

	private static Method marksman;
	private static boolean looked;

	/**
	 * The thrower's Marksman multiplier for a loosed shot: arsenal's 3.0 {@code Calling.marksmanMult(Player)} (1.0 unless a
	 * {@code #elsewhere:calling_weapon/marksman} item, such as this sling, is in either hand; 1.0 with the calling switches off).
	 * Arsenal applies it to arrows itself; a sling stone is not an arrow, so the stone carries it and applies it on a non-player hit.
	 * Soft link: gear3's dev classpath is the 2.0 jar, which has no such method; there it is 1.0.
	 */
	public static float marksmanMult(Player p) {
		Method m = marksmanMethod();
		if (m == null || p == null) return 1.0F;
		try {
			Object r = m.invoke(null, p);
			return r instanceof Float f && f > 0.0F ? f : 1.0F;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0F;
		}
	}

	/** True when arsenal's 3.0 Marksman multiplier was found (the merged 3.0 jar). */
	public static boolean marksmanLinked() {
		return marksmanMethod() != null;
	}

	private static Method marksmanMethod() {
		if (!looked) {
			looked = true;
			try {
				marksman = Class.forName("dev.elsewhere.arsenal.spell.Calling").getMethod("marksmanMult", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				marksman = null;
			}
		}
		return marksman;
	}

	// ---------------------------------------------------------------- Windwright trade (GEAR.md source)

	private static Boolean tradeOn;

	public static boolean tradeEnabled() {
		Boolean on = tradeOn;
		if (on == null) tradeOn = on = readFlag(TRADE_KEY);
		return on;
	}

	/** Self-test hook: force the trade switch (null = read the config again). */
	public static void setTradeEnabledForTest(Boolean on) {
		tradeOn = on;
	}

	/** The shared 3.0 switch file, read once (the same file and property scheme as every other 3.0 key). Default on. */
	static boolean readFlag(String key) {
		String sys = System.getProperty("elsewhere.overhaul3." + key);
		if (sys != null) return !"false".equalsIgnoreCase(sys.trim());
		try {
			Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (Files.isRegularFile(p)) {
				com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception ignored) {
			// missing or malformed config keeps the default (on)
		}
		return true;
	}

	/** The Windwright's Gale Sling offer, or empty when Petalwind's Zephyr Feather or the sling is not registered. */
	public static Optional<MerchantOffer> windwrightOffer() {
		Item sling = Gear3Items.BY_ID.get(ID);
		if (sling == null || !BuiltInRegistries.ITEM.containsKey(ZEPHYR_FEATHER)) return Optional.empty();
		Item feather = BuiltInRegistries.ITEM.getValue(ZEPHYR_FEATHER);
		return Optional.of(new MerchantOffer(new ItemCost(feather, TRADE_FEATHERS), Optional.of(new ItemCost(Items.STRING)), new ItemStack(sling),
			TRADE_USES, TRADE_XP, TRADE_PRICE_MULT));
	}

	/** Called once per Windwright when its trades are first made (mixin at the end of Windwright.updateTrades). */
	public static void addWindwrightTrade(AbstractVillager windwright) {
		if (!tradeEnabled()) return;
		windwrightOffer().ifPresent(windwright.getOffers()::add);
	}
}
