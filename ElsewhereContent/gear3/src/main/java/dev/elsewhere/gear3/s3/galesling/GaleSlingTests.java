package dev.elsewhere.gear3.s3.galesling;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gale Sling self-test block (s-galesling), called from the gear3 self-test after the per-item checks (which already cover the
 * icon, item definition, lang name/lore, JEI line, durability, the calling tag, the recipe and the Breeze Temple loot hook).
 * Synchronous on a FakePlayer in the gear3 arena (flat world, chunks around 40,100,40 forced); every entity it makes is removed.
 */
public final class GaleSlingTests {
	private GaleSlingTests() {}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		BlockPos base = new BlockPos(40, 100, 40);
		Item item = Gear3Items.BY_ID.get(GaleSling.ID);
		check("Gale Sling: catalogue item is a GaleSlingItem (a ProjectileWeaponItem)", item instanceof GaleSlingItem, item);
		if (!(item instanceof GaleSlingItem sling)) return;
		List<Entity> made = new ArrayList<>();
		FakePlayer p = FakePlayer.get(l);
		try {
			p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 90.0F, 0.0F); // facing -X, away from the other blocks' mobs
			p.getInventory().clearContent();
			ItemStack st = new ItemStack(sling);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);

			// ---- registration and numbers (GEAR.md A5: 4 dmg, 0.5 s charge, cobblestone or gravel, wind charge -> wind charge)
			check("Gale Sling: projectile type elsewhere:gale_sling_stone registered", GaleSling.STONE != null
				&& Gear3Content.id(GaleSling.STONE_ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(GaleSling.STONE)));
			check("Gale Sling: ammo is cobblestone, gravel or a wind charge, nothing else", GaleSlingItem.AMMO.test(new ItemStack(Items.COBBLESTONE))
				&& GaleSlingItem.AMMO.test(new ItemStack(Items.GRAVEL)) && GaleSlingItem.AMMO.test(new ItemStack(Items.WIND_CHARGE))
				&& !GaleSlingItem.AMMO.test(new ItemStack(Items.ARROW)) && !GaleSlingItem.AMMO.test(new ItemStack(Items.STONE))
				&& !GaleSlingItem.AMMO.test(new ItemStack(Items.COBBLED_DEEPSLATE)) && sling.getAllSupportedProjectiles() == GaleSlingItem.AMMO);
			check("Gale Sling: whirled like a thrown weapon (trident wind-up), held until released", sling.getUseAnimation(st) == ItemUseAnimation.TRIDENT
				&& sling.getUseDuration(st, p) == GaleSlingItem.USE_TICKS);
			check("Gale Sling: full charge at 0.5 s (power 1.0 at 10 ticks, 0.417 at 5, a 1-tick tap below 0.1)", near(GaleSling.power(10), 1.0)
				&& near(GaleSling.power(20), 1.0) && near(GaleSling.power(5), 0.41667) && GaleSling.power(1) < GaleSling.MIN_POWER && GaleSling.power(2) >= GaleSling.MIN_POWER,
				GaleSling.power(5));
			check("Gale Sling: 4 damage at full charge, scaled by charge", near(GaleSling.stoneDamage(1.0F), 4.0) && near(GaleSling.stoneDamage(0.5F), 2.0));
			boolean feather = BuiltInRegistries.ITEM.containsKey(GaleSling.ZEPHYR_FEATHER);
			check("Gale Sling: 384 durability, repaired with Zephyr Feathers (repairs_gale_sling tag), not with sticks", st.getMaxDamage() == 384
				&& !st.isValidRepairItem(new ItemStack(Items.STICK))
				&& (!feather || st.isValidRepairItem(new ItemStack(BuiltInRegistries.ITEM.getValue(GaleSling.ZEPHYR_FEATHER)))), st.getMaxDamage());
			info("Gale Sling: arsenal 3.0 Marksman multiplier " + (GaleSling.marksmanLinked() ? "linked (x" + GaleSling.marksmanMult(p) + " for this player)"
				: "not in this environment (2.0 devlib): stones use x1.0"));

			// ---- no ammo: no whirl
			checkS("Gale Sling: with no ammo it does not start whirling", () -> {
				boolean refused = !sling.use(l, p, InteractionHand.MAIN_HAND).consumesAction() && !p.isUsingItem();
				p.stopUsingItem();
				return refused;
			});

			// ---- a full whirl with cobblestone: use + release
			p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 2));
			checkS("Gale Sling: full whirl + release looses a stone, uses 1 cobblestone and 1 durability", () -> {
				if (!sling.use(l, p, InteractionHand.MAIN_HAND).consumesAction() || !p.isUsingItem()) return false;
				boolean released = sling.releaseUsing(st, l, p, GaleSlingItem.USE_TICKS - GaleSling.CHARGE_TICKS);
				p.stopUsingItem();
				SlingStone stone = nearestStone(l, p);
				if (stone != null) made.add(stone);
				return released && stone != null && p.getInventory().getItem(9).getCount() == 1 && st.getDamageValue() == 1 && stone.getOwner() == p;
			});
			checkS("Gale Sling: the loosed stone has full-charge damage, the pebble look and ~2 blocks/tick", () -> {
				SlingStone stone = nearestStone(l, p);
				if (stone == null) return false;
				double speed = stone.getDeltaMovement().length();
				return near(stone.damage(), 4.0) && stone.isFull() && !stone.isGravel() && speed > 1.8 && speed < 2.2
					&& GaleSling.STONE_MODEL.equals(stone.getItem().get(DataComponents.ITEM_MODEL));
			});
			checkS("Gale Sling: a 1-tick tap looses nothing and uses nothing", () -> {
				boolean released = sling.releaseUsing(st, l, p, GaleSlingItem.USE_TICKS - 1);
				return !released && p.getInventory().getItem(9).getCount() == 1 && st.getDamageValue() == 1;
			});
			for (Entity e : made) e.discard();
			made.clear();

			// ---- half charge: half damage, slower, no crit trail
			checkS("Gale Sling: a 5-tick whirl looses a weaker, slower stone (5/12 charge)", () -> {
				Projectile shot = GaleSlingItem.loose(l, p, InteractionHand.MAIN_HAND, st, p.getInventory().getItem(9), GaleSling.power(5));
				made.add(shot);
				return shot instanceof SlingStone stone && near(stone.damage(), 4.0 * 0.41667) && !stone.isFull()
					&& stone.getDeltaMovement().length() < 1.0 && p.getInventory().getItem(9).isEmpty();
			});
			for (Entity e : made) e.discard();
			made.clear();

			// ---- gravel
			p.getInventory().setItem(10, new ItemStack(Items.GRAVEL, 3));
			checkS("Gale Sling: gravel ammo looses a gravel pebble and uses 1 gravel", () -> {
				Projectile shot = GaleSlingItem.loose(l, p, InteractionHand.MAIN_HAND, st, p.getProjectile(st), 1.0F);
				made.add(shot);
				return shot instanceof SlingStone stone && stone.isGravel() && GaleSling.GRAVEL_MODEL.equals(stone.getItem().get(DataComponents.ITEM_MODEL))
					&& p.getInventory().getItem(10).getCount() == 2;
			});
			for (Entity e : made) e.discard();
			made.clear();

			// ---- wind charge held in the off hand wins over the gravel in the inventory
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WIND_CHARGE, 1));
			checkS("Gale Sling: a wind charge in the off hand is flung as a wind charge first (held ammo wins), faster than by hand", () -> {
				ItemStack ammo = p.getProjectile(st);
				if (!ammo.is(Items.WIND_CHARGE)) return false;
				Projectile shot = GaleSlingItem.loose(l, p, InteractionHand.MAIN_HAND, st, ammo, 1.0F);
				made.add(shot);
				double speed = shot.getDeltaMovement().length();
				return shot instanceof WindCharge && shot.getOwner() == p && p.getOffhandItem().isEmpty() && p.getInventory().getItem(10).getCount() == 2
					&& speed > 2.0 && speed < 2.5;
			});
			for (Entity e : made) e.discard();
			made.clear();
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, st);

			// ---- PvP neutrality of the Marksman multiplier (pure numbers)
			checkS("Gale Sling: a stone carries the Marksman multiplier to mobs only (players take the plain 4)", () -> {
				SlingStone stone = new SlingStone(l, p, false, 1.0F, 1.15F);
				Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
				boolean ok = near(stone.damageAgainst(m), 4.6) && near(stone.damageAgainst(p), 4.0);
				stone.discard();
				m.discard();
				return ok;
			});

			// ---- a stone in flight hits a mob (real projectile ticking, no shortcut)
			checkS("Gale Sling: a stone in flight hits a husk for 4 x the thrower's Marksman multiplier, then crumbles", () -> {
				Mob husk = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
				husk.snapTo(base.getX() - 4.5, base.getY(), base.getZ() + 0.5, 0, 0);
				husk.setNoAi(true);
				husk.getAttribute(Attributes.ARMOR).setBaseValue(0.0); // a husk's natural 2 armor would make the hit 3.94
				l.addFreshEntity(husk);
				made.add(husk);
				float hp = husk.getHealth();
				float mult = GaleSling.marksmanMult(p);
				SlingStone stone = new SlingStone(l, p, false, 1.0F, mult);
				stone.snapTo(base.getX() - 1.0, base.getY() + 1.0, base.getZ() + 0.5, 0, 0);
				stone.setDeltaMovement(new Vec3(-1.0, 0.0, 0.0));
				l.addFreshEntity(stone);
				made.add(stone);
				l.getEntities(stone, stone.getBoundingBox().inflate(8.0)); // warm-up query (GOTCHAS: first lookup after adding can miss)
				for (int i = 0; i < 8 && !stone.isRemoved(); i++) {
					stone.tickCount++;
					stone.tick();
				}
				var src = husk.getLastDamageSource();
				return stone.isRemoved() && near(hp - husk.getHealth(), 4.0 * mult) && src != null && src.getDirectEntity() == stone && src.getEntity() == p;
			});
			for (Entity e : made) e.discard();
			made.clear();

			// ---- Windwright trade (GEAR.md source) and its switch
			Identifier ww = Gear3Content.id("windwright");
			if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ww) || !BuiltInRegistries.ITEM.containsKey(GaleSling.ZEPHYR_FEATHER)) {
				info("Gale Sling: Windwright/Zephyr Feather not in this environment (petalwind absent); trade check skipped");
			} else {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(ww);
				checkS("Gale Sling: a new Windwright sells it for 6 Zephyr Feathers + 1 String", () -> {
					GaleSling.setTradeEnabledForTest(true);
					return slingOffers(l, type, sling) == 1;
				});
				checkS("Gale Sling: gale_sling_trade off -> new Windwrights do not sell it", () -> {
					GaleSling.setTradeEnabledForTest(false);
					return slingOffers(l, type, sling) == 0;
				});
				GaleSling.setTradeEnabledForTest(null);
			}

			// ---- sounds, lang, client art (data the client reads)
			JsonObject sounds = mergedJson("assets/elsewhere/sounds.json"), lang = mergedJson("assets/elsewhere/lang/en_us.json");
			String[] events = {"item.gale_sling.whirl", "item.gale_sling.loose", "entity.gale_sling_stone.hit"};
			checkS("Gale Sling: 3 sound events registered, each in sounds.json with a subtitle and existing vanilla sounds", () -> {
				for (String ev : events) {
					if (!BuiltInRegistries.SOUND_EVENT.containsKey(Gear3Content.id(ev)) || sounds == null || !sounds.has(ev)) return false;
					JsonObject e = sounds.getAsJsonObject(ev);
					if (!e.has("subtitle") || lang == null || !lang.has(e.get("subtitle").getAsString())) return false;
					for (JsonElement snd : e.getAsJsonArray("sounds")) {
						JsonObject o = snd.getAsJsonObject();
						if (!"event".equals(o.get("type").getAsString()) || !BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.parse(o.get("name").getAsString()))) return false;
					}
				}
				return GaleSling.WHIRL != null && GaleSling.LOOSE != null && GaleSling.HIT != null;
			});
			check("Gale Sling: the stone has an entity name", lang != null && lang.has("entity.elsewhere." + GaleSling.STONE_ID));
			checkS("Gale Sling: whirl animation (using_item -> use_cycle over 4 frames, each a model + 16x16 texture on the sling's model)", () -> {
				JsonObject def = json("assets/elsewhere/items/" + GaleSling.ID + ".json");
				if (def == null) return false;
				JsonObject m = def.getAsJsonObject("model");
				if (!"minecraft:using_item".equals(m.get("property").getAsString())) return false;
				if (!("elsewhere:item/" + GaleSling.ID).equals(m.getAsJsonObject("on_false").get("model").getAsString())) return false;
				JsonObject on = m.getAsJsonObject("on_true");
				if (!"minecraft:use_cycle".equals(on.get("property").getAsString()) || on.getAsJsonArray("entries").size() != 3) return false;
				for (int f = 0; f < 4; f++) {
					String n = GaleSling.ID + "_whirl_" + f;
					JsonObject model = json("assets/elsewhere/models/item/" + n + ".json");
					if (model == null || !def.toString().contains("elsewhere:item/" + n) || !("elsewhere:item/" + GaleSling.ID).equals(model.get("parent").getAsString())
						|| !("elsewhere:item/" + n).equals(model.getAsJsonObject("textures").get("layer0").getAsString()) || !texture16(n)) return false;
				}
				return true;
			});
			checkS("Gale Sling: stone and gravel pebble item models (item definition + model + 16x16 texture)", () -> {
				for (Identifier id : new Identifier[] {GaleSling.STONE_MODEL, GaleSling.GRAVEL_MODEL}) {
					String n = id.getPath();
					JsonObject def = json("assets/elsewhere/items/" + n + ".json"), model = json("assets/elsewhere/models/item/" + n + ".json");
					if (def == null || model == null || !def.toString().contains("elsewhere:item/" + n)
						|| !("elsewhere:item/" + n).equals(model.getAsJsonObject("textures").get("layer0").getAsString()) || !texture16(n)) return false;
				}
				return true;
			});
		} finally {
			for (Entity e : made) e.discard();
			GaleSling.setTradeEnabledForTest(null);
			p.stopUsingItem();
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		}
		info("Gale Sling block done");
	}

	/** The nearest live sling stone thrown by {@code p} (two lookups: the first after adding an entity can miss, see GOTCHAS). */
	static SlingStone nearestStone(ServerLevel l, FakePlayer p) {
		AABB box = new AABB(p.position(), p.position()).inflate(6.0);
		l.getEntities(GaleSling.STONE, box, e -> true);
		SlingStone best = null;
		for (SlingStone st : l.getEntities(GaleSling.STONE, box, e -> e.isAlive() && e.getOwner() == p))
			if (best == null || st.distanceToSqr(p) < best.distanceToSqr(p)) best = st;
		return best;
	}

	/** How many Gale Sling offers a freshly made trader of {@code type} has at the designed price. */
	static int slingOffers(ServerLevel l, EntityType<?> type, Item sling) {
		Entity e = type.create(l, EntitySpawnReason.COMMAND);
		if (!(e instanceof AbstractVillager v)) return -1;
		e.snapTo(40.5, 100, 40.5, 0, 0);
		int n = 0;
		for (MerchantOffer o : v.getOffers()) {
			if (!o.getResult().is(sling)) continue;
			ItemStack a = o.getBaseCostA(), b = o.getCostB();
			if (BuiltInRegistries.ITEM.getKey(a.getItem()).equals(GaleSling.ZEPHYR_FEATHER) && a.getCount() == GaleSling.TRADE_FEATHERS && b.is(Items.STRING) && b.getCount() == 1) n++;
			else n += 100; // a sling offer at the wrong price fails the check
		}
		e.discard();
		return n;
	}

	static boolean texture16(String name) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/item/" + name + ".png")) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			if (img.getWidth() != 16 || img.getHeight() != 16) return false;
			int opaque = 0;
			for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
			return opaque >= 8;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Every copy of a resource on the classpath merged, first copy winning per key (as the resource stack and the merged jar do). */
	static JsonObject mergedJson(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
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
