package dev.elsewhere.gear3.s3.waryak;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.minion.MinionSlots;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.summoner.GearStats;
import dev.elsewhere.summoner.SummonerEntities;
import dev.elsewhere.summoner.SummonerPerks;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * War-Yak Horn self-test block (called from the gear3 self-test, one hook line). Synchronous, on its own FakePlayer holding
 * the horn: the yak's numbers, the call (Glimmer, durability), weighted slots (2 per yak, oldest leave, a 1-slot owner is
 * refused with nothing spent), Herd focus, the charge (3 mobs trampled once each, a 4th untouched, never restarted
 * mid-run; entities ticked by hand), the horn-blast rate limit, the Quartermaster's rank-5 trade (through the mixin) and
 * the assets (sounds, lang, texture, goat-horn item models).
 */
public final class WarYakSelfTest {
	private WarYakSelfTest() {}

	static final String P = "War-Yak Horn: ";

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item horn = Gear3Items.BY_ID.get(WarYak.HORN_ID);
		check(P + "catalogue item registered as a summoner horn", horn instanceof WarYakHornItem, horn);
		check(P + "kill switch 'gear3_war_yak' is on by default", WarYak.ENABLED);
		if (!(horn instanceof WarYakHornItem staff) || !WarYak.ENABLED || WarYak.TYPE == null) return;

		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_war_yak".getBytes(StandardCharsets.UTF_8)), "gear3_war_yak"));
		// the self-test's forced chunks (x/z 16..79), high above the ground: nothing in the charge lane but the test mobs
		final double x0 = 24.5, y0 = 200, z0 = 72.5;
		p.snapTo(x0 + 3, y0, z0 + 4, 0, 0);
		Function<UUID, ServerPlayer> lookup0 = MinionEntity.testOwnerLookup;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : lookup0.apply(u);
		boolean free0 = GlimmerStub.testFree, deny0 = GlimmerStub.devDeny;
		AttributeInstance slots = SummonerAttributes.slots() == null ? null : p.getAttribute(SummonerAttributes.slots());
		Identifier slotMod = Gear3Content.id("selftest.war_yak_slots");
		List<Mob> extra = new ArrayList<>();
		String calling0 = dev.elsewhere.gear3.dev.SelfTest.primarySummoner(p); // s-asm-gear3: Unattuned caps at 1 slot; a Primary Summoner may have 1-4
		try {
			GlimmerStub.testFree = true;
			GlimmerStub.devDeny = false;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionManager.dismissAll(p.getUUID());
			ItemStack st = new ItemStack(horn);
			p.setItemInHand(InteractionHand.MAIN_HAND, st); // the horn in hand: Summoner calling

			// ---- the horn and the yak
			check(P + "calls the War-Yak, blown with the goat-horn toot pose, 1 s call",
				staff.minionType() == WarYak.TYPE && staff.getUseAnimation(st) == ItemUseAnimation.TOOT_HORN && staff.getUseDuration(st, p) == GearStats.SUMMON_CALL_TICKS);
			checkS(P + "War-Yak: 80 HP, armor 12, charges for 9 every 30 ticks (1.5 s) from 7 blocks, ground minion", () -> {
				WarYakEntity y = WarYak.TYPE.create(l, EntitySpawnReason.COMMAND);
				boolean ok = y != null && near(y.getMaxHealth(), 80) && near(y.getAttributeValue(Attributes.ARMOR), 12) && near(y.baseDamage(), 9)
					&& y.attackIntervalTicks() == 30 && near(y.attackReach(), 7) && !y.isFlyingMinion();
				if (y != null) y.discard();
				return ok && BuiltInRegistries.ENTITY_TYPE.getKey(WarYak.TYPE).equals(Gear3Content.id("war_yak"));
			});
			check(P + "War-Yak weighs 2 minion slots; MinionSlots.evictions drops just enough of the oldest",
				near(MinionSlots.weight(WarYak.TYPE), 2) && near(MinionSlots.weight(EntityTypes.HUSK), 1)
					&& MinionSlots.evictions(List.of(1.0, 1.0, 2.0), 3) == 1 && MinionSlots.evictions(List.of(2.0, 1.0, 2.0), 4) == 1
					&& MinionSlots.evictions(List.of(1.0, 1.0, 1.0), 3) == 0 && MinionSlots.evictions(List.of(2.0, 2.0), 2) == 1
					&& MinionSlots.evictions(List.of(2.0), 1) == 1);

			// ---- 1 slot: refused, nothing spent
			if (slotsTo(p, slots, slotMod, 1)) {
				int dmg0 = st.getDamageValue();
				MinionEntity none = staff.summonNow(p, st);
				check(P + "with 1 minion slot the call fails: no yak, no Glimmer, no durability", none == null && MinionManager.count(p) == 0
					&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == 0 && st.getDamageValue() == dmg0 && !MinionSlots.fits(p, WarYak.TYPE),
					"count=" + MinionManager.count(p) + " spent=" + GlimmerStub.SPENT.get(p.getUUID()));
			} else info(P + "could not set 1 minion slot here (maxSlots " + MinionApi.maxSlots(p) + "); the 1-slot refusal is not checked");

			// ---- 4 slots: two yaks; a third replaces the oldest
			if (slotsTo(p, slots, slotMod, 4)) {
				MinionEntity a = staff.summonNow(p, st);
				check(P + "calling a War-Yak costs 10 Glimmer and 1 durability (summoner minion API) and uses 2 slots",
					a instanceof WarYakEntity && MinionManager.count(p) == 1 && near(MinionSlots.used(p), 2)
						&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == GearStats.SUMMON_GLIMMER_COST && st.getDamageValue() == 1,
					"count=" + MinionManager.count(p) + " spent=" + GlimmerStub.SPENT.get(p.getUUID()) + " dmg=" + st.getDamageValue());
				MinionEntity b = staff.summonNow(p, st);
				MinionEntity c = staff.summonNow(p, st);
				check(P + "4 slots hold 2 War-Yaks; a third replaces the oldest (weighted, never over the cap)",
					b != null && c != null && a != null && a.isRemoved() && !b.isRemoved() && MinionManager.count(p) == 2 && near(MinionSlots.used(p), 4),
					"count=" + MinionManager.count(p) + " used=" + MinionSlots.used(p));
				MinionManager.dismissAll(p.getUUID());
			} else info(P + "could not set 4 minion slots here (maxSlots " + MinionApi.maxSlots(p) + "); the 2-yak cap is not checked");

			// ---- 3 slots: Rootlings make room for a yak, oldest first; Herd focus counts minions
			if (slotsTo(p, slots, slotMod, 3) && SummonerEntities.ROOTLING != null) {
				MinionEntity r1 = MinionManager.summon(p, SummonerEntities.ROOTLING, GearStats.SUMMON_GLIMMER_COST);
				MinionEntity r2 = MinionManager.summon(p, SummonerEntities.ROOTLING, GearStats.SUMMON_GLIMMER_COST);
				MinionEntity r3 = MinionManager.summon(p, SummonerEntities.ROOTLING, GearStats.SUMMON_GLIMMER_COST);
				MinionEntity y = staff.summonNow(p, st);
				check(P + "beside 1-slot minions the oldest make room (3 slots: 3 Rootlings -> newest Rootling + War-Yak)",
					r1 != null && r2 != null && r3 != null && y instanceof WarYakEntity && r1.isRemoved() && r2.isRemoved() && !r3.isRemoved()
						&& MinionManager.count(p) == 2 && near(MinionSlots.used(p), 3), "count=" + MinionManager.count(p) + " used=" + MinionSlots.used(p));
				SummonerPerks.updateHerdFocus(p);
				int n = MinionManager.count(p);
				check(P + "Herd focus counts the War-Yak as one minion (summoner rule)",
					near(SummonerPerks.herdFocus(p), GearStats.HERD_FOCUS_PER_EXTRA_MINION * Math.max(0, n - 1)), SummonerPerks.herdFocus(p));
				MinionManager.dismissAll(p.getUUID());
				MinionSlots.enforce(p); // nothing weighted left: the summoner's own count rule again
				check(P + "with no War-Yak out the summoner's own slot rule is back", !MinionSlots.handles(p));
			} else info(P + "could not set 3 minion slots here; the mixed-slot check is skipped");

			// ---- the charge: 3 in the lane trampled once each, a 4th untouched; never restarted mid-run
			slotsTo(p, slots, slotMod, 4);
			MinionEntity m = staff.summonNow(p, st);
			if (m instanceof WarYakEntity yak) {
				yak.setNoGravity(true);
				yak.snapTo(x0, y0, z0, -90, 0);
				yak.setDeltaMovement(0, 0, 0);
				Mob h1 = husk(l, x0 + 2, y0, z0, extra), h2 = husk(l, x0 + 3.5, y0, z0, extra), target = husk(l, x0 + 5, y0, z0, extra),
					h4 = husk(l, x0 + 6.5, y0, z0, extra);
				yak.startCharge(l, p, target);
				List<LivingEntity> lane = yak.lane();
				int left = yak.chargeTicksLeft();
				check(P + "charge lane: the target first, then the 2 nearest along the run (max 3)",
					lane.size() == 3 && lane.get(0) == target && lane.contains(h1) && lane.contains(h2) && !lane.contains(h4) && yak.isCharging(), lane);
				yak.performAttack(l, p, h4);
				check(P + "a charge is never restarted mid-run", yak.chargeTicksLeft() == left && yak.lane().equals(lane) && yak.charges == 1);
				double want = MinionApi.minionDamage(p, yak, target, WarYak.DAMAGE);
				for (int i = 0; i < 30 && yak.isCharging(); i++) {
					yak.tickCount++;
					yak.tick();
				}
				check(P + "charge tramples the 3 lane mobs once each for 9 x the owner's minion multipliers (" + String.format("%.2f", want) + "), a 4th untouched",
					!yak.isCharging() && yak.lastTrampleCount == 3 && hurt(h1) && hurt(h2) && hurt(target) && !hurt(h4) && near(MinionApi.LAST_HIT_DAMAGE, want),
					"trampled=" + yak.lastTrampleCount + " hp " + h1.getHealth() + "/" + h2.getHealth() + "/" + target.getHealth() + "/" + h4.getHealth()
						+ " hit " + MinionApi.LAST_HIT_DAMAGE + " yak x " + String.format("%.2f", yak.getX() - x0));
			} else check(P + "a War-Yak answers for the charge test", false, m);

			// ---- sneak-use sends the yaks home
			p.setShiftKeyDown(true);
			staff.use(l, p, InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
			check(P + "sneak-use sends the War-Yaks home", MinionManager.count(p) == 0, MinionManager.count(p));

			// ---- the horn blast: once per 3 s per player
			WarYak.resetBlasts();
			check(P + "horn blast sounds once per 3 s per player (no spam)", WarYak.hornBlast(p) && !WarYak.hornBlast(p));
			WarYak.resetBlasts();

			// ---- the Clan Quartermaster's rank-5 trade (through the mixin on IronpeakEntities.quartermasterOffers)
			quartermaster(p, horn);

			// ---- assets
			checkS(P + "sounds.json: the horn call and the War-Yak's 6 sounds, registered, each with a subtitle", () -> {
				JsonObject snd = merged("assets/elsewhere/sounds.json");
				for (String k : new String[] {"item.war_yak_horn.call", "entity.war_yak.ambient", "entity.war_yak.hurt", "entity.war_yak.death",
					"entity.war_yak.step", "entity.war_yak.charge", "entity.war_yak.trample"}) {
					if (!snd.has(k) || !snd.getAsJsonObject(k).has("subtitle")) return false;
					if (!BuiltInRegistries.SOUND_EVENT.containsKey(Gear3Content.id(k))) return false;
				}
				return true;
			});
			checkS(P + "lang: War-Yak name, subtitles, switched-off and not-enough-slots messages", () -> {
				JsonObject lang = merged("assets/elsewhere/lang/en_us.json");
				for (String k : new String[] {"entity.elsewhere.war_yak", "subtitles.elsewhere.war_yak_horn.call", "subtitles.elsewhere.war_yak.ambient",
					"subtitles.elsewhere.war_yak.hurt", "subtitles.elsewhere.war_yak.death", "subtitles.elsewhere.war_yak.charge", "subtitles.elsewhere.war_yak.trample",
					"message.elsewhere.war_yak.off", "message.elsewhere.war_yak.slots", "item.elsewhere." + WarYak.HORN_ID}) if (!lang.has(k)) return false;
				return true;
			});
			checkS(P + "War-Yak texture is a 128x128 PNG painted where the model reads it", WarYakSelfTest::texture);
			checkS(P + "held like a goat horn and raised to the mouth while blown (using_item model)", () -> {
				JsonObject def = json("assets/elsewhere/items/" + WarYak.HORN_ID + ".json");
				JsonObject toot = json("assets/elsewhere/models/item/" + WarYak.HORN_ID + "_tooting.json");
				return def != null && toot != null && def.toString().contains("minecraft:using_item") && def.toString().contains("elsewhere:item/" + WarYak.HORN_ID + "_tooting")
					&& "minecraft:item/tooting_goat_horn".equals(toot.get("parent").getAsString());
			});
		} finally {
			MinionManager.dismissAll(p.getUUID());
			MinionSlots.enforce(p);
			for (Mob e : extra) e.discard();
			if (slots != null) slots.removeModifier(slotMod);
			GlimmerStub.testFree = free0;
			GlimmerStub.devDeny = deny0;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionEntity.testOwnerLookup = lookup0;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			WarYak.resetBlasts();
			dev.elsewhere.gear3.dev.SelfTest.restoreCalling(p, calling0);
		}
		info(P + "done");
	}

