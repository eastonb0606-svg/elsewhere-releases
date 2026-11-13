package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.nether_end_bosses.BossArenaStructure;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Self-test checks for the 3.0 terrain-fit arenas (s-struct-nether), run from the module self-test:
 * <ul>
 * <li>piece versions: new pieces save {@code v 2}; a 2.x piece (no {@code v}) loads as v1 and keeps the 2.x builder;</li>
 * <li>per arena: the chunk-by-chunk build equals the one-shot build block for block, every write stays in the 3x3 chunks of
 * the centre, and no valuable block is written;</li>
 * <li>per located worldgen arena: floating footprint columns at most 1% (planned spans next to a grounded pier excluded) and
 * the Matriarch Spire stands in highlands or midlands;</li>
 * <li>at the end: no far-chunk write or unsafe terrain read was logged while the self-test generated its worlds.</li>
 * </ul>
 */
public final class FitSelfTest {
	private FitSelfTest() {}

	static final String[][] SITES = {{"grunthar", "grunthar_treasure_hall"}, {"solaxis", "solaxis_pyre_cradle"}, {"mournmaw", "mournmaw_weeping_basin"},
		{"ossyra", "ossyra_matriarch_spire"}, {"aphelion", "aphelion_shattered_halo"}};

	static String f(String fmt, Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
		FarChunkWatch.install();
		check.accept(NetherFit.ENABLED && NetherFit.ARENAS.size() == 5 && NetherFit.LEGACY.size() == 5
			&& dev.elsewhere.nether_end_bosses.ArenaBuilder.BY_BOSS.values().stream().allMatch(b -> b instanceof NetherFit.Adapter),
			"fit3: 5 terrain-fit arenas are live (kill switch elsewhere.overhaul3.nether_fit), the 2.x builders kept for 2.x pieces");
		try {
			pieceVersions(server, check);
		} catch (Throwable t) {
			check.accept(false, "fit3: piece versions: " + t);
		}
		int i = 0;
		for (FitArena a : NetherFit.ARENAS.values()) {
			try {
				chunkByChunk(server, a, i++, check);
			} catch (Throwable t) {
				check.accept(false, "fit3: " + a.id() + " chunk-by-chunk build: " + t);
			}
		}
		for (String[] s : SITES) {
			try {
				worldgenSite(server, s[0], s[1], check);
			} catch (Throwable t) {
				check.accept(false, "fit3: " + s[0] + " worldgen fit: " + t);
			}
		}
		try {
			metricControl(server, check);
		} catch (Throwable t) {
			check.accept(false, "fit3: float metric control: " + t);
		}
	}

