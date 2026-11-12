package dev.elsewhere.gear3.s3.sunglass;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Items;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sunglass Arrow self-test (owner s-sunglass), called from the gear3 self-test after the mechanic blocks. Synchronous: arrows are
 * ticked by hand ({@code tickCount++; tick()}, GOTCHAS) through a test lane of glass built at y 110 above the self-test platform.
 * Covers: item/entity/tag wiring, bow + crossbow ammo, a real bow shot by a survival FakePlayer, the dispenser, flight through
 * glass / pane / stained / tinted glass to the stone behind, vanilla arrows still stopped by glass, the off switch, fire 3 s,
 * +25% vs undead (measured hit against a pig and a skeleton behind glass), lang, textures, recipe.
 */
public final class SunglassSelfTest {
	private SunglassSelfTest() {}

	static final int Y = 110, X0 = 42, X1 = 62, Z0 = 20, Z1 = 40;
	static final int GLASS_X = 46, PANE_X = 49, STAINED_X = 52, TINTED_X = 55, STONE_X = 58;

	public static void run(MinecraftServer s) {
		try {
			runChecks(s);
		} catch (Throwable t) {
			t.printStackTrace();
			check("Sunglass Arrow: self-test block ran to the end", false, t);
		} finally {
			clear(s.overworld());
		}
		info("sunglass arrow block done (kill switch sunglass_arrow=" + SunglassArrows.ENABLED + ")");
	}

