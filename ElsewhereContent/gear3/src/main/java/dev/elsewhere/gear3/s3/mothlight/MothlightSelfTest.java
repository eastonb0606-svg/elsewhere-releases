package dev.elsewhere.gear3.s3.mothlight;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionTuning;
import dev.elsewhere.stub.summoner.GlimmerStub;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Mothlight Staff self-test block (called from gear3 dev/SelfTest.runChecks). Synchronous, on a FakePlayer owner; the staff's
 * Glimmer goes to the summoner's dev record (GlimmerStub.testFree) so the real pool is untouched. Restores every hook it sets.
 */
public final class MothlightSelfTest {
	private MothlightSelfTest() {}

	static final double X = 56.5, Y = 100, Z = 56.5; // inside the self-test's forced chunks, away from the mechanic blocks at 40,100,40

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_mothlight".getBytes(StandardCharsets.UTF_8)), "gear3_mothlight"));
		p.snapTo(X, Y, Z, 0, 0);
		Function<UUID, ServerPlayer> prevLookup = MinionEntity.testOwnerLookup;
		boolean prevFree = GlimmerStub.testFree, prevEnabled = Mothlight.enabled;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : prevLookup.apply(u);
		GlimmerStub.testFree = true;
		Mothlight.enabled = true;
		try {
			checks(l, p);
		} catch (Throwable t) {
			check("Mothlight: self-test block ran to the end", false, t);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			MinionEntity.testOwnerLookup = prevLookup;
			GlimmerStub.testFree = prevFree;
			GlimmerStub.SPENT.remove(p.getUUID());
			Mothlight.enabled = prevEnabled;
		}
	}

	static Mob mob(ServerLevel l, EntityType<? extends Mob> type, double dx, double dz) {
		Mob m = type.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(X + dx, Y, Z + dz, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static MothlightSwarm summon(ServerPlayer p, MothlightStaffItem staff) {
		ItemStack st = new ItemStack(staff);
		p.setItemInHand(InteractionHand.MAIN_HAND, st);
		return staff.summonNow(p, st) instanceof MothlightSwarm sw ? sw : null;
	}

	static DamageSource hit(ServerLevel l, LivingEntity by) {
		return l.damageSources().mobAttack(by);
	}

	static void checks(ServerLevel l, FakePlayer p) {
		Item item = Gear3Items.BY_ID.get(Mothlight.STAFF_ID);
		check("Mothlight: staff registered as a summoner minion staff calling the Mothlight Swarm",
			item instanceof MothlightStaffItem st && st.minionType() == Mothlight.SWARM && Mothlight.SWARM != null);
		if (!(item instanceof MothlightStaffItem staff)) return;
		check("Mothlight: entity type elsewhere:mothlight_swarm registered, never saved with the world",
			BuiltInRegistries.ENTITY_TYPE.getValue(Gear3Content.id(Mothlight.SWARM_ID)) == Mothlight.SWARM && !Mothlight.SWARM.canSerialize());
		check("Mothlight: effect elsewhere:mothdust registered", Mothlight.MOTHDUST != null && BuiltInRegistries.MOB_EFFECT.containsKey(Gear3Content.id(Mothlight.DUST_ID)));
		ItemStack stack = new ItemStack(staff);
		check("Mothlight: staff is a Summoner calling item, transmutation-excluded, repaired with Moonsilk Thread",
			stack.is(Gear3Items.itemTag("elsewhere:calling_weapon/summoner")) && stack.is(Gear3Items.itemTag("testmeter:transmutation_excluded"))
				&& stack.getMaxDamage() == 128);

		// ---- summon: one slot, three moths, 10 Glimmer
		MinionManager.dismissAll(p.getUUID());
		GlimmerStub.SPENT.remove(p.getUUID());
		MothlightSwarm sw = summon(p, staff);
		check("Mothlight: a call summons one Mothlight Swarm for 10 Glimmer and 1 durability", sw != null && GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == 10
			&& p.getMainHandItem().getDamageValue() == 1, GlimmerStub.SPENT.get(p.getUUID()));
		if (sw == null) return;
		check("Mothlight: 1 slot = 3 moths (one minion out, 3 moths, 18 HP = 3 x 6)", MinionManager.count(p) == 1 && sw.moths() == 3
			&& near(sw.getMaxHealth(), Mothlight.MOTHS * Mothlight.MOTH_HP) && near(sw.getHealth(), 18), MinionManager.count(p) + " minion(s), " + sw.moths() + " moths");
		check("Mothlight: the swarm reserves one minion's Glimmer, not three", MinionApi.reservedGlimmer(p) == MinionTuning.reserve(p, sw), MinionApi.reservedGlimmer(p));
		check("Mothlight: flies; flurry every 0.75 s; 1.5 per moth", sw.isFlyingMinion() && sw.attackIntervalTicks() == 15 && near(sw.baseDamage(), 1.5));

		// ---- moth HP: a blow fells one moth at most; fallen moths never come back
		Mob brute = mob(l, EntityTypes.HUSK, 3, 0);
		sw.snapTo(X + 1.5, Y + 1, Z, 0, 0);
		sw.hurtServer(l, hit(l, brute), 10F);
		check("Mothlight: a 10-damage blow fells exactly one moth (18 -> 12 HP, 2 moths)", sw.moths() == 2 && near(sw.getHealth(), 12), sw.getHealth() + " HP, " + sw.moths());
		sw.heal(10F);
		check("Mothlight: healing never raises a fallen moth (stays 12 HP, 2 moths)", sw.moths() == 2 && near(sw.getHealth(), 12), sw.getHealth());
		dev.elsewhere.gear3.dev.SelfTest.noIFrames(sw);
		sw.hurtServer(l, hit(l, brute), 3F);
		check("Mothlight: a light hit only wounds the moth in front (9 HP, still 2 moths)", sw.moths() == 2 && near(sw.getHealth(), 9), sw.getHealth());
		sw.heal(2F);
		check("Mothlight: a wounded living moth heals (11 HP)", near(sw.getHealth(), 11), sw.getHealth());
		dev.elsewhere.gear3.dev.SelfTest.noIFrames(sw);
		sw.hurtServer(l, hit(l, brute), 100F);
		check("Mothlight: even a 100-damage blow fells one moth (6 HP, 1 moth)", sw.moths() == 1 && near(sw.getHealth(), 6) && sw.isAlive(), sw.getHealth());
		dev.elsewhere.gear3.dev.SelfTest.noIFrames(sw);
		sw.hurtServer(l, hit(l, brute), 100F);
		check("Mothlight: the last moth falls and the slot frees", !sw.isAlive() && sw.moths() == 0 && MinionManager.count(p) == 0, MinionManager.count(p));
		brute.discard();

		// ---- flurry: moths x 1.5 through the minion damage rules, Mothdust on a non-boss
		MothlightSwarm a = summon(p, staff);
		check("Mothlight: called again after the swarm fell", a != null && a.moths() == 3);
		if (a == null) return;
		Mob target = mob(l, EntityTypes.HUSK, 2, 2);
		a.snapTo(X + 2, Y + 1, Z + 1, 0, 0);
		double want3 = MinionApi.minionDamage(p, a, target, 3 * Mothlight.MOTH_DAMAGE);
		MinionApi.LAST_HIT_DAMAGE = -1F;
		boolean landed = a.flurry(l, p, target);
		check("Mothlight: a 3-moth flurry deals 3 x 1.5 through the minion rules (x minion_damage, Calling)", landed && near(MinionApi.LAST_HIT_DAMAGE, want3),
			MinionApi.LAST_HIT_DAMAGE + " vs " + want3);
		check("Mothlight: the hit dusts a non-boss target (Mothdust, 3 s)", target.hasEffect(Mothlight.MOTHDUST) && target.getEffect(Mothlight.MOTHDUST).getDuration() == Mothlight.DUST_TICKS);
		dev.elsewhere.gear3.dev.SelfTest.noIFrames(a);
		a.hurtServer(l, l.damageSources().generic(), 6F); // not the dusted husk: Mothdust would cut its blow to 5.4
		Mob target2 = mob(l, EntityTypes.HUSK, 2, -2);
		double want2 = MinionApi.minionDamage(p, a, target2, 2 * Mothlight.MOTH_DAMAGE);
		MinionApi.LAST_HIT_DAMAGE = -1F;
		a.flurry(l, p, target2);
		check("Mothlight: with a moth fallen the flurry is 2 x 1.5", a.moths() == 2 && near(MinionApi.LAST_HIT_DAMAGE, want2), MinionApi.LAST_HIT_DAMAGE + " vs " + want2);
		var wither = EntityTypes.WITHER.create(l, EntitySpawnReason.COMMAND);
		check("Mothlight: bosses are never dusted (#c:bosses / #elsewhere:bosses)", wither != null && !Mothlight.dusts(wither) && Mothlight.dusts(target));
		if (wither != null) wither.discard();

		// ---- Mothdust: the dusted mob deals 10% less (melee, its projectiles, blasts: the causing entity counts)
		Mob pigA = mob(l, EntityTypes.PIG, -3, 0), pigB = mob(l, EntityTypes.PIG, -3, 2);
		Mob plain = mob(l, EntityTypes.HUSK, -5, 0);
		pigA.hurtServer(l, hit(l, plain), 4F);
		pigB.hurtServer(l, hit(l, target), 4F);
		check("Mothlight: a dusted mob's hit deals 10% less (4 -> 3.6), an undusted one's is unchanged", near(pigA.getMaxHealth() - pigA.getHealth(), 4)
			&& near(pigB.getMaxHealth() - pigB.getHealth(), 3.6), (pigA.getMaxHealth() - pigA.getHealth()) + " / " + (pigB.getMaxHealth() - pigB.getHealth()));
		check("Mothlight: the hook only reads the attacker (a dusted victim takes full damage)", near(Mothlight.outgoing(target, hit(l, plain), 10F), 10)
			&& near(Mothlight.outgoing(pigA, hit(l, target), 10F), 9));
		target.removeEffect(Mothlight.MOTHDUST);
		check("Mothlight: Mothdust gone, full damage again", near(Mothlight.outgoing(pigA, hit(l, target), 10F), 10));

		// ---- flight: the strike point is within reach for short and tall mobs
		Mob tall = mob(l, EntityTypes.ENDERMAN, 6, 6);
		checkS("Mothlight: the swarm's strike circle is within reach of a husk, an enderman and a pig", () -> {
			for (Mob m : new Mob[] {target, tall, pigA}) {
				for (double ang = 0; ang < 6.3; ang += 0.5) {
					Vec3 at = MothlightSwarm.strikePoint(m, ang);
					if (at.distanceTo(m.position()) > MothlightSwarm.REACH + m.getBbWidth() * 0.5) return false;
				}
			}
			return true;
		});

		// ---- kill switch (key mothlight_staff): off = the staff calls nothing and Mothdust does nothing
		MinionManager.dismissAll(p.getUUID());
		Mothlight.enabled = false;
		ItemStack off = new ItemStack(staff);
		p.setItemInHand(InteractionHand.MAIN_HAND, off);
		var r = staff.use(l, p, InteractionHand.MAIN_HAND);
		staff.finishUsingItem(off, l, p);
		target.addEffect(new MobEffectInstance(Mothlight.MOTHDUST, 60));
		check("Mothlight: kill switch off: no call, no swarm, Mothdust inert", !r.consumesAction() && MinionManager.count(p) == 0 && off.getDamageValue() == 0
			&& near(Mothlight.outgoing(pigA, hit(l, target), 10F), 10), r);
		Mothlight.enabled = true;
		for (Mob m : new Mob[] {target, target2, pigA, pigB, plain, tall}) m.discard();

		// ---- Sap Tapper offer (core's SapTapper.offers(), through the pseudo mixin): 20 Pale Bark + 4 Moonsilk Thread
		checkS("Mothlight: the Sap Tapper sells the staff for 20 Pale Bark + 4 Moonsilk Thread", () -> {
			java.util.List<net.minecraft.world.item.trading.MerchantOffer> offers;
			try {
				@SuppressWarnings("unchecked")
				var o = (java.util.List<net.minecraft.world.item.trading.MerchantOffer>) Class.forName("dev.elsewhere.palehollow.entity.SapTapper").getMethod("offers").invoke(null);
				offers = o;
			} catch (ReflectiveOperationException e) {
				return true; // core without the Sap Tapper: nothing to check
			}
			for (var o : offers)
				if (o.getResult().is(staff) && o.getBaseCostA().is(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "pale_bark")))
					&& o.getBaseCostA().getCount() == Mothlight.TRADE_BARK && o.getCostB().getCount() == Mothlight.TRADE_THREAD) return true;
			return false;
		});

		// ---- assets the client needs
		check("Mothlight: lang for the swarm, Mothdust and the off message", lang("entity.elsewhere.mothlight_swarm") && lang("effect.elsewhere.mothdust")
			&& lang("message.elsewhere.mothlight_off"));
		check("Mothlight: swarm texture 64x32 and Mothdust icon 18x18", png("assets/elsewhere/textures/entity/mothlight_swarm.png", 64, 32)
			&& png("assets/elsewhere/textures/mob_effect/mothdust.png", 18, 18));
	}

	static boolean lang(String key) {
		try {
			for (java.net.URL u : java.util.Collections.list(Mothlight.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json"))) {
				try (InputStream in = u.openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					if (o.has(key) && !o.get(key).getAsString().isBlank()) return true;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	/** PNG size from the IHDR header (no java.desktop on the server). */
	static boolean png(String path, int w, int h) {
		try (InputStream in = Mothlight.class.getResourceAsStream("/" + path)) {
			if (in == null) return false;
			byte[] b = in.readNBytes(24);
			if (b.length < 24 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') return false;
			var buf = java.nio.ByteBuffer.wrap(b);
			return buf.getInt(16) == w && buf.getInt(20) == h;
		} catch (Exception e) {
			return false;
		}
	}
}
