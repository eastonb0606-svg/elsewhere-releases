package dev.elsewhere.greenhaze_hush.world.fit;

import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.Sink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Elsewhere 3.0 structure overhaul for greenhaze_hush (STRUCTURES.md 2.5, 2.6, 2.9, 2.13, 4; s-struct-greenhaze).
 * <ul>
 * <li>New starts are planned into several small {@link Piece}s, each on its own ground (floors from the chunk generator, never the live world),
 *     with per-piece foundations (underpins in matching blocks, capped) and a per-piece beard read by {@code GhBeardifierMixin}.</li>
 * <li>Released 2.x pieces ({@code greenhaze_hush_built_piece}) are frozen: their blueprints are untouched, so saved starts finish as they began.</li>
 * <li>Kill switches (JVM flags, default on): {@code -Delsewhere.overhaul3.gh_struct_looks=false} plans new starts with the 2.x blueprints again;
 *     {@code -Delsewhere.overhaul3.gh_struct_beard=false} drops the per-piece beards.</li>
 * </ul>
 * Writes stay inside the chunk being generated (the clipped {@link Out}); reads in postProcess are only the fill-down loops of that chunk.
 */
public final class GhFit {
	private GhFit() {}

	/** Kill switch: new looks + per-piece plan for NEW starts (saved starts never change). */
	public static volatile boolean LOOKS = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.gh_struct_looks", "true"));
	/** Kill switch: per-piece terrain beards (noise-level smoothing under floors / encapsulation of buried rooms). */
	public static volatile boolean BEARDS = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.gh_struct_beard", "true"));
	/** Piece schema version (stored as "v"; a behaviour change bumps it and keeps the old branch). */
	public static final int V = 1;

	public static StructurePieceType PIECE;
	/** kind -> builder. Filled by {@link GreenhazeLooks} and {@link HushLooks}. */
	public static final Map<String, BiConsumer<Piece, Out>> BUILDERS = new HashMap<>();

