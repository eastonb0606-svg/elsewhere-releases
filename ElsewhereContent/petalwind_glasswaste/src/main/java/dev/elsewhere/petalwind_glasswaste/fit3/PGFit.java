package dev.elsewhere.petalwind_glasswaste.fit3;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.heliodor.SunwrightZiggurat;
import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalPagoda;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.petalwind_glasswaste.Overhaul3;
import dev.elsewhere.petalwind_glasswaste.PGStructures;
import dev.elsewhere.petalwind_glasswaste.Reg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Elsewhere 3.0 structure looks and per-piece foundations for the Petalwind/Glasswaste boss sites (STRUCTURES.md 2.5, 2.9, 2.13, 2.15, section 4).
 * NEW starts are planned into small {@link FitPiece}s ({@code elsewhere:pg_fit_piece}, v1), each with its own ground-fitted floor, written per chunk
 * inside the chunk being generated. Starts saved before keep the 2.x {@code pg_boss_site_piece} and finish exactly as they began.
 * Kill switch: {@code -Delsewhere.overhaul3.pg_fit3=false} or {@code "pg_fit3": false} in {@code config/elsewhere-overhaul3.json}: new starts use the
 * 2.x piece again. Planning reads only the chunk generator (never the live world); spacing, separation and salt are untouched.
 */
public final class PGFit {
	private PGFit() {}

	/** Kill switch key {@code pg_fit3} (default on). */
	public static volatile boolean ENABLED = Overhaul3.on("pg_fit3");

	public static StructurePieceType PIECE;

