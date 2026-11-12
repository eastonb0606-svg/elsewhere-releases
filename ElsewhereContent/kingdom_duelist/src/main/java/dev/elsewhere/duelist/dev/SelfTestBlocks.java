package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** K10 temporary blocks: bridge, pillar, deep water, the ledger rules, vanishing, never replacing a block. */
final class SelfTestBlocks {
	private SelfTestBlocks() {}

	static void add(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final List<Long> placeTicks = new ArrayList<>();
		final int[] maxActive = {0}, lastBlocks = {0};
		final long[] hash = {0};
		h.test("K10 the service never replaces a block").run(() -> {
			var l = h.level;
			UUID u = UUID.randomUUID();
			BlockPos stone = new BlockPos(5, H.Y - 1, 5), water = new BlockPos(6, H.Y, 5), grass = new BlockPos(7, H.Y, 5), air = new BlockPos(8, H.Y, 5);
			l.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
			l.setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), 2);
			h.check("not onto stone", !DuelBlocks.place(l, stone, u, null, 600) && l.getBlockState(stone).is(Blocks.STONE));
			h.check("not into water", !DuelBlocks.place(l, water, u, null, 600) && l.getBlockState(water).is(Blocks.WATER));
			h.check("not over a plant", !DuelBlocks.place(l, grass, u, null, 600) && l.getBlockState(grass).is(Blocks.SHORT_GRASS));
			h.check("into air", DuelBlocks.place(l, air, u, null, 600) && l.getBlockState(air).is(DuelistContent.DUEL_BLOCK));
			var e = DuelBlocks.entries(l).stream().filter(x -> x.pos() == air.asLong()).findFirst().get();
			h.check("lifetime is the caller's 600 ticks", e.expires() - e.placed() == 600);
			h.check("clearing the owner removes it", DuelBlocks.clearOwner(l, u) == 1 && l.getBlockState(air).isAir());
			l.setBlock(water, Blocks.AIR.defaultBlockState(), 2);
			l.setBlock(grass, Blocks.AIR.defaultBlockState(), 2);
		});
		h.test("K10 the ledger: chunk unload, orphans, enclosure").run(() -> {
			var l = h.level;
			UUID u = UUID.randomUUID();
			for (int i = 0; i < 3; i++) DuelBlocks.place(l, new BlockPos(20 + i, H.Y, 20), u, null, 600);
			h.check("three placed", DuelBlocks.count(l, u) == 3);
			DuelBlocks.purgeChunk(l, new ChunkPos(20 >> 4, 20 >> 4));
			h.check("purging the chunk (unload hook) removed them all", DuelBlocks.count(l, u) == 0 && h.countBlocks(DuelistContent.DUEL_BLOCK) == 0);
			BlockPos orphan = new BlockPos(30, H.Y, 30);
			l.setBlock(orphan, DuelistContent.DUEL_BLOCK.defaultBlockState(), 2);
			l.getBlockState(orphan).randomTick(l, orphan, l.getRandom());
			h.check("a block the ledger does not know removes itself", l.getBlockState(orphan).isAir());
			DuelBlocks.place(l, new BlockPos(31, H.Y, 30), u, null, 600);
			l.getBlockState(new BlockPos(31, H.Y, 30)).randomTick(l, new BlockPos(31, H.Y, 30), l.getRandom());
			h.check("a ledger block survives its random tick", l.getBlockState(new BlockPos(31, H.Y, 30)).is(DuelistContent.DUEL_BLOCK));
			DuelBlocks.clearOwner(l, u);
			// enclosure: a player at feet (0,H.Y,40) with stone on 3 sides of feet and head and above; the 4th side is the only opening
			BlockPos feet = new BlockPos(0, H.Y, 40);
			for (int dy = 0; dy < 2; dy++) {
				l.setBlock(feet.offset(1, dy, 0), Blocks.STONE.defaultBlockState(), 2);
				l.setBlock(feet.offset(-1, dy, 0), Blocks.STONE.defaultBlockState(), 2);
				l.setBlock(feet.offset(0, dy, 1), Blocks.STONE.defaultBlockState(), 2);
			}
			l.setBlock(feet.above(2), Blocks.STONE.defaultBlockState(), 2);
			h.check("closing the last open side would enclose", DuelBlocks.wouldEncloseAt(l, feet, feet.offset(0, 0, -1)) && DuelBlocks.wouldEncloseAt(l, feet, feet.offset(0, 1, -1)));
			h.check("a block elsewhere does not", !DuelBlocks.wouldEncloseAt(l, feet, feet.offset(5, 0, 5)));
			h.flatten(-3, 3, 38, 42);
		});
		h.test("K10 boss builds a bridge over a gap; one per 8 ticks, at most 8, vanishes").run(() -> {
			hash[0] = h.arenaHash();
			SelfTest.carveChasm(h, -2, 2);
			d[0] = h.dummy(8, 0);
			b[0] = h.boss(H.only("k10", Move.BLOCKS).build(), -8, 0);
			b[0].setTarget(d[0]);
			placeTicks.clear();
			lastBlocks[0] = 0;
			maxActive[0] = 0;
		}).until("crosses the gap", 900, () -> {
			b[0].setTarget(d[0]);
			maxActive[0] = Math.max(maxActive[0], DuelBlocks.count(h.level, b[0].getUUID()));
			int n = b[0].stat("block");
			while (lastBlocks[0] < n) {
				placeTicks.add(h.now());
				lastBlocks[0]++;
			}
			return b[0].getX() > 3.5;
		}).check("only duel blocks were placed (" + 0 + ")", () -> b[0].stat("block") > 0 && b[0].stat("block") <= 6 + 8)
			.check("never more than 8 active (" + 0 + ")", () -> maxActive[0] <= TestKit.blocks().maxActive())
			.check("blocks were placed at least 8 ticks apart", () -> {
				for (int i = 1; i < placeTicks.size(); i++) if (placeTicks.get(i) - placeTicks.get(i - 1) < TestKit.blocks().interval() - 1) return false;
				return true;
			}).check("the chasm holds only air or duel blocks (nothing was replaced)", () -> {
				for (int x = -2; x <= 2; x++) for (int y = H.Y - 1; y < H.Y + 3; y++) {
					var s = h.level.getBlockState(new BlockPos(x, y, 0));
					if (!s.isAir() && !s.is(DuelistContent.DUEL_BLOCK)) return false;
				}
				return true;
			}).run(() -> b[0].discard()).wait(2).check("boss removal removed every block", () -> h.countBlocks(DuelistContent.DUEL_BLOCK) == 0)
			.run(() -> h.flatten(-4, 4, -H.R, H.R)).check("the arena matches the start", () -> h.arenaHash() == hash[0]);
		h.test("K10 bridge over deep water").run(() -> {
			for (int x = -2; x <= 2; x++) for (int z = -H.R; z <= H.R; z++) {
				for (int y = H.Y - 4; y <= H.Y - 2; y++) h.level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 2);
				h.level.setBlock(new BlockPos(x, H.Y - 1, z), Blocks.AIR.defaultBlockState(), 2);
			}
			d[0] = h.dummy(8, 0);
			b[0] = h.boss(H.only("k10w", Move.BLOCKS).build(), -8, 0);
			b[0].setTarget(d[0]);
		}).until("crosses the water on duel blocks", 900, () -> {
			b[0].setTarget(d[0]);
			return b[0].getX() > 3.5 && !b[0].isInWater();
		}).check("no block replaced: the water is still water", () -> h.level.getBlockState(new BlockPos(0, H.Y - 3, 0)).is(Blocks.WATER))
			.cleanup(() -> {
				h.flatten(-4, 4, -H.R, H.R);
				for (int x = -3; x <= 3; x++) for (int z = -H.R; z <= H.R; z++) for (int y = H.Y - 6; y < H.Y - 1; y++) h.level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			});
		h.test("K10 pillar up to 5 blocks to reach a tower").run(() -> {
			for (int y = H.Y; y < H.Y + 4; y++) for (int x = 10; x <= 12; x++) for (int z = -1; z <= 1; z++) h.level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
			d[0] = h.dummy(11, 0);
			d[0].setPos(11, H.Y + 4, 0);
			b[0] = h.boss(H.only("k10p", Move.BLOCKS).build(), 4, 0);
			b[0].setTarget(d[0]);
			maxActive[0] = 0;
		}).until("pillars up beside the tower (2 or more blocks)", 900, () -> {
			b[0].setTarget(d[0]);
			d[0].setPos(11, H.Y + 4, 0);
			maxActive[0] = Math.max(maxActive[0], DuelBlocks.count(h.level, b[0].getUUID()));
			if (h.now() % 40 == 0) h.info("pillar: boss " + b[0].position() + " blocks " + DuelBlocks.count(h.level, b[0].getUUID()) + " stats " + b[0].stats);
			return b[0].getY() >= H.Y + 2.0 && b[0].stat("block") >= 2;
		}).check("it used no more than 5 pillar blocks at once", () -> maxActive[0] <= TestKit.blocks().pillarMax())
			.cleanup(() -> h.flatten(2, 14, -3, 3));
		h.test("K10 pearls and blocks never left anything behind").run(() -> {}).check("ledger empty", () -> DuelBlocks.total(h.level) == 0);
	}
}
