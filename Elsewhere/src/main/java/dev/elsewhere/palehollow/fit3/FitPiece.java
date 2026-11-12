package dev.elsewhere.palehollow.fit3;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jspecify.annotations.Nullable;

/**
 * One small terrain-fitted piece of a core site (STRUCTURES.md 2.5): its own floor y, footprint, rotation, terrain rule and seed, all saved
 * in NBT so the plan is never recomputed. Builds clipped to the chunk being generated, so every chunk writes only its own blocks.
 */
public final class FitPiece extends StructurePiece {
	public final String kind;
	public final int v;
	public final BlockPos origin;
	public final int rot;
	public final int floor;
	/** Local footprint {x0, z0, x1, z1}. */
	public final int[] fp;
	public final boolean beard;
	public final long seed;
	/** Kind-specific plan data (site floor, road cells...). */
	public final int[] data;

	public FitPiece(String kind, BlockPos origin, int rot, int floor, int[] fp, boolean beard, long seed, int[] data, int minY, int maxY) {
		super(CoreFit.PIECE, 0, box(origin, rot, fp, beard, minY, maxY));
		this.kind = kind;
		this.v = CoreFit.V;
		this.origin = origin;
		this.rot = rot;
		this.floor = floor;
		this.fp = fp;
		this.beard = beard;
		this.seed = seed;
		this.data = data;
	}

	public FitPiece(CompoundTag tag) {
		super(CoreFit.PIECE, tag);
		this.kind = tag.getStringOr("kind", "shrine");
		this.v = tag.getIntOr("v", 1);
		this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
		this.rot = tag.getIntOr("rot", 0);
		this.floor = tag.getIntOr("floor", origin.getY());
		this.fp = tag.getIntArray("fp").orElse(new int[] {0, 0, 0, 0});
		this.beard = tag.getBooleanOr("beard", false);
		this.seed = tag.getLongOr("seed", 0L);
		this.data = tag.getIntArray("data").orElse(new int[0]);
	}

	private static BoundingBox box(BlockPos o, int rot, int[] fp, boolean beard, int minY, int maxY) {
		int[] r = new Frame(o.getX(), o.getZ(), rot).rect(fp[0], fp[1], fp[2], fp[3]);
		// a beard reaches 12 blocks: the box covers it so every chunk the beard touches holds a reference to this start (JSON adaptation is none)
		int m = beard ? 12 : 3;
		return new BoundingBox(r[0] - m, minY, r[1] - m, r[2] + m, maxY, r[3] + m);
	}

	public Frame frame() {
		return new Frame(origin.getX(), origin.getZ(), rot);
	}

	/** World footprint {minX, minZ, maxX, maxZ}. */
	public int[] worldRect() {
		return frame().rect(fp[0], fp[1], fp[2], fp[3]);
	}

	/** The per-piece beard: footprint only, 2 high, ground at floor + 1 (solid up to the floor block). Null for pieces with rule none. */
	public @Nullable BoundingBox beardBox() {
		if (!beard) return null;
		int[] r = worldRect();
		return new BoundingBox(r[0], floor + 1, r[1], r[2], floor + 2, r[3]);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", kind);
		tag.putInt("v", v);
		tag.putLong("origin", origin.asLong());
		tag.putInt("rot", rot);
		tag.putInt("floor", floor);
		tag.putIntArray("fp", fp);
		tag.putBoolean("beard", beard);
		tag.putLong("seed", seed);
		tag.putIntArray("data", data);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
							BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		W w = new W(level, chunkBB, frame(), seed, random);
		switch (kind) {
			case "shrine" -> ShrineKit.build(this, w);
			case "chapel_nave", "chapel_east" -> ChapelKit.build(this, w);
			case "kiln_a", "kiln_b", "kiln_yard" -> KilnKit.build(this, w);
			case "road" -> RoadKit.build(this, w);
			default -> {
			}
		}
	}
}
