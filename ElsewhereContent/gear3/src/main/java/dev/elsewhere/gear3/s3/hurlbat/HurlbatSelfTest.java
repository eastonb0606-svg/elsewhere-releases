package dev.elsewhere.gear3.s3.hurlbat;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.AABB;

/**
 * Hurlbat self-test block (gear3 self-test, after the per-mechanic blocks). Synchronous on the server thread, on FakePlayers of
 * its own, at y 140, x 18-34, z 66-78 (inside the forced test chunks, clear of the other blocks). Removes everything it spawns.
 * Check names start with "hurlbat: ".
 */
public final class HurlbatSelfTest {
	private HurlbatSelfTest() {}

	static final String P = "hurlbat: ";
	static final double Y = 140.0;

	static FakePlayer fake(ServerLevel l, String name, double x, double z, float yRot) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
		p.snapTo(x, Y, z, yRot, 0.0F);
		p.setYHeadRot(yRot);
		p.setNoGravity(true);
		p.setHealth(p.getMaxHealth());
		p.getInventory().clearContent();
		p.stopUsingItem();
		return p;
	}

	static Mob cow(ServerLevel l, double x, double z, List<Entity> spawned) {
		Mob m = EntityTypes.COW.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, Y, z, 0.0F, 0.0F);
		m.setNoAi(true);
		m.setNoGravity(true);
		l.addFreshEntity(m);
		spawned.add(m);
		return m;
	}

	static HurlbatEntity hurlbat(ServerLevel l, LivingEntity owner, double x, double z, List<Entity> spawned) {
		HurlbatEntity h = owner != null ? new HurlbatEntity(l, owner, new ItemStack(Hurlbats.item())) : new HurlbatEntity(Hurlbats.ENTITY, l);
		h.snapTo(x, Y + 0.5, z, 0.0F, 0.0F);
		h.setNoGravity(true);
		if (owner == null) h.pickup = AbstractArrow.Pickup.ALLOWED; // as a thrown one whose thrower has left
		l.addFreshEntity(h);
		spawned.add(h);
		return h;
	}

	static int count(Player p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	/** Every copy of a resource on the classpath, merged per key (the merged Elsewhere jar carries one per module). */
	static JsonObject merged(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return null;
		}
		return out;
	}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		List<Entity> spawned = new ArrayList<>();
		Item item = Hurlbats.item();
		try {
			runChecks(s, l, item, spawned);
		} finally {
			HurlbatEntity.forceBreak = null;
			Hurlbats.setEnabledForTest(null);
			for (Entity e : spawned) if (!e.isRemoved()) e.discard();
			for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(14, Y - 6, 62, 38, Y + 6, 82), e -> e instanceof HurlbatEntity || e instanceof ItemEntity)) e.discard();
		}
	}

	static void runChecks(MinecraftServer s, ServerLevel l, Item item, List<Entity> spawned) {
		// ---- registration
		check(P + "entity type elsewhere:hrafnholt_hurlbat registered", Hurlbats.ENTITY != null
			&& Gear3Content.id(Hurlbats.ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(Hurlbats.ENTITY)));
		check(P + "item is the thrown-axe item and stacks to 8", item instanceof HurlbatItem && new ItemStack(item).getMaxStackSize() == 8,
			item + " max " + (item == null ? 0 : new ItemStack(item).getMaxStackSize()));
		checkS(P + "4 sound events registered (throw, hit, hit_ground, break)", () -> {
			for (String path : Hurlbats.SOUNDS) if (!BuiltInRegistries.SOUND_EVENT.containsKey(Gear3Content.id(path))) return false;
			return Hurlbats.THROW != null && Hurlbats.HIT != null && Hurlbats.HIT_GROUND != null && Hurlbats.BREAK != null;
		});
		checkS(P + "damage type elsewhere:hurlbat registered and tagged minecraft:is_projectile", () -> {
			var h = l.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(Hurlbats.DAMAGE_ID);
			return h.isPresent() && h.get().is(DamageTypeTags.IS_PROJECTILE);
		});
		if (!(item instanceof HurlbatItem) || Hurlbats.ENTITY == null) return; // the rest needs both; the failures above say why

		// ---- throwing: one per use, 8 -> 7, flies at 1.6, 0.6 s before the next
		FakePlayer thrower = fake(l, "hurlbat_thrower", 20.5, 66.5, 0.0F); // faces south (+z)
		thrower.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 8));
		AABB around = new AABB(14, Y - 4, 62, 38, Y + 4, 82);
		InteractionResult r = item.use(l, thrower, InteractionHand.MAIN_HAND);
		List<HurlbatEntity> flying = l.getEntitiesOfClass(HurlbatEntity.class, around, h -> h.getOwner() == thrower);
		spawned.addAll(flying);
		check(P + "throw spawns one hurlbat owned by the thrower (stack 8 -> 7)", r.consumesAction() && flying.size() == 1
			&& thrower.getMainHandItem().getCount() == 7, r + " flying=" + flying.size() + " left=" + thrower.getMainHandItem().getCount());
		if (flying.size() == 1) {
			HurlbatEntity f = flying.get(0);
			double v = f.getDeltaMovement().length();
			check(P + "thrown at 1.6 blocks/tick, towards where the thrower looks", Math.abs(v - Hurlbats.VELOCITY) < 0.25 && f.getDeltaMovement().z > 1.0,
				f.getDeltaMovement());
			check(P + "a survival throw can be picked up again", f.pickup == AbstractArrow.Pickup.ALLOWED, f.pickup);
			float m = Hurlbats.marksmanMult(thrower);
			boolean marked = f.entityTags().stream().anyMatch(t -> t.startsWith(Hurlbats.MARK_TAG));
			check(P + "Marksman multiplier rides on the throw as arsenal's mark tag (only when not 1.0)",
				m == 1.0F ? !marked : f.entityTags().contains(Hurlbats.MARK_TAG + m), "mult=" + m + " tags=" + f.entityTags());
		}
		check(P + "0.6 s between throws: a second use at once does nothing", thrower.getCooldowns().isOnCooldown(thrower.getMainHandItem())
			&& !item.use(l, thrower, InteractionHand.MAIN_HAND).consumesAction() && thrower.getMainHandItem().getCount() == 7);

		// ---- the strike: 7 damage, once
		HurlbatEntity.forceBreak = false;
		Mob target = cow(l, 24.5, 70.5, spawned);
		float before = target.getHealth();
		HurlbatEntity h1 = hurlbat(l, thrower, 24.5, 69.0, spawned);
		h1.hitForTest(target);
		check(P + "strikes for 7 (cow 10 -> 3)", Math.abs((before - target.getHealth()) - Hurlbats.DAMAGE) < 0.01, before + " -> " + target.getHealth());
		check(P + "one strike per throw (no further entity hits)", h1.hasDealtDamage());

		// ---- unbroken: glances off, lands, only its thrower picks it up
		check(P + "unbroken hurlbat glances off and stays in the world", !h1.isRemoved() && h1.pickup == AbstractArrow.Pickup.ALLOWED);
		FakePlayer other = fake(l, "hurlbat_stranger", 24.5, 72.5, 0.0F);
		h1.landForTest();
		h1.playerTouch(other);
		check(P + "someone else cannot pick up a thrown hurlbat", !h1.isRemoved() && count(other, item) == 0);
		int had = count(thrower, item);
		h1.playerTouch(thrower);
		check(P + "its thrower picks it back up", h1.isRemoved() && count(thrower, item) == had + 1, had + " -> " + count(thrower, item));
		HurlbatEntity loose = hurlbat(l, null, 24.5, 73.0, spawned);
		loose.landForTest();
		loose.playerTouch(other);
		check(P + "an ownerless hurlbat is anyone's", loose.isRemoved() && count(other, item) == 1);

		// ---- broken: gone, nothing to pick up
		HurlbatEntity.forceBreak = true;
		Mob target2 = cow(l, 28.5, 70.5, spawned);
		HurlbatEntity h2 = hurlbat(l, thrower, 28.5, 69.0, spawned);
		h2.hitForTest(target2);
		boolean dropped = !l.getEntitiesOfClass(ItemEntity.class, new AABB(26, Y - 3, 66, 31, Y + 3, 74), e -> e.getItem().is(item)).isEmpty();
		check(P + "a breaking hurlbat still strikes, then is gone (no item left)", h2.isRemoved() && !dropped && target2.getHealth() < target2.getMaxHealth());
		HurlbatEntity.forceBreak = null;

		// ---- 50% break
		RandomSource rnd = RandomSource.create(4242L);
		int breaks = 0;
		for (int i = 0; i < 4000; i++) if (Hurlbats.rollBreak(rnd)) breaks++;
		check(P + "breaks on 50% of hits (4000 rolls)", breaks > 1850 && breaks < 2150, breaks + "/4000");

		// ---- shields: a raised shield in front is disabled for 2 s; from behind the hit simply lands
		ItemStack vanillaShield = new ItemStack(Items.SHIELD);
		BlocksAttacks v = vanillaShield.get(DataComponents.BLOCKS_ATTACKS);
		check(P + "a vanilla shield stays down 2 s (40 ticks)", v != null && Hurlbats.shieldDisableTicks(v) == 40, v == null ? "no blocks_attacks" : Hurlbats.shieldDisableTicks(v));
		if (v != null) {
			HurlbatEntity.forceBreak = false;
			// the bearer faces north (-z); the hurlbat comes from the north. Zero block delay so it is raised at once (no ticking here).
			FakePlayer bearer = fake(l, "hurlbat_shieldbearer", 32.5, 72.5, 180.0F);
			ItemStack shield = instantShield(v);
			bearer.setItemInHand(InteractionHand.MAIN_HAND, shield);
			bearer.startUsingItem(InteractionHand.MAIN_HAND);
			boolean raised = bearer.isUsingItem() && bearer.getItemBlockingWith() != null;
			HurlbatEntity h3 = hurlbat(l, null, 32.5, 70.0, spawned); // ownerless: no PvP rule in the way
			float hp = bearer.getHealth();
			h3.hitForTest(bearer);
			ItemStack held = bearer.getMainHandItem();
			check(P + "a raised shield in front blocks the hit and is disabled (cooldown, lowered)", raised && bearer.getCooldowns().isOnCooldown(held)
				&& !bearer.isUsingItem() && bearer.getHealth() >= hp - 0.01F, "raised=" + raised + " cd=" + bearer.getCooldowns().isOnCooldown(held)
				+ " using=" + bearer.isUsingItem() + " hp " + hp + " -> " + bearer.getHealth());
			bearer.getCooldowns().removeCooldown(bearer.getCooldowns().getCooldownGroup(held));

			// turned away: the shield does not cover the back. A mob bears the shield here, because a self-test FakePlayer has no
			// loaded client connection, which makes it immune to all damage (ServerPlayer.isInvulnerableTo), so "the hit lands" could never hold on one.
			Mob back = cow(l, 32.5, 72.5, spawned);
			back.snapTo(32.5, Y, 72.5, 0.0F, 0.0F); // yaw 0 faces south (+z); the hurlbat comes from the north, behind it
			back.setYHeadRot(0.0F);
			back.setItemInHand(InteractionHand.MAIN_HAND, instantShield(v));
			back.startUsingItem(InteractionHand.MAIN_HAND);
			boolean raisedBack = back.isUsingItem() && back.getItemBlockingWith() != null;
			HurlbatEntity h4 = hurlbat(l, null, 32.5, 70.0, spawned);
			float hp2 = back.getHealth();
			DamageSource probe = Hurlbats.damageSource(h4, h4);
			boolean coversBack = Hurlbats.blockingShield(back, probe, Hurlbats.DAMAGE) != null;
			h4.hitForTest(back);
			check(P + "a shield turned away is not disabled and the hit lands", raisedBack && !coversBack && back.isUsingItem()
				&& back.getHealth() < hp2, "raised=" + raisedBack + " covers=" + coversBack + " using=" + back.isUsingItem() + " hp " + hp2 + " -> " + back.getHealth());
			back.stopUsingItem();
			HurlbatEntity.forceBreak = null;
		}

		// ---- Huntmaster: 50% for a bundle of 8, only on a player kill
		int drops = 0;
		RandomSource rnd2 = RandomSource.create(77L);
		for (int i = 0; i < 4000; i++) if (Hurlbats.rollHuntmasterDrop(rnd2)) drops++;
		check(P + "Huntmaster drops hurlbats 50% of the time (4000 rolls)", drops > 1850 && drops < 2150, drops + "/4000");
		Mob body = cow(l, 22.5, 76.5, spawned);
		ItemEntity bundle = Hurlbats.dropBundle(l, body);
		if (bundle != null) spawned.add(bundle);
		check(P + "the Huntmaster bundle is 8 hurlbats", bundle != null && bundle.getItem().is(item) && bundle.getItem().getCount() == Hurlbats.HUNTMASTER_COUNT);
		check(P + "a cow is not a Huntmaster (no drop hook)", !Hurlbats.isHuntmaster(EntityTypes.COW));
		check(P + "drop needs a player kill", Hurlbats.killedByPlayer(body, l.damageSources().playerAttack(thrower))
			&& !Hurlbats.killedByPlayer(cow(l, 22.5, 78.0, spawned), l.damageSources().generic()));
		int found = 0;
		for (String id : Hurlbats.HUNTMASTERS) {
			Identifier key = Identifier.parse(id);
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
				EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(key);
				check(P + "drop hook matches " + id, Hurlbats.isHuntmaster(t));
				found++;
			}
		}
		if (found == 0) info(P + "rival_hrafnholt not loaded here: Huntmaster ids checked in the merged jar");

		// ---- kill switch
		Hurlbats.setEnabledForTest(false);
		thrower.getCooldowns().removeCooldown(thrower.getCooldowns().getCooldownGroup(thrower.getMainHandItem()));
		int beforeOff = thrower.getMainHandItem().getCount();
		InteractionResult off = item.use(l, thrower, InteractionHand.MAIN_HAND);
		check(P + "kill switch gear3_hurlbat off: no throw, nothing used", !off.consumesAction() && thrower.getMainHandItem().getCount() == beforeOff
			&& l.getEntitiesOfClass(HurlbatEntity.class, around, h -> h.getOwner() == thrower && !h.isRemoved() && !spawned.contains(h)).isEmpty());
		Hurlbats.setEnabledForTest(null);

		// ---- resources: sounds, lang, Thing-day stock
		JsonObject sounds = merged("assets/elsewhere/sounds.json");
		checkS(P + "sounds.json has the 4 hurlbat events with subtitles", () -> {
			if (sounds == null) return false;
			for (String path : Hurlbats.SOUNDS) {
				JsonElement e = sounds.get(path);
				if (e == null || !e.getAsJsonObject().has("subtitle")) return false;
			}
			return true;
		});
		JsonObject lang = merged("assets/elsewhere/lang/en_us.json");
		checkS(P + "lang: entity name, 4 subtitles, 2 death messages", () -> {
			if (lang == null) return false;
			List<String> keys = new ArrayList<>(List.of("entity.elsewhere." + Hurlbats.ID, "death.attack.elsewhere.hurlbat", "death.attack.elsewhere.hurlbat.item"));
			for (String path : Hurlbats.SOUNDS) keys.add("subtitles.elsewhere." + path);
			for (String k : keys) if (!lang.has(k)) return false;
			return true;
		});
		JsonObject trade = null;
		try (InputStream in = Gear3Content.class.getResourceAsStream("/data/elsewhere/kingdom_trade/hrafnholt.json")) {
			if (in != null) trade = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			trade = null;
		}
		if (trade == null) info(P + "rival_hrafnholt trade file not on this classpath: Thing-day line checked in the merged jar");
		else {
			boolean line = false;
			for (JsonElement e : trade.toString().contains("hrafnholt_hurlbat") ? allObjects(trade) : List.<JsonElement>of()) {
				JsonObject o = e.getAsJsonObject();
				if (o.has("item") && "elsewhere:hrafnholt_hurlbat".equals(o.get("item").getAsString()) && o.has("price") && o.get("price").getAsInt() == 12
					&& o.has("count") && o.get("count").getAsInt() == 8) line = true;
			}
			check(P + "Thing-day sells 8 hurlbats for 12 Hacksilver", line);
		}

		// ---- Marksman Calling soft link
		check(P + "Marksman mark tag is arsenal's (\"elsewhere_mk:\")", "elsewhere_mk:".equals(Hurlbats.MARK_TAG), Hurlbats.MARK_TAG);
		if (!Hurlbats.marksmanLinked()) info(P + "arsenal Calling API not present (2.0 devlibs): Marksman multiplier checked in the merged jar");
	}

	/** A copy of a vanilla shield with no raise delay, so it blocks the moment it is raised (the test cannot tick 5 ticks). */
	static ItemStack instantShield(BlocksAttacks v) {
		ItemStack s = new ItemStack(Items.SHIELD);
		s.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.0F, v.disableCooldownScale(), v.damageReductions(), v.itemDamage(), v.bypassedBy(), v.blockSound(), v.disableSound()));
		return s;
	}

	/** Every JSON object anywhere inside {@code root} (the trade file nests its lines). */
	static List<JsonElement> allObjects(JsonElement root) {
		List<JsonElement> out = new ArrayList<>();
		collect(root, out);
		return out;
	}

	private static void collect(JsonElement e, List<JsonElement> out) {
		if (e.isJsonObject()) {
			out.add(e);
			for (var en : e.getAsJsonObject().entrySet()) collect(en.getValue(), out);
		} else if (e.isJsonArray()) {
			for (JsonElement c : e.getAsJsonArray()) collect(c, out);
		}
	}
}