	static void clear(ServerLevel l) {
		for (int x = X0; x <= X1; x++) for (int y = Y - 1; y <= Y + 2; y++) for (int z = Z0; z <= Z1; z++) {
			BlockPos p = new BlockPos(x, y, z);
			if (!l.getBlockState(p).isAir()) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		for (Entity e : l.getEntities((Entity) null, new AABB(X0 - 2, Y - 3, Z0 - 2, X1 + 3, Y + 5, Z1 + 3), e -> !(e instanceof net.minecraft.world.entity.player.Player)))
			e.discard();
	}

	/** One glass row on lane {@code z}: glass, pane, orange stained glass, tinted glass, then stone (all at y {@link #Y}). */
	static void row(ServerLevel l, int z, boolean all) {
		l.setBlock(new BlockPos(GLASS_X, Y, z), Blocks.GLASS.defaultBlockState(), 2);
		if (all) {
			l.setBlock(new BlockPos(PANE_X, Y, z), Blocks.GLASS_PANE.defaultBlockState(), 2);
			l.setBlock(new BlockPos(STAINED_X, Y, z), Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState(), 2);
			l.setBlock(new BlockPos(TINTED_X, Y, z), Blocks.TINTED_GLASS.defaultBlockState(), 2);
		}
		l.setBlock(new BlockPos(STONE_X, Y, z), Blocks.STONE.defaultBlockState(), 2);
	}

	static <A extends AbstractArrow> A launch(ServerLevel l, A a, int z, double speed) {
		a.snapTo(X0 + 1.5, Y + 0.45, z + 0.5, -90F, 0F);
		a.setNoGravity(true);
		a.setDeltaMovement(new Vec3(speed, 0, 0));
		l.addFreshEntity(a);
		return a;
	}

	/** Ticks the arrow by hand until it sticks, is removed, or 40 ticks pass. Returns the ticks used. */
	static int fly(AbstractArrow a) {
		int n = 0;
		while (n < 40 && !a.isRemoved() && a.getDeltaMovement().lengthSqr() > 1.0E-8) {
			a.tickCount++;
			a.tick();
			n++;
		}
		return n;
	}

	static SunglassArrow arrow(ServerLevel l) {
		SunglassArrow a = new SunglassArrow(l, 0, 0, 0, SunglassArrows.stack(), null);
		a.setSunlit(true);
		return a;
	}

	static Mob mob(ServerLevel l, EntityType<? extends Mob> type, int z) {
		Mob m = type.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(STONE_X - 9.5, Y, z + 0.5, 90F, 0F);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static void runChecks(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item item = SunglassArrows.item();
		check("Sunglass Arrow: catalogue item is a SunglassArrowItem (ArrowItem)", item instanceof SunglassArrowItem && item instanceof ArrowItem, item);
		if (item == null) return;
		ItemStack st = new ItemStack(item);
		check("Sunglass Arrow: entity type elsewhere:sunglass_arrow registered", SunglassArrows.TYPE != null
			&& net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(SunglassArrows.TYPE).toString().equals("elsewhere:sunglass_arrow"));
		check("Sunglass Arrow: ammo for bows and crossbows (#minecraft:arrows)", st.is(ItemTags.ARROWS) && BowItem.ARROW_ONLY.test(st)
			&& ((CrossbowItem) Items.CROSSBOW).getAllSupportedProjectiles().test(st));
		check("Sunglass Arrow: stacks to 64", st.getMaxStackSize() == 64, st.getMaxStackSize());
		check("Sunglass Arrow: entity is an arrow and an impact projectile (#minecraft:arrows, #minecraft:impact_projectiles)",
			SunglassArrows.TYPE.builtInRegistryHolder().is(EntityTypeTags.ARROWS) && SunglassArrows.TYPE.builtInRegistryHolder().is(EntityTypeTags.IMPACT_PROJECTILES));
		check("Sunglass Arrow: no calling tag (ammo; an off-hand stack must not scale a vanilla bow)", !st.is(Gear3Items.itemTag("elsewhere:calling_weapon/marksman")));
		checkS("Sunglass Arrow: passes glass, panes, stained and tinted glass; never stone, ice or barrier", () -> {
			BlockState[] pass = {Blocks.GLASS.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(),
				Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState(), Blocks.STAINED_GLASS_PANE.pick(DyeColor.BLUE).defaultBlockState()};
			BlockState[] stop = {Blocks.STONE.defaultBlockState(), Blocks.ICE.defaultBlockState(), Blocks.BARRIER.defaultBlockState(), Blocks.AIR.defaultBlockState()};
			for (BlockState b : pass) if (!b.is(SunglassArrows.PASSES)) return false;
			for (BlockState b : stop) if (b.is(SunglassArrows.PASSES)) return false;
			return true;
		});
		check("Sunglass Arrow: x1.25 against undead, x1.0 otherwise", SunglassArrows.UNDEAD_MULT == 1.25F && SunglassArrows.FIRE_SECONDS == 3.0F);

		clear(l);
		// warm-up entity query (GOTCHAS: the first query after addFreshEntity in a synchronous test can miss)
		l.getEntities((Entity) null, new AABB(X0, Y - 1, Z0, X1, Y + 2, Z1));

		// ---- item: createArrow, bow shot by a survival player, dispenser
		FakePlayer p = FakePlayer.get(l);
		checkS("Sunglass Arrow: createArrow makes a Sunglass Arrow that picks up as one", () -> {
			AbstractArrow a = ((ArrowItem) item).createArrow(l, st, p, new ItemStack(Items.BOW));
			boolean ok = a instanceof SunglassArrow sa && sa.sunlit() == SunglassArrows.ENABLED && a.getPickupItemStackOrigin().is(item)
				&& a.getPickupItemStackOrigin().getCount() == 1 && a.getOwner() == p;
			a.discard();
			return ok;
		});
		checkS("Sunglass Arrow: a survival player's full-draw bow shot looses one from the off hand", () -> {
			p.snapTo(X0 + 2.5, Y, Z1 - 0.5, -90F, 0F);
			ItemStack bow = new ItemStack(Items.BOW);
			p.setItemInHand(InteractionHand.MAIN_HAND, bow);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item, 4));
			boolean shot = ((BowItem) Items.BOW).releaseUsing(bow, l, p, ((BowItem) Items.BOW).getUseDuration(bow, p) - 20);
			l.getEntities((Entity) null, p.getBoundingBox().inflate(6));
			List<SunglassArrow> out = l.getEntitiesOfClass(SunglassArrow.class, p.getBoundingBox().inflate(6), a -> a.getOwner() == p);
			boolean ok = shot && out.size() == 1 && p.getOffhandItem().getCount() == 3 && out.get(0).pickup == AbstractArrow.Pickup.ALLOWED
				&& out.get(0).getDeltaMovement().x > 1.0;
			for (SunglassArrow a : out) a.discard();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			return ok;
		});
		checkS("Sunglass Arrow: a dispenser shoots it (projectile behaviour, pick-up allowed)", () -> {
			DispenseItemBehavior b = DispenserBlock.DISPENSER_REGISTRY.get(item);
			if (!(b instanceof ProjectileDispenseBehavior)) return false;
			BlockPos at = new BlockPos(X0 + 2, Y, Z1 - 3);
			BlockState state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST);
			l.setBlock(at, state, 2);
			if (!(l.getBlockEntity(at) instanceof DispenserBlockEntity be)) return false;
			ItemStack rest = b.dispense(new BlockSource(l, at, state, be), new ItemStack(item, 2));
			AABB front = new AABB(at.getX() + 0.5, Y - 1, at.getZ() - 1, at.getX() + 4, Y + 2, at.getZ() + 2);
			l.getEntities((Entity) null, front);
			List<SunglassArrow> out = l.getEntitiesOfClass(SunglassArrow.class, front);
			boolean ok = rest.getCount() == 1 && out.size() == 1 && out.get(0).pickup == AbstractArrow.Pickup.ALLOWED && out.get(0).getDeltaMovement().x > 0.5
				&& out.get(0).getPickupItemStackOrigin().is(item);
			for (SunglassArrow a : out) a.discard();
			return ok;
		});