	public static void init() {
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Reg.id("pg_fit_piece"), (StructurePieceType) (ctx, tag) -> new FitPiece(tag));
	}

	/** Deterministic ground model: y of the top ground block of a column, or {@link #VOID}. */
	public interface Ground {
		int at(int x, int z);
	}

	public static final int VOID = Integer.MIN_VALUE;

	/** Ground from the chunk generator (seed only, chunk-order independent), memoised for one start. */
	public static Ground ground(Structure.GenerationContext ctx) {
		Map<Long, Integer> memo = new HashMap<>();
		int min = ctx.heightAccessor().getMinY();
		return (x, z) -> memo.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
			int g = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
			return g <= min + 1 ? VOID : g;
		});
	}

	/** Ground from a level's worldgen heightmap (feature twins: only columns inside their own 3x3 chunks are asked). */
	public static Ground ground(WorldGenLevel level) {
		int min = level.getMinY();
		return (x, z) -> {
			int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
			return g <= min + 1 ? VOID : g;
		};
	}

	/** Seed of a site: from the world seed and the site origin, so every piece of one site hashes alike. */
	public static long siteSeed(long worldSeed, BlockPos origin) {
		return worldSeed * 0x5DEECE66DL ^ origin.asLong() * 0x9E3779B97F4A7C15L ^ 0x3A0L;
	}

	static BoundingBox box(int x0, int y0, int z0, int x1, int y1, int z1) {
		return new BoundingBox(x0, y0, z0, x1, y1, z1);
	}

	/**
	 * The plan of one site: its pieces, each with its own box and floor. {@code origin} is the 2.x origin of the kind (arena centres unchanged).
	 * {@code bowlR} is the Sunpit crater radius ({@link FitBuilders#BOWL_R} at worldgen; smaller for the 3x3-chunk feature twin).
	 */
	public static List<FitPiece> plan(String kind, BlockPos o, Ground g, long seed, int bowlR) {
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		List<FitPiece> out = new ArrayList<>();
		switch (kind) {
			case PGStructures.ZIGGURAT -> {
				out.add(new FitPiece(kind, "core", o, oy, 0, 0, seed, box(ox - 19, oy - 13, oz - 19, ox + 19, oy + 24, oz + 19)));
				out.add(new FitPiece(kind, "stair", o, oy, 0, 0, seed, box(ox - 3, oy - 26, oz + 1, ox + 3, oy + 16, oz + 30)));
			}
			case PGStructures.SUNPIT -> {
				out.add(new FitPiece(kind, "pit", o, oy, 0, 0, seed, box(ox - 24, oy - 18, oz - 24, ox + 24, oy + 31, oz + 24)));
				for (int q = 0; q < 4; q++) {
					boolean west = (q & 1) != 0, north = (q & 2) != 0;
					int x0 = west ? ox - bowlR : ox, x1 = west ? ox - 1 : ox + bowlR, z0 = north ? oz - bowlR : oz, z1 = north ? oz - 1 : oz + bowlR;
					out.add(new FitPiece(kind, "bowl", o, oy, q, bowlR, seed, box(x0, oy - 7, z0, x1, oy + 47, z1)));
				}
			}
			case PGStructures.PAGODA -> {
				out.add(new FitPiece(kind, "core", o, oy, 0, 0, seed, box(ox - 10, oy - 13, oz - 10, ox + 10, oy + PetalPagoda.TERRACE_Y + 8, oz + 10)));
				int[] floors = courtFloors(o, g);
				for (int i = 0; i < 9; i++) {
					if (i == 4 || floors[i] == VOID) continue;
					int gx = i / 3 - 1, gz = i % 3 - 1, y = floors[i];
					int x0 = ox + FitBuilders.lo(gx) - (gx < 0 ? 1 : 0), x1 = ox + FitBuilders.hi(gx) + (gx > 0 ? 1 : 0);
					int z0 = oz + FitBuilders.lo(gz) - (gz < 0 ? 1 : 0), z1 = oz + FitBuilders.hi(gz) + (gz > 0 ? 1 : 0);
					out.add(new FitPiece(kind, "court", o, y, i, 0, seed, box(x0, y - 13, z0, x1, Math.max(y + 11, oy + 1), z1)));
				}
			}
			case PGStructures.BREEZE -> {
				out.add(new FitPiece(kind, "hall", o, oy, 0, 0, seed, box(ox - 11, oy - 25, oz - 11, ox + 11, oy + 15, oz + 11)));
				out.add(new FitPiece(kind, "steps", o, oy, 0, 0, seed, box(ox - 3, oy - 27, oz + 9, ox + 3, oy + 6, oz + 22)));
			}
			case PGStructures.EYE -> {
				List<BlockPos> plats = EyeOfTheGale.platforms(o);
				for (int i = 0; i < plats.size(); i++) {
					BlockPos q = plats.get(i);
					out.add(new FitPiece(kind, "platform", o, q.getY(), i, 0, seed, box(q.getX() - 3, q.getY() - 12, q.getZ() - 3, q.getX() + 3, q.getY() + 4, q.getZ() + 3)));
				}
				// the eye itself (writes nothing): lets the summon lookup and the wind-charge listener find the site anywhere in the eye, as in 2.x
				out.add(new FitPiece(kind, "volume", o, oy, 0, 0, seed, box(ox - 24, oy - 30, oz - 24, ox + 24, oy + 34, oz + 24)));
				int by = oy - 14;
				out.add(new FitPiece(kind, "basin", o, by, 0, 0, seed, box(ox - 9, by - 13, oz - 9, ox + 9, by + 3, oz + 9)));
			}
			default -> {
			}
		}
		return out;
	}

	/**
	 * Court terrace floors around the pagoda platform (STRUCTURES.md 2.12): each cell takes the rounded mean of the generator ground at its 4
	 * corners and middle, never above the platform and at most 4 below it; a cell over the island's edge (fewer than 3 grounded samples, or ground
	 * more than 8 below) is left out; corner cells stay within 2 of their side neighbours. Index = (gx + 1) * 3 + (gz + 1).
	 */
	public static int[] courtFloors(BlockPos o, Ground g) {
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		int[] f = new int[9];
		for (int i = 0; i < 9; i++) {
			f[i] = VOID;
			if (i == 4) continue;
			int gx = i / 3 - 1, gz = i % 3 - 1;
			int a = FitBuilders.lo(gx), b = FitBuilders.hi(gx), c = FitBuilders.lo(gz), d = FitBuilders.hi(gz);
			int[][] pts = {{a, c}, {a, d}, {b, c}, {b, d}, {(a + b) / 2, (c + d) / 2}};
			int n = 0, sum = 0;
			for (int[] pt : pts) {
				int y = g.at(ox + pt[0], oz + pt[1]);
				if (y == VOID || y < oy - 8) continue;
				n++;
				sum += y;
			}
			if (n < 3) continue;
			f[i] = Math.max(oy - 4, Math.min(oy, Math.round(sum / (float) n)));
		}
		for (int i : new int[] {0, 2, 6, 8}) {
			if (f[i] == VOID) continue;
			int gx = i / 3 - 1, gz = i % 3 - 1;
			int sx = (gx + 1) * 3 + 1, sz = 3 + (gz + 1); // side neighbours: same gx with gz 0, and gx 0 with same gz
			int lo = Integer.MIN_VALUE, hi = Integer.MAX_VALUE;
			for (int s : new int[] {sx, sz}) {
				if (f[s] == VOID) continue;
				lo = Math.max(lo, f[s] - 2);
				hi = Math.min(hi, f[s] + 2);
			}
			if (lo <= hi) f[i] = Math.max(lo, Math.min(hi, f[i]));
		}
		return f;
	}

	/** Places the site's dormant boss exactly as 2.x did (same centres, arena radii and heights). */
	public static void spawnBoss(WorldGenLevel level, String kind, BlockPos origin, EntitySpawnReason reason) {
		BlockPos center = PGStructures.centerOf(kind, origin);
		switch (kind) {
			case PGStructures.PAGODA -> {
				Kazehana k = ModEntities.KAZEHANA.create(level.getLevel(), reason);
				if (k != null) {
					k.setArena(center, PetalPagoda.ARENA_RADIUS, 4, 12);
					k.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
					level.addFreshEntityWithPassengers(k);
				}
			}
			case PGStructures.ZIGGURAT -> {
				Heliodor h = ModEntities.HELIODOR.create(level.getLevel(), reason);
				if (h != null) {
					h.setArena(center, SunwrightZiggurat.ARENA_RADIUS, 4, 12);
					h.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
					level.addFreshEntityWithPassengers(h);
				}
			}
			case PGStructures.SUNPIT -> {
				Ankhara a = ModEntities.ANKHARA.create(level.getLevel(), reason);
				if (a != null) {
					a.setArena(center, Sunpit.ARENA_RADIUS, 6, 12);
					a.snapTo(center.getX() + 0.5, center.getY() - 4.5, center.getZ() + 0.5);
					level.addFreshEntityWithPassengers(a);
				}
			}
			default -> {
			}
		}
	}

	/** Builds pieces on a live level, each clipped to its own box and to {@code limit} (null: no extra limit). */
	public static void buildAll(WorldGenLevel level, List<FitPiece> pieces, BoundingBox limit) {
		for (FitPiece p : pieces) {
			BoundingBox b = p.box();
			if (limit != null) {
				if (!b.intersects(limit)) continue;
				b = new BoundingBox(Math.max(b.minX(), limit.minX()), Math.max(b.minY(), limit.minY()), Math.max(b.minZ(), limit.minZ()),
					Math.min(b.maxX(), limit.maxX()), Math.min(b.maxY(), limit.maxY()), Math.min(b.maxZ(), limit.maxZ()));
			}
			FitBuilders.build(new FitWriter(level, b, p.seed), p);
		}
	}

	/** A 3.0 site as seen by the summon items and the Eye: all its fit pieces. */
	public record FitSite(String kind, BlockPos origin, List<FitPiece> pieces) implements PGStructures.Site {
		public static FitSite of(StructureStart start) {
			List<FitPiece> ps = new ArrayList<>();
			for (StructurePiece p : start.getPieces()) if (p instanceof FitPiece fp) ps.add(fp);
			return ps.isEmpty() ? null : new FitSite(ps.get(0).kind, ps.get(0).origin, ps);
		}

		@Override
		public BlockPos center() {
			return PGStructures.centerOf(kind, origin);
		}

		/** Re-summon: repairs the whole site from its saved plan (runtime, loaded chunks), then wakes a new boss. */
		@Override
		public void resummon(ServerLevel level) {
			buildAll(level, pieces, null);
			spawnBoss(level, kind, origin, EntitySpawnReason.TRIGGERED);
		}
	}

	/**
	 * Feature twins ({@code /place feature elsewhere:petal_pagoda|sunwright_ziggurat|sunpit}): the same plan and builders, clipped to the 3x3
	 * chunks a feature may write, with the Sunpit bowl steepened to fit. Returns the arena centre.
	 */
	public static BlockPos placeTwin(WorldGenLevel level, String kind, BlockPos origin) {
		int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
		BoundingBox region = new BoundingBox((cx - 1) << 4, level.getMinY(), (cz - 1) << 4, ((cx + 1) << 4) + 15, level.getMaxY(), ((cz + 1) << 4) + 15);
		int reach = Math.min(Math.min(origin.getX() - region.minX(), region.maxX() - origin.getX()), Math.min(origin.getZ() - region.minZ(), region.maxZ() - origin.getZ()));
		List<FitPiece> pieces = plan(kind, origin, ground(level), siteSeed(level.getSeed(), origin), Math.max(24, Math.min(FitBuilders.BOWL_R, reach)));
		buildAll(level, pieces, region);
		spawnBoss(level, kind, origin, EntitySpawnReason.STRUCTURE);
		return PGStructures.centerOf(kind, origin);
	}
}
