package dev.elsewhere.gear3.s3.dredge;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import dev.elsewhere.gear3.Gear3Items;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tidebrass Dredge self-test (s-dredge), called from {@code dev/SelfTest.runChecks} (one hook line). Builds a sealed stone pool (7x7, sand bed under 3
 * blocks of water) and a dry sand pad in the forced test chunks, digs with the shared FakePlayer through
 * {@code ServerPlayerGameMode.destroyBlock} (the survival break path, so {@code mineBlock}, drops and durability are the real ones),
 * measures {@code Player.getDestroySpeed} (the mixin), then removes everything and puts the FakePlayer back as it was.
 * Synchronous: no world tick runs in between, so water never flows and nothing falls.
 */
public final class DredgeSelfTest {
	private DredgeSelfTest() {}

	/** Pool shell x/z 64..70, y 120..125: stone floor, sand bed y 121 (inner x/z 65..69), water y 122..124. */
	static final int PX = 64, PZ = 64, PY = 120;
	static final BlockPos CENTER = new BlockPos(67, 121, 67);
	/** Dry pad x 72..76, z 64..68: stone floor y 120, 3x3 sand y 121 around DRY, air above. */
	static final BlockPos DRY = new BlockPos(74, 121, 66);
	static final AABB POOL_BOX = new AABB(PX, PY, PZ, PX + 7, PY + 6, PZ + 7), DRY_BOX = new AABB(72, 120, 64, 77, 125, 69);

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	/** (Re)builds the pool: stone shell and floor, a full sand bed, water above it. */
	static void pool(ServerLevel l) {
		for (int x = PX; x <= PX + 6; x++) for (int z = PZ; z <= PZ + 6; z++) for (int y = PY; y <= PY + 5; y++) {
			boolean shell = x == PX || x == PX + 6 || z == PZ || z == PZ + 6 || y == PY;
			BlockState s = shell ? st(Blocks.STONE) : y == PY + 1 ? st(Blocks.SAND) : y <= PY + 4 ? st(Blocks.WATER) : st(Blocks.AIR);
			l.setBlock(new BlockPos(x, y, z), s, 3);
		}
	}

	static void dryPad(ServerLevel l) {
		for (int x = 72; x <= 76; x++) for (int z = 64; z <= 68; z++) for (int y = 120; y <= 124; y++) {
			BlockPos b = new BlockPos(x, y, z);
			boolean bed = y == 121 && Math.abs(x - DRY.getX()) <= 1 && Math.abs(z - DRY.getZ()) <= 1;
			l.setBlock(b, y == 120 ? st(Blocks.STONE) : bed ? st(Blocks.SAND) : st(Blocks.AIR), 3);
		}
	}

	static void clear(ServerLevel l, AABB box) {
		for (int x = (int) box.minX; x < box.maxX; x++) for (int z = (int) box.minZ; z < box.maxZ; z++) for (int y = (int) box.minY; y < box.maxY; y++)
			l.setBlock(new BlockPos(x, y, z), st(Blocks.AIR), 3);
		l.getEntitiesOfClass(ItemEntity.class, box.inflate(2)); // warm-up query (GOTCHAS: the first one after spawning can come back empty)
		l.getEntitiesOfClass(ItemEntity.class, box.inflate(2)).forEach(Entity::discard);
	}

	/** Puts the FakePlayer at {@code (x, y, z)} with the given look, refreshes its water state (a FakePlayer never ticks) and hands it {@code tool}. */
	static void stand(FakePlayer p, double x, double y, double z, float yRot, float xRot, ItemStack tool) {
		p.snapTo(x, y, z, yRot, xRot);
		p.setDeltaMovement(Vec3.ZERO);
		p.baseTick();
		p.setDeltaMovement(Vec3.ZERO);
		p.setOnGround(true);
		p.setShiftKeyDown(false);
		p.setItemInHand(InteractionHand.MAIN_HAND, tool);
	}

	static float speed(FakePlayer p, Item tool, boolean ground) {
		ItemStack keep = p.getMainHandItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
		p.setOnGround(ground);
		float f = p.getDestroySpeed(st(Blocks.SAND));
		p.setItemInHand(InteractionHand.MAIN_HAND, keep);
		p.setOnGround(true);
		return f;
	}

