package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.world.Blueprint;
import dev.elsewhere.greenhaze_hush.world.BuiltStructure;
import dev.elsewhere.greenhaze_hush.world.Sink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/** polish-structures: the Greenhaze Munchie Market (a food bazaar with a Budtender) and the Fern Terrace Farms (terraced gardens). */
public final class GreenhazeMarket {
	private GreenhazeMarket() {}

	public static final ResourceKey<LootTable> MARKET_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/munchie_market"));
	public static final ResourceKey<LootTable> FARM_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/fern_terrace_farms"));

	private static BlockState wool(DyeColor c) { return Blocks.WOOL.pick(c).defaultBlockState(); }

	/** A plaza ringed with four striped stalls around a campfire; two stocked chests and a Budtender at the largest stall. */
	public static final Blueprint MUNCHIE_MARKET = new Blueprint() {
		public int radius() { return 13; }
		public int below() { return 6; }
		public int above() { return 8; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			BlockState bricks = Blocks.MUD_BRICKS.defaultBlockState(), packed = Blocks.PACKED_MUD.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState();
			s.foundation(x, y, z, 12, packed, 6);
			s.clearAbove(x, y, z, 12, 8);
			// plaza floor: mud bricks in a checker, moss at the rim
			for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 11.5) continue;
				s.set(x + dx, y - 1, z + dz, d > 10 ? moss : ((dx + dz) % 2 == 0 ? bricks : packed));
			}
			// central fire pit with benches of hay
			s.fill(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, Blocks.STONE_BRICKS.defaultBlockState());
			s.set(x, y, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
			for (Direction d : Direction.Plane.HORIZONTAL) s.set(x + d.getStepX() * 3, y, z + d.getStepZ() * 3, Blocks.HAY_BLOCK.defaultBlockState());
			// four stalls at the diagonals, each a different colour: posts, striped awning, counter, food on display
			DyeColor[][] colours = {{DyeColor.LIME, DyeColor.WHITE}, {DyeColor.ORANGE, DyeColor.WHITE}, {DyeColor.PINK, DyeColor.WHITE}, {DyeColor.YELLOW, DyeColor.WHITE}};
			int[][] at = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
			for (int i = 0; i < 4; i++) {
				int sx = x + at[i][0], sz = z + at[i][1];
				for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
					s.set(sx + dx, y + 3, sz + dz, wool(colours[i][(dx + dz + 4) % 2]));
				}
				for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int h = 0; h <= 2; h++) s.set(sx + c[0], y + h, sz + c[1], Blocks.BAMBOO_FENCE.defaultBlockState());
				// counter faces the fire; display shelves behind it
				int fx = Integer.signum(-at[i][0]), fz = Integer.signum(-at[i][1]);
				s.fill(sx + fx * 2 - (fz == 0 ? 0 : 1), y, sz + fz * 2, sx + fx * 2 + (fz == 0 ? 0 : 1), y, sz + fz * 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
				s.set(sx, y, sz, i % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.CAULDRON.defaultBlockState());
				s.set(sx - fx, y, sz - fz, Blocks.HAY_BLOCK.defaultBlockState());
				s.set(sx, y + 2, sz, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
				s.set(sx + fx * 2, y + 1, sz + fz * 2 - (fz == 0 ? 0 : 0), i % 2 == 0 ? Blocks.CAKE.defaultBlockState() : Blocks.POTTED_FERN.defaultBlockState());
			}
			// two stocked chests behind the stalls
			s.set(x - 5, y, z - 10, Blocks.CHEST.defaultBlockState());
			s.loot(new BlockPos(x - 5, y, z - 10), MARKET_LOOT, r.nextLong());
			s.set(x + 5, y, z + 10, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
			s.loot(new BlockPos(x + 5, y, z + 10), MARKET_LOOT, r.nextLong());
			// the Budtender's counter (north side) and a fern border
			s.spawn(GreenhazeEntities.BUDTENDER, new BlockPos(x, y, z - 5));
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				int fx = x + (int) Math.round(Math.cos(a) * 10.5), fz = z + (int) Math.round(Math.sin(a) * 10.5);
				if (s.get(new BlockPos(fx, y, fz)).isAir()) s.set(fx, y, fz, Blocks.FERN.defaultBlockState());
			}
		}
	};

	/** Three stepped terraces of farmland fed by water channels, with ferns, munchfruit, a scarecrow and a tool chest. */
	public static final Blueprint FERN_TERRACE_FARMS = new Blueprint() {
		public int radius() { return 14; }
		public int below() { return 8; }
		public int above() { return 6; }

		public void build(Sink s, BlockPos o, RandomSource r) {
			int x = o.getX(), y = o.getY(), z = o.getZ();
			BlockState packed = Blocks.PACKED_MUD.defaultBlockState(), bricks = Blocks.MUD_BRICKS.defaultBlockState();
			s.foundation(x, y, z, 14, packed, 8);
			s.clearAbove(x, y, z, 14, 6);
			// three half-ring terraces stepping up toward the north: tier t spans |dz| bands
			CropBlock[] crops = {(CropBlock) Blocks.WHEAT, (CropBlock) Blocks.CARROTS, (CropBlock) Blocks.POTATOES};
			for (int tier = 0; tier < 3; tier++) {
				int ty = y - 1 + tier;
				int z0 = z + 8 - tier * 5;
				for (int dx = -13; dx <= 13; dx++) for (int dz = 0; dz < 5; dz++) {
					int px = x + dx, pz = z0 - dz;
					double d = Math.sqrt(dx * dx + (pz - z) * (pz - z));
					if (d > 13.5) continue;
					for (int yy = y - 2; yy < ty; yy++) s.set(px, yy, pz, packed);
					s.set(px, ty, pz, bricks);
					s.fill(px, ty + 1, pz, px, ty + 4, pz, Blocks.AIR.defaultBlockState());
					boolean channel = dz == 2 && dx % 5 == 0;
					boolean wall = dz == 4;
					if (wall) { s.set(px, ty, pz, bricks); s.set(px, ty + 1, pz, dx % 3 == 0 ? Blocks.LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState()); continue; }
					if (channel) { s.set(px, ty, pz, Blocks.WATER.defaultBlockState()); continue; }
					int band = dz;
					if (Math.abs(dx) <= 1) { s.set(px, ty, pz, Blocks.GRAVEL.defaultBlockState()); continue; } // the stair path
					if (band == 0 && r.nextInt(3) == 0) { s.set(px, ty + 1, pz, GreenhazeBlocks.MUNCHFRUIT_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3)); s.set(px, ty, pz, Blocks.GRASS_BLOCK.defaultBlockState()); continue; }
					if (band == 1 && r.nextInt(4) == 0) { s.set(px, ty, pz, Blocks.GRASS_BLOCK.defaultBlockState()); s.set(px, ty + 1, pz, Blocks.LARGE_FERN.defaultBlockState()
						.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
						s.set(px, ty + 2, pz, Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)); continue; }
					s.set(px, ty, pz, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
					s.set(px, ty + 1, pz, crops[tier].getStateForAge(crops[tier].getMaxAge()));
				}
				// a lantern post at each step edge
				s.set(x - 12, ty + 1, z0 - 4, Blocks.LANTERN.defaultBlockState());
			}
			// stair path between the tiers
			for (int tier = 0; tier < 2; tier++) {
				int z0 = z + 8 - tier * 5;
				for (int dx = -1; dx <= 1; dx++) s.set(x + dx, y + tier, z0 - 5, Blocks.MUD_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
			}
			// scarecrow and the farm chest on the top terrace
			int topY = y + 2, topZ = z + 8 - 10;
			s.set(x + 6, topY + 1, topZ - 1, Blocks.OAK_FENCE.defaultBlockState());
			s.set(x + 6, topY + 2, topZ - 1, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x + 6, topY + 3, topZ - 1, Blocks.CARVED_PUMPKIN.defaultBlockState());
			s.set(x + 5, topY + 2, topZ - 1, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x + 7, topY + 2, topZ - 1, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x - 5, topY + 1, topZ - 1, Blocks.CHEST.defaultBlockState());
			s.loot(new BlockPos(x - 5, topY + 1, topZ - 1), FARM_LOOT, r.nextLong());
			s.set(x - 4, topY + 1, topZ - 1, Blocks.COMPOSTER.defaultBlockState());
		}
	};

	public static void init() {
		BuiltStructure.BLUEPRINTS.put("munchie_market", MUNCHIE_MARKET);
		BuiltStructure.BLUEPRINTS.put("fern_terrace_farms", FERN_TERRACE_FARMS);
	}
}
