package dev.elsewhere.gear3.s3.echoarrow;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonArray;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Echo Arrow self-test block (gear3 self-test, after the per-mechanic blocks). Runs synchronously on the server thread in its own
 * corner of the forced test area (y 120, x 52-76, z 18-62) and removes everything it spawns. Check names start with "echo_arrow:".
 */
public final class EchoArrowSelfTest {
	private EchoArrowSelfTest() {}

	static final String P = "echo_arrow: ";

	static Mob husk(ServerLevel l, Vec3 at, List<Entity> spawned) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		m.setNoGravity(true);
		l.addFreshEntity(m);
		spawned.add(m);
		return m;
	}

	static boolean glowsFor(LivingEntity e, int ticks) {
		MobEffectInstance g = e.getEffect(MobEffects.GLOWING);
		return g != null && g.getDuration() == ticks;
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
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
		Item item = EchoArrows.item();
		check(P + "catalogue item registered as an EchoArrowItem (ArrowItem)", item instanceof EchoArrowItem, item);
		check(P + "entity type elsewhere:echo_arrow registered", EchoArrows.TYPE != null
			&& BuiltInRegistries.ENTITY_TYPE.getValue(Gear3Content.id(EchoArrows.ID)) == EchoArrows.TYPE);
		if (!(item instanceof EchoArrowItem) || EchoArrows.TYPE == null) return;
		ItemStack st = new ItemStack(item);

		checkS(P + "in #minecraft:arrows: bows and crossbows load it", () -> st.is(ItemTags.ARROWS)
			&& ((ProjectileWeaponItem) Items.BOW).getAllSupportedProjectiles().test(st) && ((ProjectileWeaponItem) Items.CROSSBOW).getAllSupportedProjectiles().test(st));
		checkS(P + "Marksman calling item (#elsewhere:calling_weapon/marksman)", () -> dev.elsewhere.arsenal.spell.Calling.itemPath(st) == dev.elsewhere.arsenal.spell.Calling.Path.MARKSMAN);
		checkS(P + "stacks to 64", () -> st.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) == 64);
		checkS(P + "recipe: 8 arrows around a Resonant Shard make 8", () -> {
			JsonObject r = json("data/elsewhere/recipe/echo_arrow.json");
			if (r == null || !"minecraft:crafting_shaped".equals(r.get("type").getAsString())) return false;
			JsonObject res = r.getAsJsonObject("result");
			if (!"elsewhere:echo_arrow".equals(res.get("id").getAsString()) || res.get("count").getAsInt() != 8) return false;
			JsonArray pat = r.getAsJsonArray("pattern");
			JsonObject key = r.getAsJsonObject("key");
			int arrows = 0, shards = 0;
			for (var row : pat) for (char c : row.getAsString().toCharArray()) {
				if (c == ' ') continue;
				String id = key.get(String.valueOf(c)).getAsString();
				if (id.equals("minecraft:arrow")) arrows++;
				else if (id.equals("elsewhere:resonant_shard")) shards++;
				else return false;
			}
			return arrows == 8 && shards == 1;
		});
		checkS(P + "entity texture is a 32x32 arrow-layout PNG (shaft, head and fletching painted)", () -> {
			try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/entity/projectiles/echo_arrow.png")) {
				if (in == null) return false;
				var img = ImageIO.read(in);
				// vanilla arrow layout: row 2 is the shaft, x 13-15 the head, x 0-3 rows 0-4 the fletching, rows 5-9 the nock cap
				return img.getWidth() == 32 && img.getHeight() == 32 && (img.getRGB(8, 2) >>> 24) != 0 && (img.getRGB(14, 2) >>> 24) != 0
					&& (img.getRGB(1, 0) >>> 24) != 0 && (img.getRGB(7, 7) >>> 24) != 0 && (img.getRGB(20, 20) >>> 24) == 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});
		checkS(P + "entity name lang line", () -> "Echo Arrow".equals(lang().has("entity.elsewhere.echo_arrow") ? lang().get("entity.elsewhere.echo_arrow").getAsString() : null));
		checkS(P + "dispensers fire it as an echo arrow (pickup allowed)", () -> {
			if (!(DispenserBlock.DISPENSER_REGISTRY.get(item) instanceof ProjectileDispenseBehavior)) return false;
			var proj = ((ArrowItem) item).asProjectile(l, new Vec3(64.5, 120, 40.5), st, Direction.UP);
			boolean ok = proj instanceof EchoArrowEntity a && a.pickup == AbstractArrow.Pickup.ALLOWED && a.getPickupItemStackOrigin().is(item);
			proj.discard();
			return ok;
		});

		if (!EchoArrows.ENABLED) {
			info("echo_arrow kill switch is OFF (config/elsewhere-overhaul3.json): pulse and silence not tested");
			checkS(P + "kill switch off: no pulse", () -> EchoArrows.pulse(l, new Vec3(64.5, 120, 24.5), null) == 0);
			return;
		}

		FakePlayer fake = FakePlayer.get(l);
		fake.snapTo(40.5, 100, 40.5, 0, 0);
		ItemStack bow = new ItemStack(Items.BOW);
		List<Entity> spawned = new ArrayList<>();
		try {
			checkS(P + "a bow shot makes a silent, vibration-dampening echo arrow that picks up as echo_arrow", () -> {
				AbstractArrow a = ((ArrowItem) item).createArrow(l, st, fake, bow);
				return a instanceof EchoArrowEntity e && e.getType() == EchoArrows.TYPE && e.isSilent() && e.dampensVibrations()
					&& e.getPickupItemStackOrigin().is(item) && e.getOwner() == fake;
			});

			// ---- silent flight: a real sculk sensor 3 blocks away hears a plain arrow's loosing and landing, never the echo arrow's
			checkS(P + "silent flight: a sculk sensor ignores its loosing and landing (control: a plain arrow is heard)", () -> {
				BlockPos sensor = new BlockPos(70, 119, 40);
				l.setBlock(sensor, Blocks.SCULK_SENSOR.defaultBlockState(), 3);
				try {
					if (!(l.getBlockEntity(sensor) instanceof SculkSensorBlockEntity be)) return false;
					var sel = be.getVibrationData().getSelectionStrategy();
					Vec3 from = new Vec3(67.5, 120, 40.5);
					BlockPos landed = sensor.offset(-3, 0, 0);
					var stone = Blocks.STONE.defaultBlockState();
					EchoArrowEntity echo = (EchoArrowEntity) ((ArrowItem) item).createArrow(l, st, fake, bow);
					echo.snapTo(from.x, from.y, from.z, 0, 0);
					sel.startOver();
					echo.gameEvent(GameEvent.PROJECTILE_SHOOT, fake);
					l.gameEvent(GameEvent.PROJECTILE_LAND, landed, GameEvent.Context.of(echo, stone));
					boolean echoHeard = sel.chosenCandidate(l.getGameTime() + 1).isPresent();
					boolean echoValid = be.getVibrationUser().isValidVibration(GameEvent.PROJECTILE_LAND, GameEvent.Context.of(echo, stone));
					Arrow plain = new Arrow(l, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
					sel.startOver();
					plain.gameEvent(GameEvent.PROJECTILE_SHOOT, fake);
					boolean shotHeard = sel.chosenCandidate(l.getGameTime() + 1).isPresent();
					sel.startOver();
					l.gameEvent(GameEvent.PROJECTILE_LAND, landed, GameEvent.Context.of(plain, stone));
					boolean landHeard = sel.chosenCandidate(l.getGameTime() + 1).isPresent();
					sel.startOver();
					if (echoHeard || echoValid || !shotHeard || !landHeard)
						info("echo_arrow silence: echoHeard=" + echoHeard + " echoValid=" + echoValid + " plainShotHeard=" + shotHeard + " plainLandHeard=" + landHeard);
					return !echoHeard && !echoValid && shotHeard && landHeard;
				} finally {
					l.setBlock(sensor, Blocks.AIR.defaultBlockState(), 3);
				}
			});

			// ---- the pulse itself: radius 6 to the nearest hitbox point, 5 s of Glowing, mobs only
			Vec3 c1 = new Vec3(64.5, 120, 24.5);
			Mob near = husk(l, c1.add(3, 0, 0), spawned);
			Mob edge = husk(l, c1.add(0, 0, 6.2), spawned); // hitbox half-width 0.3: nearest point 5.9 away
			Mob far = husk(l, c1.add(-7.0, 0, 0), spawned); // nearest point 6.7 away
			var stand = EntityTypes.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
			stand.snapTo(c1.x, c1.y, c1.z + 2, 0, 0);
			stand.setNoGravity(true);
			l.addFreshEntity(stand);
			spawned.add(stand);
			l.getEntitiesOfClass(Mob.class, AABB.ofSize(c1, 20, 20, 20)); // warm-up query (GOTCHAS: first query after addFreshEntity)
			checkS(P + "pulse: mobs within 6 blocks glow 5 s (3 and 5.9 away yes, 6.7 away no, armor stand no)", () -> {
				int n = EchoArrows.pulse(l, c1, fake);
				boolean ok = n >= 2 && glowsFor(near, EchoArrows.GLOW_TICKS) && glowsFor(edge, EchoArrows.GLOW_TICKS) && !far.hasEffect(MobEffects.GLOWING)
					&& !stand.hasEffect(MobEffects.GLOWING);
				if (!ok) info("echo_arrow pulse: n=" + n + " near=" + near.getEffect(MobEffects.GLOWING) + " edge=" + edge.getEffect(MobEffects.GLOWING)
					+ " far=" + far.hasEffect(MobEffects.GLOWING) + " stand=" + stand.hasEffect(MobEffects.GLOWING));
				return ok;
			});

			// ---- end to end: a shot echo arrow hits a husk; it and a neighbour glow, a husk 9 blocks away does not; the pulse fires once
			Vec3 c2 = new Vec3(64.5, 120, 56.5);
			Mob target = husk(l, c2, spawned);
			Mob neighbour = husk(l, c2.add(0, 0, 4), spawned);
			Mob distant = husk(l, c2.add(0, 0, -9), spawned);
			l.getEntitiesOfClass(Mob.class, AABB.ofSize(c2, 24, 24, 24)); // warm-up query
			checkS(P + "shot: the hit husk is damaged, it and a husk 4 blocks away glow 5 s, one 9 away does not; one pulse per arrow", () -> {
				EchoArrowEntity a = (EchoArrowEntity) ((ArrowItem) item).createArrow(l, st, fake, bow);
				a.snapTo(c2.x - 2.5, c2.y + 1.0, c2.z, 0, 0);
				a.shoot(1.0, 0.0, 0.0, 3.0F, 0.0F);
				l.addFreshEntity(a);
				spawned.add(a);
				l.getEntitiesOfClass(Mob.class, AABB.ofSize(c2, 24, 24, 24));
				float hp = target.getHealth();
				for (int i = 0; i < 6 && !a.isRemoved() && !a.hasPulsed(); i++) {
					a.tickCount++;
					a.tick();
				}
				boolean ok = a.hasPulsed() && target.getHealth() < hp && glowsFor(target, EchoArrows.GLOW_TICKS) && glowsFor(neighbour, EchoArrows.GLOW_TICKS)
					&& !distant.hasEffect(MobEffects.GLOWING);
				if (!ok) info("echo_arrow shot: pulsed=" + a.hasPulsed() + " removed=" + a.isRemoved() + " hp " + hp + "->" + target.getHealth() + " target="
					+ target.getEffect(MobEffects.GLOWING) + " neighbour=" + neighbour.getEffect(MobEffects.GLOWING) + " distant=" + distant.hasEffect(MobEffects.GLOWING));
				return ok;
			});
			checkS(P + "a landed arrow knocked loose does not pulse again", () -> {
				EchoArrowEntity a = (EchoArrowEntity) ((ArrowItem) item).createArrow(l, st, fake, bow);
				BlockPos floor = new BlockPos(56, 117, 56);
				l.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
				Mob watcher = husk(l, new Vec3(57.5, 118, 58.5), spawned);
				try {
					a.snapTo(56.5, 120, 56.5, 0, 90);
					a.shoot(0.0, -1.0, 0.0, 2.0F, 0.0F);
					l.addFreshEntity(a);
					spawned.add(a);
					for (int i = 0; i < 6 && !a.hasPulsed(); i++) {
						a.tickCount++;
						a.tick();
					}
					boolean first = a.hasPulsed() && glowsFor(watcher, EchoArrows.GLOW_TICKS);
					watcher.removeEffect(MobEffects.GLOWING);
					// knock it loose and let it land again (the block under it is replaced)
					l.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
					l.setBlock(floor.below(), Blocks.STONE.defaultBlockState(), 3);
					a.setDeltaMovement(0, -0.5, 0);
					for (int i = 0; i < 10; i++) {
						a.tickCount++;
						a.tick();
					}
					boolean quiet = !watcher.hasEffect(MobEffects.GLOWING);
					if (!first || !quiet) info("echo_arrow re-land: first=" + first + " quietAfter=" + quiet);
					return first && quiet;
				} finally {
					l.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
					l.setBlock(floor.below(), Blocks.AIR.defaultBlockState(), 3);
				}
			});
		} finally {
			for (Entity e : spawned) if (!e.isRemoved()) e.discard();
		}
	}
}
