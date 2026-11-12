package dev.elsewhere.overworld_bosses.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import dev.elsewhere.overworld_bosses.fit3.OwbFit;

/**
 * One of the four Overworld boss sites, placed by world generation: {@code croaking_shrine} (mangrove swamp),
 * {@code sun_chamber} (under desert pyramids: its structure set uses the pyramids' placement and salt, so it lands
 * in the same chunks), {@code glacier_shelf} (frozen / jagged peaks) and {@code wreck_ring} (deep ocean).
 */
public class OwbStructure extends Structure {
	/** Overhaul3 kill switch for the 3.0 site rules here (GENERATION.md G6): {@code -Delsewhere.overhaul3.gen_sites=false} restores the 2.1 checks. */
	public static volatile boolean GEN3 = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.gen_sites", "true"));

	public static final MapCodec<OwbStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
	).apply(i, OwbStructure::new));

	public final String kind;

	public OwbStructure(StructureSettings settings, String kind) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		// server switch overworldContent=false: no Overworld boss sites (and so none of their site-only mobs)
		if (!dev.elsewhere.overworld_bosses.OwbConfig.overworldContent()) return Optional.empty();
		ChunkPos cp = ctx.chunkPos();
		int sea = ctx.chunkGenerator().getSeaLevel();
		switch (this.kind) {
			case OwbPiece.SUN_CHAMBER -> {
				// Under the pyramid's centre (pyramids sit at the chunk's min corner, 21x21).
				int x = cp.getMinBlockX() + 10, z = cp.getMinBlockZ() + 10;
				int surface = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
				if (surface < sea) return Optional.empty();
				int top = ctx.chunkGenerator().getFirstOccupiedHeight(x + 16, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
				int floor = Math.min(30, surface - 36);
				BlockPos origin = new BlockPos(x, floor + 1, z);
				// The chamber itself must not sit in a cave biome (dripstone/lush caves under the desert): check it at five depths.
				for (int dy : GEN3 ? new int[] {2, 8, 16, 24, 30} : new int[] {2, 16, 30}) {
					if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(x >> 2, (floor + dy) >> 2, z >> 2))) return Optional.empty();
				}
				// 3.0 fit (s-struct-overworld): no cave or aquifer void may cross the hall; the chamber is encapsulated and the shaft kiosk
				// is a fit piece of its own. Kill switch elsewhere.overhaul3.owb_fit.
				if (OwbFit.ENABLED) {
					if (OwbFit.chamberHitsCave(x, z, floor, OwbFit.probe(ctx))) return Optional.empty();
					return Optional.of(new GenerationStub(new BlockPos(x, surface, z), b -> OwbFit.sun(origin, top).forEach(b::addPiece)));
				}
				// The biome check uses the stub position: keep it on the surface (underground could be a cave biome).
				return Optional.of(new GenerationStub(new BlockPos(x, surface, z), b -> b.addPiece(new OwbPiece(this.kind, origin, top))));
			}
			case OwbPiece.WRECK_RING -> {
				int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
				int floor = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
				if (floor > sea - 12) return Optional.empty();
				BlockPos origin = new BlockPos(x, sea + 1, z);
				if (OwbFit.ENABLED) return Optional.of(new GenerationStub(origin, b -> OwbFit.wreck(x, z, sea, OwbFit.heights(ctx)).forEach(b::addPiece)));
				return Optional.of(new GenerationStub(origin, b -> b.addPiece(new OwbPiece(this.kind, origin, 0))));
			}
			default -> {
				// 3.0 (z-gen): the Glacier Shelf tries up to 9 candidate centres (the chunk middle first, then whole-chunk offsets in an
				// order fixed per start chunk) with the same fit rules, so its density is back near 2.1 without loosening the fit. 2.1: the middle only.
				if (GEN3 && OwbPiece.GLACIER_SHELF.equals(this.kind)) return this.glacierSite(ctx);
				int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
				int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
				if (OwbPiece.CROAKING_SHRINE.equals(this.kind)) y = Math.max(y, sea);
				BlockPos origin = new BlockPos(x, y, z);
				if (OwbFit.ENABLED) return Optional.of(new GenerationStub(origin, b -> (OwbPiece.CROAKING_SHRINE.equals(this.kind)
					? OwbFit.shrine(x, z, sea, OwbFit.heights(ctx)) : OwbFit.glacier(x, z, OwbFit.heights(ctx))).forEach(b::addPiece)));
				return Optional.of(new GenerationStub(origin, b -> b.addPiece(new OwbPiece(this.kind, origin, 0))));
			}
		}
	}

	/** Candidate centre offsets for the Glacier Shelf: the chunk middle, then the 8 whole-chunk neighbours. */
	static final int[][] GLACIER_OFFSETS = {{0, 0}, {-16, 0}, {16, 0}, {0, -16}, {0, 16}, {-16, -16}, {16, -16}, {-16, 16}, {16, 16}};

	/**
	 * 3.0 Glacier Shelf site (GENERATION.md G6): the first candidate centre whose surface and core (10 down) are in the biome and whose ground
	 * spread over the shelf's box (3x3 lattice, x +-16, z -26..16) is at most 24. The fit rule per candidate is exactly the single-centre rule.
	 */
	Optional<GenerationStub> glacierSite(GenerationContext ctx) {
		ChunkPos cp = ctx.chunkPos();
		var gen = ctx.chunkGenerator();
		// the 8 neighbours start at an index taken from the start chunk (itself seed-chosen), so the order is deterministic per world
		int rot = Math.floorMod(cp.x() * 31 + cp.z() * 17, 8);
		for (int n = 0; n < GLACIER_OFFSETS.length; n++) {
			int[] off = GLACIER_OFFSETS[n == 0 ? 0 : 1 + (n - 1 + rot) % 8];
			int x = cp.getMiddleBlockX() + off[0], z = cp.getMiddleBlockZ() + off[1];
			int y = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
			// the stub's own biome (findValidGenerationPoint tests it) and the core 10 blocks down
			if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(x >> 2, y >> 2, z >> 2))) continue;
			if (!ctx.validBiome().test(ctx.biomeResolver().getNoiseBiome(x >> 2, (y - 10) >> 2, z >> 2))) continue;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			boolean ok = true;
			for (int dx : new int[] {-16, 0, 16}) {
				for (int dz : new int[] {-26, -5, 16}) {
					int g = gen.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
					lo = Math.min(lo, g);
					hi = Math.max(hi, g);
					if (hi - lo > 24) { ok = false; break; }
				}
				if (!ok) break;
			}
			if (!ok) continue;
			BlockPos origin = new BlockPos(x, y, z);
			if (OwbFit.ENABLED) {
				final int fx = x, fz = z;
				return Optional.of(new GenerationStub(origin, b -> OwbFit.glacier(fx, fz, OwbFit.heights(ctx)).forEach(b::addPiece)));
			}
			return Optional.of(new GenerationStub(origin, b -> b.addPiece(new OwbPiece(this.kind, origin, 0))));
		}
		return Optional.empty();
	}

	@Override
	public StructureType<?> type() {
		return OwbWorldgen.typeOf(this.kind);
	}
}
