package dev.elsewhere.nether_end_bosses;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.elsewhere.boss.aphelion.AphelionArena;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Structure type {@code elsewhere:nether_end_boss_arena}: one boss arena ({"boss": "solaxis"}) as a real structure, so
 * /locate, explorer maps and the finder can find it. The arena is built by the same {@link ArenaBuilder} as before, from
 * the chunk at its centre (radius at most 24, inside the 3x3 chunks a chunk may write). End arenas only generate on the
 * outer islands ({@code min_distance} blocks from the world origin).
 */
public final class BossArenaStructure extends Structure {
	public static final MapCodec<BossArenaStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Codec.STRING.fieldOf("boss").forGetter(s -> s.boss),
		Codec.INT.optionalFieldOf("min_distance", 0).forGetter(s -> s.minDistance)
	).apply(i, BossArenaStructure::new));

	public static StructureType<BossArenaStructure> TYPE;
	public static StructurePieceType PIECE;

	public final String boss;
	public final int minDistance;

	public BossArenaStructure(StructureSettings settings, String boss, int minDistance) {
		super(settings);
		this.boss = boss;
		this.minDistance = minDistance;
	}

	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Reg.id("nether_end_boss_arena"), () -> CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("nether_end_boss_arena_piece"), (StructurePieceType) (ctx, tag) -> new Piece(tag));
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos cp = context.chunkPos();
		int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
		if (minDistance > 0 && (long) x * x + (long) z * z < (long) minDistance * minDistance) return Optional.empty();
		RandomSource rnd = context.random();
		boolean end = boss.equals("ossyra") || boss.equals("aphelion");
		int r = footRadius(boss);
		// sample columns over the footprint: the centre plus 8 columns on each of two rings (r/2 and r)
		int n = 17;
		int[] sx = new int[n], sz = new int[n];
		sx[0] = x;
		sz[0] = z;
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			sx[1 + i] = x + (int) Math.round(Math.cos(a) * r);
			sz[1 + i] = z + (int) Math.round(Math.sin(a) * r);
			sx[9 + i] = x + (int) Math.round(Math.cos(a) * r * 0.5);
			sz[9 + i] = z + (int) Math.round(Math.sin(a) * r * 0.5);
		}
		NoiseColumn[] cols = new NoiseColumn[n];
		for (int i = 0; i < n; i++) cols[i] = context.chunkGenerator().getBaseColumn(sx[i], sz[i], context.heightAccessor(), context.randomState());
		int y;
		if (end) {
			// End arenas stand on real end stone: every sampled column needs ground, and the ground must be fairly level.
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, voids = 0;
			for (int i = 0; i < n; i++) {
				int top = -1;
				for (int yy = 120; yy >= 20; yy--) {
					var st = cols[i].getBlock(yy);
					if (!st.isAir() && st.getFluidState().isEmpty()) { top = yy; break; }
				}
				if (top < 0) { voids++; continue; }
				lo = Math.min(lo, top);
				hi = Math.max(hi, top);
			}
			if (voids > 3 || hi == Integer.MIN_VALUE || hi - lo > 12) return Optional.empty();
			y = boss.equals("ossyra") ? hi + 27 : hi + 1; // ossyra stands on its own island at the foot of its tower (27 below the floor)
			if (y + 24 > context.heightAccessor().getMaxY()) return Optional.empty();
		} else {
			int top = 96 - rnd.nextInt(24);
			int bottom = boss.equals("mournmaw") ? 33 : 40;
			y = Integer.MIN_VALUE;
			for (int yy = top; yy >= bottom; yy--) {
				var floor = cols[0].getBlock(yy - 1);
				if (!(cols[0].getBlock(yy).isAir() && cols[0].getBlock(yy + 1).isAir() && cols[0].getBlock(yy + 2).isAir()
					&& !floor.isAir() && floor.getFluidState().isEmpty())) continue;
				// the footprint must have ground (not lava or air) within FILL_CAP below the foundation in nearly every sampled column
				int deep = 0;
				for (int i = 1; i < n; i++) {
					int d = 0;
					for (int py = yy - 3; py >= context.heightAccessor().getMinY() && d <= FILL_CAP; py--, d++) {
						var st = cols[i].getBlock(py);
						if (!st.isAir() && st.getFluidState().isEmpty()) break;
					}
					if (d > FILL_CAP) deep++;
				}
				if (deep <= 2) { y = yy; break; }
			}
			if (y == Integer.MIN_VALUE) return Optional.empty();
		}
		// tighter biome check: the centre and four points on the footprint rim must all be in the structure's biomes
		int[][] chk = {{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}};
		for (int[] c : chk) {
			if (!context.validBiome().test(context.biomeResolver().getNoiseBiome((x + c[0]) >> 2, y >> 2, (z + c[1]) >> 2))) return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, y, z);
		long seed = rnd.nextLong();
		return Optional.of(new GenerationStub(origin, b -> b.addPiece(new Piece(boss, origin, seed))));
	}

	/** Deepest foundation a nether arena may need under its rim (also the depth it is rejected beyond). */
	static final int FILL_CAP = 16;

	static int footRadius(String boss) {
		return switch (boss) {
			case "mournmaw" -> 18;
			case "aphelion" -> 9;
			case "ossyra" -> 8;
			default -> 13;
		};
	}

	/**
	 * One-time matching foundation below the arena, down to solid ground: blackstone (basalt for Mournmaw) in the Nether,
	 * end stone in the End. Only air and fluid are replaced, depth is capped (tapering towards the rim) and nothing runs
	 * after generation. Corridors get their own capped pillars.
	 */
	static void foundation(WorldGenLevel level, String boss, BlockPos o) {
		switch (boss) {
			case "grunthar", "solaxis" -> {
				BlockState b = Blocks.BLACKSTONE.defaultBlockState();
				support(level, o.getX(), o.getZ(), 13.5, o.getY() - 3, 0, b, 24, 14);
				for (int r = 12; r <= 18; r++) for (int dx = -2; dx <= 2; dx++) {
					column(level, o.getX() + dx, o.getY() - 2, 0, o.getZ() + r, b, 12);
					if (boss.equals("solaxis")) column(level, o.getX() + dx, o.getY() - 2, 0, o.getZ() - r, b, 12);
				}
			}
			case "mournmaw" -> support(level, o.getX(), o.getZ(), 18.5, o.getY() - 3, 0, Blocks.BASALT.defaultBlockState(), 24, 14);
			case "aphelion" -> {
				BlockState b = Blocks.END_STONE.defaultBlockState();
				support(level, o.getX(), o.getZ(), 10.5, o.getY() - 2, 4, b, 24, 10);
				for (int i = 0; i < AphelionArena.ISLANDS; i++) {
					BlockPos c = AphelionArena.islandCenter(o, i);
					support(level, c.getX(), c.getZ(), 4.5, c.getY() - 2, 4, b, 14, 6);
				}
			}
			case "ossyra" -> support(level, o.getX(), o.getZ(), 7.5, o.getY() - 28, 4, Blocks.END_STONE.defaultBlockState(), 24, 10);
			default -> { }
		}
	}

	private static void support(WorldGenLevel level, int cx, int cz, double radius, int topY, int skip, BlockState block, int capCentre, int capEdge) {
		int ir = (int) Math.ceil(radius);
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > radius) continue;
			column(level, cx + dx, topY, skip, cz + dz, block, (int) Math.round(capEdge + (capCentre - capEdge) * (1.0 - d / radius)));
		}
	}

	/** Fills air and fluid downwards from topY until solid ground, the world floor or {@code cap} blocks. */
	private static void column(WorldGenLevel level, int x, int topY, int skip, int z, BlockState block, int cap) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int y = topY;
		boolean open = false;
		// first step through the arena's own tapering underside (up to skip solid blocks), then fill
		for (int i = 0; i <= skip && y >= level.getMinY() + 1; i++, y--) {
			BlockState st = level.getBlockState(p.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty()) { open = true; break; }
		}
		if (!open) return;
		for (int i = 0; i < cap && y >= level.getMinY() + 1; i++, y--) {
			BlockState st = level.getBlockState(p.set(x, y, z));
			if (!st.isAir() && st.getFluidState().isEmpty()) break;
			level.setBlock(p, block, Build.FLAGS);
		}
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	public static final class Piece extends StructurePiece {
		public final String boss;
		public final BlockPos origin;
		private final long seed;

		Piece(String boss, BlockPos origin, long seed) {
			super(PIECE, 0, new BoundingBox(origin.getX() - 24, origin.getY() - 32, origin.getZ() - 24, origin.getX() + 24, origin.getY() + 32, origin.getZ() + 24));
			this.boss = boss;
			this.origin = origin;
			this.seed = seed;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.boss = tag.getStringOr("boss", "");
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			this.seed = tag.getLongOr("seed", 0L);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putString("boss", boss);
			tag.putLong("origin", origin.asLong());
			tag.putLong("seed", seed);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			if (chunkPos.x() != (origin.getX() >> 4) || chunkPos.z() != (origin.getZ() >> 4)) return;
			ArenaBuilder builder = ArenaBuilder.BY_BOSS.get(boss);
			if (builder == null) return;
			builder.build(level, origin, RandomSource.create(seed));
			foundation(level, boss, origin);
			NetherEndBossesContent.LOGGER.debug("Generated {} arena structure at {}", boss, origin);
		}
	}
}
