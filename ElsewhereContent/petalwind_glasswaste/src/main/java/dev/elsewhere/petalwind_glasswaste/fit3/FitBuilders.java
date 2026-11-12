package dev.elsewhere.petalwind_glasswaste.fit3;

import dev.elsewhere.boss.ankhara.Sunpit;
import dev.elsewhere.boss.heliodor.SunwrightZiggurat;
import dev.elsewhere.boss.kazehana.PetalPagoda;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.glasswaste.GlasswasteBlocks;
import dev.elsewhere.glasswaste.SunMirrorBlock;
import dev.elsewhere.petalwind.PetalwindBlocks;
import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.petalwind_glasswaste.BreezeTemple;
import dev.elsewhere.petalwind_glasswaste.PGStructures;
import dev.elsewhere.petalwind_glasswaste.Reg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static dev.elsewhere.petalwind_glasswaste.fit3.FitWriter.hangingLantern;
import static dev.elsewhere.petalwind_glasswaste.fit3.FitWriter.inward;
import static dev.elsewhere.petalwind_glasswaste.fit3.FitWriter.slab;
import static dev.elsewhere.petalwind_glasswaste.fit3.FitWriter.stair;

/**
 * The 3.0 looks of the five Petalwind/Glasswaste sites (STRUCTURES.md section 4 and 7), written column by column inside the writer's clip.
 * Every arena keeps its 2.x gameplay geometry (floors, rings, pillars, mirrors, lens, terrace, platforms, vaults) at the same offsets from the same
 * origin, so the bosses and summon items work unchanged. No valuable blocks: gilt is yellow terracotta.
 */
public final class FitBuilders {
	private FitBuilders() {}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	public static void build(FitWriter w, FitPiece p) {
		switch (p.kind) {
			case PGStructures.ZIGGURAT -> {
				if (p.part.equals("core")) zigCore(w, p);
				else zigStair(w, p);
			}
			case PGStructures.SUNPIT -> {
				if (p.part.equals("pit")) pit(w, p);
				else bowl(w, p);
			}
			case PGStructures.PAGODA -> {
				if (p.part.equals("core")) pagodaCore(w, p);
				else court(w, p);
			}
			case PGStructures.BREEZE -> {
				if (p.part.equals("hall")) templeHall(w, p);
				else templeSteps(w, p);
			}
			case PGStructures.EYE -> {
				if (p.part.equals("basin")) eyeBasin(w, p);
				else if (p.part.equals("platform")) eyePlatform(w, p);
				// "volume": the eye's air, nothing to write
			}
			default -> {
			}
		}
	}

	/** The part that spawns the site's dormant boss at generation (the Eye's Tempestra is summoned by wind charges, the temple has none). */
	public static boolean spawnsBoss(FitPiece p) {
		return (p.part.equals("core") && (p.kind.equals(PGStructures.PAGODA) || p.kind.equals(PGStructures.ZIGGURAT)))
			|| (p.part.equals("pit") && p.kind.equals(PGStructures.SUNPIT));
	}

	static int cheb(int dx, int dz) {
		return Math.max(Math.abs(dx), Math.abs(dz));
	}

	/** Coordinate along the face a perimeter column sits on. */
	static int along(int dx, int dz, int c) {
		return Math.abs(dx) == c ? dz : dx;
	}

	// =============================================================================================== Sunwright Ziggurat (Heliodor)
	static final int[] ZIG_HW = {15, 13, 11, 9, 7};