		// ---- flight through glass
		int zFly = Z0 + 1, zVanilla = Z0 + 4, zOff = Z0 + 7, zPig = Z0 + 10, zUndead = Z0 + 13;
		row(l, zFly, true);
		row(l, zVanilla, true);
		row(l, zOff, true);
		row(l, zPig, false);
		row(l, zUndead, false);
		checkS("Sunglass Arrow: flies through glass, a pane, stained and tinted glass and sticks in the stone behind", () -> {
			SunglassArrow a = launch(l, arrow(l), zFly, 1.5);
			fly(a);
			boolean glassIntact = l.getBlockState(new BlockPos(GLASS_X, Y, zFly)).is(Blocks.GLASS) && l.getBlockState(new BlockPos(PANE_X, Y, zFly)).is(Blocks.GLASS_PANE)
				&& l.getBlockState(new BlockPos(TINTED_X, Y, zFly)).is(Blocks.TINTED_GLASS);
			boolean ok = a.stuck() && a.getX() > STONE_X - 0.5 && a.getX() <= STONE_X + 0.01 && glassIntact;
			if (!ok) info("sunglass flight: stuck=" + a.stuck() + " x=" + a.getX() + " glass intact=" + glassIntact);
			a.discard();
			return ok;
		});
		checkS("Sunglass Arrow: a vanilla arrow on the same line still stops at the first glass", () -> {
			Arrow a = launch(l, new Arrow(l, 0, 0, 0, new ItemStack(Items.ARROW), null), zVanilla, 1.5);
			fly(a);
			boolean ok = a.getX() <= GLASS_X + 0.01 && a.getDeltaMovement().lengthSqr() < 1.0E-8;
			if (!ok) info("vanilla arrow: x=" + a.getX() + " v=" + a.getDeltaMovement());
			a.discard();
			return ok;
		});
		checkS("Sunglass Arrow: switched off (sunlit=false) it stops at glass like a plain arrow", () -> {
			SunglassArrow a = arrow(l);
			a.setSunlit(false);
			launch(l, a, zOff, 1.5);
			fly(a);
			boolean ok = a.stuck() && a.getX() <= GLASS_X + 0.01;
			if (!ok) info("sunglass off: stuck=" + a.stuck() + " x=" + a.getX());
			a.discard();
			return ok;
		});

