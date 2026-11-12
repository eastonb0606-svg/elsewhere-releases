package dev.elsewhere.greenhaze_hush.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.greenhaze_hush.Reg;
import java.util.HashMap;
import java.util.Map;
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
 * One structure type for every code-built structure in this module ({@code elsewhere:greenhaze_hush_built}).
 * The JSON names a blueprint {@code kind} and how to find its floor: {@code surface} (top of the terrain),
 * {@code cave_floor} (first floor going down from {@code y}) or {@code fixed} (exactly {@code y}).
 */
public final class BuiltStructure extends Structure {
	public static final Map<String, Blueprint> BLUEPRINTS = new HashMap<>();

	public static final MapCodec<BuiltStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Codec.STRING.fieldOf("kind").forGetter(s -> s.kind),
		Codec.STRING.optionalFieldOf("anchor", "surface").forGetter(s -> s.anchor),
		Codec.INT.optionalFieldOf("y", 64).forGetter(s -> s.y)
	).apply(i, BuiltStructure::new));

	public static StructureType<BuiltStructure> TYPE;
	public static StructurePieceType PIECE;

	public final String kind, anchor;
	public final int y;

	public BuiltStructure(StructureSettings settings, String kind, String anchor, int y) {
		super(settings);
		this.kind = kind;
		this.anchor = anchor;
		this.y = y;
	}

	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Reg.id("greenhaze_hush_built"), () -> CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("greenhaze_hush_built_piece"),
			(StructurePieceType) (ctx, tag) -> new Piece(tag));
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		Blueprint bp = BLUEPRINTS.get(kind);
		if (bp == null) return Optional.empty();
		ChunkPos cp = context.chunkPos();
		int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
		int floor;
		switch (anchor) {
			case "fixed" -> {
				floor = y;
				// 3.0 site rules (GENERATION.md G3, STRUCTURES.md 4): carved Hush builds must not open into an aquifer (no flooded
				// interiors), and the hanging undercity needs rock overhead to hang from. Generator columns only.
				if (GenFit.ENABLED && !fixedSiteOk(context, kind, x, z, y, Math.min(bp.radius(), 16))) return Optional.empty();
			}
			case "cave_floor" -> {
				var column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
				floor = Integer.MIN_VALUE;
				for (int yy = y; yy > context.heightAccessor().getMinY() + 8; yy--) {
					if (!column.getBlock(yy).isAir() && column.getBlock(yy + 1).isAir() && column.getBlock(yy + 2).isAir()) { floor = yy + 1; break; }
				}
				if (floor == Integer.MIN_VALUE) floor = y;
				// 3.0 site rule (GENERATION.md G3): the cave floor must continue under the whole footprint (within 3 blocks at 4 corners)
				if (GenFit.ENABLED && !caveFloorAround(context, x, z, floor, Math.min(bp.radius(), 8))) return Optional.empty();
			}
			default -> {
				if (GenFit.ENABLED) {
					// 3.0 site rules (GENERATION.md G2/G3): seeded multi-offset choice, cut/fill within the foundation cap, dry ground
					var site = GenFit.pick(context, siteLimits(kind, bp.radius(), context));
					if (site.isEmpty()) return Optional.empty();
					x = site.get().x();
					z = site.get().z();
					floor = site.get().floor();
				} else {
					floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
					if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(x >> 2, floor >> 2, z >> 2))) return Optional.empty();
				}
			}
		}
		BlockPos origin = new BlockPos(x, floor, z);
		long seed = context.random().nextLong();
		return Optional.of(new GenerationStub(origin, b -> b.addPiece(new Piece(kind, origin, seed, bp))));
	}

	/** Fixed-y Hush sites: no fluid in the build's shell (5 columns, y-r/2..y+r/2); the undercity also needs solid rock within 12 above. */
	static boolean fixedSiteOk(GenerationContext context, String kind, int x, int z, int y, int r) {
		int h = Math.max(2, r / 2);
		for (int[] c : new int[][] {{0, 0}, {h, h}, {-h, h}, {h, -h}, {-h, -h}}) {
			var column = context.chunkGenerator().getBaseColumn(x + c[0], z + c[1], context.heightAccessor(), context.randomState());
			for (int yy = y - h; yy <= y + h; yy++) if (!column.getBlock(yy).getFluidState().isEmpty()) return false;
			if (kind.equals("hanging_undercity") && c[0] == 0) {
				boolean roof = false;
				for (int yy = y + 1; yy <= y + 12 && !roof; yy++) roof = !column.getBlock(yy).isAir() && column.getBlock(yy).getFluidState().isEmpty();
				if (!roof) return false;
			}
		}
		return true;
	}

	/** True when each of the 4 corners at {@code r} has a floor (solid, 2 air above) within 3 blocks of {@code floor}. Generator only. */
	static boolean caveFloorAround(GenerationContext context, int x, int z, int floor, int r) {
		for (int[] c : new int[][] {{r, r}, {-r, r}, {r, -r}, {-r, -r}}) {
			var column = context.chunkGenerator().getBaseColumn(x + c[0], z + c[1], context.heightAccessor(), context.randomState());
			boolean ok = false;
			for (int yy = floor + 3; yy >= floor - 3 && !ok; yy--) {
				var below = column.getBlock(yy - 1);
				ok = !below.isAir() && below.getFluidState().isEmpty() && column.getBlock(yy).isAir() && column.getBlock(yy + 1).isAir();
			}
			if (!ok) return false;
		}
		return true;
	}

	/** GENERATION.md G3: fill stays under each builder's own foundation cap, cut under its clear height. */
	public static GenFit.Limits siteLimits(String kind, int radius, GenerationContext context) {
		int minY = context.heightAccessor().getMinY() + 8, maxY = context.heightAccessor().getMaxY() - 40;
		int r = Math.min(radius, 18);
		return switch (kind) {
			case "great_hotbox" -> GenFit.Limits.surface(r, 5, 7, 10, minY, maxY);       // foundation cap 8
			case "bonfire_terrace" -> GenFit.Limits.surface(r, 5, 8, 10, minY, maxY);    // cap 10 from y-2
			case "munchie_market" -> GenFit.Limits.surface(r, 5, 5, 10, minY, maxY);     // cap 6
			case "fern_terrace_farms" -> GenFit.Limits.surface(r, 4, 7, 10, minY, maxY); // cap 8, clear 6
			case "lucid_spring" -> GenFit.Limits.surface(r, 3, 4, 40, minY, maxY);       // a spring may touch water
			case "greenhaze_return_rift" -> GenFit.Limits.surface(r, 4, 5, 15, minY, maxY);
			default -> GenFit.Limits.surface(r, 3, 4, 20, minY, maxY);                   // sesh_hut and small sites
		};
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	public static final class Piece extends StructurePiece {
		private final String kind;
		private final BlockPos origin;
		private final long seed;

		Piece(String kind, BlockPos origin, long seed, Blueprint bp) {
			super(PIECE, 0, new BoundingBox(origin.getX() - bp.radius(), origin.getY() - bp.below(), origin.getZ() - bp.radius(),
				origin.getX() + bp.radius(), origin.getY() + bp.above(), origin.getZ() + bp.radius()));
			this.kind = kind;
			this.origin = origin;
			this.seed = seed;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.kind = tag.getStringOr("kind", "");
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			this.seed = tag.getLongOr("seed", 0L);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putString("kind", kind);
			tag.putLong("origin", origin.asLong());
			tag.putLong("seed", seed);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			Blueprint bp = BLUEPRINTS.get(kind);
			if (bp == null) return;
			bp.build(Sink.clipped(level, chunkBB), origin, RandomSource.create(seed));
		}
	}
}
