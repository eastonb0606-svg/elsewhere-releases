package dev.elsewhere.overworld_bosses.fit3;

import dev.elsewhere.overworld_bosses.world.BlockSink;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 terrain-fit piece of an Overworld boss site (STRUCTURES.md 2.5/2.6/2.9, section 4 "owb"). A new start is many of these: the arena
 * core plus its satellites (boardwalks, hull stacks, sunken hulks, ice buttresses, the entrance stair, the pyramid-side shaft), each on its
 * own ground ({@link #at}, planned from the generator's heights when the start was created, so every chunk agrees) with its own terrain
 * rule ({@link #adapt}, applied at noise level by {@code OwbBeardifierMixin}). Saved 2.x starts keep the frozen {@code OwbPiece}.
 *
 * <p>Writes are clipped to this piece's box and the chunk being generated, use {@code UPDATE_CLIENTS} only, and read only the chunk's own
 * columns, so the result does not depend on chunk order.
 */
public final class OwbFitPiece extends StructurePiece {
	public static final int VERSION = 3;

	public final String kind;
	public final String part;
	/** The arena origin (where players stand at the centre); trigger offsets in {@code Blueprints} are relative to it. */
	public final BlockPos o;
	/** This piece's own anchor: its floor / bed / start point. */
	public final BlockPos at;
	public final int a, b;
	public final int[] prof;
	public final TerrainAdjustment adapt;

	public OwbFitPiece(String kind, String part, BlockPos o, BlockPos at, int a, int b, int[] prof, TerrainAdjustment adapt) {
		super(OwbFit.pieceType(), 0, FitBuilds.box(kind, part, o, at, a, b, prof));
		this.kind = kind;
		this.part = part;
		this.o = o.immutable();
		this.at = at.immutable();
		this.a = a;
		this.b = b;
		this.prof = prof;
		this.adapt = adapt;
	}

	public OwbFitPiece(CompoundTag tag) {
		this(tag.getStringOr("Kind", ""), tag.getStringOr("Part", ""),
			new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0)),
			new BlockPos(tag.getIntOr("AX", 0), tag.getIntOr("AY", 64), tag.getIntOr("AZ", 0)),
			tag.getIntOr("A", 0), tag.getIntOr("B", 0), tag.getIntArray("Prof").orElse(new int[0]), adaptOf(tag.getStringOr("Adapt", "none")));
	}

	private static TerrainAdjustment adaptOf(String s) {
		for (TerrainAdjustment t : TerrainAdjustment.values()) if (t.getSerializedName().equals(s)) return t;
		return TerrainAdjustment.NONE;
	}

	/** The box the noise-level terrain rule applies to (footprint only, never the whole piece), or null for none. */
	public @Nullable BoundingBox beardBox() {
		return this.adapt == TerrainAdjustment.NONE ? null : FitBuilds.beardBox(this);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("V", VERSION);
		tag.putString("Kind", this.kind);
		tag.putString("Part", this.part);
		tag.putInt("OX", this.o.getX());
		tag.putInt("OY", this.o.getY());
		tag.putInt("OZ", this.o.getZ());
		tag.putInt("AX", this.at.getX());
		tag.putInt("AY", this.at.getY());
		tag.putInt("AZ", this.at.getZ());
		tag.putInt("A", this.a);
		tag.putInt("B", this.b);
		tag.putIntArray("Prof", this.prof);
		tag.putString("Adapt", this.adapt.getSerializedName());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		BoundingBox box = this.boundingBox;
		BlockSink sink = new BlockSink() {
			@Override
			public void set(BlockPos pos, BlockState state) {
				if (chunkBB.isInside(pos) && box.isInside(pos)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}

			@Override
			public BlockState get(BlockPos pos) {
				// outside this chunk counts as solid ground, like the frozen builders
				return chunkBB.isInside(pos) ? level.getBlockState(pos) : Blocks.STONE.defaultBlockState();
			}

			@Override
			public boolean writes(int x, int z) {
				return x >= chunkBB.minX() && x <= chunkBB.maxX() && z >= chunkBB.minZ() && z <= chunkBB.maxZ()
					&& x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
			}
		};
		FitBuilds.build(this, sink);
	}
}
