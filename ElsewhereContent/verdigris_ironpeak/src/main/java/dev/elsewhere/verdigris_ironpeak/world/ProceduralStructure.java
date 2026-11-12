package dev.elsewhere.verdigris_ironpeak.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.verdigris_ironpeak.Reg;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * A structure made of one {@link Blueprint}. JSON fields: {@code blueprint} (name), {@code placement}
 * ({@code surface} | {@code ocean_floor} | {@code fixed}), {@code min_height}/{@code max_height} (the terrain height
 * the site must have, or the fixed Y), {@code y_offset}.
 */
public class ProceduralStructure extends Structure {
	public static final MapCodec<ProceduralStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Codec.STRING.fieldOf("blueprint").forGetter(s -> s.blueprint),
		Codec.STRING.optionalFieldOf("placement", "surface").forGetter(s -> s.placement),
		Codec.INT.optionalFieldOf("min_height", -64).forGetter(s -> s.minHeight),
		Codec.INT.optionalFieldOf("max_height", 2000).forGetter(s -> s.maxHeight),
		Codec.INT.optionalFieldOf("y_offset", 0).forGetter(s -> s.yOffset)
	).apply(i, ProceduralStructure::new));

	public static final StructureType<ProceduralStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Reg.id("vi_procedural"), () -> CODEC);
	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("vi_procedural_piece"),
		(StructurePieceType.ContextlessType) Piece::new);

	final String blueprint;
	public String blueprint() {
		return blueprint;
	}

	final String placement;
	final int minHeight, maxHeight, yOffset;

	public String placement() {
		return placement;
	}

	public int minHeight() {
		return minHeight;
	}

	public int maxHeight() {
		return maxHeight;
	}

	public int yOffset() {
		return yOffset;
	}

	public ProceduralStructure(StructureSettings settings, String blueprint, String placement, int minHeight, int maxHeight, int yOffset) {
		super(settings);
		this.blueprint = blueprint;
		this.placement = placement;
		this.minHeight = minHeight;
		this.maxHeight = maxHeight;
		this.yOffset = yOffset;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		// 3.0 structure looks + per-piece ground (s-struct-verdigris): new starts only, kill switch elsewhere.overhaul3.vi_structures
		if (dev.elsewhere.verdigris_ironpeak.world.fit.FitBuilds.applies(blueprint)) return dev.elsewhere.verdigris_ironpeak.world.fit.FitBuilds.findGenerationPoint(this, context);
		Blueprint bp = Blueprint.ALL.get(blueprint);
		if (bp == null) return Optional.empty();
		ChunkPos cp = context.chunkPos();
		int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
		int y;
		if (placement.equals("fixed")) {
			y = minHeight;
		} else if (GenFit.ENABLED) {
			// 3.0 site rules (GENERATION.md G2/G3): seeded multi-offset choice over the whole footprint, cut/fill <= 6, dry (or sea bed)
			boolean bed = placement.equals("ocean_floor");
			GenFit.Limits lim = new GenFit.Limits(Math.min(bp.radius(), 12), 6, 8, 15, minHeight, maxHeight, bed); // fill 8: VI foundation pillars reach the ground
			var site = GenFit.pick(context, lim);
			if (site.isEmpty()) return Optional.empty();
			x = site.get().x();
			z = site.get().z();
			y = site.get().floor();
		} else {
			Heightmap.Types type = placement.equals("ocean_floor") ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
			ChunkGenerator gen = context.chunkGenerator();
			y = gen.getFirstOccupiedHeight(x, z, type, context.heightAccessor(), context.randomState());
			// Reject steep sites: the four corners must be within 6 blocks of the centre.
			int r = Math.min(bp.radius(), 8);
			for (int[] c : new int[][] {{r, r}, {-r, r}, {r, -r}, {-r, -r}}) {
				int h = gen.getFirstOccupiedHeight(x + c[0], z + c[1], type, context.heightAccessor(), context.randomState());
				if (Math.abs(h - y) > 6 && !placement.equals("ocean_floor")) return Optional.empty();
			}
			if (y < minHeight || y > maxHeight) return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, y + yOffset, z);
		if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(x >> 2, origin.getY() >> 2, z >> 2))) {
			return Optional.empty();
		}
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new Piece(blueprint, origin, bp))));
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	public static void init() {
		dev.elsewhere.verdigris_ironpeak.world.fit.FitBuilds.init();
	}

	public static class Piece extends StructurePiece {
		private final String blueprint;
		private final BlockPos origin;
		/** 1 = 2.x blueprint; 2 = 3.0 fit blueprint with its saved ground model. */
		private final int v;
		private final dev.elsewhere.verdigris_ironpeak.world.fit.Terrain terrain;

		public int version() {
			return v;
		}

		public dev.elsewhere.verdigris_ironpeak.world.fit.Terrain terrain() {
			return terrain;
		}

		/** A 3.0 fit piece: the FIT blueprint, its own box and the ground model sampled when the start was made. */
		public static Piece fit(String blueprint, BlockPos origin, Blueprint bp, dev.elsewhere.verdigris_ironpeak.world.fit.Terrain terrain) {
			return new Piece(blueprint, origin, bp, 2, terrain);
		}

		private Piece(String blueprint, BlockPos origin, Blueprint bp, int v, dev.elsewhere.verdigris_ironpeak.world.fit.Terrain terrain) {
			super(PIECE, 0, new BoundingBox(origin.getX() - bp.radius(), origin.getY() - bp.depth(), origin.getZ() - bp.radius(),
				origin.getX() + bp.radius(), origin.getY() + bp.height(), origin.getZ() + bp.radius()));
			this.blueprint = blueprint;
			this.origin = origin;
			this.v = v;
			this.terrain = terrain;
		}

		public String blueprint() {
			return blueprint;
		}

		public BlockPos origin() {
			return origin;
		}

		Piece(String blueprint, BlockPos origin, Blueprint bp) {
			super(PIECE, 0, new BoundingBox(origin.getX() - bp.radius(), origin.getY() - bp.depth(), origin.getZ() - bp.radius(),
				origin.getX() + bp.radius(), origin.getY() + bp.height(), origin.getZ() + bp.radius()));
			this.blueprint = blueprint;
			this.origin = origin;
			this.v = 1;
			this.terrain = null;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.blueprint = tag.getStringOr("Blueprint", "");
			this.origin = BlockPos.of(tag.getLongOr("Origin", 0));
			this.v = tag.getIntOr("v", 1);
			this.terrain = v >= 2 ? dev.elsewhere.verdigris_ironpeak.world.fit.Terrain.load(tag) : null;
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putString("Blueprint", blueprint);
			tag.putLong("Origin", origin.asLong());
			if (v >= 2) {
				tag.putInt("v", v);
				if (terrain != null) terrain.save(tag);
			}
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			Blueprint bp = v >= 2 ? Blueprint.FIT.getOrDefault(blueprint, Blueprint.ALL.get(blueprint)) : Blueprint.ALL.get(blueprint);
			if (bp == null) return;
			RandomSource r = RandomSource.create(origin.asLong() ^ level.getSeed());
			bp.build(new BlockSink(level, origin, chunkBB, r, terrain), r);
		}
	}
}
