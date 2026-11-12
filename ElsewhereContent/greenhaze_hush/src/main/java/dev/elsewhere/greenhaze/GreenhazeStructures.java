package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.Blueprint;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import dev.elsewhere.greenhaze_hush.world.Sink;
import dev.elsewhere.stub.greenhaze_hush.ReturnRift;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/** Code-built Greenhaze structures: The Great Hotbox, The Bonfire Terrace, Sesh Huts, Lucid Springs and its Return Rifts. */
public final class GreenhazeStructures {
	private GreenhazeStructures() {}

	public static final ResourceKey<LootTable> HOTBOX_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/great_hotbox"));
	public static final ResourceKey<LootTable> TERRACE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/bonfire_terrace"));
	public static final ResourceKey<LootTable> SPRING_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/lucid_spring"));
	public static final ResourceKey<LootTable> SESH_HUT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/sesh_hut"));

	public static final Blueprint GREAT_HOTBOX = new Blueprint() {
		public int radius() { return 18; }
		public int below() { return 8; }
		public int above() { return 18; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			int R = 15;
			BlockState mud = Blocks.MUD_BRICKS.defaultBlockState(), packed = Blocks.PACKED_MUD.defaultBlockState(),
				bamboo = Blocks.BAMBOO_BLOCK.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
			s.foundation(x, y, z, R + 1, packed, 8);
			s.disc(x, y - 1, z, R + 1, packed);
			s.clearAbove(x, y, z, R + 1, R + 2);
			// floor: mud bricks with a moss ring
			for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > R) continue;
				s.set(x + dx, y - 1, z + dz, d > 5 && d < 8 ? moss : (Math.floorMod(dx + dz, 4) == 0 ? Blocks.MUD_BRICKS.defaultBlockState() : packed));
			}
			// dome shell with bamboo ribs
			for (int dx = -R - 1; dx <= R + 1; dx++) for (int dz = -R - 1; dz <= R + 1; dz++) for (int dy = 0; dy <= R + 1; dy++) {
				double d = Math.sqrt(dx * dx + dz * dz + (dy * 1.15) * (dy * 1.15));
				if (d <= R + 1 && d > R) {
					boolean rib = Math.abs(dx) <= 0 || Math.abs(dz) <= 0 || Math.abs(Math.abs(dx) - Math.abs(dz)) <= 0;
					s.set(x + dx, y + dy, z + dz, rib ? bamboo : mud);
				}
			}
			// doorway (south)
			s.fill(x - 1, y, z + R - 1, x + 1, y + 3, z + R + 2, air);
			// 6 vent shutters in the wall at height 6, and their levers on the floor below
			for (int i = 0; i < 6; i++) {
				double a = Math.PI / 2 + Math.PI / 6 + i * Math.PI / 3; // skip the doorway
				int vx = x + (int) Math.round(Math.cos(a) * (R - 1.5));
				int vz = z + (int) Math.round(Math.sin(a) * (R - 1.5));
				int wx = x + (int) Math.round(Math.cos(a) * (R + 0.5));
				int wz = z + (int) Math.round(Math.sin(a) * (R + 0.5));
				for (int k = -1; k <= 1; k++) for (int dy = 6; dy <= 8; dy++) {
					int px = wx + (Math.abs(Math.sin(a)) > 0.5 ? k : 0), pz = wz + (Math.abs(Math.sin(a)) > 0.5 ? 0 : k);
					// cut through the shell, plug with a vent
					for (int t = -1; t <= 1; t++) s.set(px + (int) Math.round(Math.cos(a) * t), y + dy, pz + (int) Math.round(Math.sin(a) * t), air);
					s.set(px, y + dy, pz, GreenhazeBlocks.HOTBOX_VENT.defaultBlockState());
				}
				s.set(vx, y, vz, GreenhazeBlocks.VENT_LEVER.defaultBlockState());
				s.set(vx, y - 1, vz, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
			}
			// moss couches (moss block + moss carpet) between the levers
			for (int i = 0; i < 6; i++) {
				double a = Math.PI / 2 + i * Math.PI / 3;
				int cx = x + (int) Math.round(Math.cos(a) * 9), cz = z + (int) Math.round(Math.sin(a) * 9);
				if (i == 0) continue;
				s.set(cx, y, cz, moss);
				s.set(cx, y + 1, cz, Blocks.MOSS_CARPET.defaultBlockState());
				s.set(cx + 1, y, cz, moss);
			}
			// campfires (smoke) and shroomlights in the ceiling
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int cx = x + (int) Math.round(Math.cos(a) * 6), cz = z + (int) Math.round(Math.sin(a) * 6);
				s.set(cx, y - 1, cz, Blocks.HAY_BLOCK.defaultBlockState());
				s.set(cx, y, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
			}
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + 0.3;
				s.set(x + (int) Math.round(Math.cos(a) * 7), y + 12, z + (int) Math.round(Math.sin(a) * 7), Blocks.SHROOMLIGHT.defaultBlockState());
			}
			s.set(x, y + 15, z, Blocks.SHROOMLIGHT.defaultBlockState());
			// the doorman's stash outside the south door (outside the sealed arena, so a reset never touches it)
			s.foundation(x + 3, y, z + R + 3, 2, packed, 8);
			s.fill(x + 2, y - 1, z + R + 2, x + 4, y - 1, z + R + 4, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
			s.fill(x + 2, y, z + R + 2, x + 4, y + 2, z + R + 4, air);
			s.set(x + 3, y, z + R + 3, Blocks.CHEST.defaultBlockState());
			s.loot(new BlockPos(x + 3, y, z + R + 3), HOTBOX_LOOT, r.nextLong());
			s.set(x + 2, y, z + R + 3, Blocks.LANTERN.defaultBlockState());
			// centre altar
			s.fill(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
			s.set(x, y, z, GreenhazeBlocks.HOTBOX_ALTAR.defaultBlockState());
		}
	};

