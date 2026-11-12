package dev.elsewhere.petalwind_glasswaste.fit3;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.heliodor.Heliodor;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.petalwind_glasswaste.BreezeTemple;
import dev.elsewhere.petalwind_glasswaste.PGStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;

/**
 * Self-test checks for the 3.0 Petalwind/Glasswaste structure looks and foundations ({@link PGFit}). Called from {@code SiteChecks} with the
 * module's {@code SelfTest::check}; every check name starts with {@code fit3}.
 */
public final class FitChecks {
	private FitChecks() {}

	public interface Check {
		void check(boolean ok, String what);
	}

	static final Set<Block> VALUABLE = Set.of(Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK,
		Blocks.NETHERITE_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK);

	static List<FitPiece> fits(StructureStart start) {
		List<FitPiece> out = new ArrayList<>();
		for (StructurePiece p : start.getPieces()) if (p instanceof FitPiece f) out.add(f);
		return out;
	}

	static BoundingBox union(List<FitPiece> ps) {
		BoundingBox b = ps.get(0).box();
		for (FitPiece p : ps) b = BoundingBox.encapsulating(b, p.box());
		return b;
	}

	static boolean soft(ServerLevel l, int x, int y, int z) {
		return FitWriter.soft(l.getBlockState(new BlockPos(x, y, z)));
	}

