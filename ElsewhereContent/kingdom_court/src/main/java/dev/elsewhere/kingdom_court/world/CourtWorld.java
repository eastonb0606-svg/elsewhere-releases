package dev.elsewhere.kingdom_court.world;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.CourtContent;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * Worldgen for the realm: structure {@code elsewhere:castle_aldermark} (castle, keep, two walled towns, road) with
 * terrain adaptation {@code beard_box}, placed by {@link Placement} only in the one chunk the server picked at start
 * ({@link CourtSite}), so it only ever generates in fresh chunks and never touches player builds.
 */
public final class CourtWorld {
	private CourtWorld() {}

	public static final ResourceKey<Structure> STRUCTURE_KEY = ResourceKey.create(Registries.STRUCTURE, CourtContent.id("castle_aldermark"));
	/** Level seed -> the realm site (centre, y = floor level) chosen at server start. */
	static final Map<Long, BlockPos> SITES = new ConcurrentHashMap<>();
	/** Structure key of a faction's castle: {@code elsewhere:castle_<faction path>} (Aldermark's is {@link #STRUCTURE_KEY}). */
	public static ResourceKey<Structure> structureKey(Identifier faction) {
		return Factions.ALDERMARK.equals(faction) ? STRUCTURE_KEY : ResourceKey.create(Registries.STRUCTURE, CourtContent.id("castle_" + faction.getPath()));
	}

	public static StructureType<RealmStructure> TYPE;
	public static StructurePieceType PIECE, WATER_PIECE;
	/** Seed + faction -> {direction (1 south, 3 north), shore distance} of a waterfront realm. */
	static final Map<String, int[]> WATER = new ConcurrentHashMap<>();

	public static void setWater(long seed, Identifier faction, int @Nullable [] info) {
		if (info == null) WATER.remove(key(seed, faction));
		else WATER.put(key(seed, faction), info);
	}

	public static int @Nullable [] water(long seed, Identifier faction) {
		return WATER.get(key(seed, faction));
	}

