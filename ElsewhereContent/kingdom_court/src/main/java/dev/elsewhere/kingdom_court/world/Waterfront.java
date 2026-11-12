package dev.elsewhere.kingdom_court.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * RIVALS-DECISIONS 16a: the waterfront of a seafaring realm (Hrafnholt): a plank jetty from the castle down to the water, a boathouse beside it and one or two
 * moored longships built from blocks (hull, mast, sail, shield rail). It runs along one of the four compass directions ({@code dir}: 0 east, 1 south, 2 west, 3 north)
 * towards the water, {@code shore} blocks from the castle centre. Only air and water blocks are ever replaced (no flooding, nothing of the land or of other builds is touched),
 * and every write is clipped to the chunk being generated like the rest of the realm. Styled from the optional {@code waterfront} object of {@code kingdom_castle/<faction>.json}
 * (jetty / boathouse / longship blocks); spruce, dark oak and wool defaults otherwise.
 */
public final class Waterfront {
	private Waterfront() {}

	public static final int U_MIN = 36, AFTER_SHORE = 16, V_MIN = -18, V_MAX = 12;

	/** The area the waterfront may touch (castle side to 16 blocks past the shore, 31 wide); also the chunks that must be fresh when the site is chosen. */
	public static BoundingBox box(BlockPos o, int dir, int shore, int sea) {
		int u1 = U_MIN - 2, u2 = shore + AFTER_SHORE;
		int[] a = world(o, dir, u1, V_MIN), b = world(o, dir, u2, V_MAX);
		return new BoundingBox(Math.min(a[0], b[0]), sea - 10, Math.min(a[1], b[1]), Math.max(a[0], b[0]), sea + 9, Math.max(a[1], b[1]));
	}

	static int[] world(BlockPos o, int dir, int u, int v) {
		return switch (Math.floorMod(dir, 4)) {
			case 0 -> new int[] {o.getX() + u, o.getZ() + v};
			case 1 -> new int[] {o.getX() - v, o.getZ() + u};
			case 2 -> new int[] {o.getX() - u, o.getZ() - v};
			default -> new int[] {o.getX() + v, o.getZ() - u};
		};
	}

	/** Blocks of the waterfront, from the style object or the defaults. */
	public static final class Blocks_ {
		Block planks = Blocks.SPRUCE_PLANKS, slab = Blocks.SPRUCE_SLAB, post = Blocks.DARK_OAK_LOG, fence = Blocks.SPRUCE_FENCE, light = Blocks.LANTERN;
		Block bhWall = Blocks.DARK_OAK_PLANKS, bhFrame = Blocks.DARK_OAK_LOG, bhRoof = Blocks.SPRUCE_SLAB, bhFloor = Blocks.SPRUCE_PLANKS;
		Block hull = Blocks.SPRUCE_PLANKS, keel = Blocks.DARK_OAK_LOG, rail = Blocks.SPRUCE_FENCE, trim = Blocks.DARK_OAK_SLAB, stem = Blocks.DARK_OAK_LOG, mast = Blocks.SPRUCE_FENCE;
		Block sail1 = named("minecraft:green_wool"), sail2 = named("minecraft:brown_wool"), shield = named("minecraft:green_banner"), shield2 = named("minecraft:brown_banner");
	}

	public static Blocks_ parse(@Nullable JsonObject o) {
		Blocks_ b = new Blocks_();
		if (o == null) return b;
		JsonObject j = sub(o, "jetty"), h = sub(o, "boathouse"), s = sub(o, "longship");
		b.planks = get(j, "planks", b.planks);
		b.slab = get(j, "slab", b.slab);
		b.post = get(j, "post", b.post);
		b.fence = get(j, "fence", b.fence);
		b.light = get(j, "light", b.light);
		b.bhWall = get(h, "wall", b.bhWall);
		b.bhFrame = get(h, "frame", b.bhFrame);
		b.bhRoof = get(h, "roof_slab", b.bhRoof);
		b.bhFloor = get(h, "floor", b.bhFloor);
		b.hull = get(s, "hull", b.hull);
		b.keel = get(s, "keel", b.keel);
		b.rail = get(s, "rail", b.rail);
		b.trim = get(s, "trim", b.trim);
		b.stem = get(s, "stem", b.stem);
		b.mast = get(s, "mast", b.mast);
		b.shield = get(s, "shield", b.shield);
		b.shield2 = get(s, "shield_alt", b.shield2);
		if (s != null && s.has("sail") && s.get("sail").isJsonArray()) {
			var arr = s.getAsJsonArray("sail");
			if (arr.size() > 0) b.sail1 = blk(arr.get(0), b.sail1);
			if (arr.size() > 1) b.sail2 = blk(arr.get(1), b.sail2);
			else b.sail2 = b.sail1;
		}
		return b;
	}

	static Block named(String id) {
		Block b = CastleStyles.block(id);
		return b == null ? Blocks.SPRUCE_PLANKS : b;
	}

	static @Nullable JsonObject sub(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
	}

	static Block get(@Nullable JsonObject o, String k, Block def) {
		return o == null || !o.has(k) ? def : blk(o.get(k), def);
	}

