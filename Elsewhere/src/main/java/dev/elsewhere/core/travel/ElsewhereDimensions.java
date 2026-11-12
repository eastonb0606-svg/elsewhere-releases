package dev.elsewhere.core.travel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Every Elsewhere dimension registers one {@link Info} here (Pale Hollow does it in {@code PaleHollow.init()}).
 * Rifts, Return Rifts, safe landing and accidental trips read their per-dimension settings from it.
 */
public final class ElsewhereDimensions {
	/**
	 * @param color         rift, beam and particle colour (0xRRGGBB)
	 * @param maxLandingY   safe-landing search ignores ground above this (e.g. world-trunk tops)
	 * @param platformBlock what an emergency landing platform is made of
	 * @param returnRifts   whether Return Rifts generate and landings are pulled toward them (false for The Hush)
	 */
	public record Info(ResourceKey<Level> key, String name, int color, int maxLandingY, Block platformBlock, boolean returnRifts) {
	}

	private static final Map<ResourceKey<Level>, Info> DIMENSIONS = new LinkedHashMap<>();

	private ElsewhereDimensions() {
	}

	public static Info register(final Info info) {
		DIMENSIONS.put(info.key(), info);
		return info;
	}

	public static @Nullable Info get(final ResourceKey<Level> key) {
		return DIMENSIONS.get(key);
	}

	public static boolean isElsewhere(final ResourceKey<Level> key) {
		return DIMENSIONS.containsKey(key);
	}

	public static Collection<Info> all() {
		return DIMENSIONS.values();
	}

	public static int colorOf(final ResourceKey<Level> key) {
		Info info = DIMENSIONS.get(key);
		return info == null ? 0xB89CFF : info.color();
	}

	public static Block platformOf(final ResourceKey<Level> key) {
		Info info = DIMENSIONS.get(key);
		return info == null ? Blocks.COBBLESTONE : info.platformBlock();
	}
}