	public static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, CourtContent.id("castle_aldermark"), () -> RealmStructure.CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, CourtContent.id("castle_aldermark_piece"), (StructurePieceType.ContextlessType) RealmPiece::new);
		WATER_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, CourtContent.id("castle_aldermark_waterfront_piece"), (StructurePieceType.ContextlessType) WaterPiece::new);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, CourtContent.id("castle_aldermark"), Placement.CODEC);
		CastleStyles.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> RealmFit.clearMemo()); // s-heapleak: no realm memo kept across worlds
	}

	/** Level seed + faction -> that faction's realm site (every faction except Aldermark). */
	static final Map<String, BlockPos> FACTION_SITES = new ConcurrentHashMap<>();

	static String key(long seed, Identifier faction) {
		return seed + "|" + faction;
	}

	public static void setSite(long seed, @Nullable BlockPos site) {
		if (site == null) SITES.remove(seed);
		else SITES.put(seed, site);
	}

	/** Sets the realm site of {@code faction} (Aldermark's is the 2.0 map). */
	public static void setSite(long seed, Identifier faction, @Nullable BlockPos site) {
		if (Factions.ALDERMARK.equals(faction)) {
			setSite(seed, site);
			return;
		}
		if (site == null) FACTION_SITES.remove(key(seed, faction));
		else FACTION_SITES.put(key(seed, faction), site);
	}

	public static @Nullable BlockPos site(long seed, Identifier faction) {
		return Factions.ALDERMARK.equals(faction) ? SITES.get(seed) : FACTION_SITES.get(key(seed, faction));
	}

	public static @Nullable BlockPos site(long seed) {
		return SITES.get(seed);
	}

	/** Realm site (castle origin, as a long) -> {west, east} town floors of an fv 2 start (3.0 G4); fv 1 realms are never in it. */
	static final Map<Long, int[]> TOWN_FLOORS = new ConcurrentHashMap<>();

	/** World y of a town's floor: its own floor for an fv 2 realm (filled when the start is made or loaded), else the castle floor. */
	public static int townFloor(BlockPos site, boolean west) {
		int[] f = TOWN_FLOORS.get(site.asLong());
		return f == null ? site.getY() : f[west ? 0 : 1];
	}

	/** Places the structure start only in the chosen site chunk. */
	public static final class Placement implements StructurePlacement {
		public static final Placement INSTANCE = new Placement(Factions.ALDERMARK);
		/** {@code {"type": "elsewhere:castle_aldermark", "faction": "ns:id"}}; the faction defaults to Aldermark so the 2.0 structure set is unchanged. */
		public static final MapCodec<Placement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Identifier.CODEC.optionalFieldOf("faction", Factions.ALDERMARK).forGetter(p -> p.faction)
		).apply(i, Placement::new));

		final Identifier faction;

		public Placement(Identifier faction) {
			this.faction = faction;
		}

		public Identifier faction() {
			return faction;
		}

		@Override
		public boolean isStructureChunk(ChunkGeneratorStructureState state, int x, int z) {
			BlockPos s = site(state.getLevelSeed(), faction);
			return s != null && (s.getX() >> 4) == x && (s.getZ() >> 4) == z;
		}

		@Override
		public MapCodec<? extends StructurePlacement> codec() {
			return CODEC;
		}
	}

	public static final class RealmStructure extends Structure {
		/** {@code "faction"} (optional, default Aldermark) says whose realm site this structure is placed at. */
		public static final MapCodec<RealmStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Identifier.CODEC.optionalFieldOf("faction", Factions.ALDERMARK).forGetter(r -> r.faction),
			com.mojang.serialization.Codec.BOOL.optionalFieldOf("waterfront", false).forGetter(r -> r.waterfront)
		).apply(i, RealmStructure::new));

		final Identifier faction;
		/** True for the separate waterfront structure of a seafaring realm (jetty, boathouse, longships): no terrain adaptation, so the shore is never dug or flooded. */
		final boolean waterfront;

		public RealmStructure(StructureSettings settings) {
			this(settings, Factions.ALDERMARK, false);
		}

		public RealmStructure(StructureSettings settings, Identifier faction) {
			this(settings, faction, false);
		}

		public RealmStructure(StructureSettings settings, Identifier faction, boolean waterfront) {
			super(settings);
			this.faction = faction;
			this.waterfront = waterfront;
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
			BlockPos s = site(ctx.seed(), faction);
			if (s == null || (s.getX() >> 4) != ctx.chunkPos().x() || (s.getZ() >> 4) != ctx.chunkPos().z()) return Optional.empty();
			int[] w = water(ctx.seed(), faction);
			if (waterfront && w == null) return Optional.empty();
			// 3.0 G4 (realm_fit): new starts are fv 2: per-part floors from the generator HeightField, thin beards per piece, contour walls/roads
			int fv = !waterfront && RealmFit.ENABLED ? 2 : 1, tw = s.getY(), te = s.getY();
			if (fv == 2 && RealmFit.anchorsFollowTownFloor()) {
				RealmFit.HeightField hf = new RealmFit.HeightField(ctx.chunkGenerator(), ctx.randomState(), ctx.heightAccessor(), ctx.seed(), s, tw, te);
				tw = RealmFit.townFloor(hf, s, true);
				te = RealmFit.townFloor(hf, s, false);
			}
			final int fw = tw, fe = te;
			return Optional.of(new GenerationStub(s, b -> {
				if (waterfront) {
					b.addPiece(new WaterPiece(s, w[0], w[1], ctx.chunkGenerator().getSeaLevel(), faction, RealmFit.ENABLED ? 2 : 1)); // s-layout-hraf: fv 2 = new realm
					return;
				}
				for (CastleLayout.Part p : CastleLayout.Part.values()) b.addPiece(new RealmPiece(p, s, faction, fv, fw, fe));
			}));
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}
	}

	public static final class RealmPiece extends StructurePiece {
		final CastleLayout.Part part;
		final BlockPos origin;
		final Identifier faction;
		/** 1 = the 2.x layout (one beard_box start, every part at the castle floor); 2 = the 3.0 terrain fit (G4). Saved; never changes. */
		final int fv;
		/** fv 2: the castle origin (site) and both town floors. */
		final BlockPos site;
		final int tw, te;

		public RealmPiece(CastleLayout.Part part, BlockPos origin) {
			this(part, origin, Factions.ALDERMARK);
		}

		public RealmPiece(CastleLayout.Part part, BlockPos origin, Identifier faction) {
			this(part, origin, faction, 1, origin.getY(), origin.getY());
		}

		/** {@code site} is the castle origin; an fv 2 piece's own origin is {@code (site x, part floor, site z)}. */
		public RealmPiece(CastleLayout.Part part, BlockPos site, Identifier faction, int fv, int tw, int te) {
			this(part, partOrigin(part, site, fv, tw, te), faction, fv, site, tw, te);
		}

		private RealmPiece(CastleLayout.Part part, BlockPos origin, Identifier faction, int fv, BlockPos site, int tw, int te) {
			super(PIECE, 0, fv == 2 ? CastleLayout.fitBox(part, origin) : CastleLayout.box(part, origin));
			this.part = part;
			this.origin = origin;
			this.faction = faction;
			this.fv = fv;
			this.site = site;
			this.tw = tw;
			this.te = te;
			if (fv == 2) TOWN_FLOORS.put(site.asLong(), new int[] {tw, te});
		}

		static BlockPos partOrigin(CastleLayout.Part part, BlockPos site, int fv, int tw, int te) {
			if (fv != 2) return site;
			return switch (part) {
				case TOWN_WEST, KIT_TOWN_WEST -> new BlockPos(site.getX(), tw, site.getZ());
				case TOWN_EAST, KIT_TOWN_EAST -> new BlockPos(site.getX(), te, site.getZ());
				default -> site;
			};
		}

		/** True for a 3.0 terrain-fit piece (its beard comes from {@link #fitBeard}, see {@code BeardifierMixin}). */
		public boolean fit() {
			return fv == 2;
		}

		/** fv 2 beard: the castle yard pad as a 1-high box at floor - 1 (beard_thin under the structure's beard_box), or null for none. */
		public @Nullable BoundingBox fitBeard() {
			return RealmFit.beard(part, origin);
		}

		public RealmPiece(CompoundTag tag) {
			super(PIECE, tag);
			CastleLayout.Part p = CastleLayout.Part.CASTLE;
			try {
				p = CastleLayout.Part.valueOf(tag.getStringOr("part", "CASTLE"));
			} catch (IllegalArgumentException ignored) {
			}
			this.part = p;
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			Identifier f = Identifier.tryParse(tag.getStringOr("faction", Factions.ALDERMARK.toString()));
			this.faction = f == null ? Factions.ALDERMARK : f;
			this.fv = tag.getIntOr("fv", 1);
			this.site = BlockPos.of(tag.getLongOr("site", this.origin.asLong()));
			this.tw = tag.getIntOr("tw", this.site.getY());
			this.te = tag.getIntOr("te", this.site.getY());
			if (fv == 2) TOWN_FLOORS.put(site.asLong(), new int[] {tw, te});
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
			tag.putString("part", part.name());
			tag.putLong("origin", origin.asLong());
			tag.putString("faction", faction.toString());
			if (fv == 2) {
				tag.putInt("fv", 2);
				tag.putLong("site", site.asLong());
				tag.putInt("tw", tw);
				tag.putInt("te", te);
			}
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator gen, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos ref) {
			CastleLayout.Placer pl = new CastleLayout.Placer(level, chunkBB);
			if (!Factions.ALDERMARK.equals(faction)) pl.style = CastleStyles.of(faction);
			// fv 2 (3.0 G4): contour walls, roads and palisades on the generator ground; fv 1 keeps the frozen 2.x path (ground null)
			if (fv == 2) pl.ground = new RealmFit.HeightField(gen, level.getLevel().getChunkSource().randomState(), level, level.getSeed(), site, tw, te);
			CastleLayout.build(part, origin, pl);
		}
	}

	/** The waterfront of a seafaring realm (jetty, boathouse, longships), see {@link Waterfront}. */
	public static final class WaterPiece extends StructurePiece {
		final BlockPos origin;
		final int dir, shore, sea;
		final Identifier faction;
		/** s-layout-hraf: 2 for a new (3.0 fv 2) realm (adds the Hrafnholt shore road and nausts); saved waterfronts read 1 and build as before. */
		final int fv;

		public WaterPiece(BlockPos origin, int dir, int shore, int sea, Identifier faction) {
			this(origin, dir, shore, sea, faction, 1);
		}

		public WaterPiece(BlockPos origin, int dir, int shore, int sea, Identifier faction, int fv) {
			super(WATER_PIECE, 0, Waterfront.box(origin, dir, shore, sea));
			this.origin = origin;
			this.dir = dir;
			this.shore = shore;
			this.sea = sea;
			this.faction = faction;
			this.fv = fv;
		}

		public WaterPiece(CompoundTag tag) {
			super(WATER_PIECE, tag);
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			this.dir = tag.getIntOr("dir", 1);
			this.shore = tag.getIntOr("shore", 60);
			this.sea = tag.getIntOr("sea", 63);
			Identifier f = Identifier.tryParse(tag.getStringOr("faction", Factions.ALDERMARK.toString()));
			this.faction = f == null ? Factions.ALDERMARK : f;
			this.fv = tag.getIntOr("fv", 1);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
			tag.putLong("origin", origin.asLong());
			if (fv == 2) tag.putInt("fv", 2);
			tag.putInt("dir", dir);
			tag.putInt("shore", shore);
			tag.putInt("sea", sea);
			tag.putString("faction", faction.toString());
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator gen, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos ref) {
			CastleLayout.Placer pl = new CastleLayout.Placer(level, chunkBB);
			CastleStyles.Style st = CastleStyles.of(faction);
			Waterfront.build(pl, origin, dir, shore, sea, Waterfront.parse(st == null ? null : st.waterfront), (int) (origin.asLong() >>> 3));
			if (fv == 2 && st != null) { // s-layout-hraf: Hrafnholt's shore road and nausts (HrafnholtShore checks the kit and its kill switch)
				pl.style = st;
				HrafnholtShore.build(pl, origin, dir, shore, sea, new RealmFit.HeightField(gen, level.getLevel().getChunkSource().randomState(), level, level.getSeed(), origin, townFloor(origin, true), townFloor(origin, false)));
			}
		}
	}

	/** Test/command hook: builds the whole realm at {@code origin} into a loaded level, chunk by chunk like worldgen. */
	public static int buildNow(ServerLevel level, BlockPos origin) {
		return buildNow(level, origin, Factions.ALDERMARK);
	}

	/** As above in {@code faction}'s castle style. */
	public static int buildNow(ServerLevel level, BlockPos origin, Identifier faction) {
		int n = 0;
		int[] w = water(level.getSeed(), faction);
		if (w != null) n += buildWaterfrontNow(level, origin, faction, w[0], w[1], level.getSeaLevel());
		for (CastleLayout.Part p : CastleLayout.Part.values()) {
			BoundingBox box = CastleLayout.box(p, origin);
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					BoundingBox cb = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
					CastleLayout.Placer pl = new CastleLayout.Placer(level, cb);
					if (!Factions.ALDERMARK.equals(faction)) pl.style = CastleStyles.of(faction);
					CastleLayout.build(p, origin, pl);
					n += pl.placed;
				}
		}
		return n;
	}

	/** Builds the waterfront chunk by chunk like worldgen (tests and commands). */
	public static int buildWaterfrontNow(ServerLevel level, BlockPos origin, Identifier faction, int dir, int shore, int sea) {
		int n = 0;
		BoundingBox box = Waterfront.box(origin, dir, shore, sea);
		CastleStyles.Style st = CastleStyles.of(faction);
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				BoundingBox cb = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				CastleLayout.Placer pl = new CastleLayout.Placer(level, cb);
				Waterfront.build(pl, origin, dir, shore, sea, Waterfront.parse(st == null ? null : st.waterfront), (int) (origin.asLong() >>> 3));
				n += pl.placed;
			}
		return n;
	}
}