	static Block blk(JsonElement e, Block def) {
		if (!e.isJsonPrimitive()) return def;
		Block b = CastleStyles.block(e.getAsString());
		return b == null ? def : b;
	}

	/** Builds the waterfront into the placer's chunk. {@code sea}: sea level (water fills up to sea - 1, the deck is at {@code sea}). */
	public static void build(CastleLayout.Placer pl, BlockPos o, int dir, int shore, int sea, Blocks_ b, int variant) {
		int deckA = Math.max(U_MIN, shore - 16), deckB = shore + 14;
		// jetty: 5 wide plank deck, pilings every 4, fence posts with lanterns
		for (int u = deckA; u <= deckB; u++) {
			for (int v = -2; v <= 2; v++) put(pl, o, dir, u, sea, v, b.planks);
			if ((u - deckA) % 4 == 0) {
				for (int v : new int[] {-2, 2}) {
					for (int y = sea - 1; y >= sea - 9; y--) if (!put(pl, o, dir, u, y, v, b.post)) break;
					put(pl, o, dir, u, sea + 1, v, b.fence);
					if (u >= shore) put(pl, o, dir, u, sea + 2, v, b.light);
				}
			}
		}
		// boathouse beside the jetty (open to the water end)
		int bu1 = shore - 6, bu2 = shore + 4;
		for (int u = bu1; u <= bu2; u++) for (int v = 4; v <= 10; v++) {
			put(pl, o, dir, u, sea, v, b.bhFloor);
			boolean edgeV = v == 4 || v == 10, back = u == bu1, corner = (v == 4 || v == 10) && (u == bu1 || u == bu2);
			if (corner) {
				for (int y = sea - 1; y >= sea - 9; y--) if (!put(pl, o, dir, u, y, v, b.bhFrame)) break;
				for (int y = sea + 1; y <= sea + 4; y++) put(pl, o, dir, u, y, v, b.bhFrame);
			} else if (edgeV || back) for (int y = sea + 1; y <= sea + 3; y++) put(pl, o, dir, u, y, v, b.bhWall);
			put(pl, o, dir, u, sea + 5, v, b.bhRoof);
		}
		// one or two moored longships on the other side of the jetty
		longship(pl, o, dir, shore + 2, -7, sea, b);
		if ((variant & 1) == 0) longship(pl, o, dir, shore + 4, -14, sea, b);
	}

	static void longship(CastleLayout.Placer pl, BlockPos o, int dir, int u0, int vc, int sea, Blocks_ b) {
		int len = 13;
		int[] half = {0, 0, 1, 1, 2, 2, 2, 2, 2, 1, 1, 0, 0};
		for (int i = 0; i < len; i++) {
			int u = u0 + i, w = half[i];
			for (int v = -w; v <= w; v++) put(pl, o, dir, u, sea - 1, vc + v, v == 0 ? b.keel : b.hull);
			if (w > 0) {
				for (int s : new int[] {-w, w}) {
					put(pl, o, dir, u, sea, vc + s, b.hull);
					put(pl, o, dir, u, sea + 1, vc + s, b.trim);
					if (i >= 3 && i <= 9 && w == 2 && i % 2 == 1) put(pl, o, dir, u, sea + 2, vc + s, ((i / 2) & 1) == 0 ? b.shield : b.shield2);
				}
			}
		}
		// curved stems at both ends, a rail along the sides
		for (int[] st : new int[][] {{0, 3}, {1, 1}, {12, 3}, {11, 1}}) for (int y = 0; y <= st[1]; y++) put(pl, o, dir, u0 + st[0], sea + y, vc, b.stem);
		for (int i = 2; i <= 10; i += 4) for (int s : new int[] {-1, 1}) put(pl, o, dir, u0 + i, sea + 2, vc + s * half[i], b.rail);
		// mast, yard and a striped square sail
		int mu = u0 + 6;
		for (int y = 0; y <= 7; y++) put(pl, o, dir, mu, sea + y, vc, b.mast);
		for (int v = -2; v <= 2; v++) {
			put(pl, o, dir, mu, sea + 7, vc + v, b.mast);
			for (int y = 2; y <= 6; y++) if (v != 0 || true) put(pl, o, dir, mu + 1, sea + y, vc + v, (v + 2) % 2 == 0 ? b.sail1 : b.sail2);
		}
	}

	/** Writes {@code block} at local (u, y, v) if the cell is inside the chunk being generated and holds only air or water; true if the cell was free (placed). */
	static boolean put(CastleLayout.Placer pl, BlockPos o, int dir, int u, int y, int v, Block block) {
		int[] w = world(o, dir, u, v);
		BlockPos p = new BlockPos(w[0], y, w[1]);
		if (!pl.clip.isInside(p)) return true;
		BlockState cur = pl.level.getBlockState(p);
		if (!cur.isAir() && !cur.is(Blocks.WATER)) return false; // only air and plain water are ever replaced
		pl.level.setBlock(p, Valuables.safe(block.defaultBlockState()), Block.UPDATE_CLIENTS); // s-throne B9: no valuable blocks
		pl.placed++;
		return true;
	}
}