	/** How many of the 8 neighbours of CENTER (floor plane) are still dredgeable. */
	static int bedLeft(ServerLevel l) {
		int n = 0;
		for (BlockPos b : TidebrassDredgeItem.plane(CENTER, Direction.UP)) if (l.getBlockState(b).is(TidebrassDredgeItem.MINEABLE)) n++;
		return n;
	}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item dredge = Gear3Items.BY_ID.get(TidebrassDredgeItem.ID);
		check("Tidebrass Dredge: the catalogue item is a TidebrassDredgeItem (mechanic \"dredge\")", dredge instanceof TidebrassDredgeItem, dredge);
		if (!(dredge instanceof TidebrassDredgeItem)) return;
		checkS("Tidebrass Dredge: digs sand, red sand, gravel, clay and mud (block tag); never suspicious sand or gravel, dirt or stone", () -> {
			for (Block b : new Block[] {Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD}) if (!st(b).is(TidebrassDredgeItem.MINEABLE)) return false;
			for (Block b : new Block[] {Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.DIRT, Blocks.STONE}) if (st(b).is(TidebrassDredgeItem.MINEABLE)) return false;
			return true;
		});
		checkS("Tidebrass Dredge: a real shovel (#minecraft:shovels for its enchantments, makes paths, netherite speed 9 on sand, fire-proof)", () -> {
			ItemStack st = new ItemStack(dredge);
			return st.is(ItemTags.SHOVELS) && st.has(DataComponents.BLOCK_TRANSFORMER) && st.has(DataComponents.DAMAGE_RESISTANT)
				&& near(st.getDestroySpeed(st(Blocks.SAND)), 9.0) && st.isCorrectToolForDrops(st(Blocks.CLAY));
		});
		checkS("Tidebrass Dredge: plane() is the 8 neighbours in the dug face's plane (floor: same y; north/south wall: same z; east/west wall: same x)", () -> {
			BlockPos c = new BlockPos(5, 5, 5);
			for (Direction d : Direction.values()) {
				List<BlockPos> ps = TidebrassDredgeItem.plane(c, d);
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
		if (!TidebrassDredgeItem.enabled) {
			info("tidebrass_dredge kill switch is OFF (config/elsewhere-overhaul3.json): dredging and full speed not tested");
			return;
		}
		FakePlayer p = FakePlayer.get(l);
		Vec3 pos0 = p.position();
		float yRot0 = p.getYRot(), xRot0 = p.getXRot();
		ItemStack main0 = p.getMainHandItem(), off0 = p.getOffhandItem();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		double px = CENTER.getX() + 0.5, py = CENTER.getY() + 1.0, pz = CENTER.getZ() + 0.5;
		try {
			// ---- dry land: a plain netherite-class shovel
			dryPad(l);
			stand(p, DRY.getX() + 0.5, DRY.getY() + 1.0, DRY.getZ() + 0.5, 0, 90, ItemStack.EMPTY);
			check("Tidebrass Dredge test setup: dry pad, eyes not in water", !p.isEyeInFluid(FluidTags.WATER) && !p.isInWater());
			float land = speed(p, Items.NETHERITE_SHOVEL, true);
			check("Tidebrass Dredge: on land it mines sand exactly like a netherite shovel", land > 1 && near(speed(p, dredge, true), land), speed(p, dredge, true) + " vs " + land);
			ItemStack dry = new ItemStack(dredge);
			p.setItemInHand(InteractionHand.MAIN_HAND, dry);
			boolean broke = p.gameMode.destroyBlock(DRY);
			int left = 0;
			for (BlockPos b : TidebrassDredgeItem.plane(DRY, Direction.UP)) if (l.getBlockState(b).is(Blocks.SAND)) left++;
			check("Tidebrass Dredge: on land it digs one block only (1 durability)", broke && l.getBlockState(DRY).isAir() && left == 8 && dry.getDamageValue() == 1,
				"left " + left + ", damage " + dry.getDamageValue());

			// ---- underwater: full speed
			pool(l);
			stand(p, px, py, pz, 0, 90, ItemStack.EMPTY);
			check("Tidebrass Dredge test setup: standing on the pool bed, eyes in water", p.isEyeInFluid(FluidTags.WATER) && p.isInWater());
			double sub = p.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
			float dGround = speed(p, dredge, true), dFloat = speed(p, dredge, false), nGround = speed(p, Items.NETHERITE_SHOVEL, true), nFloat = speed(p, Items.NETHERITE_SHOVEL, false);
			check("Tidebrass Dredge: full speed with eyes underwater (= land speed; a netherite shovel gets x" + sub + ")", near(dGround, land) && near(nGround, land * sub),
				"dredge " + dGround + ", netherite " + nGround + ", land " + land);
			check("Tidebrass Dredge: full speed while swimming too (a netherite shovel gets another /5)", near(dFloat, land) && near(nFloat, land * sub / 5.0),
				"dredge " + dFloat + ", netherite " + nFloat);

			// ---- underwater: 3x3 of sand, gravel, clay, mud; a stone in the square stays
			BlockPos stone = CENTER.offset(-1, 0, -1);
			l.setBlock(stone, st(Blocks.STONE), 3);
			l.setBlock(CENTER.offset(-1, 0, 0), st(Blocks.GRAVEL), 3);
			l.setBlock(CENTER.offset(1, 0, -1), st(Blocks.CLAY), 3);
			l.setBlock(CENTER.offset(1, 0, 1), st(Blocks.MUD), 3);
			l.setBlock(CENTER.offset(0, 0, 1), st(Blocks.RED_SAND), 3);
			ItemStack tool = new ItemStack(dredge);
			stand(p, px, py, pz, 0, 90, tool);
			check("Tidebrass Dredge: digging the bed from above hits its top face (floor plane)", TidebrassDredgeItem.face(p, CENTER) == Direction.UP, TidebrassDredgeItem.face(p, CENTER));
			broke = p.gameMode.destroyBlock(CENTER);
			check("Tidebrass Dredge: underwater it digs the 3x3 of sand, red sand, gravel, clay and mud (8 blocks, 8 durability)",
				broke && !l.getBlockState(CENTER).is(TidebrassDredgeItem.MINEABLE) && bedLeft(l) == 0 && tool.getDamageValue() == 8, "left " + bedLeft(l) + ", damage " + tool.getDamageValue());
			check("Tidebrass Dredge: a non-dredgeable block in the square (stone) and the bed outside the 3x3 stay",
				l.getBlockState(stone).is(Blocks.STONE) && l.getBlockState(CENTER.offset(2, 0, 0)).is(Blocks.SAND) && l.getBlockState(CENTER.offset(-2, 0, 2)).is(Blocks.SAND));
			l.getEntitiesOfClass(ItemEntity.class, POOL_BOX); // warm-up query
			List<ItemEntity> drops = l.getEntitiesOfClass(ItemEntity.class, POOL_BOX);
			check("Tidebrass Dredge: every dug block dropped its item (8 stacks)", drops.size() >= 8, drops.size());
			drops.forEach(Entity::discard);

			// ---- sneaking: one block
			pool(l);
			tool = new ItemStack(dredge);
			stand(p, px, py, pz, 0, 90, tool);
			p.setShiftKeyDown(true);
			broke = p.gameMode.destroyBlock(CENTER);
			p.setShiftKeyDown(false);
			check("Tidebrass Dredge: sneaking underwater digs one block", broke && l.getBlockState(CENTER).isAir() && bedLeft(l) == 8 && tool.getDamageValue() == 1, "left " + bedLeft(l));

			// ---- a wall: the 3x3 stands in the wall's plane
			pool(l);
			BlockPos wall = CENTER.offset(0, 2, -2); // (67, 123, 65): north inner row, eye height
			for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) l.setBlock(wall.offset(x, y, 0), st(Blocks.SAND), 3);
			tool = new ItemStack(dredge);
			stand(p, px, py, pz, 180, 0, tool); // facing north, looking level
			Direction f = TidebrassDredgeItem.face(p, wall);
			broke = p.gameMode.destroyBlock(wall);
			int wallLeft = 0;
			for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) if (l.getBlockState(wall.offset(x, y, 0)).is(Blocks.SAND)) wallLeft++;
			check("Tidebrass Dredge: facing a sand wall underwater it digs the wall's 3x3 (south face, 9 blocks)", broke && f == Direction.SOUTH && wallLeft == 0
				&& bedLeft(l) == 8 && tool.getDamageValue() == 9, f + ", left " + wallLeft + ", damage " + tool.getDamageValue());

			// ---- the dredge breaks mid-dig: digging stops
			pool(l);
			tool = new ItemStack(dredge);
			tool.setDamageValue(tool.getMaxDamage() - 2);
			stand(p, px, py, pz, 0, 90, tool);
			broke = p.gameMode.destroyBlock(CENTER);
			check("Tidebrass Dredge: if it breaks mid-dig, digging stops (2 durability left: the block and one neighbour)",
				broke && p.getMainHandItem().isEmpty() && bedLeft(l) == 7, "left " + bedLeft(l) + ", hand " + p.getMainHandItem());

			// ---- kill switch
			pool(l);
			tool = new ItemStack(dredge);
			stand(p, px, py, pz, 0, 90, tool);
			TidebrassDredgeItem.enabled = false;
			try {
				float off = speed(p, dredge, true);
				broke = p.gameMode.destroyBlock(CENTER);
				check("Tidebrass Dredge: kill switch off = a plain shovel (one block, the vanilla underwater penalty)", broke && bedLeft(l) == 8 && near(off, land * sub), off);
			} finally {
				TidebrassDredgeItem.enabled = true;
			}
			info("Tidebrass Dredge: nothing ticks (dig = mineBlock of the dredge itself; speed = two checks on the getDestroySpeed path)");
		} finally {
			clear(l, POOL_BOX);
			clear(l, DRY_BOX);
			p.snapTo(pos0.x, pos0.y, pos0.z, yRot0, xRot0);
			p.baseTick(); // out of the water again
			p.setDeltaMovement(Vec3.ZERO);
			p.setOnGround(false);
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.MAIN_HAND, main0);
			p.setItemInHand(InteractionHand.OFF_HAND, off0);
		}
	}
}
