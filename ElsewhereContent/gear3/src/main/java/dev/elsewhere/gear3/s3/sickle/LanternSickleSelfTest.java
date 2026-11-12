package dev.elsewhere.gear3.s3.sickle;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lantern Sickle self-test block (gear3 self-test, after the per-mechanic blocks). Runs synchronously on the server thread in its own
 * corner of the forced test area, high in open sky so crops have light (x 20-36, y 279-286, z 20-36), and removes everything it
 * places or drops. Check names start with "Lantern Sickle:".
 */
public final class LanternSickleSelfTest {
	private LanternSickleSelfTest() {}

	static final String P = "Lantern Sickle: ";
	/** Farm: farmland y 280 (x/z 22..26), crops y 281; H is the crop the sickle is used on. */
	static final BlockPos H = new BlockPos(24, 281, 24);
	/** Nether wart on soul sand, and a dirt block to till. */
	static final BlockPos WART = new BlockPos(30, 281, 24), TILL = new BlockPos(34, 280, 24);
	/** Hedge: a 3x3 wall in the plane z = 32 around LC (x 23..25, y 281..283); the cutter stands north of it, looking south. */
	static final BlockPos LC = new BlockPos(24, 282, 32);
	static final AABB BOX = new AABB(20, 279, 20, 37, 287, 37);
	/** setBlock flags for building the arena: send to clients, no neighbour or shape updates (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE). */
	static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	static BlockState wheat(int age) {
		return ((CropBlock) Blocks.WHEAT).getStateForAge(age);
	}

	static int age(ServerLevel l, BlockPos q) {
		BlockState s = l.getBlockState(q);
		return s.getBlock() instanceof CropBlock c ? c.getAge(s) : s.getBlock() instanceof NetherWartBlock ? s.getValue(NetherWartBlock.AGE) : -1;
	}

	static void clear(ServerLevel l) {
		for (int x = (int) BOX.minX; x < BOX.maxX; x++) for (int z = (int) BOX.minZ; z < BOX.maxZ; z++) for (int y = (int) BOX.minY; y < BOX.maxY; y++)
			l.setBlock(new BlockPos(x, y, z), st(Blocks.AIR), QUIET);
		drops(l, null).forEach(Entity::discard);
	}

	/** Item entities in the arena (after a warm-up query: GOTCHAS, the first one after spawning can come back empty). */
	static List<ItemEntity> drops(ServerLevel l, Item only) {
		l.getEntitiesOfClass(ItemEntity.class, BOX.inflate(3));
		return l.getEntitiesOfClass(ItemEntity.class, BOX.inflate(3), e -> only == null || e.getItem().is(only));
	}

	static int count(List<ItemEntity> es) {
		int n = 0;
		for (ItemEntity e : es) n += e.getItem().getCount();
		return n;
	}