	/**
	 * Negative control for the float metric: a block hung in open air over Nether ground and over End ground or void must
	 * measure as floating (gap > 1), so the 0% results above are not a metric that cannot fail. The block is removed after.
	 */
	private static void metricControl(MinecraftServer server, BiConsumer<Boolean, String> check) {
		StringBuilder msg = new StringBuilder();
		boolean ok = true;
		int found = 0;
		for (var key : java.util.List.of(Level.NETHER, Level.END)) {
			ServerLevel level = server.getLevel(key);
			if (level == null) { ok = false; continue; }
			var gen = level.getChunkSource().getGenerator();
			var rs = level.getChunkSource().randomState();
			java.util.function.BiFunction<Integer, Integer, net.minecraft.world.level.NoiseColumn> col = (cx, cz) -> gen.getBaseColumn(cx, cz, level, rs);
			int x = 8008, z = 7880;
			level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
			int y = -1;
			for (int yy = 110; yy >= 40 && y < 0; yy--) {
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, yy, z);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isAir() && level.getBlockState(p.below(2)).isAir() && level.getBlockState(p.below(3)).isAir()
					&& level.getBlockState(p.above()).isAir()) {
					// open in the terrain noise too: a carver cave under solid noise ground is air here but "ground" to the metric
					var nc = col.apply(x, z);
					if (nc.getBlock(yy).isAir() && nc.getBlock(yy - 1).isAir() && nc.getBlock(yy - 2).isAir() && nc.getBlock(yy - 3).isAir()) y = yy;
				}
			}
			if (y < 0) { msg.append(key.identifier().getPath()).append(": no open air column; "); continue; }
			BlockPos at = new BlockPos(x, y, z);
			level.setBlock(at, net.minecraft.world.level.block.Blocks.NETHER_BRICKS.defaultBlockState(), 2);
			int gap = gapBelow(level, col, x, z, y);
			level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			found++;
			ok &= gap > 1;
			msg.append(key.identifier().getPath()).append(" gap ").append(gap).append(" at ").append(at.toShortString()).append("; ");
		}
		check.accept(ok && found > 0, f("fit3: float metric control: a block hung in open air measures as floating (%s)", msg.toString().trim()));
	}

	/** Called as the last step of the module self-test. */
	public static void finalChecks(BiConsumer<Boolean, String> check) {
		check.accept(FarChunkWatch.installed() && FarChunkWatch.count() == 0,
			f("fit3: 0 far-chunk writes or unsafe terrain reads logged while the self-test generated the Nether and End (%d %s)", FarChunkWatch.count(), FarChunkWatch.sources()));
	}

	private static void pieceVersions(MinecraftServer server, BiConsumer<Boolean, String> check) {
		ServerLevel level = server.getLevel(Level.NETHER);
		var ctx = StructurePieceSerializationContext.fromLevel(level);
		BlockPos o = new BlockPos(8, 64, 8);
		CompoundTag v2 = BossArenaStructure.Piece.of("solaxis", o, 42L, 2).createTag(ctx);
		CompoundTag old = BossArenaStructure.Piece.of("solaxis", o, 42L, 1).createTag(ctx);
		var p2 = BossArenaStructure.Piece.fromTag(v2);
		var p1 = BossArenaStructure.Piece.fromTag(old);
		check.accept(v2.getIntOr("v", 0) == 2 && !old.contains("v") && p2.version() == 2 && p1.version() == 1 && p1.seed() == 42L
			&& p1.getBoundingBox().minY() == o.getY() - 32 && p2.getBoundingBox().minX() == o.getX() - 23,
			"fit3: new arena pieces save v 2 (chunk-by-chunk); a 2.x piece (no v) loads as v1 and finishes from its centre with the 2.x builder");
	}

	/** Builds the arena chunk by chunk, then once whole on top: nothing may change, and nothing may be written outside the 3x3. */
	private static void chunkByChunk(MinecraftServer server, FitArena a, int index, BiConsumer<Boolean, String> check) {
		boolean end = a.id().equals("ossyra") || a.id().equals("aphelion");
		ServerLevel level = server.getLevel(end ? Level.END : Level.NETHER);
		int cx = (8000 + index * 96) >> 4, cz = 8000 >> 4;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
		int x = (cx << 4) + 8, z = (cz << 4) + 8;
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		int y = FitSite.pick(a.id(), x, z, (px, pz) -> gen.getBaseColumn(px, pz, level, rs), level.getMinY(), level.getMaxY(), net.minecraft.util.RandomSource.create(index));
		if (y == FitSite.NONE) y = end ? 70 : 64;
		BlockPos o = new BlockPos(x, y, z);
		long seed = 0x5EEDL + index;
		BoundingBox box3 = new BoundingBox((cx - 1) << 4, level.getMinY(), (cz - 1) << 4, ((cx + 1) << 4) + 15, level.getMaxY(), ((cz + 1) << 4) + 15);
		int valuable = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			int ccx = cx + dx, ccz = cz + dz;
			FitW w = new FitW(level, new BoundingBox(ccx << 4, level.getMinY(), ccz << 4, (ccx << 4) + 15, level.getMaxY(), (ccz << 4) + 15), seed);
			a.build(w, o);
			w.finish();
			valuable += w.valuable;
		}
		int y0 = Math.max(level.getMinY() + 1, o.getY() - 70), y1 = Math.min(level.getMaxY() - 1, o.getY() + 36);
		BlockState[] snap = new BlockState[47 * 47 * (y1 - y0 + 1)];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int k = 0;
		for (int yy = y0; yy <= y1; yy++) for (int dz = -23; dz <= 23; dz++) for (int dx = -23; dx <= 23; dx++) snap[k++] = level.getBlockState(p.set(x + dx, yy, z + dz));
		FitW one = new FitW(level, null, seed);
		one.watch = box3;
		a.build(one, o);
		one.finish();
		int diff = 0;
		StringBuilder dd = new StringBuilder();
		k = 0;
		for (int yy = y0; yy <= y1; yy++) for (int dz = -23; dz <= 23; dz++) for (int dx = -23; dx <= 23; dx++) {
			BlockState was = snap[k++];
			BlockState now = level.getBlockState(p.set(x + dx, yy, z + dz));
			if (was != now) {
				diff++;
				if (diff <= 6) dd.append(p.toShortString()).append(" chunkwise ").append(was).append(" -> whole ").append(now).append("; ");
			}
		}
		check.accept(diff == 0, f("fit3: %s built chunk by chunk equals the one-shot build (%d blocks differ at %s; %d writes)", a.id(), diff, o.toShortString(), one.writes) + (diff == 0 ? "" : " :: " + dd));
		check.accept(one.outsideWatch == 0 && one.writes > 500, f("fit3: %s writes stay in the 3x3 chunks around its centre (%d outside of %d)", a.id(), one.outsideWatch, one.writes));
		check.accept(valuable == 0 && one.valuable == 0, f("fit3: %s places no valuable blocks (gold, iron, diamond, emerald, lapis, netherite, gilded blackstone, ores: %d)", a.id(), valuable + one.valuable));
		BlockState anchor = level.getBlockState(a.anchor(o));
		check.accept(!anchor.isAir() || !level.getBlockState(o.below()).isAir(), f("fit3: %s summon anchor in place (%s, floor %s)", a.id(), anchor.getBlock(), level.getBlockState(o.below()).getBlock()));
	}

	/** The located worldgen arena: float metric on the main floor, and the spire's biome. */
	private static void worldgenSite(MinecraftServer server, String id, String structure, BiConsumer<Boolean, String> check) {
		boolean end = id.equals("ossyra") || id.equals("aphelion");
		ServerLevel level = server.getLevel(end ? Level.END : Level.NETHER);
		var holder = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("elsewhere", structure)));
		if (level == null || holder.isEmpty()) {
			check.accept(false, "fit3: " + id + " structure registered");
			return;
		}
		BlockPos from = end ? new BlockPos(3000, 64, 3000) : new BlockPos(-3000, 64, 3000);
		BlockPos at = level.findNearestMapStructure(HolderSet.direct(holder.get()), from, 100, false);
		if (at == null) {
			check.accept(false, "fit3: " + id + " located for the fit check");
			return;
		}
		StructureStart start = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.FULL, true).getStartForStructure(holder.get().value());
		if (start == null || !start.isValid() || !(start.getPieces().get(0) instanceof BossArenaStructure.Piece piece)) {
			check.accept(false, "fit3: " + id + " start at " + at.toShortString() + " is an arena piece");
			return;
		}
		BlockPos o = piece.origin;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, ChunkStatus.FULL, true);
		FitArena a = NetherFit.ARENAS.get(id);
		check.accept(piece.version() == 2, f("fit3: %s generates as a v2 fit piece in a new world (v %d at %s)", id, piece.version(), o.toShortString()));
		int r = a.floorRadius(), top = o.getY() + a.footingTop() + 1;
		int total = 0, floating = 0, maxGap = 0, piers = 0, piersDown = 0;
		int[] worst = null;
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		java.util.Map<Long, net.minecraft.world.level.NoiseColumn> noise = new java.util.HashMap<>();
		java.util.function.BiFunction<Integer, Integer, net.minecraft.world.level.NoiseColumn> col = (cx, cz) ->
			noise.computeIfAbsent(BlockPos.asLong(cx, 0, cz), k -> gen.getBaseColumn(cx, cz, level, rs));
		for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
			if (dx * dx + dz * dz > r * r) continue;
			total++;
			int gap = gapBelow(level, col, o.getX() + dx, o.getZ() + dz, top);
			if (FitW.isPier(dx, dz) && gap > 1) piers++;
			if (gap <= 1) continue;
			if (!FitW.isPier(dx, dz) && pierNear(level, col, o, dx, dz, top)) continue; // planned span between grounded piers
			floating++;
			if (gap > maxGap) { maxGap = gap; worst = new int[] {dx, dz}; }
		}
		for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) if (dx * dx + dz * dz <= r * r && FitW.isPier(dx, dz)) piersDown++;
		double pct = total == 0 ? 100 : floating * 100.0 / total;
		check.accept(pct <= 1.0, f("fit3: %s worldgen arena at %s: %.1f%% of %d floor columns float (target <= 1%%; max gap %d; %d of %d pier columns not down)",
			id, o.toShortString(), pct, total, maxGap, piers, piersDown));
		if (pct > 1.0 && worst != null)
			dev.elsewhere.nether_end_bosses.NetherEndBossesContent.LOGGER.info("[selftest] fit3 {} worst column {} {}: {}", id, worst[0], worst[1], profile(level, col, o.getX() + worst[0], o.getZ() + worst[1], top));
		if (id.equals("ossyra")) {
			var biome = level.getBiome(o.below(27));
			check.accept(biome.is(Biomes.END_HIGHLANDS) || biome.is(Biomes.END_MIDLANDS), "fit3: the Matriarch Spire stands in end highlands or midlands (" + biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?") + ")");
		}
	}

	/**
	 * STRUCTURES.md 2.16 "floating": air or fluid under the structure's lowest block before solid ground. Walks down the solid run
	 * from the floor; the column is grounded as soon as the run reaches natural ground (solid in the generator's noise column, the
	 * terrain before the structure), so natural caves and the void under a floating End island are not counted against it.
	 * @return 0 when grounded, else the gap length (999 over the void)
	 */
	static int gapBelow(ServerLevel level, java.util.function.BiFunction<Integer, Integer, net.minecraft.world.level.NoiseColumn> noise, int x, int z, int top) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
		var nc = noise.apply(x, z);
		int min = level.getMinY();
		while (p.getY() > min && FitW.ground(level.getBlockState(p))) {
			BlockState natural = nc.getBlock(p.getY());
			if (!natural.isAir() && natural.getFluidState().isEmpty()) return 0;
			p.move(0, -1, 0);
		}
		if (p.getY() <= min) return 0;
		int gap = 0;
		while (p.getY() > min && !FitW.ground(level.getBlockState(p)) && gap < 200) {
			gap++;
			p.move(0, -1, 0);
		}
		return p.getY() <= min || gap >= 200 ? 999 : gap;
	}

	/** Diagnostic: the column from the floor down, as runs of "y:block(noise block)*n". */
	static String profile(ServerLevel level, java.util.function.BiFunction<Integer, Integer, net.minecraft.world.level.NoiseColumn> noise, int x, int z, int top) {
		var nc = noise.apply(x, z);
		StringBuilder b = new StringBuilder();
		String last = null;
		int run = 0, from = top;
		for (int y = top + 3; y >= Math.max(level.getMinY(), top - 90); y--) {
			String k = level.getBlockState(new BlockPos(x, y, z)).getBlock().toString().replace("Block{minecraft:", "").replace("Block{", "").replace("}", "")
				+ "/" + nc.getBlock(y).getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
			if (k.equals(last)) { run++; continue; }
			if (last != null) b.append(from).append(':').append(last).append('*').append(run).append(' ');
			last = k;
			run = 1;
			from = y;
		}
		if (last != null) b.append(from).append(':').append(last).append('*').append(run);
		return b.toString();
	}

	private static boolean pierNear(ServerLevel level, java.util.function.BiFunction<Integer, Integer, net.minecraft.world.level.NoiseColumn> noise, BlockPos o, int dx, int dz, int top) {
		for (int ez = -3; ez <= 3; ez++) for (int ex = -3; ex <= 3; ex++) {
			if (!FitW.isPier(dx + ex, dz + ez)) continue;
			if (gapBelow(level, noise, o.getX() + dx + ex, o.getZ() + dz + ez, top) <= 1) return true;
		}
		return false;
	}
}
