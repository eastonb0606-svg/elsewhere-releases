package dev.elsewhere.gear3.s3.harpoon;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Harpoon Bolt self-test block (gear3 self-test, after the per-mechanic blocks; lane s-harpoon). Runs synchronously on the server
 * thread in its own corner of the forced test area (y 159-161, x 20-48, z 22-48), ticks entities by hand ({@code tickCount++; tick()},
 * GOTCHAS) and removes every entity and block it places. Check names start with "harpoon_bolt:".
 */
public final class HarpoonSelfTest {
	private HarpoonSelfTest() {}

	static final String P = "harpoon_bolt: ";
	static final int Y = 160;

	static Mob husk(ServerLevel l, double x, double z, List<Entity> spawned) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, Y, z, 0, 0);
		m.setNoAi(true);
		m.setNoGravity(true);
		l.addFreshEntity(m);
		spawned.add(m);
		return m;
	}

	/** A crossbow bolt from {@code fake}, 1.5 blocks short of {@code target}, flying +z at speed 3 (damage ceil(3 x 2) = 6), no crit. Not added to the world. */
	static HarpoonBolt bolt(ServerLevel l, Item item, FakePlayer fake, ItemStack crossbow, LivingEntity target) {
		HarpoonBolt b = (HarpoonBolt) ((ArrowItem) item).createArrow(l, new ItemStack(item), fake, crossbow);
		b.snapTo(target.getX(), target.getY() + 1.0, target.getZ() - 1.5, 0, 0);
		b.setDeltaMovement(0.0, 0.0, 3.0);
		b.setCritArrow(false);
		return b;
	}

	static boolean close(double a, double b, double tol) {
		return Math.abs(a - b) <= tol;
	}

	/** Every copy of the lang file on the classpath (the merged jar carries its own), merged. */
	static JsonObject lang() {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json")))
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
		} catch (Exception e) {
			return out;
		}
		return out;
	}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item item = HarpoonBolts.item();
		check(P + "catalogue item registered as a HarpoonBoltItem (ArrowItem)", item instanceof HarpoonBoltItem, item);
		check(P + "entity type elsewhere:tidebrass_harpoon_bolt registered", HarpoonBolts.TYPE != null
			&& BuiltInRegistries.ENTITY_TYPE.getValue(Gear3Content.id(HarpoonBolts.ID)) == HarpoonBolts.TYPE);
		if (!(item instanceof HarpoonBoltItem) || HarpoonBolts.TYPE == null) return;
		ItemStack st = new ItemStack(item);
		ProjectileWeaponItem bowItem = (ProjectileWeaponItem) Items.BOW, crossbowItem = (ProjectileWeaponItem) Items.CROSSBOW;

		// ---- ammo rules: an arrow (crossbows load it), tagged crossbow-only (bows skip it), vanilla arrows untouched
		checkS(P + "in #minecraft:arrows and #elsewhere:crossbow_only_ammo", () -> st.is(ItemTags.ARROWS) && st.is(HarpoonBolts.CROSSBOW_ONLY));
		checkS(P + "crossbows load it, bows don't (bows still take arrows and spectral arrows)", () -> crossbowItem.getAllSupportedProjectiles().test(st)
			&& !bowItem.getAllSupportedProjectiles().test(st) && bowItem.getAllSupportedProjectiles().test(new ItemStack(Items.ARROW))
			&& bowItem.getAllSupportedProjectiles().test(new ItemStack(Items.SPECTRAL_ARROW)));
		checkS(P + "kill switch reader: default on, -Delsewhere.overhaul3.<key>=false turns a key off", () -> {
			String probe = "gear3_harpoon_selftest_probe";
			boolean def = HarpoonBolts.on(probe);
			System.setProperty("elsewhere.overhaul3." + probe, "false");
			try {
				return def && !HarpoonBolts.on(probe);
			} finally {
				System.clearProperty("elsewhere.overhaul3." + probe);
			}
		});
		checkS(P + "entity texture is a 32x32 arrow-layout PNG (shaft, head, fletching and back cross painted)", () -> {
			try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/entity/projectiles/" + HarpoonBolts.ID + ".png")) {
				if (in == null) return false;
				var img = ImageIO.read(in);
				return img.getWidth() == 32 && img.getHeight() == 32 && (img.getRGB(6, 2) >>> 24) != 0 && (img.getRGB(14, 2) >>> 24) != 0
					&& (img.getRGB(1, 0) >>> 24) != 0 && (img.getRGB(7, 7) >>> 24) != 0 && (img.getRGB(20, 20) >>> 24) == 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});
		checkS(P + "entity name lang line", () -> {
			JsonObject lang = lang();
			String k = "entity.elsewhere." + HarpoonBolts.ID;
			return lang.has(k) && "Harpoon Bolt".equals(lang.get(k).getAsString());
		});
		checkS(P + "dispensers fire it as a harpoon bolt (pickup allowed)", () -> {
			if (!(DispenserBlock.DISPENSER_REGISTRY.get(item) instanceof ProjectileDispenseBehavior)) return false;
			var proj = ((ArrowItem) item).asProjectile(l, new Vec3(24.5, Y, 24.5), st, Direction.UP);
			boolean ok = proj instanceof HarpoonBolt b && b.pickup == AbstractArrow.Pickup.ALLOWED && b.getPickupItemStackOrigin().is(item);
			proj.discard();
			return ok;
		});
		checkS(P + "pull distance: 3 blocks, never closer than 1.5 to the shooter (10 -> 3, 3.5 -> 2, 1 -> 0)", () -> close(HarpoonBolts.pullDistance(10), 3.0, 1e-9)
			&& close(HarpoonBolts.pullDistance(3.5), 2.0, 1e-9) && HarpoonBolts.pullDistance(1.0) == 0.0);

		if (!HarpoonBolts.ON) {
			info("harpoon_bolt kill switch is OFF (config/elsewhere-overhaul3.json): pull, water bonus and water flight not tested");
			checkS(P + "kill switch off: vanilla arrow water drag", () -> {
				HarpoonBolt b = new HarpoonBolt(l, 24.5, Y, 24.5, st.copy(), null);
				boolean ok = b.waterInertiaForTest() == 0.6F;
				b.discard();
				return ok;
			});
			return;
		}

		FakePlayer fake = FakePlayer.get(l);
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		List<Entity> spawned = new ArrayList<>();
		List<BlockPos> placed = new ArrayList<>();
		fake.getInventory().clearContent();
		try {
			fake.snapTo(24.5, Y, 30.5, 0, 0);
			checkS(P + "a survival player's crossbow finds bolts in the inventory, a bow does not", () -> {
				fake.getInventory().setItem(9, new ItemStack(item, 16));
				return fake.getProjectile(crossbow).is(item) && !fake.getProjectile(new ItemStack(Items.BOW)).is(item);
			});
			fake.getInventory().clearContent();
			checkS(P + "a crossbow shot makes a harpoon bolt owned by the shooter that picks up as a Harpoon Bolt; it flies true underwater (inertia 0.99)", () -> {
				AbstractArrow a = ((ArrowItem) item).createArrow(l, st, fake, crossbow);
				boolean ok = a instanceof HarpoonBolt b && b.getType() == HarpoonBolts.TYPE && b.getPickupItemStackOrigin().is(item) && b.getOwner() == fake
					&& b.waterInertiaForTest() == HarpoonBolts.WATER_INERTIA;
				a.discard();
				return ok;
			});

			// ---- a real hit: a husk 8 blocks from the shooter is hurt and pulled 3 blocks toward it (velocity 3 x 0.2484 + hop)
			Mob target = husk(l, 24.5, 38.5, spawned);
			checkS(P + "hit: a husk 8 blocks away is hurt and pulled toward the shooter (velocity -z 0.745, hop 0.2)", () -> {
				float hp = target.getHealth();
				HarpoonBolt b = bolt(l, item, fake, crossbow, target);
				b.hitForTest(new EntityHitResult(target));
				Vec3 v = target.getDeltaMovement();
				boolean ok = target.getHealth() < hp && close(v.x, 0.0, 1e-3) && close(v.y, HarpoonBolts.PULL_HOP, 1e-3)
					&& close(v.z, -HarpoonBolts.pullSpeed(HarpoonBolts.PULL_BLOCKS), 1e-3);
				if (!ok) info("harpoon_bolt hit: hp " + hp + "->" + target.getHealth() + " velocity " + v);
				return ok;
			});

			// ---- x1.5 damage against a target standing in water (same bolt, same speed; the base damage is put back after)
			Mob dry = husk(l, 30.5, 34.5, spawned);
			Mob wet = husk(l, 34.5, 34.5, spawned);
			BlockPos water = BlockPos.containing(wet.getX(), Y, wet.getZ());
			l.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
			placed.add(water);
			wet.tickCount++;
			wet.tick(); // the in-water flag is updated by the entity's own tick
			checkS(P + "x1.5 damage to a target in water (dry vs wet husk, same shot), base damage restored", () -> {
				if (!wet.isInWater() || dry.isInWater()) {
					info("harpoon_bolt water: wet.isInWater=" + wet.isInWater() + " dry.isInWater=" + dry.isInWater());
					return false;
				}
				float d0 = dry.getHealth(), w0 = wet.getHealth();
				HarpoonBolt bd = bolt(l, item, fake, crossbow, dry);
				bd.hitForTest(new EntityHitResult(dry));
				HarpoonBolt bw = bolt(l, item, fake, crossbow, wet);
				bw.hitForTest(new EntityHitResult(wet));
				double dd = d0 - dry.getHealth(), dw = w0 - wet.getHealth();
				boolean ok = dd > 0 && close(dw / dd, HarpoonBolts.WATER_MULT, 0.02) && bw.baseDamageForTest() == 2.0;
				if (!ok) info("harpoon_bolt water: dry dmg " + dd + " wet dmg " + dw + " base after " + bw.baseDamageForTest());
				return ok;
			});
			l.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
			placed.remove(water);

			// ---- who is not pulled, and the pull without a shooter (a dispenser's bolt) or too close
			checkS(P + "never pulls a boss (a wither is not moved)", () -> {
				var wither = EntityTypes.WITHER.create(l, EntitySpawnReason.COMMAND);
				wither.snapTo(40.5, Y, 30.5, 0, 0);
				try {
					return !HarpoonBolts.canPull(wither) && HarpoonBolts.pull(wither, fake.position(), new Vec3(0, 0, 3)) == 0.0
						&& wither.getDeltaMovement().horizontalDistance() < 1e-6;
				} finally {
					wither.discard();
				}
			});
			Mob loose = husk(l, 40.5, 40.5, spawned);
			checkS(P + "no shooter (dispenser): pulled 3 blocks back along the bolt's path", () -> {
				double blocks = HarpoonBolts.pull(loose, null, new Vec3(0.0, -0.2, 3.0));
				Vec3 v = loose.getDeltaMovement();
				return blocks == HarpoonBolts.PULL_BLOCKS && close(v.z, -HarpoonBolts.pullSpeed(3.0), 1e-3) && close(v.x, 0.0, 1e-3) && close(v.y, HarpoonBolts.PULL_HOP, 1e-3);
			});
			Mob near = husk(l, 44.5, 40.5, spawned);
			checkS(P + "a target 2.5 blocks away is pulled only 1 block (stops 1.5 short of the shooter)", () -> {
				double blocks = HarpoonBolts.pull(near, new Vec3(44.5, Y, 38.0), new Vec3(0, 0, -3));
				Vec3 v = near.getDeltaMovement();
				return close(blocks, 1.0, 1e-6) && close(v.z, -HarpoonBolts.pullSpeed(1.0), 1e-3);
			});

			// ---- calibration on real physics: an armor stand (moves like any living body, no AI) on stone slides 3 blocks
			for (int x = 34; x <= 47; x++) for (int z = 44; z <= 46; z++) {
				BlockPos p = new BlockPos(x, Y - 1, z);
				l.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
				placed.add(p);
			}
			var stand = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			stand.snapTo(44.5, Y, 45.5, 0, 0);
			l.addFreshEntity(stand);
			spawned.add(stand);
			for (int i = 0; i < 3; i++) {
				stand.tickCount++;
				stand.tick();
			}
			checkS(P + "pull on real physics: a body on stone 8 blocks away slides 3 blocks toward the shooter (2.6-3.4)", () -> {
				double x0 = stand.getX(), z0 = stand.getZ();
				double blocks = HarpoonBolts.pull(stand, new Vec3(36.5, Y, 45.5), Vec3.ZERO);
				for (int i = 0; i < 60; i++) {
					stand.tickCount++;
					stand.tick();
				}
				double dx = x0 - stand.getX(), dz = Math.abs(stand.getZ() - z0);
				boolean ok = blocks == HarpoonBolts.PULL_BLOCKS && dx >= 2.6 && dx <= 3.4 && dz < 0.05 && stand.onGround();
				info("harpoon_bolt pull calibration: slid " + String.format(java.util.Locale.ROOT, "%.3f", dx) + " blocks (dz " + String.format(java.util.Locale.ROOT, "%.3f", dz) + ", onGround " + stand.onGround() + ")");
				return ok;
			});
		} finally {
			for (Entity e : spawned) if (!e.isRemoved()) e.discard();
			for (BlockPos p : placed) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			fake.getInventory().clearContent();
		}
	}
}