	/** Sets the owner's minion slots to {@code want} with a transient slot modifier (the horn in hand gives the Summoner calling cap). */
	static boolean slotsTo(ServerPlayer p, AttributeInstance slots, Identifier mod, int want) {
		if (slots != null) slots.removeModifier(mod);
		if (MinionApi.maxSlots(p) == want) return true;
		if (slots == null) return false;
		for (int add = -3; add <= 4; add++) {
			if (add == 0) continue;
			slots.addOrUpdateTransientModifier(new AttributeModifier(mod, add, AttributeModifier.Operation.ADD_VALUE));
			if (MinionApi.maxSlots(p) == want) return true;
		}
		slots.removeModifier(mod);
		return false;
	}

	static Mob husk(ServerLevel l, double x, double y, double z, List<Mob> extra) {
		Mob h = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		h.snapTo(x, y, z, 90, 0);
		h.setNoAi(true);
		h.setNoGravity(true);
		h.setPersistenceRequired();
		l.addFreshEntity(h);
		extra.add(h);
		return h;
	}

	static boolean hurt(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth() - 1.0E-3;
	}

	/** Rank 4 and below: no horn on offer; rank 5 (Hrolf beaten): exactly one, 8 Skyiron + a goat horn, one use (the TestMeter gate as the Quartermaster's own). */
	static void quartermaster(ServerPlayer p, Item horn) {
		try {
			Class.forName("dev.elsewhere.ironpeak.IronpeakEntities");
		} catch (ClassNotFoundException e) {
			info(P + "Ironpeak is not in this environment; the Quartermaster trade is not checked");
			return;
		}
		checkS(P + "Clan Quartermaster sells it from clan rank 5 (8 Skyiron + a goat horn, 1 use), not below", () -> {
			CompoundTag d = dev.elsewhere.ironpeak.Ironpeak.data(p);
			boolean had = d.contains("hrolf");
			boolean was = d.getBooleanOr("hrolf", false);
			try {
				d.remove("hrolf");
				int low = dev.elsewhere.ironpeak.Ironpeak.rank(p);
				int lowCount = hornOffers(dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(p), horn).size();
				d.putBoolean("hrolf", true);
				int high = dev.elsewhere.ironpeak.Ironpeak.rank(p);
				List<MerchantOffer> mine = hornOffers(dev.elsewhere.ironpeak.IronpeakEntities.quartermasterOffers(p), horn);
				boolean gate = WarYakTrade.sells(p);
				boolean shape = mine.isEmpty() || (mine.get(0).getItemCostA().itemStack().is(dev.elsewhere.ironpeak.IronpeakItems.SKYIRON)
					&& mine.get(0).getItemCostA().count() == WarYakTrade.SKYIRON && mine.get(0).getItemCostB().isPresent()
					&& mine.get(0).getItemCostB().get().itemStack().is(Items.GOAT_HORN) && mine.get(0).getMaxUses() == WarYakTrade.MAX_USES);
				info(P + "Quartermaster: rank " + low + " -> " + lowCount + " offers, rank " + high + " -> " + mine.size() + " (TestMeter gate " + gate + ")");
				return (low >= WarYakTrade.RANK || lowCount == 0) && high >= WarYakTrade.RANK && mine.size() == (gate ? 1 : 0) && shape;
			} finally {
				if (had) d.putBoolean("hrolf", was);
				else d.remove("hrolf");
			}
		});
	}

	static List<MerchantOffer> hornOffers(List<MerchantOffer> offers, Item horn) {
		List<MerchantOffer> out = new ArrayList<>();
		for (MerchantOffer o : offers) if (o.getResult().is(horn)) out.add(o);
		return out;
	}

	/** 128x128, with paint on the body side, the head front, a horn tip and a leg (box UVs of WarYakModel). */
	static boolean texture() {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/entity/war_yak.png")) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img != null && img.getWidth() == 128 && img.getHeight() == 128 && (img.getRGB(25, 25) >>> 24) != 0 && (img.getRGB(80, 25) >>> 24) != 0
				&& (img.getRGB(122, 3) >>> 24) != 0 && (img.getRGB(75, 37) >>> 24) != 0;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	/** Every copy of a JSON resource on the classpath, merged (first copy wins per key, like the resource stack and the merged jar). */
	static JsonObject merged(String path) {
		JsonObject out = new JsonObject();
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(path))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				}
			}
		} catch (Exception e) {
			return new JsonObject();
		}
		return out;
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
