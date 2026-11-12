package dev.elsewhere.petalwind_glasswaste;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.heliodor.SunwrightZiggurat;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalPagoda;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Boss sites as real structures (2.0 alpha.2): Petal Pagoda (Kazehana), Sunwright Ziggurat (Heliodor), the Sunpit (Ankhara)
 * and the Eye of the Gale (Tempestra). One structure type {@code elsewhere:pg_boss_site}; the JSON names the {@code kind}.
 * Also the survival summons: the three re-summon items and the three wind charges thrown into the Eye.
 * The old feature types stay registered (0.3.0 worlds reference them) but nothing places them any more.
 */
public final class PGStructures {
	private PGStructures() {}

	public static final String PAGODA = "petal_pagoda", ZIGGURAT = "sunwright_ziggurat", SUNPIT = "sunpit", EYE = "eye_of_the_gale", BREEZE = "breeze_temple";
	public static final int REARM_TICKS = 12000; // 10 minutes after a kill
	public static final int CHARGES_NEEDED = 3, CHARGE_WINDOW = 200;

	public static StructureType<PGBossStructure> TYPE;
	public static StructurePieceType PIECE;
	public static Item GALE_PINION, BLOSSOM_SEAL_CHARM, SUNWRIGHT_MIRROR_KEY, SUNPIT_LURE;
	/** arena centre -> game time of the last kill (in memory; a restart simply re-arms). */
	/** Re-arm cooldowns live in the persistent boss-kit store (a restart no longer re-arms an arena). */
	static String rearmKey(BlockPos center) {
		return "pg_rearm_" + center.asLong();
	}

	public static boolean rearmReady(ServerLevel level, BlockPos center) {
		return dev.elsewhere.boss.kit.KitStore.of(level.getServer()).cooldownReady(rearmKey(center));
	}

