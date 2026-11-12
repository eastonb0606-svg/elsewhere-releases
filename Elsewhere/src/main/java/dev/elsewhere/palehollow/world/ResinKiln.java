package dev.elsewhere.palehollow.world;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.registry.ModEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.RandomizableContainer;
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
 * Resin Kiln (Pale Hollow): a small pale-oak and stone-brick hut with a kiln, a barrel of supplies and the Sap Tapper.
 * Built once from the chunk at its centre (like the boss sites), so it stays inside the 3x3 chunks a chunk may write.
 */
public final class ResinKiln {
	private ResinKiln() {}

	public static final int R = 4;
	public static StructureType<KilnStructure> TYPE;
	public static StructurePieceType PIECE;

	public static ResourceKey<LootTable> loot() {
		return ResourceKey.create(Registries.LOOT_TABLE, Elsewhere.id("chests/resin_kiln"));
	}

	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Elsewhere.id("resin_kiln"), () -> KilnStructure.CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Elsewhere.id("resin_kiln_piece"), (StructurePieceType) (ctx, tag) -> new Piece(tag));
	}

	public static void build(WorldGenLevel level, BlockPos base, RandomSource random) {
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), planks = Blocks.PALE_OAK_PLANKS.defaultBlockState(),
			log = Blocks.PALE_OAK_LOG.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), slab = Blocks.PALE_OAK_SLAB.defaultBlockState();
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
			for (int y = -1; y >= -6; y--) {
				BlockPos q = base.offset(x, y, z);
				if (!level.getBlockState(q).isAir() && !level.getBlockState(q).canBeReplaced()) break;
				level.setBlock(q, brick, Block.UPDATE_CLIENTS);
			}
			boolean edge = Math.abs(x) == R || Math.abs(z) == R, corner = Math.abs(x) == R && Math.abs(z) == R;
			level.setBlock(base.offset(x, 0, z), planks, Block.UPDATE_CLIENTS);
			for (int y = 1; y <= 4; y++) {
				boolean door = z == R && Math.abs(x) <= 0 && y <= 2;
				level.setBlock(base.offset(x, y, z), corner ? log : edge && !door ? (y == 2 && (x + z) % 2 == 0 ? Blocks.GLASS_PANE.defaultBlockState() : planks) : air, Block.UPDATE_CLIENTS);
			}
			level.setBlock(base.offset(x, 5, z), slab, Block.UPDATE_CLIENTS);
		}
		// the kiln: a stone-brick stack with a furnace and a lantern, in the north-west corner
		level.setBlock(base.offset(-3, 1, -3), brick, Block.UPDATE_CLIENTS);
		level.setBlock(base.offset(-3, 2, -3), Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.BlastFurnaceBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.BlastFurnaceBlock.LIT, true), Block.UPDATE_CLIENTS);
		level.setBlock(base.offset(-3, 3, -3), Blocks.STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(base.offset(3, 4, 3), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(base.offset(-3, 4, 3), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		// supplies: a barrel with the kiln's loot
		BlockPos barrel = base.offset(3, 1, -3);
		level.setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
		RandomizableContainer.setBlockEntityLootTable(level, random, barrel, loot());
		level.setBlock(base.offset(2, 1, -3), Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	/** The trader's standing spot. */
	public static BlockPos traderSpot(BlockPos base) {
		return base.offset(0, 1, 1);
	}

	public static final class KilnStructure extends Structure {
		public static final MapCodec<KilnStructure> CODEC = simpleCodec(KilnStructure::new);

		public KilnStructure(StructureSettings settings) {
			super(settings);
		}

		@Override
		public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			ChunkPos cp = context.chunkPos();
			int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
			int y;
			if (GenFit.ENABLED) {
				// 3.0 site rules (GENERATION.md G2/G3): seeded multi-offset choice; fill stays under the 4-6 block foundations
				GenFit.Limits lim = GenFit.Limits.surface(dev.elsewhere.palehollow.fit3.CoreFit.ENABLED ? 6 : 5, 4, 4, 10, 40, 200);
				var site = GenFit.pick(context, lim);
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
				// 3.0 (core_fit): the L-shaped workshop as three pieces on their own floors, built per chunk
				final var ground = dev.elsewhere.palehollow.fit3.CoreFit.ground(context);
				final long seed = dev.elsewhere.palehollow.fit3.CoreFit.seed(context, 0x4B494C4E);
				return Optional.of(new GenerationStub(origin, b -> dev.elsewhere.palehollow.fit3.CoreFit.planKiln(ground, origin, seed).forEach(b::addPiece)));
			}
			return Optional.of(new GenerationStub(origin, b -> b.addPiece(new Piece(origin))));
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}
	}

	public static final class Piece extends StructurePiece {
		public final BlockPos origin;

		Piece(BlockPos origin) {
			super(PIECE, 0, new BoundingBox(origin.getX() - 8, origin.getY() - 10, origin.getZ() - 8, origin.getX() + 8, origin.getY() + 10, origin.getZ() + 8));
			this.origin = origin;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putLong("origin", origin.asLong());
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			if (chunkPos.x() != (origin.getX() >> 4) || chunkPos.z() != (origin.getZ() >> 4)) return;
			build(level, origin, random);
			var tapper = ModEntities.SAP_TAPPER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (tapper != null) {
				BlockPos s = traderSpot(origin);
				tapper.snapTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, random.nextFloat() * 360F, 0F);
				tapper.setWanderTarget(s);
				level.addFreshEntity(tapper);
			}
		}
	}
}