		// ---- hits behind glass: fire 3 s and +25% vs undead (pig 10 HP vs skeleton 20 HP, no armor, same shot)
		Mob pig = mob(l, EntityTypes.PIG, zPig);
		Mob skel = mob(l, EntityTypes.SKELETON, zUndead);
		l.getEntities((Entity) null, new AABB(X0, Y - 1, Z0, X1, Y + 3, Z1));
		float pig0 = pig.getHealth(), skel0 = skel.getHealth();
		SunglassArrow ap = launch(l, arrow(l), zPig, 2.0);
		fly(ap);
		SunglassArrow as = launch(l, arrow(l), zUndead, 2.0);
		fly(as);
		float dPig = pig0 - pig.getHealth(), dSkel = skel0 - skel.getHealth();
		info("sunglass hits: pig -" + dPig + " (fire " + pig.getRemainingFireTicks() + "), skeleton -" + dSkel + " (fire " + skel.getRemainingFireTicks() + ")");
		check("Sunglass Arrow: hits a mob behind glass (pig took damage, arrow used up)", dPig > 0F && ap.isRemoved(), dPig);
		check("Sunglass Arrow: the hit sets the target on fire for 3 s", pig.getRemainingFireTicks() > 40 && pig.getRemainingFireTicks() <= 60, pig.getRemainingFireTicks());
		check("Sunglass Arrow: +25% against undead (skeleton hit = 1.25 x the pig hit)", dPig > 0F && Math.abs(dSkel - dPig * SunglassArrows.UNDEAD_MULT) < 0.01F, dSkel + " vs " + dPig);
		check("Sunglass Arrow: undead check (skeleton yes, pig no)", SunglassArrows.undead(skel) && !SunglassArrows.undead(pig)
			&& SunglassArrows.damageAgainst(skel, 4F) == 5F && SunglassArrows.damageAgainst(pig, 4F) == 4F);
		pig.discard();
		skel.discard();

		// ---- resources
		JsonObject lang = mergedJson("assets/elsewhere/lang/en_us.json");
		check("Sunglass Arrow: entity lang line (death messages, /summon)", lang != null && "Sunglass Arrow".equals(lang.has("entity.elsewhere.sunglass_arrow")
			? lang.get("entity.elsewhere.sunglass_arrow").getAsString() : null));
		checkS("Sunglass Arrow: entity texture is a 32x32 vanilla-layout arrow (head, shaft, fletching pixels)", () -> {
			try (InputStream in = SunglassSelfTest.class.getResourceAsStream("/assets/elsewhere/textures/entity/projectiles/sunglass_arrow.png")) {
				if (in == null) return false;
				var img = ImageIO.read(in);
				return img.getWidth() == 32 && img.getHeight() == 32 && (img.getRGB(15, 2) >>> 24) != 0 && (img.getRGB(6, 2) >>> 24) != 0
					&& (img.getRGB(1, 0) >>> 24) != 0 && (img.getRGB(2, 7) >>> 24) != 0 && (img.getRGB(20, 20) >>> 24) == 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});
		checkS("Sunglass Arrow: recipe is 4 arrows + a Sunglass Shard -> 4", () -> {
			JsonObject r = json("data/elsewhere/recipe/sunglass_arrow.json");
			if (r == null) return false;
			String t = r.toString();
			int arrows = 0;
			for (var row : r.getAsJsonArray("pattern")) for (char c : row.getAsString().toCharArray()) if (c == 'A') arrows++;
			return arrows == 4 && t.contains("\"minecraft:arrow\"") && t.contains("\"elsewhere:sunglass_shard\"") && r.getAsJsonObject("result").get("count").getAsInt() == 4;
		});
	}

	static JsonObject json(String path) {
		try (InputStream in = SunglassSelfTest.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Every copy of a resource on the classpath, merged (the merged Elsewhere jar and devlibs carry their own lang files). */
	static JsonObject mergedJson(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(SunglassSelfTest.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return null;
		}
		return out;
	}
}
