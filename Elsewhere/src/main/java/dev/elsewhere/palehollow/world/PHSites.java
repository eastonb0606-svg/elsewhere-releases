package dev.elsewhere.palehollow.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Pale Hollow sites (one structure type, three kinds): Hollow Shrine, Drowned Chapel (guaranteed Paleweave Mantle) and the
 * Lantern Procession road (a Return Rift at every third). Built once from the chunk at the centre, like the Resin Kiln.
 */
public final class PHSites {
	private PHSites() {}

	public static final int ROAD_HALF = 14;
	public static StructureType<SiteStructure> TYPE;
	public static StructurePieceType PIECE;

	public static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, Elsewhere.id("chests/" + name));
	}

	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Elsewhere.id("ph_site"), () -> SiteStructure.CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Elsewhere.id("ph_site_piece"), (StructurePieceType) (ctx, tag) -> new Piece(tag));
	}

	/** Every third road carries a Return Rift. */
	public static boolean roadHasRift(BlockPos origin) {
		return Math.floorMod((origin.getX() >> 4) * 31 + (origin.getZ() >> 4) * 17, 3) == 0;
	}

	private static void set(WorldGenLevel l, BlockPos p, BlockState s) {
		l.setBlock(p, s, Block.UPDATE_CLIENTS);
	}

	private static void foundation(WorldGenLevel l, BlockPos p, BlockState s, int depth) {
		for (int y = 0; y >= -depth; y--) {
			BlockPos q = p.offset(0, y, 0);
			if (y < 0 && !l.getBlockState(q).isAir() && !l.getBlockState(q).canBeReplaced() && l.getBlockState(q).getFluidState().isEmpty()) break;
			set(l, q, s);
		}
	}

	public static void buildShrine(WorldGenLevel l, BlockPos b, RandomSource r) {
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
			foundation(l, b.offset(x, -1, z), r.nextInt(3) == 0 ? mossy : brick, 5);
			for (int y = 0; y <= 4; y++) set(l, b.offset(x, y, z), air);
			boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
			if (corner) for (int y = 0; y <= 3; y++) set(l, b.offset(x, y, z), y == 3 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
			if (corner) set(l, b.offset(x, 4, z), Blocks.LANTERN.defaultBlockState());
		}
		set(l, b.offset(0, 0, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		set(l, b.offset(0, 1, 0), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
		RandomizableContainer.setBlockEntityLootTable(l, r, b.offset(0, 1, 0), loot("hollow_shrine"));
		set(l, b.offset(-1, 0, 0), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
		set(l, b.offset(1, 0, 0), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
		set(l, b.offset(0, 0, -1), ModBlocks.MOON_BLOOM.defaultBlockState());
	}

	public static void buildChapel(WorldGenLevel l, BlockPos b, RandomSource r) {
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
			crack = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), planks = Blocks.PALE_OAK_PLANKS.defaultBlockState(),
			air = Blocks.AIR.defaultBlockState(), water = Blocks.WATER.defaultBlockState(), log = Blocks.PALE_OAK_LOG.defaultBlockState();
		int hx = 5, hz = 8;
		for (int x = -hx; x <= hx; x++) for (int z = -hz; z <= hz; z++) {
			foundation(l, b.offset(x, -3, z), mossy, 4);
			boolean wall = Math.abs(x) == hx || Math.abs(z) == hz;
			for (int y = -2; y <= 6; y++) {
				BlockPos p = b.offset(x, y, z);
				if (wall) {
					boolean door = z == hz && Math.abs(x) <= 1 && y >= 0 && y <= 2;
					boolean window = y == 2 && Math.abs(x) == hx && Math.abs(z) % 3 == 0 || y == 2 && Math.abs(z) == hz && Math.abs(x) == 3;
					set(l, p, door || y > 5 ? air : window ? Blocks.GLASS_PANE.defaultBlockState() : r.nextInt(4) == 0 ? crack : brick);
				} else {
					set(l, p, y <= -1 ? water : air);
				}
			}
			// roof of pale oak slabs, with a hole where the roof has fallen in
			if (!(Math.abs(x) <= 1 && z > 2 && z < 6)) set(l, b.offset(x, 6, z), Math.abs(x) == hx || Math.abs(z) == hz ? log : Blocks.PALE_OAK_SLAB.defaultBlockState());
		}
		// pews (submerged benches) and the altar at the far end
		for (int z = -3; z <= 5; z += 2) {
			set(l, b.offset(-3, -2, z), Blocks.PALE_OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, true));
			set(l, b.offset(3, -2, z), Blocks.PALE_OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, true));
		}
		BlockPos altar = b.offset(0, -2, -hz + 1);
		set(l, altar, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		BlockPos chest = altar.above();
		set(l, chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED, true));
		RandomizableContainer.setBlockEntityLootTable(l, r, chest, loot("drowned_chapel"));
		set(l, b.offset(-2, -1, -hz + 1), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.WATERLOGGED, true));
		set(l, b.offset(2, -1, -hz + 1), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.WATERLOGGED, true));
	}

	/** The road runs along x, 3 wide; candles along both edges, lantern posts every 7 blocks; a rift pad at the centre when {@code rift}. */
	public static void buildRoad(WorldGenLevel l, BlockPos b, RandomSource r, boolean rift) {
		BlockState planks = Blocks.PALE_OAK_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int x = -ROAD_HALF; x <= ROAD_HALF; x++) {
			int gy = surfaceY(l, b.getX() + x, b.getZ(), b.getY());
			for (int z = -1; z <= 1; z++) {
				BlockPos p = new BlockPos(b.getX() + x, gy, b.getZ() + z);
				set(l, p.below(), planks);
				for (int y = 0; y <= 3; y++) set(l, p.above(y), air);
			}
			for (int z : new int[] {-2, 2}) {
				BlockPos p = new BlockPos(b.getX() + x, gy, b.getZ() + z);
				if (!l.getBlockState(p.below()).isSolid()) set(l, p.below(), Blocks.DIRT.defaultBlockState());
				if (Math.floorMod(x, 7) == 0) {
					set(l, p, Blocks.PALE_OAK_FENCE.defaultBlockState());
					set(l, p.above(), Blocks.PALE_OAK_FENCE.defaultBlockState());
					set(l, p.above(2), Blocks.LANTERN.defaultBlockState());
				} else if (x % 2 == 0) {
					set(l, p, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
				} else {
					set(l, p, air);
				}
			}
		}
		if (rift) {
			BlockPos c = new BlockPos(b.getX(), surfaceY(l, b.getX(), b.getZ(), b.getY()), b.getZ());
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(l, c.offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState());
			set(l, c, ModBlocks.RETURN_RIFT.defaultBlockState());
			set(l, c.above(), ModBlocks.RETURN_RIFT.defaultBlockState());
		}
	}

	private static int surfaceY(WorldGenLevel l, int x, int z, int fallback) {
		int y = l.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		return Math.abs(y - fallback) > 6 ? fallback : y;
	}

	public static final class SiteStructure extends Structure {
		public static final MapCodec<SiteStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i), Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)).apply(i, SiteStructure::new));
		public final String kind;

		public SiteStructure(StructureSettings settings, String kind) {
			super(settings);
			this.kind = kind;
		}

		@Override
		public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			ChunkPos cp = context.chunkPos();
			int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
			int y;
			if (GenFit.ENABLED) {
				// 3.0 site rules (GENERATION.md G2/G3): seeded multi-offset choice; fill stays under the 4-6 block foundations
				GenFit.Limits lim = switch (kind) {
					// a bog chapel may stand in water; 3.0 look (core_fit): only where the ground spread is at most 2 (STRUCTURES.md 4)
					case "chapel" -> dev.elsewhere.palehollow.fit3.CoreFit.ENABLED ? GenFit.Limits.surface(8, 1, 1, 60, 40, 200) : GenFit.Limits.surface(7, 4, 4, 60, 40, 200);
					case "road" -> GenFit.Limits.surface(14, 6, 4, 25, 40, 200);   // the road follows the ground per column
					default -> GenFit.Limits.surface(5, 4, 4, 10, 40, 200);        // shrine
				};
				var site = GenFit.pickAligned(context, lim, 15); // built from the origin chunk: stay on a chunk middle (piece box +-15)
				if (site.isEmpty()) return Optional.empty();
				x = site.get().x();
				z = site.get().z();
				y = site.get().floor();
			} else {
				y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			}
			if (y < 40 || y > 200) return Optional.empty();
			if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(x >> 2, y >> 2, z >> 2))) return Optional.empty();
			final BlockPos origin = new BlockPos(x, y, z);
			if (dev.elsewhere.palehollow.fit3.CoreFit.ENABLED) {
				// 3.0 (core_fit): many small terrain-fitted pieces on their own floors; planned once, from generator noise only
				final var ground = dev.elsewhere.palehollow.fit3.CoreFit.ground(context);
				final long seed = dev.elsewhere.palehollow.fit3.CoreFit.seed(context, kind.hashCode());
				return Optional.of(new GenerationStub(origin, b -> dev.elsewhere.palehollow.fit3.CoreFit.planSite(kind, ground, origin, seed).forEach(b::addPiece)));
			}
			return Optional.of(new GenerationStub(origin, b -> b.addPiece(new Piece(origin, kind))));
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}
	}

	public static final class Piece extends StructurePiece {
		public final BlockPos origin;
		public final String kind;

		Piece(BlockPos origin, String kind) {
			super(PIECE, 0, new BoundingBox(origin.getX() - 15, origin.getY() - 12, origin.getZ() - 15, origin.getX() + 15, origin.getY() + 12, origin.getZ() + 15));
			this.origin = origin;
			this.kind = kind;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			this.kind = tag.getStringOr("kind", "shrine");
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putLong("origin", origin.asLong());
			tag.putString("kind", kind);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			if (chunkPos.x() != (origin.getX() >> 4) || chunkPos.z() != (origin.getZ() >> 4)) return;
			switch (kind) {
				case "chapel" -> buildChapel(level, origin, random);
				case "road" -> buildRoad(level, origin, random, roadHasRift(origin));
				default -> buildShrine(level, origin, random);
			}
		}
	}
}
