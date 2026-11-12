package dev.elsewhere.hush;

import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.Blueprint;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import dev.elsewhere.greenhaze_hush.world.Sink;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Code-built Hush structures: The Listening Engine (Auris), The Choir Geode (Lithara, with the dimension's one exit deep
 * below its floor), Silent Monasteries (wool-lined safe rooms with the Mute Archivist) and the Hanging Undercity.
 * The Hush deliberately has no Return Rifts (CONTRACT.md user decision 2).
 */
public final class HushStructures {
	private HushStructures() {}

	public static final ResourceKey<LootTable> UNDERCITY_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/hanging_undercity"));
	public static final ResourceKey<LootTable> ENGINE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/listening_engine"));
	public static final ResourceKey<LootTable> GEODE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/choir_geode"));
	public static final ResourceKey<LootTable> MONASTERY_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/silent_monastery"));

	static BlockState s(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState();
	}

	/** Carve an ellipsoid room of air (keeps the shell solid), return nothing. */
	static void carve(Sink sk, int x, int y, int z, double rx, double ry, double rz, BlockState shell) {
		int irx = (int) Math.ceil(rx) + 1, iry = (int) Math.ceil(ry) + 1, irz = (int) Math.ceil(rz) + 1;
		for (int dx = -irx; dx <= irx; dx++) for (int dy = -iry; dy <= iry; dy++) for (int dz = -irz; dz <= irz; dz++) {
			double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
			if (d <= 1) sk.set(x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
			else if (d <= 1.25 && shell != null) sk.set(x + dx, y + dy, z + dz, shell);
		}
	}

	public static final Blueprint LISTENING_ENGINE = new Blueprint() {
		public int radius() { return 24; }
		public int below() { return 10; }
		public int above() { return 22; }

		public void build(Sink sk, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			carve(sk, x, y + 8, z, 22, 14, 22, s(Blocks.DEEPSLATE_TILES));
			// the ear funnel: concentric terraces stepping down toward the catalyst throne
			for (int dx = -21; dx <= 21; dx++) for (int dz = -21; dz <= 21; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 21) continue;
				int step = (int) Math.min(4, Math.max(0, (d - 5) / 4));
				int top = y - 4 + step;
				for (int yy = y - 8; yy <= top; yy++) sk.set(x + dx, yy, z + dz, (yy == top) ? (step % 2 == 0 ? s(Blocks.POLISHED_DEEPSLATE) : s(Blocks.DEEPSLATE_TILES)) : s(Blocks.DEEPSLATE));
				if (yy(top) && d > 5 && Math.floorMod((int) (d * 3), 7) == 0) sk.set(x + dx, top, z + dz, s(Blocks.SCULK));
			}
			int floor = y - 4;
			// giant catalyst throne (3x3x3) on a reinforced deepslate dais
			sk.fill(x - 2, floor, z - 2, x + 2, floor, z + 2, s(Blocks.REINFORCED_DEEPSLATE));
			sk.fill(x - 1, floor + 1, z - 1, x + 1, floor + 3, z + 1, s(Blocks.SCULK_CATALYST));
			// 4 tympanum membranes on pillars at the cardinal points (r = 12)
			int[][] cards = {{12, 0}, {-12, 0}, {0, 12}, {0, -12}};
			for (int[] c : cards) {
				int px = x + c[0], pz = z + c[1];
				int base = floor + 2;
				for (int yy = base; yy < base + 5; yy++) sk.set(px, yy, pz, s(Blocks.DEEPSLATE_BRICK_WALL));
				sk.fill(px - 1, base + 5, pz - 1, px + 1, base + 7, pz + 1, s(HushBlocks.TYMPANUM_MEMBRANE));
				sk.set(px, base + 8, pz, s(Blocks.SCULK_SENSOR));
			}
			// wool silence pads on the second terrace
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + Math.PI / 6;
				int px = x + (int) Math.round(Math.cos(a) * 9), pz = z + (int) Math.round(Math.sin(a) * 9);
				int py = floor + 1;
				sk.fill(px - 1, py, pz - 1, px + 1, py, pz + 1, s(i % 2 == 0 ? Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK) : Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.GRAY)));
			}
			// soul lanterns hanging from the vault, sculk veins for mood
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int px = x + (int) Math.round(Math.cos(a) * 15), pz = z + (int) Math.round(Math.sin(a) * 15);
				sk.set(px, y + 16, pz, s(Blocks.DEEPSLATE_TILES));
				sk.set(px, y + 15, pz, s(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
			}
			// entrance tunnel (east)
			for (int t = 18; t <= 28; t++) sk.fill(x + t, floor + 4, z - 1, x + t, floor + 6, z + 1, s(Blocks.AIR));
			// a surveyor's cache in the tunnel (outside the sealed funnel, so a reset never touches it)
			sk.set(x + 26, floor + 4, z, s(Blocks.CHEST));
			sk.loot(new BlockPos(x + 26, floor + 4, z), ENGINE_LOOT, r.nextLong());
		}

		private boolean yy(int v) { return true; }
	};

	public static final Blueprint CHOIR_GEODE = new Blueprint() {
		public int radius() { return 26; }
		public int below() { return 24; }
		public int above() { return 30; }

		public void build(Sink sk, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			// geode cathedral: smooth basalt outer, calcite middle, amethyst inner, hollow
			int R = 24;
			for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) for (int dy = -16; dy <= R + 4; dy++) {
				double d = Math.sqrt(dx * dx + dz * dz + (dy * 0.9) * (dy * 0.9));
				BlockState b = null;
				if (d <= R - 1) b = s(Blocks.AIR);
				else if (d <= R) b = r.nextInt(9) == 0 ? s(Blocks.BUDDING_AMETHYST) : s(Blocks.AMETHYST_BLOCK);
				else if (d <= R + 1) b = s(Blocks.CALCITE);
				else if (d <= R + 2) b = s(Blocks.SMOOTH_BASALT);
				if (b != null) sk.set(x + dx, y + dy, z + dz, b);
			}
			// the black lake below the floor
			for (int dx = -R + 2; dx <= R - 2; dx++) for (int dz = -R + 2; dz <= R - 2; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > R - 3) continue;
				for (int yy = y - 14; yy <= y - 6; yy++) sk.set(x + dx, yy, z + dz, s(Blocks.WATER));
				sk.set(x + dx, y - 15, z + dz, s(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK)));
			}
			// the floor: calcite and amethyst slabs over the lake (it breaks into islands in Lithara's last phase)
			for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 18) continue;
				sk.set(x + dx, y - 1, z + dz, Math.floorMod(dx * 3 + dz * 5, 7) == 0 ? s(Blocks.AMETHYST_BLOCK) : s(Blocks.CALCITE));
			}
			// 8 resonating crystal pillars in a ring (r = 14), one note each
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int px = x + (int) Math.round(Math.cos(a) * 14), pz = z + (int) Math.round(Math.sin(a) * 14);
				for (int yy = y; yy < y + 7; yy++) sk.set(px, yy, pz, s(HushBlocks.CHOIR_PILLAR).setValue(HushBlocks.ChoirPillarBlock.NOTE, i));
				sk.set(px, y + 7, pz, s(Blocks.AMETHYST_CLUSTER));
			}
			// central dais
			sk.disc(x, y - 1, z, 3.5, s(Blocks.POLISHED_DEEPSLATE));
			sk.set(x, y - 1, z, s(Blocks.CRYING_OBSIDIAN));
			// end-rod lights in the vault
			for (int i = 0; i < 10; i++) {
				double a = i * Math.PI / 5;
				sk.set(x + (int) Math.round(Math.cos(a) * 9), y + 20, z + (int) Math.round(Math.sin(a) * 9), s(Blocks.END_ROD));
			}
			// THE EXIT: deep below the lake, a sealed chamber with the Hush's only rift home (reach it by diving the black lake)
			sk.fill(x - 3, y - 22, z - 3, x + 3, y - 16, z + 3, s(Blocks.REINFORCED_DEEPSLATE));
			sk.fill(x - 2, y - 21, z - 2, x + 2, y - 17, z + 2, s(Blocks.AIR));
			sk.fill(x - 1, y - 16, z - 1, x + 1, y - 15, z + 1, s(Blocks.WATER)); // shaft from the lake bed
			sk.set(x, y - 21, z, s(Blocks.CRYING_OBSIDIAN));
			sk.set(x, y - 20, z, s(StubBlocks.RETURN_RIFT));
			sk.set(x, y - 19, z, s(StubBlocks.RETURN_RIFT));
			sk.set(x + 2, y - 21, z + 2, s(Blocks.SOUL_LANTERN));
			sk.set(x - 2, y - 21, z - 2, s(Blocks.SOUL_LANTERN));
			// entrance tunnel (north)
			for (int t = R - 2; t <= R + 8; t++) sk.fill(x - 1, y, z - t, x + 1, y + 3, z - t, s(Blocks.AIR));
			// a cantor's reliquary in the entrance tunnel (outside the arena)
			sk.set(x, y, z - (R + 5), s(Blocks.CHEST));
			sk.loot(new BlockPos(x, y, z - (R + 5)), GEODE_LOOT, r.nextLong());
		}
	};

	public static final Blueprint SILENT_MONASTERY = new Blueprint() {
		public int radius() { return 8; }
		public int below() { return 2; }
		public int above() { return 9; }

		public void build(Sink sk, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			sk.fill(x - 7, y - 1, z - 7, x + 7, y + 7, z + 7, s(HushBlocks.HUSHSTONE_BRICKS));
			sk.fill(x - 6, y, z - 6, x + 6, y + 6, z + 6, s(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK)));
			sk.fill(x - 5, y, z - 5, x + 5, y + 5, z + 5, s(Blocks.AIR));
			sk.fill(x - 5, y, z - 5, x + 5, y, z + 5, s(HushBlocks.HUSHWEAVE_CARPET));
			sk.fill(x - 5, y - 1, z - 5, x + 5, y - 1, z + 5, s(HushBlocks.HUSHWEAVE));
			sk.fill(x - 1, y, z + 6, x + 1, y + 2, z + 7, s(Blocks.AIR)); // doorway
			sk.fill(x - 1, y - 1, z + 6, x + 1, y - 1, z + 7, s(HushBlocks.HUSHWEAVE));
			for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
				sk.set(x + c[0], y + 5, z + c[1], s(Blocks.SOUL_LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
			}
			sk.set(x - 4, y, z - 5, s(Blocks.LECTERN));
			sk.set(x + 4, y, z - 5, s(Blocks.BARREL));
			sk.loot(new BlockPos(x + 4, y, z - 5), MONASTERY_LOOT, r.nextLong());
			sk.set(x, y, z - 5, s(Blocks.DYED_CANDLE.pick(net.minecraft.world.item.DyeColor.WHITE)).setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
			sk.spawn(HushEntities.MUTE_ARCHIVIST, new BlockPos(x, y + 1, z - 2));
		}
	};

	public static final Blueprint HANGING_UNDERCITY = new Blueprint() {
		public int radius() { return 20; }
		public int below() { return 30; }
		public int above() { return 6; }

		public void build(Sink sk, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			// a slab of "ceiling" the city hangs from
			for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
				if (dx * dx + dz * dz > 400) continue;
				sk.fill(x + dx, y + 1, z + dz, x + dx, y + 5, z + dz, s(Blocks.DEEPSLATE));
				sk.set(x + dx, y, z + dz, s(Blocks.SCULK_VEIN).setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.UP), true));
			}
			// upside-down towers
			for (int i = 0; i < 7; i++) {
				double a = i * 2 * Math.PI / 7 + r.nextDouble() * 0.4;
				double rad = i == 0 ? 0 : 8 + r.nextInt(8);
				int tx = x + (int) Math.round(Math.cos(a) * rad), tz = z + (int) Math.round(Math.sin(a) * rad);
				int h = 10 + r.nextInt(16);
				int w = 2 + r.nextInt(2);
				for (int dy = 0; dy < h; dy++) {
					int ww = dy > h - 4 ? Math.max(0, w - (dy - (h - 4))) : w;
					for (int dx = -ww; dx <= ww; dx++) for (int dz = -ww; dz <= ww; dz++) {
						boolean edge = Math.abs(dx) == ww || Math.abs(dz) == ww;
						BlockState b = edge ? ((dy % 5 == 2 && (dx == 0 || dz == 0)) ? s(Blocks.SCULK_SENSOR) : s(Blocks.DEEPSLATE_BRICKS)) : s(Blocks.AIR);
						if (dy % 6 == 0) b = s(Blocks.DEEPSLATE_TILES);
						sk.set(tx + dx, y - dy, tz + dz, b);
					}
				}
				// a chest in each tower's lowest room
				int cy = y - h + 5;
				sk.set(tx, cy, tz, s(Blocks.CHEST));
				sk.loot(new BlockPos(tx, cy, tz), UNDERCITY_LOOT, r.nextLong());
				sk.set(tx, cy + 1, tz, s(Blocks.SOUL_LANTERN));
				if (i == 0) {
					// THE ANCIENTS' DOOR (BALANCE.md): one-way rift home in the deepest vault of the central tower
					sk.fill(tx - 1, cy - 1, tz - 1, tx + 1, cy - 1, tz + 1, s(Blocks.REINFORCED_DEEPSLATE));
					sk.set(tx + 1, cy, tz, s(StubBlocks.RETURN_RIFT));
					sk.set(tx + 1, cy + 1, tz, s(StubBlocks.RETURN_RIFT));
				}
			}
		}
	};

	static void init() {
		BuiltStructure.BLUEPRINTS.put("listening_engine", LISTENING_ENGINE);
		BuiltStructure.BLUEPRINTS.put("choir_geode", CHOIR_GEODE);
		BuiltStructure.BLUEPRINTS.put("silent_monastery", SILENT_MONASTERY);
		BuiltStructure.BLUEPRINTS.put("hanging_undercity", HANGING_UNDERCITY);
	}
}