	public static void rearmStart(ServerLevel level, BlockPos center) {
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).cooldownStart(rearmKey(center), REARM_TICKS);
	}
	private static final Map<Long, Map<UUID, Long>> CHARGES = new HashMap<>();

	/** A boss site as the summon items, the Eye and the self-test see it: a 2.x piece or a 3.0 fit site ({@link dev.elsewhere.petalwind_glasswaste.fit3.PGFit.FitSite}). */
	public interface Site {
		String kind();

		BlockPos origin();

		BlockPos center();

		/** Repairs the arena and wakes a new boss (re-summon item). */
		void resummon(ServerLevel level);
	}

	/** The site a structure start holds (3.0 fit pieces or the 2.x piece), or null. */
	public static @Nullable Site siteOf(StructureStart start) {
		if (start == null || !start.isValid()) return null;
		for (StructurePiece p : start.getPieces()) if (p instanceof Piece pg) return pg;
		return dev.elsewhere.petalwind_glasswaste.fit3.PGFit.FitSite.of(start);
	}

	/**
	 * Pieces of a NEW start: 3.0 fit pieces (STRUCTURES.md, structures overhaul) unless the {@code pg_fit3} kill switch is off, then the 2.x piece.
	 * Starts already saved keep whatever pieces they were saved with.
	 */
	public static java.util.List<StructurePiece> newPieces(String kind, BlockPos origin, dev.elsewhere.petalwind_glasswaste.fit3.PGFit.Ground ground, long seed) {
		if (!dev.elsewhere.petalwind_glasswaste.fit3.PGFit.ENABLED) return java.util.List.of(new Piece(kind, origin));
		return new java.util.ArrayList<>(dev.elsewhere.petalwind_glasswaste.fit3.PGFit.plan(kind, origin, ground, seed, dev.elsewhere.petalwind_glasswaste.fit3.FitBuilders.BOWL_R));
	}

	public static Identifier structureId(String kind) {
		return Reg.id(kind);
	}

	/** Arena centre (standing position) for a site whose build origin is {@code base}. */
	public static BlockPos centerOf(String kind, BlockPos base) {
		return switch (kind) {
			case PAGODA -> base.above(PetalPagoda.TERRACE_Y + 1);
			case ZIGGURAT -> base.above(SunwrightZiggurat.SUMMIT_Y);
			default -> base;
		};
	}

	/** Builds the site and (for the three dormant-boss sites) places the boss, exactly like the old features did. */
	public static void buildAndSpawn(WorldGenLevel level, String kind, BlockPos base, EntitySpawnReason reason, boolean spawnBoss) {
		switch (kind) {
			case PAGODA -> {
				BlockPos center = PetalPagoda.build(level, base, false);
				if (!spawnBoss) return;
				Kazehana k = ModEntities.KAZEHANA.create(level.getLevel(), reason);
				if (k != null) {
					k.setArena(center, PetalPagoda.ARENA_RADIUS, 4, 12);
					k.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
					level.addFreshEntityWithPassengers(k);
				}
			}
			case ZIGGURAT -> {
				BlockPos summit = SunwrightZiggurat.build(level, base);
				if (!spawnBoss) return;
				Heliodor h = ModEntities.HELIODOR.create(level.getLevel(), reason);
				if (h != null) {
					h.setArena(summit, SunwrightZiggurat.ARENA_RADIUS, 4, 12);
					h.snapTo(summit.getX() + 0.5, summit.getY(), summit.getZ() + 0.5);
					level.addFreshEntityWithPassengers(h);
				}
			}
			case SUNPIT -> {
				BlockPos floor = Sunpit.build(level, base);
				if (!spawnBoss) return;
				Ankhara a = ModEntities.ANKHARA.create(level.getLevel(), reason);
				if (a != null) {
					a.setArena(floor, Sunpit.ARENA_RADIUS, 6, 12);
					a.snapTo(floor.getX() + 0.5, floor.getY() - 4.5, floor.getZ() + 0.5);
					level.addFreshEntityWithPassengers(a);
				}
			}
			case EYE -> EyeOfTheGale.build(level, base);
			case BREEZE -> BreezeTemple.build(level, base);
			default -> {
			}
		}
	}

	public static @Nullable Tempestra spawnTempestra(ServerLevel level, BlockPos center) {
		Tempestra t = ModEntities.TEMPESTRA.create(level, EntitySpawnReason.TRIGGERED);
		if (t == null) return null;
		t.setArena(center, EyeOfTheGale.ARENA_RADIUS, 18, 20);
		t.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		level.addFreshEntity(t);
		return t;
	}

	// ------------------------------------------------------------------------------------------ the structure
	public static final class PGBossStructure extends Structure {
		public static final MapCodec<PGBossStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
		).apply(i, PGBossStructure::new));

		public final String kind;

		public PGBossStructure(StructureSettings settings, String kind) {
			super(settings);
			this.kind = kind;
		}

		@Override
		public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			ChunkPos cp = context.chunkPos();
			int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
			int y;
			if (GenFit.ENABLED && !kind.equals(EYE)) {
				// 3.0 site rules (GENERATION.md G2/G3): seeded multi-offset choice, cut <= 5, fill within the foundation, dry ground
				int top = context.heightAccessor().getMaxY() - 40, bottom = context.heightAccessor().getMinY() + 8;
				GenFit.Limits lim = switch (kind) {
					case SUNPIT -> GenFit.Limits.surface(12, 6, 4, 0, bottom, top);    // dug 6 down: no water at all
					case ZIGGURAT -> GenFit.Limits.surface(14, 5, 6, 10, bottom, top);
					default -> GenFit.Limits.surface(12, 5, 6, 10, bottom, top);       // pagoda, breeze temple (foundation cap 8)
				};
				var site = GenFit.pickAligned(context, lim, 24); // built from the origin chunk: stay on a chunk middle (piece box +-24)
				if (site.isEmpty()) return Optional.empty();
				x = site.get().x();
				z = site.get().z();
				y = site.get().floor();
			} else {
				y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			}
			BlockPos base;
			switch (kind) {
				case PAGODA -> {
					if (y < 100 || y > 280) return Optional.empty();
					base = new BlockPos(x, y, z);
				}
				case ZIGGURAT -> {
					if (y < 50) return Optional.empty();
					base = new BlockPos(x, y - 1, z);
				}
				case BREEZE -> {
					if (y < 70 || y > 300) return Optional.empty();
					base = new BlockPos(x, y, z);
				}
				case SUNPIT -> {
					if (y < 50) return Optional.empty();
					base = new BlockPos(x, y - 6, z);
				}
				default -> base = new BlockPos(x, 190, z); // the Eye floats in the sky
			}
			if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(x >> 2, Math.max(y, 64) >> 2, z >> 2))
				&& !kind.equals(EYE)) return Optional.empty();
			final BlockPos origin = base;
			// planned only when the start is really generated (locate and maps stop at the stub); the ground comes from the generator alone
			return Optional.of(new GenerationStub(origin, b -> newPieces(kind, origin, dev.elsewhere.petalwind_glasswaste.fit3.PGFit.ground(context),
				dev.elsewhere.petalwind_glasswaste.fit3.PGFit.siteSeed(context.seed(), origin)).forEach(b::addPiece)));
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}
	}

	public static final class Piece extends StructurePiece implements Site {
		final String kind;
		public final BlockPos origin;

		Piece(String kind, BlockPos origin) {
			super(PIECE, 0, new BoundingBox(origin.getX() - 24, origin.getY() - 30, origin.getZ() - 24, origin.getX() + 24, origin.getY() + 34, origin.getZ() + 24));
			this.kind = kind;
			this.origin = origin;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.kind = tag.getStringOr("kind", "");
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
		}

		public String kind() {
			return kind;
		}

		public BlockPos center() {
			return centerOf(kind, origin);
		}

		@Override
		public BlockPos origin() {
			return origin;
		}

		@Override
		public void resummon(ServerLevel level) {
			buildAndSpawn(level, kind, origin, EntitySpawnReason.TRIGGERED, true);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putString("kind", kind);
			tag.putLong("origin", origin.asLong());
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
								BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
			if (chunkPos.x() != (origin.getX() >> 4) || chunkPos.z() != (origin.getZ() >> 4)) return; // built once, from the chunk at its centre (stays inside the 3x3 a chunk may write)
			buildAndSpawn(level, kind, origin, EntitySpawnReason.STRUCTURE, true);
		}
	}

	/** The site of {@code kind} whose pieces the position is inside (2.x piece or 3.0 fit site), or null. */
	public static @Nullable Site pieceAt(ServerLevel level, BlockPos pos, String kind) {
		var key = net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, structureId(kind));
		var st = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key);
		if (st.isEmpty()) return null;
		StructureStart start = level.structureManager().getStructureAt(pos, st.get().value());
		return siteOf(start);
	}

	// ------------------------------------------------------------------------------------------ summon items
	/** Re-summon item: used inside its site's arena (right-click a block there), when the boss is gone and 10 minutes have passed. */
	public static final class SummonItem extends Item {
		private final String kind;

		public SummonItem(Item.Properties props, String kind) {
			super(props);
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext ctx) {
			if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
			var player = ctx.getPlayer();
			if (player == null) return InteractionResult.PASS;
			Site piece = pieceAt(level, ctx.getClickedPos(), kind);
			if (piece == null) {
				player.sendOverlayMessage(Component.literal("Use this inside the arena it belongs to."));
				return InteractionResult.FAIL;
			}
			BlockPos center = piece.center();
			double dx = ctx.getClickedPos().getX() - center.getX(), dz = ctx.getClickedPos().getZ() - center.getZ();
			int reach = kind.equals(SUNPIT) ? Sunpit.ARENA_RADIUS : PetalPagoda.ARENA_RADIUS + 2;
			if (dx * dx + dz * dz > reach * reach) {
				player.sendOverlayMessage(Component.literal("Use this inside the arena it belongs to."));
				return InteractionResult.FAIL;
			}
			if (!level.getEntitiesOfClass(BossBase.class, new AABB(center).inflate(60)).isEmpty()) {
				player.sendOverlayMessage(Component.literal("Something already waits here."));
				return InteractionResult.FAIL;
			}
			if (!rearmReady(level, center)) {
				player.sendOverlayMessage(Component.literal("The arena is still quiet. Give it a few minutes."));
				return InteractionResult.FAIL;
			}
			if (!player.isCreative()) ctx.getItemInHand().shrink(1);
			piece.resummon(level);
			level.playSound(null, center, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
			return InteractionResult.SUCCESS;
		}
	}

	// ------------------------------------------------------------------------------------------ init
	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Reg.id("pg_boss_site"), () -> PGBossStructure.CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("pg_boss_site_piece"), (StructurePieceType) (ctx, tag) -> new Piece(tag));
		dev.elsewhere.petalwind_glasswaste.fit3.PGFit.init(); // 3.0 fit pieces (s-struct-petalwind)

		Reg.currentTab = "petalwind";
		GALE_PINION = Reg.item("gale_pinion", new Item.Properties().rarity(Rarity.EPIC));
		BLOSSOM_SEAL_CHARM = Reg.item("blossom_seal_charm", p -> new SummonItem(p, PAGODA), new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
		Reg.currentTab = "glasswaste";
		SUNWRIGHT_MIRROR_KEY = Reg.item("sunwright_mirror_key", p -> new SummonItem(p, ZIGGURAT), new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
		SUNPIT_LURE = Reg.item("sunpit_lure", p -> new SummonItem(p, SUNPIT), new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
		Reg.currentTab = "petalwind";

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof BossBase b && b.arenaCenter() != null && entity.level() instanceof ServerLevel l) {
				rearmStart(l, b.arenaCenter());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 != 0) return;
			ServerLevel level = server.getLevel(PetalwindDimension.LEVEL);
			if (level == null || level.players().isEmpty()) return;
			for (ServerPlayer p : level.players()) {
				Site piece = pieceAt(level, p.blockPosition(), EYE);
				if (piece != null) eyeTick(level, piece.center(), p);
			}
		});
	}

	static void eyeTick(ServerLevel level, BlockPos center, @Nullable ServerPlayer player) {
		long now = level.getGameTime();
		Map<UUID, Long> seen = CHARGES.computeIfAbsent(center.asLong(), k -> new LinkedHashMap<>());
		seen.values().removeIf(t -> now - t > CHARGE_WINDOW);
		for (WindCharge c : level.getEntitiesOfClass(WindCharge.class, new AABB(center).inflate(8, 0, 8).expandTowards(0, -16, 0).expandTowards(0, 8, 0))) {
			seen.putIfAbsent(c.getUUID(), now);
		}
		if (seen.size() < CHARGES_NEEDED) return;
		seen.clear();
		if (!level.getEntitiesOfClass(Tempestra.class, new AABB(center).inflate(80)).isEmpty()) return;
		if (!rearmReady(level, center)) {
			if (player != null) player.sendOverlayMessage(Component.literal("The storm is still settling. Give it a few minutes."));
			return;
		}
		spawnTempestra(level, center);
		level.playSound(null, center, net.minecraft.sounds.SoundEvents.BREEZE_IDLE_AIR, net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.5F);
	}
}