	/**
	 * Five stepped tiers of Sunwright brick on a variable plinth: every column of the lowest terrace goes down to the real ground in a
	 * Sunwright brick, cut sandstone, sandstone ladder, with a stepped smooth-sandstone batter where the ground falls away and a sloped apron
	 * cut where it rises, so the lowest terrace meets the ground on every side. Chiseled band and quoins, yellow-terracotta gilt studs.
	 */
	static void zigCore(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState brick = GlasswasteBlocks.SUNWRIGHT_BRICKS.defaultBlockState(), chis = GlasswasteBlocks.CHISELED_SUNWRIGHT_BRICKS.defaultBlockState();
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), sst = Blocks.SANDSTONE.defaultBlockState();
		BlockState gilt = Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz, c = cheb(dx, dz);
			if (c > 18) continue;
			if (c <= 15) {
				int tmax = Math.min(4, (15 - c) / 2), top = 3 * (tmax + 1) - 1;
				w.clear(x, z, oy + top + 1, oy + 23); // natural ground above the step (cut side) and the summit's open air
				for (int dy = 0; dy <= top; dy++) {
					int t = dy / 3, hw = ZIG_HW[t];
					BlockState s = brick;
					if (c == hw) {
						boolean corner = Math.abs(dx) == Math.abs(dz);
						int a = along(dx, dz, c);
						if (dy % 3 == 2) s = !corner && Math.floorMod(a, 4) == 0 ? gilt : chis; // band with gilt studs
						else if (corner) s = chis;                                                 // quoins
					}
					w.set(x, oy + dy, z, s);
				}
				w.foundation(x, z, oy, brick, cut, sst, 12);
			} else {
				int top = w.surface(x, z, oy + 20, oy - 14);
				if (top >= oy + 20) continue; // ground taller than the scan: leave it whole rather than leave a cap hanging
				if (top < oy - 1 && (c == 16 || (c == 17 && top < oy - 3))) {
					// stepped batter at the plinth foot where the ground falls away (no sheer face)
					int sy = c == 16 ? oy - 1 : oy - 3;
					w.set(x, sy, z, stair(Blocks.SMOOTH_SANDSTONE_STAIRS, inward(dx, dz), false));
					w.foundation(x, z, sy, sst, sst, sst, 12);
				} else if (top > oy + 2 + (c - 16)) {
					// sloped apron where the ground rises: the lowest terrace is never buried
					int limit = oy + 2 + (c - 16);
					BlockState surf = w.get(x, top, z);
					w.clear(x, z, limit + 1, top + 4);
					w.set(x, limit, z, FitWriter.soft(surf) ? Blocks.SAND.defaultBlockState() : surf);
				}
			}
		}
		// summit: lens ring, central lens, mirrors, chest (2.x offsets)
		BlockPos summit = p.origin.above(SunwrightZiggurat.SUMMIT_Y);
		for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
			int d2 = dx * dx + dz * dz;
			if (d2 >= 9 && d2 <= 20) w.set(summit.getX() + dx, summit.getY() - 1, summit.getZ() + dz, Blocks.GLASS.defaultBlockState());
		}
		w.set(summit.getX(), summit.getY() - 1, summit.getZ(), chis);
		for (BlockPos m : SunwrightZiggurat.mirrors(summit)) {
			w.set(m.getX(), m.getY(), m.getZ(), GlasswasteBlocks.SUN_MIRROR.defaultBlockState().setValue(SunMirrorBlock.AIM, (m.getX() + m.getZ()) & 7));
		}
		BlockPos chest = summit.offset(-6, 0, -6);
		if (w.in(chest.getX(), chest.getY(), chest.getZ())) {
			w.set(chest.getX(), chest.getY(), chest.getZ(), Blocks.CHEST.defaultBlockState());
			if (w.level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity rc) {
				rc.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/sunwright_ziggurat")), chest.asLong());
			}
		}
	}

	/** The south flight (2.x line), framed by chiseled cheeks, continued down the slope to the real ground, or a sunken approach when the ground rises. */
	static void zigStair(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState brick = GlasswasteBlocks.SUNWRIGHT_BRICKS.defaultBlockState(), chis = GlasswasteBlocks.CHISELED_SUNWRIGHT_BRICKS.defaultBlockState();
		BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState(), sst = Blocks.SANDSTONE.defaultBlockState();
		BlockState step = GlasswasteBlocks.SUNWRIGHT_BRICK_STAIRS.defaultBlockState(); // faces north: climbs toward the summit
		BlockState cap = GlasswasteBlocks.SUNWRIGHT_BRICK_SLAB.defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, k = z - oz - 16;
			if (Math.abs(dx) > 2) continue;
			if (k <= 0) { // the flight on the tiers (i = -k)
				int i = -k;
				if (i > 14) continue;
				if (Math.abs(dx) <= 1) {
					w.set(x, oy + i, z, step);
					if (i < 14) w.clear(x, z, oy + i + 1, oy + i + 2); // head room: the flight is carved into the tiers
				} else if (i <= 11) w.set(x, oy + i + 1, z, chis);
				if (i == 0) {
					w.clear(x, z, oy + (Math.abs(dx) <= 1 ? 1 : 2), oy + 5);
					w.foundation(x, z, oy, Math.abs(dx) <= 1 ? cut : brick, cut, sst, 12);
				}
				continue;
			}
			if (k > 13) continue;
			int y = oy - k;
			int ground = w.surface(x, z, oy + 8, oy - 26);
			if (ground >= oy) {
				// the ground rises in front: a sunken, paved approach lined with brick (cut <= 4)
				if (k > 4) continue;
				if (Math.abs(dx) <= 1) {
					w.set(x, oy - 1, z, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
					w.clear(x, z, oy, Math.min(ground, oy + 4));
				} else for (int yy = oy; yy <= Math.min(ground, oy + 3); yy++) w.set(x, yy, z, brick);
				continue;
			}
			if (ground >= y) continue; // the flight has met the ground
			if (Math.abs(dx) <= 1) w.set(x, y, z, step);
			else {
				w.set(x, y, z, brick);
				w.set(x, y + 1, z, cap);
			}
			w.foundation(x, z, y, Math.abs(dx) <= 1 ? cut : brick, cut, sst, 12);
		}
	}

	// =============================================================================================== Sunpit (Ankhara)
	public static final int BOWL_R = 46;

	/** Surface y of the bowl slope at radius r: sand at about its angle of repose, rising from the kerb. */
	public static int bowlY(int oy, double r, int bowlR) {
		double k = Math.max(0.7, 12.0 / Math.max(1, bowlR - 22));
		return oy + (int) Math.floor((r - 22) * k);
	}

	/**
	 * The arena (2.x layers: sand rings over magma, smooth floor, glass pillars) with a 1-high cut-sandstone kerb, open to the sky: the cut follows
	 * the column's own ground, never a fixed 10-high disc. Its sandstone bed goes down to the ground.
	 */
	static void pit(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState sand = Blocks.SAND.defaultBlockState(), sst = Blocks.SANDSTONE.defaultBlockState(), smooth = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState(), cutS = Blocks.CUT_SANDSTONE.defaultBlockState(), chis = Blocks.CHISELED_SANDSTONE.defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r > 23.5) continue;
			int top = w.surface(x, z, oy + 30, oy);
			w.clear(x, z, oy, Math.max(top, oy + 9) + 2);
			for (int dy = -5; dy <= -3; dy++) w.set(x, oy + dy, z, sst);
			if (r <= Sunpit.ARENA_RADIUS) {
				boolean ring = (r >= Sunpit.OUTER_IN && r <= Sunpit.OUTER_OUT) || (r >= Sunpit.MID_IN && r <= Sunpit.MID_OUT);
				w.set(x, oy - 2, z, ring ? magma : sst);
				w.set(x, oy - 1, z, ring ? sand : smooth);
				// cave air over the arena floor: vanilla's dead-bush and cactus patches place only into plain air, so no plant (and no cactus) grows in Ankhara's
				// arena, also from a neighbouring chunk's decoration; it is air to players, light and heightmaps
				if (ring) w.set(x, oy, z, Blocks.CAVE_AIR.defaultBlockState());
			} else {
				w.set(x, oy - 2, z, sst);
				w.set(x, oy - 1, z, smooth);
				if (r <= 22.5) w.set(x, oy, z, w.h(x, 0, z) % 7 == 0 ? chis : cutS); // the kerb
				else {
					w.set(x, oy, z, sst);
					w.set(x, oy + 1, z, sand); // the foot of the slope
				}
			}
			w.foundation(x, z, oy - 5, sst, sst, sst, 12);
		}
		for (BlockPos q : Sunpit.pillars(p.origin)) for (int dy = 0; dy < 6; dy++) w.set(q.getX(), q.getY() + dy, q.getZ(), Blocks.GLASS.defaultBlockState());
	}

	/**
	 * One quadrant of the crater: a sand slope at the angle of repose from the kerb up to wherever it meets the dunes (no vertical cut, no
	 * overhang), sandstone outcrops, a sand rampart where a trough would open the pit, a half-buried serpent skeleton (Ankhara's kin) on one
	 * slope and a broken sandstone marker on each quadrant's rim.
	 */
	static void bowl(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ(), q = p.ix, R = p.param;
		BlockState sand = Blocks.SAND.defaultBlockState(), sst = Blocks.SANDSTONE.defaultBlockState(), bone = Blocks.BONE_BLOCK.defaultBlockState();
		int ribQ = 1; // the south-west slope: in view of both the south overview and the east-side shot
		double qa = quadrantAngle(q);
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz;
			if (!inQuadrant(dx, dz, q)) continue;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r <= 23.5 || r > R) continue;
			int yb = bowlY(oy, r, R);
			int top = w.surface(x, z, oy + 30, oy - 6);
			if (top >= oy + 30) continue; // a dune taller than the scan: the slope ends against it, nothing is left hanging
			int surf;
			if (top > yb) {
				// near the rim the cut rises to meet the dune: a steeper sand slope, never a sheer wall at the crater edge
				double t = Math.max(0.0, Math.min(1.0, (r - (R - 12)) / 12.0));
				int yc = Math.min(top, yb + (int) Math.floor((top - yb) * t));
				w.clear(x, z, yc + 1, oy + 30);
				w.set(x, yc, z, w.h(x, 1, z) % 17 == 0 ? sst : sand);
				if (!w.solid(x, yc - 1, z)) w.set(x, yc - 1, z, sst);
				surf = yc;
			} else if (r <= 28 && top < yb) {
				int to = Math.min(yb, top + 5); // a low sand rampart so the pit never opens sideways
				for (int y = top + 1; y < to; y++) w.set(x, y, z, sst);
				w.set(x, to, z, sand);
				surf = to;
			} else surf = top;
			double ang = Math.atan2(dz, dx);
			// serpent skeleton: five ribs arching out of the sand, and the spine behind them
			if (q == ribQ) {
				for (int j = 0; j < 5; j++) {
					double aj = qa - 0.44 + 0.22 * j;
					if (Math.abs(angDiff(ang, aj)) * r < 1.0 && r >= 26 && r <= 34) {
						int hgt = (int) Math.round(4.2 * Math.sin(Math.PI * (r - 26) / 8.0));
						for (int y = 1; y <= hgt; y++) w.set(x, surf + y, z, bone);
					}
				}
				if (Math.abs(r - 35) <= 0.8 && Math.abs(angDiff(ang, qa)) <= 0.62) w.set(x, surf + 1, z, bone);
			}
			// a broken sandstone marker on the rim of each quadrant
			if (r >= 37.5 && r <= 38.5 && Math.abs(angDiff(ang, qa)) * r < 0.7) {
				int hgt = 2 + w.h(ox, q, oz) % 3;
				for (int y = 1; y <= hgt; y++) w.set(x, surf + y, z, y == hgt ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState());
				w.foundation(x, z, surf + 1, sst, sst, sst, 12);
			}
		}
	}

	/** Quadrant bits: bit 0 set = west (dx < 0), bit 1 set = north (dz < 0). q 0 = south-east, 1 = south-west, 2 = north-east, 3 = north-west. */
	static boolean inQuadrant(int dx, int dz, int q) {
		return ((q & 1) != 0) == (dx < 0) && ((q & 2) != 0) == (dz < 0);
	}

	/** Diagonal angle (atan2(dz, dx)) of quadrant q. */
	static double quadrantAngle(int q) {
		return switch (q) {
			case 0 -> Math.PI / 4;
			case 1 -> 3 * Math.PI / 4;
			case 2 -> -Math.PI / 4;
			default -> -3 * Math.PI / 4;
		};
	}

	static double angDiff(double a, double b) {
		double d = a - b;
		while (d > Math.PI) d -= 2 * Math.PI;
		while (d < -Math.PI) d += 2 * Math.PI;
		return d;
	}

	// =============================================================================================== Petal Pagoda (Kazehana)
	static final int[][] PAGODA_TIERS = {{6, 1, 5}, {5, 7, 4}, {4, 12, 4}};

	/**
	 * A calcite platform with a polished-tuff kerb on a ground-fitted foundation, three skyblossom tiers with stripped-log posts and lattice
	 * windows, sweeping red roofs (stair eaves, a second sweep ring, upturned corner tips, hanging lanterns), the roof terrace (2.x arena) carried on
	 * posts and corbels, the central ladder.
	 */
	static void pagodaCore(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState calcite = Blocks.CALCITE.defaultBlockState(), tuffB = Blocks.TUFF_BRICKS.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState();
		BlockState planks = PetalwindBlocks.SKYBLOSSOM_PLANKS.defaultBlockState(), log = PetalwindBlocks.SKYBLOSSOM_LOG.defaultBlockState();
		BlockState post = PetalwindBlocks.STRIPPED_SKYBLOSSOM_LOG.defaultBlockState(), lattice = PetalwindBlocks.SKYBLOSSOM_FENCE.defaultBlockState();
		BlockState roof = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		int ty = PetalPagoda.TERRACE_Y;
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz, c = cheb(dx, dz);
			if (c > 9) continue;
			w.clear(x, z, oy + 1, oy + ty + 7);
			w.set(x, oy, z, c == 9 ? Blocks.POLISHED_TUFF.defaultBlockState() : calcite);
			w.foundation(x, z, oy, c == 9 ? Blocks.POLISHED_TUFF.defaultBlockState() : calcite, tuffB, tuff, 12);
			for (int ti = 0; ti < PAGODA_TIERS.length; ti++) {
				int hw = PAGODA_TIERS[ti][0], y0 = PAGODA_TIERS[ti][1], h = PAGODA_TIERS[ti][2];
				boolean top = ti == PAGODA_TIERS.length - 1;
				if (c <= hw) for (int dy = 0; dy < h; dy++) {
					BlockState s;
					if (c < hw) s = AIR;
					else if (Math.abs(dx) == hw && Math.abs(dz) == hw) s = log;
					else {
						int a = along(dx, dz, c);
						boolean door = Math.abs(dz) == hw && Math.abs(dx) <= 1 && (dy == 1 || dy == 2);
						if (door) s = AIR;
						else if (Math.floorMod(a, 3) == 0) s = post;
						else if (dy == 2) s = lattice;
						else s = planks;
					}
					w.set(x, oy + y0 + dy, z, s);
				}
				int ry = oy + y0 + h;
				if (top) {
					// the terrace underside: roof plate, red corbels and skyblossom brackets under the deck rim
					if (c <= hw + 2) w.set(x, ry, z, roof);
					else if (c <= 8) {
						boolean corner = Math.abs(dx) == Math.abs(dz);
						w.set(x, ry, z, corner ? roof : stair(c == 7 ? Blocks.RED_NETHER_BRICK_STAIRS : PetalwindBlocks.SKYBLOSSOM_STAIRS, inward(dx, dz), true));
					}
					continue;
				}
				int er = hw + 2;
				if (c <= er) {
					boolean corner = Math.abs(dx) == Math.abs(dz);
					if (c == er) {
						if (corner) {
							w.set(x, ry, z, roof);
							w.set(x, ry + 1, z, stair(Blocks.RED_NETHER_BRICK_STAIRS, inward(dx, dz).getOpposite(), false)); // upturned tip
							w.set(x, ry - 1, z, hangingLantern());
						} else w.set(x, ry, z, stair(Blocks.RED_NETHER_BRICK_STAIRS, inward(dx, dz), false)); // eave
					} else w.set(x, ry, z, roof);
					if (c == hw) w.set(x, ry + 1, z, corner ? roof : stair(Blocks.RED_NETHER_BRICK_STAIRS, inward(dx, dz), false)); // second sweep
				}
			}
			// the terrace (2.x arena): calcite rim, planks, open air above
			if (c <= 8) {
				w.set(x, oy + ty, z, c == 8 ? calcite : planks);
				for (int dy = 1; dy <= 6; dy++) w.set(x, oy + ty + dy, z, AIR);
			}
			// deck posts standing on the middle roof
			if (Math.abs(dx) == 6 && Math.abs(dz) == 6) for (int y = oy + 12; y <= oy + 15; y++) w.set(x, y, z, post);
		}
		// wind-bell posts on the terrace, central ladder, trapdoor (2.x)
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			int px = ox + sx * 7, pz = oz + sz * 7;
			w.set(px, oy + ty + 1, pz, PetalwindBlocks.SKYBLOSSOM_FENCE.defaultBlockState());
			w.set(px, oy + ty + 2, pz, PetalwindBlocks.SKYBLOSSOM_FENCE.defaultBlockState());
			w.set(px, oy + ty + 3, pz, PetalwindBlocks.WINDCHIME.defaultBlockState());
		}
		for (int y = 1; y <= ty; y++) {
			w.set(ox, oy + y, oz, calcite);
			w.set(ox, oy + y, oz + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		w.set(ox, oy + ty, oz + 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
	}

	/** Court cell ranges: g -1 = [-15, -10], 0 = [-9, 9], 1 = [10, 15]. */
	public static int lo(int g) {
		return g < 0 ? -15 : g == 0 ? -9 : 10;
	}

	public static int hi(int g) {
		return g < 0 ? -10 : g == 0 ? 9 : 15;
	}

	/**
	 * One court terrace cell around the platform, on its own floor (planned from the generator's ground, stepping down the slope): grass with
	 * pink petals, a polished-tuff path and tuff-brick steps up to the platform on the four axes, a tuff-brick retaining kerb on the low side and a
	 * masonry face where the cell is cut into higher ground, stone lanterns beside the paths, a skyblossom sapling in each corner cell.
	 */
	static void court(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ(), y = p.floor;
		int gx = p.ix / 3 - 1, gz = p.ix % 3 - 1;
		BlockState tuffB = Blocks.TUFF_BRICKS.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz;
			boolean inX = dx >= lo(gx) && dx <= hi(gx), inZ = dz >= lo(gz) && dz <= hi(gz);
			if (inX && inZ) {
				w.clear(x, z, y + 1, y + 10);
				boolean outer = (gx != 0 && Math.abs(dx) == 15) || (gz != 0 && Math.abs(dz) == 15);
				boolean path = (gx == 0 && Math.abs(dx) <= 1) || (gz == 0 && Math.abs(dz) <= 1);
				BlockState top = outer ? tuffB : path ? Blocks.POLISHED_TUFF.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
				w.set(x, y, z, top);
				// a column over a pit or cave deeper than the usual cap still gets carried down to real ground (never a floating cell column)
				if (w.foundation(x, z, y, outer ? tuffB : dirt, outer ? tuffB : dirt, outer ? tuffB : tuff, 12) < 0)
					w.foundation(x, z, y, outer ? tuffB : dirt, outer ? tuffB : dirt, outer ? tuffB : tuff, 64);
				// steps up to the platform on the axis paths
				int k = gz != 0 && gx == 0 ? Math.abs(dz) - 9 : gx != 0 && gz == 0 ? Math.abs(dx) - 9 : 99;
				int n = oy - y;
				if (path && k >= 1 && k <= n) {
					for (int yy = y + 1; yy <= oy - k; yy++) w.set(x, yy, z, tuffB);
					w.set(x, oy - k + 1, z, stair(Blocks.TUFF_BRICK_STAIRS, inward(dx, dz), false));
				} else if (!outer && !path && w.h(x, y, z) % 6 == 0) {
					w.set(x, y + 1, z, Blocks.PINK_PETALS.defaultBlockState());
				}
			} else if ((gx != 0 && Math.abs(dx) == 16 && inZ) || (gz != 0 && Math.abs(dz) == 16 && inX)) {
				// outside the outer edge: a masonry face where the cell was cut into higher ground (no bare dirt wall)
				int top = w.surface(x, z, y + 10, y - 14);
				for (int yy = y + 1; yy <= Math.min(top, y + 6); yy++) w.set(x, yy, z, tuffB);
			}
		}
		// stone lanterns flanking each axis path, a sapling in each corner cell
		if (gx == 0 || gz == 0) {
			for (int s = -1; s <= 1; s += 2) {
				int lx = gx == 0 ? s * 3 : gx * 12, lz = gz == 0 ? s * 3 : gz * 12;
				w.set(ox + lx, y + 1, oz + lz, Blocks.POLISHED_TUFF.defaultBlockState());
				w.set(ox + lx, y + 2, oz + lz, Blocks.LANTERN.defaultBlockState());
				w.set(ox + lx, y + 3, oz + lz, Blocks.POLISHED_TUFF_SLAB.defaultBlockState());
			}
		} else {
			w.set(ox + gx * 12, y + 1, oz + gz * 12, PetalwindBlocks.SKYBLOSSOM_SAPLING.defaultBlockState());
		}
	}

	// =============================================================================================== Breeze Temple
	/**
	 * An open tuff colonnade: a stepped stylobate, columns every 2 blocks on chiseled bases with brick capitals, each one carried down to the
	 * real ground as a pier, a solid back wall behind the two vaults (2.x positions), architrave and cornice, a low hipped tuff-brick roof with a
	 * wind-chime finial, hanging lanterns, and a sloped apron where the ground rises.
	 */
	static void templeHall(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState brick = Blocks.TUFF_BRICKS.defaultBlockState(), pol = Blocks.POLISHED_TUFF.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState();
		BlockState chisT = Blocks.CHISELED_TUFF.defaultBlockState(), chisB = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, dz = z - oz, c = cheb(dx, dz);
			if (c > 11) continue;
			if (c <= 8) {
				w.clear(x, z, oy + 1, oy + 14);
				if (c == 8) {
					w.set(x, oy, z, Math.abs(dx) == Math.abs(dz) ? brick : stair(Blocks.TUFF_BRICK_STAIRS, inward(dx, dz), false));
					w.foundation(x, z, oy, brick, brick, tuff, 12);
				} else {
					w.set(x, oy, z, c == 7 ? pol : (dx + dz) % 2 == 0 ? pol : brick);
					boolean column = c == 7 && Math.floorMod(along(dx, dz, c), 2) == 1 && !(dz == 7 && Math.abs(dx) <= 2);
					if (column) {
						w.set(x, oy + 1, z, chisT);
						w.set(x, oy + 2, z, pol);
						w.set(x, oy + 3, z, pol);
						w.set(x, oy + 4, z, chisB);
						w.foundation(x, z, oy, pol, pol, pol, 24); // the column goes on down to the ground as a pier
					} else {
						if (c == 7 && dz == -7) for (int y = oy + 1; y <= oy + 4; y++) w.set(x, y, z, y == oy + 3 && dx == 0 ? chisB : brick); // back wall
						w.foundation(x, z, oy, pol, brick, tuff, 12);
					}
				}
				// entablature, cornice, low hipped roof
				if (c <= 7) w.set(x, oy + 5, z, c == 7 && Math.floorMod(along(dx, dz, c), 2) == 1 ? chisB : brick);
				else w.set(x, oy + 5, z, Math.abs(dx) == Math.abs(dz) ? brick : stair(Blocks.TUFF_BRICK_STAIRS, inward(dx, dz), true));
				int topY = oy + 6 + (8 - c) / 2;
				for (int y = oy + 6; y < topY; y++) w.set(x, y, z, brick);
				if (c == 0) {
					w.set(x, topY, z, chisB);
					w.set(x, topY + 1, z, PetalwindBlocks.WINDCHIME.defaultBlockState());
				} else if (Math.abs(dx) == Math.abs(dz) || (8 - c) % 2 == 1) w.set(x, topY, z, brick);
				else w.set(x, topY, z, stair(Blocks.TUFF_BRICK_STAIRS, inward(dx, dz), false));
			} else {
				int top = w.surface(x, z, oy + 14, oy - 8), limit = oy + (c - 8);
				if (top > limit) {
					BlockState surf = w.get(x, top, z);
					w.clear(x, z, limit + 1, top + 6);
					w.set(x, limit, z, surf.is(Blocks.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : surf);
				}
			}
		}
		for (int[] l : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) w.set(ox + l[0], oy + 4, oz + l[1], hangingLantern());
		for (int[] v : BreezeTemple.VAULTS) {
			int vx = ox + v[0], vy = oy + v[1], vz = oz + v[2];
			if (w.in(vx, vy, vz)) BreezeTemple.vault(w.level, new BlockPos(vx, vy, vz), Direction.SOUTH, PetalwindItems.ZEPHYR_KEY, BreezeTemple.loot());
		}
		w.set(ox, oy + 1, oz - 6, chisT);
	}

	/** The south approach: steps down to the real ground, or a paved level path cut into rising ground. */
	static void templeSteps(FitWriter w, FitPiece p) {
		int ox = p.origin.getX(), oy = p.origin.getY(), oz = p.origin.getZ();
		BlockState brick = Blocks.TUFF_BRICKS.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - ox, k = z - oz - 8;
			if (Math.abs(dx) > 2 || k < 1 || k > 14) continue;
			int ground = w.surface(x, z, oy + 6, oy - 14);
			if (ground >= oy) {
				if (k > 4) continue;
				w.set(x, oy, z, Blocks.POLISHED_TUFF.defaultBlockState());
				w.clear(x, z, oy + 1, Math.min(ground, oy + 5));
				continue;
			}
			int y = oy - k;
			if (ground >= y) continue;
			w.set(x, y, z, Math.abs(dx) == 2 ? brick : stair(Blocks.TUFF_BRICK_STAIRS, Direction.NORTH, false));
			w.foundation(x, z, y, brick, brick, tuff, 12);
		}
	}

	// =============================================================================================== Eye of the Gale (Tempestra)
	/** A shard platform (2.x top) on a tapered rock underside: calcite, tuff, stone, rooted dirt with hanging roots, petals on top. A sky island by design. */
	static void eyePlatform(FitWriter w, FitPiece p) {
		BlockPos c = EyeOfTheGale.platforms(p.origin).get(Math.floorMod(p.ix, 8));
		int cx = c.getX(), cy = c.getY(), cz = c.getZ();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - cx, dz = z - cz;
			if (Math.abs(dx) > 2 || Math.abs(dz) > 2 || (Math.abs(dx) == 2 && Math.abs(dz) == 2)) continue;
			for (int dy = 0; dy < 4; dy++) w.set(x, cy + dy, z, AIR);
			w.set(x, cy - 1, z, Blocks.CALCITE.defaultBlockState());
			double r = Math.sqrt(dx * dx + dz * dz);
			taper(w, x, z, cy - 1, (int) Math.round(8.0 - 2.6 * r) + w.h(x, 2, z) % 3 - 1, 2, 9);
			if ((dx != 0 || dz != 0) && w.h(x, 3, z) % 5 == 0) w.set(x, cy, z, Blocks.PINK_PETALS.defaultBlockState());
		}
	}

	/** The updraft basin under the eye (2.x disc and vents) with a raised lip and the same tapered, rooted underside. */
	static void eyeBasin(FitWriter w, FitPiece p) {
		int bx = p.origin.getX(), by = p.floor, bz = p.origin.getZ();
		for (int x = w.x0; x <= w.x1; x++) for (int z = w.z0; z <= w.z1; z++) {
			int dx = x - bx, dz = z - bz, d2 = dx * dx + dz * dz;
			if (d2 > 26) continue;
			w.set(x, by, z, Blocks.CALCITE.defaultBlockState());
			if (d2 > 18) w.set(x, by + 1, z, Blocks.CALCITE.defaultBlockState());
			else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) w.set(x, by + 1, z, PetalwindBlocks.UPDRAFT_VENT.defaultBlockState());
			taper(w, x, z, by, (int) Math.round(10.0 - 1.9 * Math.sqrt(d2)) + w.h(x, 4, z) % 3 - 1, 2, 11);
		}
	}

	/** Rock under a sky-island top: depth clamped to [min, max], calcite then tuff then stone, half the columns end in rooted dirt with hanging roots. */
	static void taper(FitWriter w, int x, int z, int topY, int depth, int min, int max) {
		int d = Math.max(min, Math.min(max, depth));
		boolean roots = w.h(x, 5, z) % 2 == 0;
		for (int k = 1; k <= d; k++) {
			BlockState s = k <= 1 ? Blocks.CALCITE.defaultBlockState() : k <= 3 ? Blocks.TUFF.defaultBlockState()
				: k == d && roots ? Blocks.ROOTED_DIRT.defaultBlockState() : w.h(x, k, z) % 4 == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
			w.set(x, topY - k, z, s);
		}
		if (roots) w.set(x, topY - d - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
	}
}