	public static final Blueprint BONFIRE_TERRACE = new Blueprint() {
		public int radius() { return 20; }
		public int below() { return 10; }
		public int above() { return 16; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			int R = 18;
			BlockState mud = Blocks.MUD_BRICKS.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
			s.foundation(x, y - 2, z, R + 1, Blocks.PACKED_MUD.defaultBlockState(), 10);
			s.clearAbove(x, y - 1, z, R + 1, 14);
			// three stepped rings: outer ring y-2, middle y-1, inner y
			for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > R) continue;
				int top = d > 13 ? y - 2 : d > 8 ? y - 1 : y;
				for (int yy = y - 3; yy < top; yy++) s.set(x + dx, yy, z + dz, mud);
				s.set(x + dx, top - 1, z + dz, (d > 12.4 && d < 13.6) || (d > 7.4 && d < 8.6) ? mud : (d > 8 ? grass : moss));
			}
			// giant everburning bonfire: a 5x5 pit of campfires around a magma heart, ringed with mud-brick walls
			s.fill(x - 3, y - 1, z - 3, x + 3, y - 1, z + 3, Blocks.MUD_BRICKS.defaultBlockState());
			for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) == 3 || Math.abs(dz) == 3) s.set(x + dx, y, z + dz, Blocks.MUD_BRICK_WALL.defaultBlockState());
				else s.set(x + dx, y, z + dz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
			}
			s.set(x, y, z, Blocks.MAGMA_BLOCK.defaultBlockState());
			s.set(x, y + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
			// snack altar in front of the bonfire (south)
			s.set(x, y, z + 5, GreenhazeBlocks.SNACK_ALTAR.defaultBlockState());
			// moss couches on the middle ring
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + 0.5;
				int cx = x + (int) Math.round(Math.cos(a) * 10.5), cz = z + (int) Math.round(Math.sin(a) * 10.5);
				s.fill(cx - 1, y - 1, cz, cx + 1, y - 1, cz, moss);
				s.fill(cx - 1, y, cz, cx + 1, y, cz, Blocks.MOSS_CARPET.defaultBlockState());
				s.set(cx, y, cz + 1, moss);
			}
			// four fruit trees on the outer ring, each with ripe munchfruit bushes
			for (int i = 0; i < 4; i++) {
				double a = i * Math.PI / 2 + Math.PI / 4;
				int tx = x + (int) Math.round(Math.cos(a) * 15.5), tz = z + (int) Math.round(Math.sin(a) * 15.5);
				fruitTree(s, tx, y - 2, tz);
			}
			// a stocked chest on the outer ring beside the first fruit tree
			s.set(x + 16, y - 2, z - 4, Blocks.CHEST.defaultBlockState());
			s.loot(new BlockPos(x + 16, y - 2, z - 4), TERRACE_LOOT, r.nextLong());
			// torches on the step edges
			for (int i = 0; i < 12; i++) {
				double a = i * Math.PI / 6;
				s.set(x + (int) Math.round(Math.cos(a) * 13), y - 1, z + (int) Math.round(Math.sin(a) * 13), Blocks.LANTERN.defaultBlockState());
			}
		}
	};

	public static void fruitTree(Sink s, int x, int y, int z) {
		for (int i = 0; i < 4; i++) s.set(x, y + i, z, Blocks.JUNGLE_LOG.defaultBlockState());
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
			if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 4) > 3 || (dx == 0 && dz == 0 && dy < 4)) continue;
			s.set(x + dx, y + dy, z + dz, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			s.set(x + d.getStepX(), y, z + d.getStepZ(), GreenhazeBlocks.MUNCHFRUIT_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
		}
	}

	public static final Blueprint SESH_HUT = new Blueprint() {
		public int radius() { return 5; }
		public int below() { return 6; }
		public int above() { return 9; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY() + 3, z = o.getZ();
			BlockState planks = GreenhazeBlocks.KUSHWOOD_PLANKS.defaultBlockState(), log = GreenhazeBlocks.KUSHWOOD_LOG.defaultBlockState(),
				bamboo = Blocks.BAMBOO_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
			// stilts
			for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
				for (int yy = y - 1; yy >= o.getY() - 6; yy--) {
					BlockState here = s.get(new BlockPos(x + c[0], yy, z + c[1]));
					if (yy < o.getY() && !here.isAir() && here.getFluidState().isEmpty()) break;
					s.set(x + c[0], yy, z + c[1], log);
				}
			}
			s.fill(x - 3, y, z - 3, x + 3, y, z + 3, planks);
			s.fill(x - 3, y + 1, z - 3, x + 3, y + 3, z + 3, bamboo);
			s.fill(x - 2, y + 1, z - 2, x + 2, y + 3, z + 2, air);
			s.fill(x - 1, y + 1, z + 3, x - 1, y + 2, z + 3, air); // door gap
			s.set(x + 3, y + 2, z, Blocks.GLASS_PANE.defaultBlockState());
			s.set(x - 3, y + 2, z, Blocks.GLASS_PANE.defaultBlockState());
			// thatch roof of kushwood leaves
			for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
				int h = 4 - Math.max(Math.abs(dx), Math.abs(dz)) / 2;
				s.set(x + dx, y + 3 + h, z + dz, GreenhazeBlocks.KUSHWOOD_LEAVES.defaultBlockState()
					.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
			}
			// ladder down
			for (int yy = y; yy >= o.getY(); yy--) s.set(x - 1, yy, z + 4, Blocks.LADDER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
			s.set(x + 2, y + 1, z - 2, Blocks.CHEST.defaultBlockState());
			s.loot(new BlockPos(x + 2, y + 1, z - 2), SESH_HUT_LOOT, r.nextLong());
			s.set(x - 2, y + 1, z - 2, Blocks.LANTERN.defaultBlockState());
			s.set(x + 2, y + 1, z + 2, Blocks.CAULDRON.defaultBlockState());
			s.spawn(GreenhazeEntities.BUDTENDER, new BlockPos(x, y + 1, z));
		}
	};

	public static final Blueprint LUCID_SPRING = new Blueprint() {
		public int radius() { return 6; }
		public int below() { return 4; }
		public int above() { return 4; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 6) continue;
				s.set(x + dx, y + 1, z + dz, Blocks.AIR.defaultBlockState());
				s.set(x + dx, y + 2, z + dz, Blocks.AIR.defaultBlockState());
				if (d > 4.5) {
					s.set(x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
					s.set(x + dx, y - 1, z + dz, r.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
				} else {
					s.set(x + dx, y - 3, z + dz, Blocks.SEA_LANTERN.defaultBlockState());
					s.set(x + dx, y - 2, z + dz, Blocks.WATER.defaultBlockState());
					s.set(x + dx, y - 1, z + dz, Blocks.WATER.defaultBlockState());
					s.set(x + dx, y, z + dz, Blocks.AIR.defaultBlockState());
					if (r.nextInt(5) == 0) s.set(x + dx, y, z + dz, GreenhazeBlocks.LUCID_LILY.defaultBlockState());
				}
			}
			// an offering chest at the bottom of the pool (swim down for it)
			s.set(x, y - 2, z, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED, true));
			s.loot(new BlockPos(x, y - 2, z), SPRING_LOOT, r.nextLong());
		}
	};

	public static void init() {
		GreenhazeMarket.init();
		BuiltStructure.BLUEPRINTS.put("great_hotbox", GREAT_HOTBOX);
		BuiltStructure.BLUEPRINTS.put("bonfire_terrace", BONFIRE_TERRACE);
		BuiltStructure.BLUEPRINTS.put("sesh_hut", SESH_HUT);
		BuiltStructure.BLUEPRINTS.put("lucid_spring", LUCID_SPRING);
		BuiltStructure.BLUEPRINTS.put("greenhaze_return_rift", ReturnRift.blueprint(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIME).defaultBlockState(),
			Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState()));
	}
}
