package dev.elsewhere.overworld_bosses.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The single piece of an {@link OwbStructure}: runs the matching {@link Blueprints} method clipped to each chunk. */
public class OwbPiece extends StructurePiece {
	public static final String CROAKING_SHRINE = "croaking_shrine";
	public static final String SUN_CHAMBER = "sun_chamber";
	public static final String GLACIER_SHELF = "glacier_shelf";
	public static final String WRECK_RING = "wreck_ring";

	private final String kind;
	private final BlockPos origin;
	private final int top;

	public OwbPiece(String kind, BlockPos origin, int top) {
		super(OwbWorldgen.pieceTypeOf(kind), 0, boxFor(kind, origin, top));
		this.kind = kind;
		this.origin = origin;
		this.top = top;
	}

	public OwbPiece(CompoundTag tag) {
		this(tag.getStringOr("Kind", CROAKING_SHRINE),
			new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0)), tag.getIntOr("Top", 0));
	}

	public static BoundingBox boxFor(String kind, BlockPos o, int top) {
		int x = o.getX(), y = o.getY(), z = o.getZ();
		return switch (kind) {
			case SUN_CHAMBER -> new BoundingBox(x - 14, y - 6, z - 14, x + 19, Math.max(top + 6, y + 20), z + 14);
			case GLACIER_SHELF -> new BoundingBox(x - 19, y - 44, z - 29, x + 19, y + 24, z + 19);
			case WRECK_RING -> new BoundingBox(x - 25, y - 18, z - 25, x + 25, y + 16, z + 25);
			default -> new BoundingBox(x - 16, y - 16, z - 16, x + 16, y + 16, z + 16);
		};
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Kind", this.kind);
		tag.putInt("OX", this.origin.getX());
		tag.putInt("OY", this.origin.getY());
		tag.putInt("OZ", this.origin.getZ());
		tag.putInt("Top", this.top);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		BlockSink sink = new BlockSink() {
			@Override
			public void set(BlockPos pos, BlockState state) {
				if (chunkBB.isInside(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}

			@Override
			public BlockState get(BlockPos pos) {
				return chunkBB.isInside(pos) ? level.getBlockState(pos) : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
			}

			@Override
			public boolean writes(int x, int z) {
				return x >= chunkBB.minX() && x <= chunkBB.maxX() && z >= chunkBB.minZ() && z <= chunkBB.maxZ();
			}
		};
		build(this.kind, sink, this.origin, this.top, false);
	}

	public static void build(String kind, BlockSink sink, BlockPos origin, int top, boolean dev) {
		switch (kind) {
			case CROAKING_SHRINE -> Blueprints.croakingShrine(sink, origin);
			case SUN_CHAMBER -> Blueprints.sunChamber(sink, origin, top);
			case GLACIER_SHELF -> Blueprints.glacierShelf(sink, origin);
			case WRECK_RING -> Blueprints.wreckRing(sink, origin, dev);
			default -> {}
		}
	}
}
