package dev.elsewhere.verdigris_ironpeak.world;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * A structure built in Java, block by block, relative to an origin. The same blueprint is used by world generation
 * (clipped to one chunk at a time) and by the dev commands / boss summons (placed straight into the level).
 */
public interface Blueprint {
	Map<String, Blueprint> ALL = new HashMap<>();

	/** Horizontal half-size, height above and depth below the origin (for bounding boxes). */
	int radius();

	int height();

	default int depth() {
		return 6;
	}

	void build(BlockSink sink, RandomSource random);

	static Blueprint register(String name, Blueprint b) {
		ALL.put(name, b);
		return b;
	}

	/**
	 * 3.0 terrain-fit versions (STRUCTURES.md section 4), used only by pieces saved with {@code v >= 2}. Pieces saved by 2.x keep
	 * the {@link #ALL} blueprint they began with, so a half-generated old start finishes exactly as it started.
	 */
	Map<String, Blueprint> FIT = new HashMap<>();
}