	public static void init() {
		if (PIECE != null) return;
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("greenhaze_hush_fit_piece"), (StructurePieceType) (ctx, tag) -> new Piece(tag));
		GreenhazeLooks.register();
		HushLooks.register();
	}

	// ------------------------------------------------------------------------------------------------ ground

	/** Ground model: y of the top solid block (ocean floor heightmap) and whether water stands over it. */
	public interface Ground {
		int y(int x, int z);

		boolean wet(int x, int z);
	}

	/** Deterministic ground from the chunk generator (the plan never reads the live world). */
	public static Ground gen(Structure.GenerationContext c) {
		ChunkGenerator g = c.chunkGenerator();
		return new Ground() {
			public int y(int x, int z) {
				return g.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, c.heightAccessor(), c.randomState());
			}

			public boolean wet(int x, int z) {
				return g.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, c.heightAccessor(), c.randomState()) > y(x, z);
			}
		};
	}

	/** Ground of a loaded level (dev builds and self-tests only). */
	public static Ground live(ServerLevel l) {
		return new Ground() {
			public int y(int x, int z) {
				return l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
			}

			public boolean wet(int x, int z) {
				return l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 > y(x, z);
			}
		};
	}

	/** Block columns (Hush caves: shelves, ceilings). */
	public interface Columns {
		BlockState at(int x, int y, int z);
	}

	public static Columns gen(Structure.GenerationContext c, boolean unused) {
		Map<Long, net.minecraft.world.level.NoiseColumn> cache = new HashMap<>();
		return (x, y, z) -> cache.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> c.chunkGenerator().getBaseColumn(x, z, c.heightAccessor(), c.randomState())).getBlock(y);
	}

	public static Columns live(ServerLevel l, boolean unused) {
		return (x, y, z) -> l.getBlockState(new BlockPos(x, y, z));
	}

	static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
	}

	/** First floor shelf (solid with 3 air above) whose walk level is in [lo, hi], nearest to {@code want}; or MIN_VALUE. */
	public static int shelf(Columns c, int x, int z, int want, int lo, int hi) {
		int best = Integer.MIN_VALUE;
		for (int y = lo; y <= hi; y++) {
			if (solid(c.at(x, y - 1, z)) && c.at(x, y, z).isAir() && c.at(x, y + 1, z).isAir() && c.at(x, y + 2, z).isAir()) {
				if (best == Integer.MIN_VALUE || Math.abs(y - want) < Math.abs(best - want)) best = y;
			}
		}
		return best;
	}

	/** First solid block above {@code y} within {@code range}; or MIN_VALUE. */
	public static int ceiling(Columns c, int x, int z, int y, int range) {
		for (int yy = y + 1; yy <= y + range; yy++) if (solid(c.at(x, yy, z))) return yy;
		return Integer.MIN_VALUE;
	}

	// ------------------------------------------------------------------------------------------------ piece

	/** One small piece of a new-style structure: own floor, own footprint, own beard. Plans are saved, never recomputed. */
	public static final class Piece extends StructurePiece {
		public final String kind;
		public final int v;
		/** Anchor at walk level (feet). */
		public final BlockPos o;
		public final long seed;
		/** 0..3: the piece's front faces south, west, north, east. */
		public final int rot;
		/** Planned numbers (floors, levels, ground samples): the plan, so postProcess never re-plans. */
		public final int[] p;
		public final @Nullable BoundingBox beard;
		public final @Nullable TerrainAdjustment adapt;

		public Piece(String kind, BlockPos o, long seed, int rot, int[] p, BoundingBox box, @Nullable BoundingBox beard, @Nullable TerrainAdjustment adapt) {
			super(PIECE, 0, box);
			this.kind = kind;
			this.v = V;
			this.o = o.immutable();
			this.seed = seed;
			this.rot = rot & 3;
			this.p = p;
			this.beard = beard;
			this.adapt = beard == null ? null : adapt;
		}

		Piece(CompoundTag t) {
			super(PIECE, t);
			this.kind = t.getStringOr("kind", "");
			this.v = t.getIntOr("v", 1);
			this.o = BlockPos.of(t.getLongOr("o", 0L));
			this.seed = t.getLongOr("seed", 0L);
			this.rot = t.getIntOr("rot", 0) & 3;
			this.p = t.getIntArray("p").orElse(new int[0]);
			int[] b = t.getIntArray("beard").orElse(new int[0]);
			this.beard = b.length == 6 ? new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]) : null;
			TerrainAdjustment a = null;
			String an = t.getStringOr("adapt", "");
			for (TerrainAdjustment x : TerrainAdjustment.values()) if (x.name().equals(an)) a = x;
			this.adapt = this.beard == null ? null : a;
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag t) {
			t.putString("kind", kind);
			t.putInt("v", v);
			t.putLong("o", o.asLong());
			t.putLong("seed", seed);
			t.putInt("rot", rot);
			t.putIntArray("p", p);
			if (beard != null && adapt != null) {
				t.putIntArray("beard", new int[] {beard.minX(), beard.minY(), beard.minZ(), beard.maxX(), beard.maxY(), beard.maxZ()});
				t.putString("adapt", adapt.name());
			}
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			BiConsumer<Piece, Out> b = BUILDERS.get(kind);
			if (b != null) b.accept(this, Out.gen(level, chunkBB));
		}

		public int p(int i) {
			return i < p.length ? p[i] : 0;
		}

		/** Local frame of this piece: +z is the piece's front. */
		public Frame frame() {
			return new Frame(o.getX(), o.getY(), o.getZ(), rot);
		}
	}

	/** Builds one planned piece list into a live level (dev summon, self-test): same builders as worldgen. */
	public static void buildLive(ServerLevel level, List<Piece> pieces) {
		Out out = Out.live(level);
		for (Piece pc : pieces) {
			BiConsumer<Piece, Out> b = BUILDERS.get(pc.kind);
			if (b != null) b.accept(pc, out);
		}
		out.finish();
	}

	// ------------------------------------------------------------------------------------------------ writer

	/** The only writer of new pieces: clipped to the generating chunk (worldgen) or a live level (dev). Shape-marks fences, walls, panes, stairs. */
	public static final class Out implements Sink {
		final @Nullable WorldGenLevel wg;
		final @Nullable BoundingBox box;
		final @Nullable ServerLevel lv;
		final List<BlockPos> shaped = new ArrayList<>();

		private Out(@Nullable WorldGenLevel wg, @Nullable BoundingBox box, @Nullable ServerLevel lv) {
			this.wg = wg;
			this.box = box;
			this.lv = lv;
		}

		public static Out gen(WorldGenLevel level, BoundingBox chunkBB) {
			return new Out(level, chunkBB, null);
		}

		public static Out live(ServerLevel level) {
			return new Out(null, null, level);
		}

		/** True when column (x, z) is writable now (always for live builds). */
		public boolean has(int x, int z) {
			return box == null || (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ());
		}

		static boolean needsShape(BlockState s) {
			Block b = s.getBlock();
			return b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock || b instanceof StairBlock || b instanceof FenceGateBlock;
		}

		@Override
		public void set(BlockPos pos, BlockState state) {
			if (wg != null) {
				if (box == null || !box.isInside(pos)) return;
				wg.setBlock(pos, state, Block.UPDATE_CLIENTS);
				if (needsShape(state)) wg.getChunk(pos).markPosForPostProcessing(pos);
			} else if (lv != null) {
				lv.setBlock(pos, state, Block.UPDATE_CLIENTS);
				if (needsShape(state)) shaped.add(pos.immutable());
			}
		}

		@Override
		public BlockState get(BlockPos pos) {
			if (wg != null) return box != null && box.isInside(pos) ? wg.getBlockState(pos) : Blocks.STONE.defaultBlockState();
			return lv == null ? Blocks.STONE.defaultBlockState() : lv.getBlockState(pos);
		}

		@Override
		public ServerLevel live() {
			return lv;
		}

		@Override
		public void loot(BlockPos pos, ResourceKey<LootTable> table, long seed) {
			if (wg != null) {
				if (box != null && box.isInside(pos)) net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(wg, RandomSource.create(seed), pos, table);
			} else if (lv != null) {
				net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(lv, RandomSource.create(seed), pos, table);
			}
		}

		@Override
		public <T extends Mob> T spawn(EntityType<T> type, BlockPos pos) {
			ServerLevel l = wg != null ? wg.getLevel() : lv;
			if (l == null || (wg != null && (box == null || !box.isInside(pos)))) return null;
			T mob = type.create(l, EntitySpawnReason.STRUCTURE);
			if (mob == null) return null;
			mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
			mob.setPersistenceRequired();
			if (wg != null) wg.addFreshEntityWithPassengers(mob);
			else l.addFreshEntity(mob);
			return mob;
		}

		/** Live builds: connect fences, walls, panes and stairs once everything is placed (worldgen does this through post-processing). */
		public void finish() {
			if (lv == null) return;
			for (BlockPos p : shaped) {
				BlockState s = lv.getBlockState(p);
				BlockState u = Block.updateFromNeighbourShapes(s, lv, p);
				if (u != s) lv.setBlock(p, u, Block.UPDATE_CLIENTS);
			}
			shaped.clear();
		}
	}

	// ------------------------------------------------------------------------------------------------ frame and kit

	/** A rotated local frame: local +z is the front. rot 0 = south, 1 = west, 2 = north, 3 = east. */
	public record Frame(int ox, int oy, int oz, int rot) {
		public int x(int lx, int lz) {
			return switch (rot) { case 1 -> ox - lz; case 2 -> ox - lx; case 3 -> ox + lz; default -> ox + lx; };
		}

		public int z(int lx, int lz) {
			return switch (rot) { case 1 -> oz + lx; case 2 -> oz - lz; case 3 -> oz - lx; default -> oz + lz; };
		}

		public BlockPos at(int lx, int ly, int lz) {
			return new BlockPos(x(lx, lz), oy + ly, z(lx, lz));
		}

		public net.minecraft.world.level.block.Rotation rotation() {
			return switch (rot) {
				case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
				case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
				case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
				default -> net.minecraft.world.level.block.Rotation.NONE;
			};
		}

		/** Set a block given in local orientation (local south = front). */
		public void set(Sink s, int lx, int ly, int lz, BlockState st) {
			s.set(at(lx, ly, lz), st.rotate(rotation()));
		}

		public BlockState get(Sink s, int lx, int ly, int lz) {
			return s.get(at(lx, ly, lz));
		}

		/** World direction of a local direction. */
		public Direction dir(Direction local) {
			return rotation().rotate(local);
		}
	}

	public static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	public static BlockState stair(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	public static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	public static BlockState pillar(Block b, Direction.Axis axis) {
		BlockState s = b.defaultBlockState();
		return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, axis) : s;
	}

	/** Horizontal direction closest to the vector (dx, dz). */
	public static Direction toward(double dx, double dz) {
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Stable hash in [0, 1) of a world position and a seed (weathering, scatter): no Math.random, no map order. */
	public static double hash(long seed, int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 31;
		h *= 0x7FB5D329728EA185L;
		h ^= h >>> 27;
		h *= 0x81DADEF4BC2DD44DL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	public static boolean ground(BlockState s) {
		return solid(s) && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS);
	}

	/**
	 * Foundation ladder (STRUCTURES.md 2.9): fills air, fluid and replaceables under a floor block at {@code yTop} downwards, first
	 * {@code plinth} for 2 blocks then {@code rubble}, until real ground; capped. Only in the writable chunk.
	 */
	public static int underpin(Out o, int x, int yTop, int z, BlockState plinth, BlockState rubble, int cap) {
		if (!o.has(x, z)) return 0;
		int n = 0;
		for (int d = 0; d < cap; d++) {
			BlockPos p = new BlockPos(x, yTop - d, z);
			if (ground(o.get(p))) break;
			o.set(p, d < 2 ? plinth : rubble);
			n++;
		}
		return n;
	}

	/** Clears natural blocks and plants in a column from y0 to y1 (a room interior or a cut). */
	public static void clear(Out o, int x, int y0, int y1, int z) {
		if (!o.has(x, z)) return;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = y0; y <= y1; y++) {
			BlockPos p = new BlockPos(x, y, z);
			if (!o.get(p).isAir()) o.set(p, air);
		}
	}

	/** Removes only soft blocks (plants, leaves, snow, replaceables) from y0 to y1: no terrain is cut. */
	public static void soft(Out o, int x, int y0, int y1, int z) {
		if (!o.has(x, z)) return;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = y0; y <= y1; y++) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = o.get(p);
			if (!s.isAir() && s.getFluidState().isEmpty() && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES))) o.set(p, air);
		}
	}

	/** Top solid block in a column, scanning down from {@code from} at most {@code depth}; MIN_VALUE when none or not writable. */
	public static int topSolid(Out o, int x, int z, int from, int depth) {
		if (!o.has(x, z)) return Integer.MIN_VALUE;
		for (int y = from; y > from - depth; y--) if (ground(o.get(new BlockPos(x, y, z)))) return y;
		return Integer.MIN_VALUE;
	}

	/** A standing spot at or above {@code y} (two free blocks), for mobs spawned onto contour terraces. */
	public static int standY(ServerLevel level, int x, int y, int z) {
		for (int yy = y; yy < y + 8; yy++) {
			BlockPos p = new BlockPos(x, yy, z);
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return yy;
		}
		return y;
	}

	/** Packs a box for the beard / bounding helpers. */
	public static BoundingBox box(int x0, int y0, int z0, int x1, int y1, int z1) {
		return new BoundingBox(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
	}

	/** Centre of the Listening Engine's throne in a new-style start (Auris answers there), or null for 2.x starts. */
	public static @Nullable BlockPos engineThrone(net.minecraft.world.level.levelgen.structure.StructureStart start) {
		for (StructurePiece pc : start.getPieces()) if (pc instanceof Piece fp && fp.kind.equals("engine")) return fp.o.below(4);
		return null;
	}
}