	/** Checks one generated site (called by SiteChecks after /locate found and generated it). */
	public static void site(ServerLevel level, String kind, StructureStart start, Check ck) {
		List<FitPiece> ps = fits(start);
		if (ps.isEmpty()) {
			ck.check(!PGFit.ENABLED || start.getPieces().stream().anyMatch(p -> p instanceof PGStructures.Piece),
				"fit3 " + kind + ": a start saved before 3.0 keeps its 2.x piece (" + start.getPieces().size() + " pieces)");
			return;
		}
		ck.check(ps.stream().allMatch(p -> p.v == FitPiece.V && p.kind.equals(kind)) && ps.size() == expected(kind, ps),
			String.format("fit3 %s: new start is %d versioned fit pieces (v%d), each with its own floor", kind, ps.size(), FitPiece.V));
		BoundingBox all = union(ps);
		long t0 = System.nanoTime();
		for (int cx = all.minX() >> 4; cx <= all.maxX() >> 4; cx++) for (int cz = all.minZ() >> 4; cz <= all.maxZ() >> 4; cz++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
		long genMs = (System.nanoTime() - t0) / 1_000_000;
		// no valuable blocks anywhere in the site
		int valuable = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (FitPiece p : ps) {
			BoundingBox b = p.box();
			for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int y = b.minY(); y <= b.maxY(); y++) {
				if (VALUABLE.contains(level.getBlockState(m.set(x, y, z)).getBlock())) valuable++;
			}
		}
		ck.check(valuable == 0, String.format("fit3 %s: no valuable blocks in the site (%d found; %d chunks generated in %d ms)", kind, valuable,
			((all.maxX() >> 4) - (all.minX() >> 4) + 1) * ((all.maxZ() >> 4) - (all.minZ() >> 4) + 1), genMs));
		BlockPos o = ps.get(0).origin;
		int ox = o.getX(), oy = o.getY(), oz = o.getZ();
		switch (kind) {
			case PGStructures.ZIGGURAT -> {
				int n = 0, floating = 0, buried = 0;
				for (int a = -15; a <= 15; a++) for (int[] s : new int[][] {{a, 15}, {a, -15}, {15, a}, {-15, a}}) {
					n++;
					if (soft(level, ox + s[0], oy - 1, oz + s[1])) floating++;
					int bx = s[0] + Integer.signum(s[0]) * (Math.abs(s[0]) == 15 ? 1 : 0), bz = s[1] + Integer.signum(s[1]) * (Math.abs(s[1]) == 15 ? 1 : 0);
					if (!soft(level, ox + bx, oy + 3, oz + bz)) buried++;
				}
				ck.check(floating == 0 && buried * 100 <= 5 * n, String.format(
					"fit3 sunwright_ziggurat: the lowest terrace meets the ground on every side (%d of %d perimeter columns float, %d buried above the first tier)", floating, n, buried));
				boolean gilt = level.getBlockState(new BlockPos(ox + 4, oy + 2, oz - 15)).is(Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.YELLOW));
				int blocked = 0; // the south flight is walkable: two blocks of head room over every step
				for (int i = 0; i <= 13; i++) for (int dx = -1; dx <= 1; dx++) {
					if (!level.getBlockState(new BlockPos(ox + dx, oy + i + 1, oz + 16 - i)).isAir() || !level.getBlockState(new BlockPos(ox + dx, oy + i + 2, oz + 16 - i)).isAir()) blocked++;
				}
				ck.check(blocked == 0, "fit3 sunwright_ziggurat: the south flight has head room over every step (" + blocked + " blocked)");
				ck.check(gilt && level.getBlockState(new BlockPos(ox + 6, oy + 15, oz + 6)).is(dev.elsewhere.glasswaste.GlasswasteBlocks.SUN_MIRROR),
					"fit3 sunwright_ziggurat: gilt band is yellow terracotta and the summit mirrors stand at their 2.x places");
			}
			case PGStructures.SUNPIT -> {
				int bowlR = ps.stream().filter(p -> p.part.equals("bowl")).mapToInt(p -> p.param).findFirst().orElse(FitBuilders.BOWL_R);
				int maxStep = 0, shaped = 0, notOpen = 0, overhang = 0;
				String firstShut = "";
				for (int i = 0; i < 16; i++) {
					double ang = Math.PI * 2 * i / 16 + 0.11;
					int prev = Integer.MIN_VALUE;
					for (int r = 24; r <= bowlR; r++) {
						int x = ox + (int) Math.round(Math.cos(ang) * r), z = oz + (int) Math.round(Math.sin(ang) * r);
						int top = oy + 30;
						while (top > oy - 8 && (soft(level, x, top, z) || decor(level, x, top, z))) top--;
						int yb = FitBuilders.bowlY(oy, Math.sqrt((x - ox) * (x - ox) + (z - oz) * (z - oz)), bowlR);
						if (top != yb) { // not a cut cell: natural dune or trough
							prev = Integer.MIN_VALUE;
							continue;
						}
						shaped++;
						for (int y = top + 1; y <= oy + 30; y++) if (!soft(level, x, y, z) && !decor(level, x, y, z)) overhang++;
						if (prev != Integer.MIN_VALUE) maxStep = Math.max(maxStep, Math.abs(top - prev));
						prev = top;
					}
				}
				for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
					if (dx * dx + dz * dz > 400) continue;
					for (int dy = 0; dy <= 8; dy++) {
						var st = level.getBlockState(new BlockPos(ox + dx, oy + dy, oz + dz));
						if (!st.isAir() && !st.is(Blocks.GLASS) && notOpen++ == 0) firstShut = st.getBlock().getDescriptionId() + " at " + dx + "," + dy + "," + dz;
					}
				}
				ck.check(maxStep <= 1 && overhang == 0 && notOpen == 0, String.format(
					"fit3 sunpit: the crater is a sand slope at the angle of repose (%d cut cells on 16 rays, largest step %d, %d overhanging blocks, no vertical cut), open to the sky over the arena (%d blocks above the floor %s)",
					shaped, maxStep, overhang, notOpen, firstShut));
				boolean rings = level.getBlockState(new BlockPos(ox + 18, oy - 2, oz)).is(Blocks.MAGMA_BLOCK) && level.getBlockState(new BlockPos(ox + 18, oy - 1, oz)).is(Blocks.SAND);
				ck.check(rings, "fit3 sunpit: Ankhara's collapse rings keep their 2.x layers (sand over magma)");
			}
			case PGStructures.PAGODA -> {
				int n = 0, floating = 0;
				for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
					n++;
					if (soft(level, ox + dx, oy - 1, oz + dz)) floating++;
				}
				ck.check(floating * 100 <= n, String.format("fit3 petal_pagoda: platform on a ground-fitted foundation (%d of %d columns unsupported)", floating, n));
				int courts = 0, badCourt = 0, maxStep = 0;
				int[] f = new int[9];
				java.util.Arrays.fill(f, PGFit.VOID);
				for (FitPiece p : ps) if (p.part.equals("court")) {
					courts++;
					f[p.ix] = p.floor;
					int gx = p.ix / 3 - 1, gz = p.ix % 3 - 1;
					int cx = ox + (FitBuilders.lo(gx) + FitBuilders.hi(gx)) / 2, cz = oz + (FitBuilders.lo(gz) + FitBuilders.hi(gz)) / 2;
					if (gx != 0 && gz != 0) cx += 1; // off the sapling
					if (soft(level, cx, p.floor, cz) || soft(level, cx, p.floor - 1, cz) || p.floor > oy) badCourt++;
				}
				for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++) {
					if (f[i] == PGFit.VOID || f[j] == PGFit.VOID) continue;
					if (Math.abs(i / 3 - j / 3) + Math.abs(i % 3 - j % 3) == 1) maxStep = Math.max(maxStep, Math.abs(f[i] - f[j]));
				}
				ck.check(badCourt == 0 && maxStep <= 2, String.format("fit3 petal_pagoda: %d court terraces on their own ground, none above the platform, neighbours within 2 (max %d, %d unsupported)", courts, maxStep, badCourt));
				boolean deck = level.getBlockState(new BlockPos(ox + 3, oy + 17, oz + 3)).is(dev.elsewhere.petalwind.PetalwindBlocks.SKYBLOSSOM_PLANKS)
					&& level.getBlockState(new BlockPos(ox + 7, oy + 20, oz + 7)).is(dev.elsewhere.petalwind.PetalwindBlocks.WINDCHIME)
					&& level.getBlockState(new BlockPos(ox + 8, oy + 6, oz)).is(Blocks.RED_NETHER_BRICK_STAIRS);
				ck.check(deck, "fit3 petal_pagoda: sweeping stair eaves and the 2.x roof terrace with its wind bells");
			}
			case PGStructures.BREEZE -> {
				int cols = 0, short_ = 0;
				for (int a = -7; a <= 7; a += 2) for (int[] s : new int[][] {{a, 7}, {a, -7}, {7, a}, {-7, a}}) {
					if (s[1] == 7 && Math.abs(s[0]) <= 2) continue;
					cols++;
					if (soft(level, ox + s[0], oy - 1, oz + s[1]) || !level.getBlockState(new BlockPos(ox + s[0], oy + 2, oz + s[1])).is(Blocks.POLISHED_TUFF)) short_++;
				}
				int vaults = 0;
				for (int[] v : BreezeTemple.VAULTS) if (level.getBlockState(new BlockPos(ox + v[0], oy + v[1], oz + v[2])).is(Blocks.VAULT)) vaults++;
				ck.check(short_ == 0 && vaults == 2, String.format("fit3 breeze_temple: open colonnade, every column down to the ground (%d of %d short), both vaults in place (%d)", short_, cols, vaults));
			}
			case PGStructures.EYE -> {
				int tapered = 0, roots = 0;
				for (FitPiece p : ps) {
					if (!p.part.equals("platform")) continue;
					BlockPos c = EyeOfTheGale.platforms(o).get(p.ix);
					if (!level.getBlockState(c.below(2)).isAir() && !level.getBlockState(c.below(3)).isAir()) tapered++;
					BoundingBox b = p.box();
					for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int y = b.minY(); y < c.getY(); y++) {
						if (level.getBlockState(m.set(x, y, z)).is(Blocks.HANGING_ROOTS)) roots++;
					}
				}
				ck.check(tapered == 8 && roots >= 8, String.format("fit3 eye_of_the_gale: 8 sky-island platforms with tapered rock undersides (%d) and hanging roots (%d)", tapered, roots));
			}
			default -> {
			}
		}
	}

	/** Skeleton ribs and rim markers stand on the slope; the slope measure looks through them. */
	static boolean decor(ServerLevel l, int x, int y, int z) {
		var s = l.getBlockState(new BlockPos(x, y, z));
		return s.is(Blocks.BONE_BLOCK) || s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.CHISELED_SANDSTONE);
	}

	static int expected(String kind, List<FitPiece> ps) {
		return switch (kind) {
			case PGStructures.ZIGGURAT, PGStructures.BREEZE -> 2;
			case PGStructures.SUNPIT -> 5;
			case PGStructures.EYE -> 10;
			default -> ps.size(); // pagoda: core plus the court cells that have ground
		};
	}

	/** Breeze temple site, feature twins, plan determinism, NBT round trip and the kill switch. */
	public static void run(MinecraftServer server, Check ck) {
		ServerLevel pw = server.getLevel(PetalwindDimension.LEVEL), gw = server.getLevel(GlasswasteDimension.LEVEL);
		// 1. the breeze temple, generated by normal worldgen
		var holder = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, PGStructures.structureId(PGStructures.BREEZE)));
		if (pw == null || holder.isEmpty()) ck.check(false, "fit3 breeze_temple: structure registered and Petalwind loaded");
		else {
			BlockPos from = new BlockPos(-6000 - new java.util.Random().nextInt(300000), 100, 6000 + new java.util.Random().nextInt(300000));
			BlockPos at = pw.findNearestMapStructure(HolderSet.direct(holder.get()), from, 100, false);
			if (at == null) ck.check(false, "fit3 breeze_temple: /locate finds one within 100 chunks of " + from.toShortString());
			else {
				StructureStart st = pw.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.FULL, true).getStartForStructure(holder.get().value());
				if (st == null || !st.isValid()) ck.check(false, "fit3 breeze_temple: chunk generated with a valid start at " + at.toShortString());
				else site(pw, PGStructures.BREEZE, st, ck);
			}
		}
		// 2. plan: deterministic, court terraces step down a synthetic hillside, NBT round trip
		BlockPos o = new BlockPos(1000, 150, 1000);
		PGFit.Ground hill = (x, z) -> 150 - Math.max(0, x - 1000) / 2; // flat in the west, falling 1 in 2 to the east
		List<FitPiece> a = PGFit.plan(PGStructures.PAGODA, o, hill, 42L, FitBuilders.BOWL_R), b = PGFit.plan(PGStructures.PAGODA, o, hill, 42L, FitBuilders.BOWL_R);
		boolean same = a.size() == b.size();
		for (int i = 0; same && i < a.size(); i++) same = a.get(i).floor == b.get(i).floor && a.get(i).box().equals(b.get(i).box()) && a.get(i).part.equals(b.get(i).part);
		int[] f = PGFit.courtFloors(o, hill);
		boolean steps = true;
		for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++) {
			if (i == 4 || j == 4 || Math.abs(i / 3 - j / 3) + Math.abs(i % 3 - j % 3) != 1) continue;
			if (Math.abs(f[i] - f[j]) > 2) steps = false;
		}
		ck.check(same && a.size() == 9 && f[1] == 150 && f[5] == 148 && f[7] == 146 && steps,
			String.format("fit3 plan: deterministic, 8 court terraces stepping down a hillside within 2 of each other (west %d, south %d, east %d)", f[1], f[5], f[7]));
		if (pw != null) {
			FitPiece p0 = a.get(1);
			CompoundTag tag = p0.createTag(StructurePieceSerializationContext.fromLevel(pw));
			FitPiece back = new FitPiece(tag);
			ck.check(back.kind.equals(p0.kind) && back.part.equals(p0.part) && back.floor == p0.floor && back.ix == p0.ix && back.v == FitPiece.V
				&& back.origin.equals(p0.origin) && back.seed == p0.seed && back.box().equals(p0.box()), "fit3 plan: a fit piece survives an NBT round trip (kind, part, v, floor, origin, seed, box)");
		}
		// 3. kill switch: off = the 2.x piece for new starts
		boolean was = PGFit.ENABLED;
		PGFit.ENABLED = false;
		List<StructurePiece> legacy = PGStructures.newPieces(PGStructures.SUNPIT, o, hill, 1L);
		PGFit.ENABLED = true;
		List<StructurePiece> fit = PGStructures.newPieces(PGStructures.SUNPIT, o, hill, 1L);
		PGFit.ENABLED = was;
		ck.check(legacy.size() == 1 && legacy.get(0) instanceof PGStructures.Piece && fit.size() == 5 && fit.stream().allMatch(p -> p instanceof FitPiece),
			"fit3 kill switch pg_fit3: off gives new starts the 2.x piece, on gives 5 fit pieces (Sunpit)");
		// 4. feature twins (/place) go through the same builders, inside their 3x3 chunks
		if (gw != null) {
			for (String kind : new String[] {PGStructures.SUNPIT, PGStructures.ZIGGURAT}) {
				BlockPos spot = new BlockPos(((9000 + new java.util.Random().nextInt(200000)) & ~15) + 8, 0, ((9000 + new java.util.Random().nextInt(200000)) & ~15) + 8);
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) gw.getChunk((spot.getX() >> 4) + dx, (spot.getZ() >> 4) + dz, ChunkStatus.FULL, true);
				int top = gw.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ()) - 1;
				long t0 = System.nanoTime();
				PGFit.placeTwin(gw, kind, spot.atY(kind.equals(PGStructures.SUNPIT) ? top - 6 : top - 1)); // what ModWorldgen's twins call
				boolean placed = true;
				long ms = (System.nanoTime() - t0) / 1_000_000;
				AABB box = new AABB(spot.atY(top)).inflate(40);
				boolean ok;
				if (kind.equals(PGStructures.SUNPIT)) {
					var bs = gw.getEntitiesOfClass(Ankhara.class, box);
					ok = placed && bs.size() == 1 && gw.getBlockState(bs.get(0).arenaCenter().offset(18, -2, 0)).is(Blocks.MAGMA_BLOCK);
					bs.forEach(e -> e.discard());
				} else {
					var bs = gw.getEntitiesOfClass(Heliodor.class, box);
					ok = placed && bs.size() == 1 && gw.getBlockState(bs.get(0).arenaCenter().offset(6, 0, 6)).is(dev.elsewhere.glasswaste.GlasswasteBlocks.SUN_MIRROR);
					bs.forEach(e -> e.discard());
				}
				ck.check(ok && ms <= 400, String.format("fit3 twin %s: /place builds the fit arena and its boss inside its 3x3 chunks (%d ms)", kind, ms));
			}
		}
	}
}
