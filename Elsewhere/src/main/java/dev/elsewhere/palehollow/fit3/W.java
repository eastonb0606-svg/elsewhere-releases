package dev.elsewhere.palehollow.fit3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * FitWriter (STRUCTURES.md 2.13): writes only inside the chunk being generated ({@code clip}), UPDATE_CLIENTS only, shape blocks marked
 * for post-processing like vanilla {@code StructurePiece.placeBlock}, hashed variation from world position and piece seed (no Math.random),
 * and reads blocks only inside the same chunk (the underpin's fill-down loop).
 */
public final class W {
	public final WorldGenLevel l;
	public final BoundingBox clip;
	public final Frame f;
	public final long seed;
	public final RandomSource random;
	private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

	public W(WorldGenLevel l, BoundingBox clip, Frame f, long seed, RandomSource random) {
		this.l = l;
		this.clip = clip;
		this.f = f;
		this.seed = seed;
		this.random = random;
	}

	public boolean in(int lx, int y, int lz) {
		return clip.isInside(f.x(lx, lz), y, f.z(lx, lz));
	}

	public boolean inCol(int lx, int lz) {
		int x = f.x(lx, lz), z = f.z(lx, lz);
		return x >= clip.minX() && x <= clip.maxX() && z >= clip.minZ() && z <= clip.maxZ();
	}

	public BlockPos pos(int lx, int y, int lz) {
		return new BlockPos(f.x(lx, lz), y, f.z(lx, lz));
	}

	public void set(int lx, int y, int lz, BlockState s) {
		setW(f.x(lx, lz), y, f.z(lx, lz), s);
	}

	public void setW(int x, int y, int z, BlockState s) {
		if (!clip.isInside(x, y, z)) return;
		m.set(x, y, z);
		l.setBlock(m, s, Block.UPDATE_CLIENTS);
		Block b = s.getBlock();
		if (b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof StairBlock || b instanceof LadderBlock) {
			BlockPos p = m.immutable();
			l.getChunk(p).markPosForPostProcessing(p);
		}
	}

	public BlockState get(int lx, int y, int lz) {
		return getW(f.x(lx, lz), y, f.z(lx, lz));
	}

	public BlockState getW(int x, int y, int z) {
		if (!clip.isInside(x, y, z)) return Blocks.AIR.defaultBlockState();
		return l.getBlockState(m.set(x, y, z));
	}

	/** Air, fluid, plants, snow layers, leaves: what an underpin may fill and a soft clear may remove. */
	/**
	 * y of the top real ground block in a column: from the worldgen heightmap down through leaves, plants and air (a neighbour's tree canopy is
	 * never ground). {@link Integer#MIN_VALUE} when the column is a tree trunk or no ground is found within 24 blocks.
	 */
	public static int ground(WorldGenLevel l, int x, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, l.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, z);
		for (int i = 0; i < 24 && m.getY() > l.getMinY(); i++, m.move(Direction.DOWN)) {
			BlockState s = l.getBlockState(m);
			if (s.is(BlockTags.LOGS)) return Integer.MIN_VALUE;
			if (!open(s)) return m.getY();
		}
		return Integer.MIN_VALUE;
	}

	public static boolean open(BlockState s) {
		return s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced() || s.is(BlockTags.LEAVES);
	}

	/** Natural blocks that may be cut inside a room or above a step (never fluids: those are handled by the plan). */
	public static boolean soft(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(Blocks.PALE_HANGING_MOSS) || s.is(Blocks.PALE_MOSS_CARPET));
	}

	/** Removes plants, leaves and snow only (STRUCTURES.md 2.13 {@code soft(box)}). */
	public void soft(int lx, int y, int lz) {
		if (!in(lx, y, lz)) return;
		BlockState s = get(lx, y, lz);
		if (soft(s)) set(lx, y, lz, Blocks.AIR.defaultBlockState());
	}

	/** Room interior air (allowed only inside a piece's rooms). */
	public void room(int lx, int y, int lz) {
		if (!in(lx, y, lz)) return;
		if (!get(lx, y, lz).isAir()) set(lx, y, lz, Blocks.AIR.defaultBlockState());
	}

	/**
	 * Underpin (STRUCTURES.md 2.9): fills air, fluid and replaceables below {@code topY} down to real ground, in matching blocks (the ladder:
	 * first entries right under the piece, last entry for the rest), capped at {@link CoreFit#UNDERPIN_CAP}. Returns the depth filled.
	 */
	public int underpin(int lx, int lz, int topY, BlockState... ladder) {
		if (!inCol(lx, lz)) return 0;
		int n = 0;
		for (int y = topY; y > topY - CoreFit.UNDERPIN_CAP && y > l.getMinY(); y--) {
			BlockState s = get(lx, y, lz);
			if (!open(s)) break;
			set(lx, y, lz, ladder[Math.min(n, ladder.length - 1)]);
			n++;
		}
		return n;
	}

	public int hash(int lx, int y, int lz) {
		long h = seed ^ (f.x(lx, lz) * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (f.z(lx, lz) * 0x165667B19E3779F9L);
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) (h & 0x7FFFFFFF);
	}

	public boolean chance(int lx, int y, int lz, int pct) {
		return hash(lx, y, lz) % 100 < pct;
	}

	public BlockState stair(Block b, Direction localFacing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, f.d(localFacing)).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	public static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	public static BlockState waterlog(BlockState s) {
		return s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
			? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true) : s;
	}

	/** Pale Hollow stone with ~15% weathering (mossy / cracked), hashed. */
	public BlockState brick(int lx, int y, int lz) {
		int h = hash(lx, y, lz) % 100;
		return h < 9 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : h < 16 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
	}

	/** Underpin ladder: plinth course, then mossy cobblestone rubble, then cobblestone (STRUCTURES.md 2.9, Pale Hollow palette). */
	public static final BlockState[] LADDER = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
	public static final BlockState[] RUBBLE = {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
}
