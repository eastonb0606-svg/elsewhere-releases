package dev.elsewhere.greenhaze;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Soft tie-in with Green Leaf (mod id {@code greenleaf}): no compile dependency. Items are detected by registry
 * namespace. The high meter is reached through Green Leaf's stable API ({@code dev.greenleaf.api.GreenLeafApi}, Green Leaf
 * 2.1+), so every Elsewhere source (the Greenhaze's haze, vents, smog, Percolax, the Everflow Bong) goes through Green Leaf's
 * own dose rules: tolerance, strains and the session cap/comedown apply, and nothing here can keep a player high forever.
 * Older Green Leaf jars without the API are still reached by reflection on the per-player data
 * ({@code HighHolder.greenleaf$data()} / {@code GreenLeaf.data(player)} with public {@code high} / {@code pending} fields).
 * If neither works, the bridge quietly falls back to the Greenhaze's own Haze meter.
 */
public final class GreenLeafBridge {
	private GreenLeafBridge() {}

	public static final String MOD_ID = "greenleaf";
	private static Boolean loaded;
	private static boolean reflectionFailed;
	private static @Nullable Method dataGetter;
	private static boolean staticGetter;
	private static @Nullable Field highField, pendingField;
	/** Green Leaf 2.1+ API: high(ServerPlayer), dose(ServerPlayer, double, String), lower(ServerPlayer, double). */
	private static boolean apiLooked;
	private static @Nullable Method apiHigh, apiDose, apiLower, apiContactRadius, apiContactAmount, apiRefusesHits;

	public static boolean loaded() {
		if (loaded == null) loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
		return loaded;
	}

	/** Testing hook. */
	public static void forceLoaded(Boolean value) {
		loaded = value;
	}

	public static boolean isGreenLeafItem(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return MOD_ID.equals(id.getNamespace());
	}

	public static boolean isGreenLeafBlock(BlockState state) {
		return MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
	}

	/** A lit smokable that can be passed (joint, blunt, vape) in any strain. */
	public static boolean isSmokable(ItemStack stack) {
		if (!isGreenLeafItem(stack)) return false;
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("joint") || path.contains("blunt") || path.contains("vape") || path.contains("bong");
	}

	public static boolean isBud(ItemStack stack) {
		return isGreenLeafItem(stack) && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_bud");
	}

	/** "sativa" / "indica" / "hybrid" for a Green Leaf bud ({@code greenleaf:<strain>_bud}), else null. */
	public static @Nullable String budStrain(ItemStack stack) {
		if (!isBud(stack)) return null;
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.substring(0, path.length() - "_bud".length());
	}

	/**
	 * Kill switch {@code greenleaf_api} (default on; {@code "greenleaf_api": false} in config/elsewhere-overhaul3.json or
	 * {@code -Delsewhere.overhaul3.greenleaf_api=false}): off = the 2.x field path (smoke added straight to "pending").
	 */
	public static final boolean API_ON = overhaul3("greenleaf_api");

	/** True when Green Leaf's stable API is present (Green Leaf 2.1+) and the switch is on. */
	public static boolean hasApi() {
		if (!API_ON || !loaded()) return false;
		lookUpApi();
		return apiHigh != null && apiDose != null && apiLower != null;
	}

	private static boolean overhaul3(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			java.nio.file.Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (java.nio.file.Files.isRegularFile(p)) {
				com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p)).getAsJsonObject();
				if (o.has(key)) return o.get(key).getAsBoolean();
			}
		} catch (Exception e) {
			// a missing or malformed file keeps the default
		}
		return true;
	}

	private static void lookUpApi() {
		if (apiLooked) return;
		apiLooked = true;
		try {
			Class<?> api = Class.forName("dev.greenleaf.api.GreenLeafApi");
			apiHigh = api.getMethod("high", ServerPlayer.class);
			apiDose = api.getMethod("dose", ServerPlayer.class, double.class, String.class);
			apiLower = api.getMethod("lower", ServerPlayer.class, double.class);
			apiContactRadius = api.getMethod("contactRadius");
			apiContactAmount = api.getMethod("contactAmount");
		} catch (Throwable t) {
			apiHigh = apiDose = apiLower = apiContactRadius = apiContactAmount = null; // an older Green Leaf: the field path below still works
		}
		try {
			apiRefusesHits = Class.forName("dev.greenleaf.api.GreenLeafApi").getMethod("refusesHits", ServerPlayer.class); // API version 2
		} catch (Throwable t) {
			apiRefusesHits = null;
		}
	}

	/**
	 * True while Green Leaf refuses every hit (2.1's session cap come-down): its own joints and bong do nothing then, so the
	 * Everflow Bong must not burn a bud and a refused puff must not count as a Green Leaf use (trips, Hush cough). Reads the
	 * API, or the {@code comedown} field and {@code sessionCap} config on a 2.1 jar without it; false on Green Leaf 2.0.
	 */
	public static boolean refusesHits(ServerPlayer p) {
		if (hasApi() && apiRefusesHits != null) {
			try {
				return (boolean) apiRefusesHits.invoke(null, p);
			} catch (Throwable t) {
				return false;
			}
		}
		Object d = data(p);
		if (d == null) return false;
		try {
			if (!d.getClass().getField("comedown").getBoolean(d)) return false;
			Object cfg = Class.forName("dev.greenleaf.GreenLeaf").getField("config").get(null);
			return cfg.getClass().getField("sessionCap").getBoolean(cfg);
		} catch (Throwable t) {
			return false; // Green Leaf 2.0: no come-down
		}
	}

	private static @Nullable Object data(ServerPlayer p) {
		if (!loaded() || reflectionFailed) return null;
		try {
			if (dataGetter == null) {
				try {
					dataGetter = p.getClass().getMethod("greenleaf$data");
				} catch (NoSuchMethodException e) {
					Class<?> main = Class.forName("dev.greenleaf.GreenLeaf");
					dataGetter = main.getMethod("data", ServerPlayer.class);
					staticGetter = true;
				}
			}
			Object d = staticGetter ? dataGetter.invoke(null, p) : dataGetter.invoke(p);
			if (d != null && highField == null) {
				highField = d.getClass().getField("high");
				pendingField = d.getClass().getField("pending");
			}
			return d;
		} catch (Throwable t) {
			reflectionFailed = true;
			dev.elsewhere.greenhaze_hush.GreenhazeHushContent.LOGGER.warn("Green Leaf is loaded but its high meter could not be reached; using the Greenhaze's own Haze meter", t);
			return null;
		}
	}

	/** Green Leaf high (0-100), or -1 when it can't be read. */
	public static double high(ServerPlayer p) {
		if (hasApi()) {
			try {
				return ((Number) apiHigh.invoke(null, p)).doubleValue();
			} catch (Throwable t) {
				return -1;
			}
		}
		Object d = data(p);
		if (d == null || highField == null) return -1;
		try {
			return ((Number) highField.get(d)).doubleValue();
		} catch (Throwable t) {
			return -1;
		}
	}

	/**
	 * Adds to Green Leaf's high in the strain of the player's current high (haze, smog, contact smoke), or lowers it for a
	 * negative amount. Returns false when Green Leaf can't be reached.
	 */
	public static boolean addHigh(ServerPlayer p, double amount) {
		return dose(p, amount, null);
	}

	/**
	 * A hit in a given strain ("sativa", "indica", "hybrid"; null = the current high's strain). With Green Leaf 2.1+ this is
	 * Green Leaf's own dose (tolerance, strain, session cap); older jars get it as smoke still kicking in. Negative amounts
	 * lower the high. Returns false when Green Leaf can't be reached.
	 */
	public static boolean dose(ServerPlayer p, double amount, @Nullable String strain) {
		if (hasApi()) {
			try {
				if (amount >= 0) apiDose.invoke(null, p, amount, strain == null ? "" : strain);
				else apiLower.invoke(null, p, -amount);
				return true;
			} catch (Throwable t) {
				return false;
			}
		}
		Object d = data(p);
		if (d == null || highField == null || pendingField == null) return false;
		try {
			if (amount >= 0) {
				pendingField.setDouble(d, pendingField.getDouble(d) + amount);
			} else {
				highField.setDouble(d, Math.max(0, highField.getDouble(d) + amount));
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Second-hand smoke from a hit {@code smoker} just took, like Green Leaf's own bong: every other player within Green Leaf's
	 * contact radius (times {@code radiusFactor}) catches one contact hit in {@code strain}, unless {@code immune} says no
	 * (Frond set, Hotbox Visor). Players are the only targets: mobs, soldiers and townsfolk have no high. Returns how many.
	 */
	public static int contactAround(ServerPlayer smoker, double radiusFactor, @Nullable String strain, java.util.function.Predicate<ServerPlayer> immune) {
		if (!usable()) return 0;
		double radius = 4, amount = 1.5;
		if (hasApi() && apiContactRadius != null && apiContactAmount != null) {
			try {
				radius = ((Number) apiContactRadius.invoke(null)).doubleValue();
				amount = ((Number) apiContactAmount.invoke(null)).doubleValue();
			} catch (Throwable t) {
				// keep Green Leaf's defaults
			}
		}
		double r = radius * radiusFactor;
		int reached = 0;
		for (ServerPlayer other : smoker.level().players()) {
			if (other == smoker || other.isSpectator() || other.distanceToSqr(smoker) > r * r || immune.test(other)) continue;
			if (dose(other, amount, strain)) reached++;
		}
		return reached;
	}

	public static boolean usable() {
		return loaded() && (hasApi() || !reflectionFailed);
	}
}