	/** Farmland x/z 22..26 at y 280; crops in the 3x3 around H: ripe wheat, ripe carrots at +1/+1, young wheat (age 3) at -1/-1; a ripe wheat outside at +2/0. */
	static void farm(ServerLevel l, boolean allYoung) {
		for (int x = 22; x <= 26; x++) for (int z = 22; z <= 26; z++) l.setBlock(new BlockPos(x, 280, z), st(Blocks.FARMLAND).setValue(FarmlandBlock.MOISTURE, 7), QUIET);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			BlockState s = allYoung || (dx == -1 && dz == -1) ? wheat(3) : (dx == 1 && dz == 1) ? ((CropBlock) Blocks.CARROTS).getStateForAge(7) : wheat(7);
			l.setBlock(H.offset(dx, 0, dz), s, QUIET);
		}
		l.setBlock(H.offset(2, 0, 0), wheat(7), QUIET);
	}

	static InteractionResult use(ServerLevel l, FakePlayer p, ItemStack tool, BlockPos at) {
		p.setItemInHand(InteractionHand.MAIN_HAND, tool);
		return tool.useOn(new UseOnContext(l, p, InteractionHand.MAIN_HAND, tool, new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false)));
	}

	/** The hedge: leaves (persistent, so nothing decays) or vines hung on a stone wall behind them; a stone in the top-right corner. */
	static void hedge(ServerLevel l, boolean vines) {
		for (int x = 22; x <= 26; x++) for (int y = 280; y <= 284; y++) for (int z = 31; z <= 34; z++) l.setBlock(new BlockPos(x, y, z), st(Blocks.AIR), QUIET);
		BlockState leaf = st(Blocks.OAK_LEAVES).setValue(LeavesBlock.PERSISTENT, true);
		BlockState vine = st(Blocks.VINE).setValue(VineBlock.SOUTH, true);
		for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
			BlockPos q = LC.offset(dx, dy, 0);
			if (vines) l.setBlock(q.south(), st(Blocks.STONE), QUIET);
			l.setBlock(q, vines ? vine : (dx == 1 && dy == 1 ? st(Blocks.STONE) : leaf), QUIET);
		}
		if (!vines) {
			l.setBlock(LC.south(), leaf, QUIET); // behind the centre: not in the plane
			l.setBlock(LC.offset(2, 0, 0), leaf, QUIET); // beside the 3x3: outside it
		}
	}

	static int hedgeLeft(ServerLevel l) {
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (l.getBlockState(LC.offset(dx, dy, 0)).is(LanternSickleItem.CUTS)) n++;
		return n;
	}

	/** Stands the FakePlayer north of the hedge, eyes level with LC, looking south at it. */
	static void standAtHedge(FakePlayer p, ItemStack tool) {
		double feet = LC.getY() + 0.5 - p.getEyeHeight();
		p.snapTo(LC.getX() + 0.5, feet, LC.getZ() - 2.5, 0.0F, 0.0F);
		p.setYHeadRot(0.0F);
		p.setYBodyRot(0.0F);
		p.setDeltaMovement(Vec3.ZERO);
		p.setShiftKeyDown(false);
		p.setItemInHand(InteractionHand.MAIN_HAND, tool);
	}

	public static void run(MinecraftServer s) {
		try {
			runChecks(s);
		} catch (Throwable t) {
			t.printStackTrace();
			check(P + "block ran to the end", false, t);
		}
	}

	static void runChecks(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item sickle = Gear3Items.BY_ID.get(LanternSickleItem.ID);
		check(P + "the catalogue item is a LanternSickleItem (mechanic \"lantern_sickle\")", sickle instanceof LanternSickleItem, sickle);
		if (!(sickle instanceof LanternSickleItem)) return;
		checkS(P + "a real hoe: #minecraft:hoes (hoe enchantments, Fortune on crops), tills through the hoe block transformer, diamond speed 8 on leaves", () -> {
			ItemStack st = new ItemStack(sickle);
			return st.is(ItemTags.HOES) && st.is(ItemTags.MINING_LOOT_ENCHANTABLE) && st.has(DataComponents.TOOL) && st.has(DataComponents.BLOCK_TRANSFORMER)
				&& near(st.getDestroySpeed(st(Blocks.OAK_LEAVES)), 8.0);
		});
		checkS(P + "cuts leaves and vines (block tag); never logs, stone or crops", () -> {
			for (Block b : new Block[] {Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES, Blocks.VINE, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
				Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT})
				if (!st(b).is(LanternSickleItem.CUTS)) return false;
			for (Block b : new Block[] {Blocks.OAK_LOG, Blocks.STONE, Blocks.WHEAT, Blocks.GRASS_BLOCK, Blocks.HAY_BLOCK}) if (st(b).is(LanternSickleItem.CUTS)) return false;
			return true;
		});
		check(P + "vines mine at leaf speed (8), stone stays slow", near(new ItemStack(sickle).getDestroySpeed(st(Blocks.VINE)), LanternSickleItem.CUT_SPEED)
			&& new ItemStack(sickle).getDestroySpeed(st(Blocks.STONE)) <= 1.0F);
		checkS(P + "ripe = max-age crop or age-3 nether wart; torchflower crop never; replant is the same crop at age 0", () ->
			LanternSickleItem.isRipe(wheat(7)) && !LanternSickleItem.isRipe(wheat(6)) && LanternSickleItem.isRipe(st(Blocks.NETHER_WART).setValue(NetherWartBlock.AGE, 3))
				&& !LanternSickleItem.isRipe(st(Blocks.NETHER_WART)) && LanternSickleItem.isCrop(st(Blocks.TORCHFLOWER_CROP)) && !LanternSickleItem.isRipe(st(Blocks.TORCHFLOWER_CROP))
				&& !LanternSickleItem.isCrop(st(Blocks.SWEET_BERRY_BUSH)) && LanternSickleItem.replanted(((CropBlock) Blocks.BEETROOTS).getStateForAge(3)).equals(((CropBlock) Blocks.BEETROOTS).getStateForAge(0))
				&& LanternSickleItem.replanted(st(Blocks.NETHER_WART).setValue(NetherWartBlock.AGE, 3)).getValue(NetherWartBlock.AGE) == 0);
		checkS(P + "plane() is the 8 neighbours in the hit face's plane", () -> {
			BlockPos c = new BlockPos(5, 5, 5);
			for (Direction d : Direction.values()) {
				List<BlockPos> ps = LanternSickleItem.plane(c, d);
				if (ps.size() != 8 || ps.contains(c) || ps.stream().distinct().count() != 8) return false;
				for (BlockPos b : ps) {
					int same = switch (d.getAxis()) {
						case X -> b.getX() - c.getX();
						case Y -> b.getY() - c.getY();
						case Z -> b.getZ() - c.getZ();
					};
					if (same != 0 || b.distManhattan(c) > 2) return false;
				}
			}
			return true;
		});
		scripDesk();
		if (!LanternSickleItem.enabled) {
			info("lantern_sickle kill switch is OFF (config/elsewhere-overhaul3.json): harvesting and cutting not tested");
			return;
		}

		FakePlayer p = FakePlayer.get(l);
		Vec3 pos0 = p.position();
		float yRot0 = p.getYRot(), xRot0 = p.getXRot();
		ItemStack main0 = p.getMainHandItem(), off0 = p.getOffhandItem();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		try {
			clear(l);
			p.snapTo(H.getX() + 0.5, H.getY(), H.getZ() - 2.5, 0.0F, 45.0F);

			// ---- harvest and replant
			farm(l, false);
			check(P + "test setup: the farm is in open sky (crops have light)", l.getRawBrightness(H, 0) >= 9, l.getRawBrightness(H, 0));
			ItemStack tool = new ItemStack(sickle);
			InteractionResult r = use(l, p, tool, H);
			int replanted = 0;
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (!(dx == -1 && dz == -1) && age(l, H.offset(dx, 0, dz)) == 0) replanted++;
			check(P + "use on a crop harvests the 8 ripe crops of the 3x3 and replants each at age 0 (wheat stays wheat, carrots stay carrots)",
				r.consumesAction() && replanted == 8 && l.getBlockState(H).is(Blocks.WHEAT) && l.getBlockState(H.offset(1, 0, 1)).is(Blocks.CARROTS), r + ", replanted " + replanted);
			check(P + "the young crop in the 3x3 and the ripe crop outside it are left alone",
				age(l, H.offset(-1, 0, -1)) == 3 && age(l, H.offset(2, 0, 0)) == 7, age(l, H.offset(-1, 0, -1)) + " / " + age(l, H.offset(2, 0, 0)));
			check(P + "one sweep costs 1 durability", tool.getDamageValue() == LanternSickleItem.HARVEST_COST, tool.getDamageValue());
			int wheat = count(drops(l, Items.WHEAT)), carrots = count(drops(l, Items.CARROT));
			check(P + "the harvest drops where the crops stood: 7 wheat (one per ripe wheat; the seed went back in the ground)", wheat == 7, "wheat " + wheat + ", carrots " + carrots);
			drops(l, null).forEach(Entity::discard);

			// ---- nothing ripe: nothing happens
			farm(l, true);
			ItemStack tool2 = new ItemStack(sickle);
			r = use(l, p, tool2, H);
			check(P + "nothing ripe in the 3x3: the use passes, no durability, every crop untouched",
				!r.consumesAction() && tool2.getDamageValue() == 0 && age(l, H) == 3 && age(l, H.offset(1, 0, 1)) == 3 && drops(l, null).isEmpty(), r);

			// ---- nether wart
			l.setBlock(WART.below(), st(Blocks.SOUL_SAND), QUIET);
			l.setBlock(WART, st(Blocks.NETHER_WART).setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE), QUIET);
			r = use(l, p, new ItemStack(sickle), WART);
			int wart = count(drops(l, Items.NETHER_WART));
			check(P + "ripe nether wart is harvested and replanted on its soul sand", r.consumesAction() && l.getBlockState(WART).is(Blocks.NETHER_WART) && age(l, WART) == 0 && wart >= 1,
				"age " + age(l, WART) + ", dropped " + wart);
			drops(l, null).forEach(Entity::discard);

			// ---- replant from the inventory when the drops hold no seed
			int slot = p.getInventory().getContainerSize() - 1;
			ItemStack keep = p.getInventory().getItem(slot);
			p.getInventory().setItem(slot, new ItemStack(Items.WHEAT_SEEDS, 3));
			boolean took = LanternSickleItem.takeOne(p, Items.WHEAT_SEEDS) && p.getInventory().getItem(slot).getCount() == 2;
			p.getInventory().setItem(slot, ItemStack.EMPTY);
			boolean none = !LanternSickleItem.takeOne(p, Items.TORCHFLOWER_SEEDS);
			p.getInventory().setItem(slot, keep);
			check(P + "a seed the drops lack comes from the inventory; none there means the crop is left standing", took && none);

			// ---- still a hoe
			l.setBlock(TILL, st(Blocks.DIRT), QUIET);
			r = use(l, p, new ItemStack(sickle), TILL);
			check(P + "used on dirt it tills like any hoe", r.consumesAction() && l.getBlockState(TILL).is(Blocks.FARMLAND), l.getBlockState(TILL));

			// ---- kill switch off: a plain hoe
			farm(l, false);
			LanternSickleItem.enabled = false;
			try {
				r = use(l, p, new ItemStack(sickle), H);
				check(P + "kill switch off: no harvest (a plain hoe)", !r.consumesAction() && age(l, H) == 7, r);
			} finally {
				LanternSickleItem.enabled = true;
			}

			// ---- cutting leaves 3x3
			hedge(l, false);
			tool = new ItemStack(sickle);
			standAtHedge(p, tool);
			check(P + "test setup: cutting the hedge from the north hits its north face (wall plane)", LanternSickleItem.face(p, LC) == Direction.NORTH, LanternSickleItem.face(p, LC));
			boolean broke = p.gameMode.destroyBlock(LC);
			check(P + "breaking a leaf cuts the 3x3 of leaves facing you (8 leaves, 8 durability)", broke && hedgeLeft(l) == 0 && tool.getDamageValue() == 8,
				"left " + hedgeLeft(l) + ", damage " + tool.getDamageValue());
			check(P + "a stone in the square, the leaf behind and the leaf beside the 3x3 stay",
				l.getBlockState(LC.offset(1, 1, 0)).is(Blocks.STONE) && l.getBlockState(LC.south()).is(Blocks.OAK_LEAVES) && l.getBlockState(LC.offset(2, 0, 0)).is(Blocks.OAK_LEAVES));
			drops(l, null).forEach(Entity::discard);

			// ---- sneaking: one leaf
			hedge(l, false);
			tool = new ItemStack(sickle);
			standAtHedge(p, tool);
			p.setShiftKeyDown(true);
			broke = p.gameMode.destroyBlock(LC);
			p.setShiftKeyDown(false);
			check(P + "sneaking cuts one leaf", broke && l.getBlockState(LC).isAir() && hedgeLeft(l) == 7 && tool.getDamageValue() == 1, "left " + hedgeLeft(l));
			drops(l, null).forEach(Entity::discard);

			// ---- vines 3x3 (hung on a stone wall, so none falls on its own)
			hedge(l, true);
			tool = new ItemStack(sickle);
			standAtHedge(p, tool);
			broke = p.gameMode.destroyBlock(LC);
			int wall = 0;
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (l.getBlockState(LC.offset(dx, dy, 1)).is(Blocks.STONE)) wall++;
			check(P + "breaking a vine cuts the 3x3 of vines (9 vines, 9 durability; the wall stays)", broke && hedgeLeft(l) == 0 && tool.getDamageValue() == 9 && wall == 9,
				"left " + hedgeLeft(l) + ", damage " + tool.getDamageValue() + ", wall " + wall);

			// ---- the cut stops when the sickle breaks
			hedge(l, false);
			tool = new ItemStack(sickle);
			tool.setDamageValue(tool.getMaxDamage() - 3); // 3 uses left
			standAtHedge(p, tool);
			p.gameMode.destroyBlock(LC);
			check(P + "a sickle with 3 uses left cuts 3 leaves, then breaks and stops", tool.isEmpty() && hedgeLeft(l) == 5, "left " + hedgeLeft(l) + ", empty " + tool.isEmpty());

			// ---- kill switch off: one leaf
			hedge(l, false);
			tool = new ItemStack(sickle);
			standAtHedge(p, tool);
			LanternSickleItem.enabled = false;
			try {
				p.gameMode.destroyBlock(LC);
				check(P + "kill switch off: one leaf per break", hedgeLeft(l) == 7, hedgeLeft(l));
			} finally {
				LanternSickleItem.enabled = true;
			}
		} finally {
			clear(l);
			p.setShiftKeyDown(false);
			p.snapTo(pos0.x, pos0.y, pos0.z, yRot0, xRot0);
			p.setItemInHand(InteractionHand.MAIN_HAND, main0);
			p.setItemInHand(InteractionHand.OFF_HAND, off0);
		}
		info(P + "block done");
	}

	/**
	 * Source: Mirewick's Scrip-Desk market sells it (rival_mirewick data/elsewhere/kingdom_trade/mirewick.json, a "market" line). Checked on
	 * every copy of that file on the classpath; the gear3 dev run's devlib may hold an older copy, then the merged jar checks it.
	 */
	static void scripDesk() {
		String path = "data/elsewhere/kingdom_trade/mirewick.json";
		int copies = 0;
		JsonObject line = null;
		try {
			for (URL u : Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
				copies++;
				try (InputStream in = u.openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement e : o.getAsJsonArray("lines"))
						if (e.getAsJsonObject().get("item").getAsString().equals("elsewhere:" + LanternSickleItem.ID)) line = e.getAsJsonObject();
				}
			}
		} catch (Exception e) {
			check(P + "Mirewick Scrip-Desk stock is readable", false, e);
			return;
		}
		if (line != null) {
			JsonObject x = line;
			checkS(P + "sold at Mirewick's Scrip-Desk (market line, 24 Lantern Scrip, sells back for 9)", () ->
				x.get("price").getAsInt() == 24 && "market".equals(x.get("tier").getAsString()) && x.get("sell").getAsInt() == 9);
		} else if (copies == 0) {
			info(P + "rival_mirewick is not in this environment; the Scrip-Desk line is checked in the merged jar");
		} else {
			info(P + "this environment's Mirewick stock (" + copies + " cop" + (copies == 1 ? "y" : "ies") + ", the devlib) predates the Scrip-Desk line; the merged jar checks it");
		}
	}
}
