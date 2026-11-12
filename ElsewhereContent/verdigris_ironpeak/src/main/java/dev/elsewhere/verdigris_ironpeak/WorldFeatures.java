package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Small structures as blueprints: Clockwork Rig, Engineer's Atoll hut, Clan Hold, Climber's Cairn. */
public final class WorldFeatures {
	private WorldFeatures() {}

	public static void init() {
		Blueprint.register("clockwork_rig", new Blueprint() {
			public int radius() {
				return 8;
			}

			public int height() {
				return 12;
			}

			public int depth() {
				return 100;
			}

			public void build(BlockSink s, RandomSource r) {
				// Four copper-grate legs from the sea floor to a deck 6 above the surface.
				int deck = 6;
				for (int[] l : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
					for (int y = deck - 1; y > -100; y--) {
						if (y < -2 && s.solid(l[0], y, l[1])) break;
						s.set(l[0], y, l[1], VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState());
					}
				}
				for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
					s.set(x, deck, z, Math.abs(x) == 6 || Math.abs(z) == 6 ? VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE.defaultBlockState() : VerdigrisBlocks.VERDIGRIS_GRATING.defaultBlockState());
					if (Math.abs(x) == 6 || Math.abs(z) == 6) s.set(x, deck + 1, z, VerdigrisBlocks.VERDIGRIS_PLATE_WALL.defaultBlockState());
				}
				for (int[] b : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) s.set(b[0], deck + 2, b[1], Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, true));
				s.chest(0, deck + 1, 0, Direction.SOUTH, BlockSink.loot("chests/clockwork_rig"));
				s.set(2, deck + 1, 0, VerdigrisBlocks.STORM_COIL.defaultBlockState());
				s.set(-2, deck + 1, 2, VerdigrisBlocks.BARNACLE_CLUSTER.defaultBlockState());
				for (int y = deck + 1; y <= deck + 5; y++) s.set(0, y, -4, VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState());
				s.set(0, deck + 6, -4, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
			}
		});
		Blueprint.register("engineers_atoll", new Blueprint() {
			public int radius() {
				return 5;
			}

			public int height() {
				return 7;
			}

			public void build(BlockSink s, RandomSource r) {
				BlockState planks = VerdigrisBlocks.KELPWOOD_PLANKS.defaultBlockState();
				for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
					s.foundation(x, -1, z, Blocks.TUFF.defaultBlockState());
					s.set(x, 0, z, planks);
					boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
					for (int y = 1; y <= 3; y++) s.set(x, y, z, edge && !(z == 3 && x == 0 && y <= 2) ? (y == 2 && (x == 0 || z == 0) ? Blocks.GLASS_PANE.defaultBlockState() : planks) : Blocks.AIR.defaultBlockState());
					s.set(x, 4, z, VerdigrisBlocks.VERDIGRIS_PLATE_SLAB.defaultBlockState());
				}
				s.set(0, 3, 0, Blocks.COPPER_LANTERN.weathering().unaffected().defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
				s.chest(2, 1, -2, Direction.WEST, BlockSink.loot("chests/engineers_atoll"));
			}
		});
		Blueprint.register("clan_hold", new Blueprint() {
			public int radius() {
				return 6;
			}

			public int height() {
				return 8;
			}

			public void build(BlockSink s, RandomSource r) {
				for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
					s.foundation(x, -1, z, Blocks.COBBLESTONE.defaultBlockState());
					s.set(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
					boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
					boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
					for (int y = 1; y <= 4; y++) {
						BlockState w = corner ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : y == 1 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
						s.set(x, y, z, edge && !(z == 5 && Math.abs(x) <= 1 && y <= 2) ? w : Blocks.AIR.defaultBlockState());
					}
					s.set(x, 5, z, Blocks.SPRUCE_SLAB.defaultBlockState());
				}
				s.set(0, 1, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
				s.set(0, 5, 0, Blocks.AIR.defaultBlockState());
				s.set(-3, 1, -3, IronpeakBlocks.HEARTHSTONE.defaultBlockState());
				s.chest(3, 1, -3, Direction.SOUTH, BlockSink.loot("chests/clan_hold"));
				s.set(-2, 6, 6, IronpeakBlocks.PRAYER_FLAGS.defaultBlockState());
			}
		});
		Blueprint.register("tidelock_vault", new Blueprint() {
			public int radius() {
				return 5;
			}

			public int height() {
				return 7;
			}

			public int depth() {
				return 12;
			}

			public void build(BlockSink s, RandomSource r) {
				// A sunken plate chamber on the sea floor; the vault inside opens with a Tide Key, once per player.
				BlockState plate = VerdigrisBlocks.VERDIGRIS_PLATE.defaultBlockState();
				BlockState riveted = VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE.defaultBlockState();
				for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
					s.foundation(x, -1, z, Blocks.TUFF.defaultBlockState());
					s.set(x, 0, z, riveted);
					boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
					for (int y = 1; y <= 4; y++) s.set(x, y, z, edge && !(z == 4 && x == 0 && y <= 2) ? (y == 3 && (x + z) % 2 == 0 ? VerdigrisBlocks.VERDIGRIS_GRATING.defaultBlockState() : plate) : Blocks.WATER.defaultBlockState());
					s.set(x, 5, z, VerdigrisBlocks.VERDIGRIS_PLATE_SLAB.defaultBlockState());
				}
				for (int[] b : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) s.set(b[0], 1, b[1], VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState());
				s.vault(0, 1, -3, Direction.SOUTH, VerdigrisItems.TIDE_KEY, BlockSink.loot("chests/tidelock_vault"));
				s.set(0, 4, -3, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, true));
				s.set(-2, 1, -3, VerdigrisBlocks.BARNACLE_CLUSTER.defaultBlockState());
				s.set(2, 1, -3, VerdigrisBlocks.STORM_COIL.defaultBlockState());
			}
		});
		Blueprint.register("climbers_cairn", new Blueprint() {
			public int radius() {
				return 2;
			}

			public int height() {
				return 5;
			}

			public void build(BlockSink s, RandomSource r) {
				for (int y = 0; y <= 3; y++) {
					int w = 2 - y / 2;
					for (int x = -w; x <= w; x++) for (int z = -w; z <= w; z++) if (r.nextInt(4) > 0 || y == 0) s.set(x, y, z, r.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
				s.chest(0, 0, 0, Direction.NORTH, BlockSink.loot("chests/climbers_cairn"));
				s.set(0, 4, 0, IronpeakBlocks.PRAYER_FLAGS.defaultBlockState());
			}
		});
	}
}
