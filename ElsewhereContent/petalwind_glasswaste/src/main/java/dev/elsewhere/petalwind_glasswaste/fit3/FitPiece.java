package dev.elsewhere.petalwind_glasswaste.fit3;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One part of a 3.0 Petalwind/Glasswaste site ({@code elsewhere:pg_fit_piece}, schema {@code v}): a small box with its own floor, written per chunk
 * (clipped to the chunk being generated) by {@link FitBuilders}. The plan is saved in NBT and never recomputed: {@code origin} is the site origin in the
 * 2.x convention (so arena centres are unchanged), {@code floor} this part's own ground-fitted floor, {@code ix} the part index, {@code param} a
 * part setting (the Sunpit bowl radius). The boss is spawned by the core part when the chunk holding the arena centre generates.
 */
public final class FitPiece extends StructurePiece {
	public static final int V = 1;
	public final String kind, part;
	public final int v, floor, ix, param;
	public final BlockPos origin;
	public final long seed;

	public FitPiece(String kind, String part, BlockPos origin, int floor, int ix, int param, long seed, BoundingBox box) {
		super(PGFit.PIECE, 0, box);
		this.kind = kind;
		this.part = part;
		this.v = V;
		this.origin = origin;
		this.floor = floor;
		this.ix = ix;
		this.param = param;
		this.seed = seed;
	}

	public FitPiece(CompoundTag tag) {
		super(PGFit.PIECE, tag);
		this.kind = tag.getStringOr("kind", "");
		this.part = tag.getStringOr("part", "");
		this.v = tag.getIntOr("v", V);
		this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
		this.floor = tag.getIntOr("floor", origin.getY());
		this.ix = tag.getIntOr("ix", 0);
		this.param = tag.getIntOr("param", 0);
		this.seed = tag.getLongOr("seed", 0L);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", kind);
		tag.putString("part", part);
		tag.putInt("v", v);
		tag.putLong("origin", origin.asLong());
		tag.putInt("floor", floor);
		tag.putInt("ix", ix);
		tag.putInt("param", param);
		tag.putLong("seed", seed);
	}

	public BoundingBox box() {
		return boundingBox;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
							BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		BoundingBox b = boundingBox;
		int minX = Math.max(b.minX(), chunkBB.minX()), maxX = Math.min(b.maxX(), chunkBB.maxX());
		int minZ = Math.max(b.minZ(), chunkBB.minZ()), maxZ = Math.min(b.maxZ(), chunkBB.maxZ());
		int minY = Math.max(b.minY(), chunkBB.minY()), maxY = Math.min(b.maxY(), chunkBB.maxY());
		if (minX > maxX || minZ > maxZ || minY > maxY) return;
		FitBuilders.build(new FitWriter(level, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), seed), this);
		// the boss: spawned once, by the core part, in the chunk that holds the arena centre (STRUCTURES.md 2.13)
		if (FitBuilders.spawnsBoss(this)) {
			BlockPos c = dev.elsewhere.petalwind_glasswaste.PGStructures.centerOf(kind, origin);
			if (chunkBB.isInside(c.getX(), chunkBB.minY(), c.getZ())) PGFit.spawnBoss(level, kind, origin, EntitySpawnReason.STRUCTURE);
		}
	}
}
