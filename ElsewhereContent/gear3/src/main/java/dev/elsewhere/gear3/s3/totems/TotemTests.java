package dev.elsewhere.gear3.s3.totems;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.summoner.GearStats;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Totem self-test block ("Totems: ..." checks), called from the gear3 self-test. Synchronous, on its own FakePlayer and
 * a stone floor at y=139 over x/z 40..72 (forced chunks 2..4), removed afterwards. Totems and targets are ticked by hand.
 */
public final class TotemTests {
	private TotemTests() {}

	static final String P = "Totems: ";
	static final int FY = 139, LO = 40, HI = 72;

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item bramble = Gear3Items.BY_ID.get(Totems.BRAMBLE_ID), sentry = Gear3Items.BY_ID.get(Totems.SENTRY_ID), obelisk = Gear3Items.BY_ID.get(Totems.OBELISK_ID);
		check(P + "the 3 totem items are registered as TotemItem (mechanic 'totem')",
			bramble instanceof TotemItem && sentry instanceof TotemItem && obelisk instanceof TotemItem, bramble + " " + sentry + " " + obelisk);
		check(P + "entity types elsewhere:bramble_totem / clockwork_sentry / azmar_sunglass_obelisk registered, never saved",
			Totems.BRAMBLE != null && Totems.SENTRY != null && Totems.OBELISK != null
				&& Gear3Content.id(Totems.BRAMBLE_ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(Totems.BRAMBLE))
				&& Gear3Content.id(Totems.SENTRY_ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(Totems.SENTRY))
				&& Gear3Content.id(Totems.OBELISK_ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(Totems.OBELISK))
				&& !Totems.BRAMBLE.canSerialize() && !Totems.SENTRY.canSerialize() && !Totems.OBELISK.canSerialize());
		check(P + "kill switch 'gear3_totems' is on by default", Totems.enabled);
		assets();
		if (!(bramble instanceof TotemItem) || Totems.BRAMBLE == null || !Totems.enabled) return;

		checkS(P + "GEAR.md stats: Bramble 30 HP; Sentry 40 HP armor 8; Obelisk 50 HP; none can be walked off (speed 0, knockback res 1)", () -> {
			boolean ok = stats(l, Totems.BRAMBLE, 30, 0) && stats(l, Totems.SENTRY, 40, 8) && stats(l, Totems.OBELISK, 50, 0);
			return ok && near(BrambleTotem.RANGE, 5) && BrambleTotem.INTERVAL == 40 && near(ClockworkSentry.RANGE, 16) && ClockworkSentry.INTERVAL == 20
				&& near(SunglassObelisk.DAMAGE, 4) && near(SunglassObelisk.RAMP_DAMAGE, 8) && SunglassObelisk.RAMP_TICKS == 60;
		});

		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_totems".getBytes(StandardCharsets.UTF_8)), "gear3_totems"));
		p.snapTo(56.5, FY + 1, 42.5, 0, 0);
		Function<UUID, ServerPlayer> lookup0 = MinionEntity.testOwnerLookup;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : lookup0.apply(u);
		boolean free0 = GlimmerStub.testFree, deny0 = GlimmerStub.devDeny, enabled0 = Totems.enabled;
		AttributeInstance slots = SummonerAttributes.slots() == null ? null : p.getAttribute(SummonerAttributes.slots());
		var slotMod = Gear3Content.id("selftest.totem_slots");
		List<Entity> extra = new ArrayList<>();
		try {
			for (int x = LO; x <= HI; x++) for (int z = LO; z <= HI; z++) {
				l.setBlockAndUpdate(new BlockPos(x, FY, z), Blocks.STONE.defaultBlockState());
				for (int y = FY + 1; y <= FY + 4; y++) l.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}
			GlimmerStub.testFree = true;
			GlimmerStub.devDeny = false;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionManager.dismissAll(p.getUUID());
			if (slots != null) slots.addOrUpdateTransientModifier(new AttributeModifier(slotMod, 3, AttributeModifier.Operation.ADD_VALUE));
			info(P + "slot cap with test slot gear: " + MinionApi.maxSlots(p));

			// ---- placement
			ItemStack bs = new ItemStack(bramble);
			p.setItemInHand(InteractionHand.MAIN_HAND, bs);
			check(P + "no placement on thin air (needs a sturdy top face below)", TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(56, FY + 20, 56), Direction.UP) == null);
			TotemEntity b = TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(56, FY, 56), Direction.UP);
			check(P + "placing on a block top sets a Bramble Totem there for 10 Glimmer and 1 durability, facing the player",
				b instanceof BrambleTotem && near(b.getX(), 56.5) && near(b.getY(), FY + 1) && near(b.getZ(), 56.5) && bs.getDamageValue() == 1
					&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == GearStats.SUMMON_GLIMMER_COST && MinionManager.minions(p).contains(b),
				b == null ? "null" : b.position() + " dmg=" + bs.getDamageValue() + " spent=" + GlimmerStub.SPENT.get(p.getUUID()));
			if (!(b instanceof BrambleTotem bt)) return;
			check(P + "2 s item cooldown after a placement", TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(60, FY, 60), Direction.UP) == null
				&& p.getCooldowns().isOnCooldown(bs));
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(bs));
			check(P + "no room: cannot place inside another totem", TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(56, FY, 56), Direction.UP) == null);
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(bs));
			GlimmerStub.devDeny = true;
			check(P + "no Glimmer, no totem", TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(60, FY, 60), Direction.UP) == null);
			GlimmerStub.devDeny = false;
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(bs));

			// ---- never moves: owner walks off, recall, ticks
			p.snapTo(56.5, FY + 1, 72.5, 0, 0);
			Vec3 at = b.position();
			b.teleportToOwner(p);
			for (int i = 0; i < 40; i++) tick(b);
			check(P + "a totem never moves: owner 16 blocks off, recalled, 40 ticks later it stands where it was put",
				!b.isRemoved() && b.position().distanceTo(at) < 0.01 && b.lifeTicks >= 40, b.position() + " life=" + b.lifeTicks);
			p.snapTo(56.5, FY + 1, 42.5, 0, 0);

			// ---- Bramble: up to 3 mobs within 5, 2 damage + Slowness I 1 s; not 6.5 away, not a cow
			Mob h2 = mob(l, EntityTypes.HUSK, 58.5, 56.5, extra), h3 = mob(l, EntityTypes.HUSK, 56.5, 59.5, extra);
			Mob h4 = mob(l, EntityTypes.HUSK, 52.5, 56.5, extra), h45 = mob(l, EntityTypes.HUSK, 56.5, 52.0, extra);
			Mob far = mob(l, EntityTypes.HUSK, 63.0, 56.5, extra), cow = mob(l, EntityTypes.COW, 55.0, 58.0, extra);
			int n = bt.lash(l, p);
			check(P + "Bramble lash hits the nearest 3 hostiles within 5 blocks (2 damage, Slowness I 1 s)",
				n == 3 && hurt(h2) && hurt(h3) && hurt(h4) && !hurt(h45) && slowed(h2) && slowed(h3) && slowed(h4), "hit=" + n);
			check(P + "Bramble ignores a hostile 6.5 blocks away and a passive cow", !hurt(far) && !hurt(cow));
			check(P + "Bramble lash damage is minion damage (base 2 through the summoner)", MinionApi.LAST_HIT_DAMAGE > 0, MinionApi.LAST_HIT_DAMAGE);
			check(P + "no sneeze before the 4th lashed mob", bt.sneezes == 0, bt.sneezes);
			for (Entity e : extra) e.setInvulnerableTime(0);
			bt.lash(l, p);
			check(P + "gag: haze pollen makes every 4th lashed mob sneeze (cosmetic)", bt.sneezes == 1, bt.sneezes);
			int before = bt.strikes;
			for (Entity e : extra) e.setInvulnerableTime(0);
			for (int i = 0; i < 40; i++) {
				tick(bt);
				for (Entity e : extra) e.setInvulnerableTime(0);
			}
			check(P + "Bramble lashes once per 2 s on its own timer (40 ticks: 1 lash of 3)", bt.strikes - before == 3, bt.strikes - before);
			clear(extra);

			// ---- Clockwork Sentry: 3-damage bolt / 1 s, range 16, needs line of sight
			ItemStack ss = new ItemStack(sentry);
			ClockworkSentry cs = (ClockworkSentry) TotemItem.place(p, ss, InteractionHand.MAIN_HAND, new BlockPos(44, FY, 56), Direction.UP);
			check(P + "Sentry placed (2 totems out)", cs != null && count(p) == 2);
			if (cs == null) return;
			Mob t18 = mob(l, EntityTypes.HUSK, 62.5, 56.5, extra);
			check(P + "Sentry: nothing to shoot at 18 blocks", !cs.fire(l, p) && cs.aimTargetId() == -1 && !hurt(t18));
			clear(extra);
			for (int y = FY + 1; y <= FY + 3; y++) for (int z = 54; z <= 58; z++) l.setBlockAndUpdate(new BlockPos(48, y, z), Blocks.STONE.defaultBlockState());
			Mob walled = mob(l, EntityTypes.HUSK, 52.5, 56.5, extra);
			check(P + "Sentry: no shot through a wall", !cs.fire(l, p) && !hurt(walled));
			for (int y = FY + 1; y <= FY + 3; y++) for (int z = 54; z <= 58; z++) l.setBlockAndUpdate(new BlockPos(48, y, z), Blocks.AIR.defaultBlockState());
			clear(extra);
			Mob t10 = mob(l, EntityTypes.HUSK, 54.5, 56.5, extra);
			t10.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
			t10.setHealth(200);
			check(P + "Sentry: a bolt at a hostile 10 blocks away, aimed (synced target id)", cs.fire(l, p) && hurt(t10) && cs.aimTargetId() == t10.getId());
			int s0 = cs.strikes;
			for (int i = 0; i < 40; i++) {
				t10.setInvulnerableTime(0);
				tick(cs);
			}
			check(P + "Sentry fires once per second on its own timer (40 ticks: 2 bolts)", cs.strikes - s0 == 2, cs.strikes - s0);
			clear(extra);

			// ---- max 2 totems: a third crumbles the oldest (the Bramble)
			ItemStack os = new ItemStack(obelisk);
			SunglassObelisk ob = (SunglassObelisk) TotemItem.place(p, os, InteractionHand.MAIN_HAND, new BlockPos(64, FY, 64), Direction.UP);
			check(P + "max 2 totems: placing a 3rd takes down the oldest", ob != null && bt.isRemoved() && !cs.isRemoved() && count(p) == 2, "totems=" + count(p));
			if (ob == null) return;

			// ---- Sunglass Obelisk: 4/s, 8/s after 3 s on one target, bright light only
			Mob tank = mob(l, EntityTypes.HUSK, 64.5, 60.5, extra);
			tank.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			tank.setHealth(500);
			SunglassObelisk.testBrightness = 15;
			double[] base = new double[4];
			float[] dealt = new float[4];
			for (int i = 0; i < 4; i++) {
				tank.setInvulnerableTime(0);
				base[i] = ob.burn(l, p);
				dealt[i] = MinionApi.LAST_HIT_DAMAGE;
			}
			check(P + "Obelisk beam: 4, 4, 4, then 8 once it has held the same target 3 s",
				near(base[0], 4) && near(base[1], 4) && near(base[2], 4) && near(base[3], 8) && ob.isRamped() && ob.aimTargetId() == tank.getId(),
				base[0] + "," + base[1] + "," + base[2] + "," + base[3]);
			check(P + "Obelisk ramp doubles the dealt minion damage", dealt[0] > 0 && Math.abs(dealt[3] / dealt[0] - 2.0) < 0.01, dealt[0] + " -> " + dealt[3]);
			SunglassObelisk.testBrightness = 4;
			tank.setInvulnerableTime(0);
			check(P + "Obelisk dormant in the dark (light < 12): no beam, lens dim", ob.burn(l, p) == 0 && !ob.isLit() && ob.aimTargetId() == -1 && !ob.isRamped());
			SunglassObelisk.testBrightness = 12;
			tank.setInvulnerableTime(0);
			check(P + "Obelisk relit at light 12: the ramp starts over (4)", near(ob.burn(l, p), 4) && ob.isLit());
			SunglassObelisk.testBrightness = -1;
			info(P + "world light at the test lens: " + ob.light(l));
			clear(extra);

			// ---- leash, sneak take-down
			p.snapTo(56.5, FY + 1, 56.5 + Totems.LEASH + 12, 0, 0);
			for (int i = 0; i < 20; i++) tick(cs);
			check(P + "a totem crumbles when its owner is more than 48 blocks away", cs.isRemoved());
			p.snapTo(56.5, FY + 1, 42.5, 0, 0);
			check(P + "sneak-use takes down your totems of that kind", TotemItem.takeDown(p, os) == 1 && ob.isRemoved() && count(p) == 0);

			// ---- kill switch
			Totems.enabled = false;
			p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(bs));
			check(P + "kill switch off: the items place nothing, the Budtender adds no offer",
				TotemItem.place(p, bs, InteractionHand.MAIN_HAND, new BlockPos(56, FY, 56), Direction.UP) == null && !TotemTrades.addTo(new ArrayList<>()));
			Totems.enabled = true;

			// ---- Budtender trade (greenhaze_hush)
			budtender(l, bramble);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			clear(extra);
			SunglassObelisk.testBrightness = -1;
			Totems.enabled = enabled0;
			if (slots != null) slots.removeModifier(slotMod);
			GlimmerStub.testFree = free0;
			GlimmerStub.devDeny = deny0;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionEntity.testOwnerLookup = lookup0;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			for (int x = LO; x <= HI; x++) for (int z = LO; z <= HI; z++) l.setBlockAndUpdate(new BlockPos(x, FY, z), Blocks.AIR.defaultBlockState());
		}
		info(P + "done");
	}

	private static void budtender(ServerLevel l, Item bramble) {
		EntityType<?> bt = BuiltInRegistries.ENTITY_TYPE.getValue(TotemTrades.BUDTENDER);
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(TotemTrades.BUDTENDER) || bt == null) {
			info(P + "Budtender trade: greenhaze_hush not loaded here, skipped");
			return;
		}
		Entity e = bt.create(l, EntitySpawnReason.COMMAND);
		try {
			MerchantOffer o = TotemTrades.offer();
			check(P + "Budtender sells the Bramble Totem: 12 Haze Resin + 4 Giant Fern, 2 per Budtender",
				o != null && o.getResult().is(bramble) && o.getItemCostA().count() == 12 && o.getItemCostB().isPresent() && o.getItemCostB().get().count() == 4
					&& o.getMaxUses() == 2 && BuiltInRegistries.ITEM.getKey(o.getItemCostA().item().value()).equals(TotemTrades.HAZE_RESIN)
					&& BuiltInRegistries.ITEM.getKey(o.getItemCostB().get().item().value()).equals(TotemTrades.GIANT_FERN));
			if (e instanceof Merchant m) {
				boolean first = TotemTrades.addTo(m.getOffers()), again = TotemTrades.addTo(m.getOffers());
				long n = m.getOffers().stream().filter(x -> x.getResult().is(bramble)).count();
				check(P + "the offer is added to a Budtender once (its own trades kept)", first && !again && n == 1 && m.getOffers().size() > 1, m.getOffers().size());
			} else check(P + "the Budtender is a merchant", false, e);
		} finally {
			if (e != null) e.discard();
		}
	}

	private static void assets() {
		for (String id : new String[] {Totems.BRAMBLE_ID, Totems.SENTRY_ID, Totems.OBELISK_ID}) {
			check(P + "entity texture textures/entity/totem/" + id + ".png is 64x64", size("/assets/elsewhere/textures/entity/totem/" + id + ".png", 64));
		}
		check(P + "beam texture textures/entity/totem/totem_beam.png present", size("/assets/elsewhere/textures/entity/totem/totem_beam.png", 16));
		JsonObject lang = lang();
		boolean ok = true;
		StringBuilder miss = new StringBuilder();
		for (String k : new String[] {"entity.elsewhere.bramble_totem", "entity.elsewhere.clockwork_sentry", "entity.elsewhere.azmar_sunglass_obelisk",
			"message.elsewhere.totem.disabled", "message.elsewhere.totem.no_room", "message.elsewhere.totem.taken_down"}) {
			if (!lang.has(k)) {
				ok = false;
				miss.append(k).append(' ');
			}
		}
		check(P + "lang: entity names and the 3 action-bar messages", ok, miss.toString().trim());
	}

	private static boolean size(String path, int w) {
		try (InputStream in = TotemTests.class.getResourceAsStream(path)) {
			if (in == null) return false;
			BufferedImage img = ImageIO.read(in);
			return img != null && img.getWidth() == w && img.getHeight() == w;
		} catch (Exception e) {
			return false;
		}
	}

	private static JsonObject lang() {
		JsonObject all = new JsonObject();
		try {
			Enumeration<URL> urls = TotemTests.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
			while (urls.hasMoreElements()) {
				try (InputStream in = urls.nextElement().openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (String k : o.keySet()) if (!all.has(k)) all.add(k, o.get(k));
				}
			}
		} catch (Exception e) {
			info(P + "lang read failed: " + e);
		}
		return all;
	}

	private static boolean stats(ServerLevel l, EntityType<? extends TotemEntity> type, double hp, double armor) {
		TotemEntity e = type.create(l, EntitySpawnReason.COMMAND);
		if (e == null) return false;
		boolean ok = near(e.getMaxHealth(), hp) && near(e.getAttributeValue(Attributes.ARMOR), armor) && near(e.getAttributeValue(Attributes.MOVEMENT_SPEED), 0)
			&& near(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1) && !e.isFlyingMinion() && !e.isPushedByFluid();
		e.discard();
		return ok;
	}

	private static Mob mob(ServerLevel l, EntityType<? extends Mob> type, double x, double z, List<Entity> extra) {
		Mob m = type.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, FY + 1, z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		extra.add(m);
		return m;
	}

	private static void tick(Entity e) {
		e.tickCount++;
		e.tick();
	}

	private static boolean hurt(Mob m) {
		return m.getHealth() < m.getMaxHealth();
	}

	private static boolean slowed(Mob m) {
		var eff = m.getEffect(MobEffects.SLOWNESS);
		return eff != null && eff.getAmplifier() == 0 && eff.getDuration() <= BrambleTotem.SLOW_TICKS;
	}

	private static int count(ServerPlayer p) {
		int n = 0;
		for (MinionEntity m : MinionManager.minions(p)) if (m instanceof TotemEntity && !m.isRemoved()) n++;
		return n;
	}

	private static void clear(List<Entity> extra) {
		for (Entity e : extra) e.discard();
		extra.clear();
	}
}
