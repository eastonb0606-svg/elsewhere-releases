package dev.elsewhere.gear3.s3.reliquary;

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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Echo Reliquary self-test block ("Echo Reliquary: ..." checks), called from one line in gear3 dev/SelfTest.runChecks.
 * Synchronous, on its own FakePlayer; Glimmer goes to the summoner's dev record (GlimmerStub.testFree). Restores every
 * hook, modifier and switch it sets, and discards every entity it makes.
 */
public final class ReliquarySelfTest {
	private ReliquarySelfTest() {}

	static final String P = "Echo Reliquary: ";
	static final double X = 72.5, Y = 100, Z = 52.5; // inside the self-test's forced chunks (1..4), clear of Mothlight / Wolf-Call
	static final Identifier SLOT_MOD = Gear3Content.id("selftest.echo_reliquary_slot");
	static final Identifier ARMOR_MOD = Gear3Content.id("selftest.echo_reliquary_armor");

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_reliquary".getBytes(StandardCharsets.UTF_8)), "gear3_reliquary"));
		p.snapTo(X, Y, Z, 0, 0);
		Function<UUID, ServerPlayer> lookup0 = MinionEntity.testOwnerLookup;
		boolean free0 = GlimmerStub.testFree, deny0 = GlimmerStub.devDeny, on0 = EchoReliquary.enabled;
		AttributeInstance slots = SummonerAttributes.slots() == null ? null : p.getAttribute(SummonerAttributes.slots());
		List<Entity> made = new ArrayList<>();
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : lookup0.apply(u);
		try {
			GlimmerStub.testFree = true;
			GlimmerStub.devDeny = false;
			EchoReliquary.enabled = true;
			checks(s, l, p, slots, made);
		} catch (Throwable t) {
			check(P + "self-test block ran to the end", false, t);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			for (Entity e : made) if (!e.isRemoved()) e.discard();
			if (slots != null) slots.removeModifier(SLOT_MOD);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			MinionEntity.testOwnerLookup = lookup0;
			GlimmerStub.testFree = free0;
			GlimmerStub.devDeny = deny0;
			GlimmerStub.SPENT.remove(p.getUUID());
			EchoReliquary.enabled = on0;
		}
	}

	static void checks(MinecraftServer s, ServerLevel l, FakePlayer p, AttributeInstance slots, List<Entity> made) {
		Item item = Gear3Items.BY_ID.get(EchoReliquary.ITEM_ID);
		EntityType<EchoKnight> type = EchoReliquary.ECHO_KNIGHT;
		check(P + "item is a summoner minion staff calling the Echo Knight", item instanceof EchoReliquaryItem ri && type != null && ri.minionType() == type);
		if (!(item instanceof EchoReliquaryItem relic) || type == null) return;
		check(P + "entity elsewhere:echo_knight registered, never saved, 2 slots (MinionSlots), a Greater Bond 2-slot minion",
			BuiltInRegistries.ENTITY_TYPE.getValue(Gear3Content.id(EchoReliquary.ENTITY_ID)) == type && !type.canSerialize()
				&& near(MinionSlots.weight(type), 2) && dev.elsewhere.gear3.setbonus.a.GreaterBond.TWO_SLOT.contains(type));
		ItemStack plain = new ItemStack(relic);
		check(P + "Summoner calling item, transmutation-excluded, 256 durability",
			plain.is(Gear3Items.itemTag("elsewhere:calling_weapon/summoner")) && plain.is(Gear3Items.itemTag("testmeter:transmutation_excluded")) && plain.getMaxDamage() == 256,
			plain.getMaxDamage());

		// ---- the knight's numbers (GEAR.md A5 row) and its silence
		checkS(P + "Echo Knight: 60 HP, armor 16, 10 damage every 24 ticks (1.2 s), reach 2.4, ground minion", () -> {
			EchoKnight k = type.create(l, EntitySpawnReason.COMMAND);
			if (k == null) return false;
			boolean ok = near(k.getMaxHealth(), 60) && near(k.getAttributeValue(Attributes.ARMOR), 16) && near(k.baseDamage(), 10)
				&& k.attackIntervalTicks() == 24 && near(k.attackReach(), 2.4) && !k.isFlyingMinion();
			k.discard();
			return ok;
		});
		checkS(P + "Echo Knight is silent: no sounds, no step vibrations, dampens its own vibrations", () -> {
			EchoKnight k = type.create(l, EntitySpawnReason.COMMAND);
			if (k == null) return false;
			boolean ok = k.silentByDesign();
			k.discard();
			return ok;
		});

		// ---- 2 slots
		MinionManager.dismissAll(p.getUUID());
		GlimmerStub.SPENT.remove(p.getUUID());
		check(P + "summoner slot attribute present on the player", slots != null);
		if (slots == null) return;
		slots.removeModifier(SLOT_MOD);
		ItemStack st = new ItemStack(relic);
		p.setItemInHand(InteractionHand.MAIN_HAND, st);
		if (MinionApi.maxSlots(p) < 2) {
			MinionEntity none = relic.summonNow(p, st);
			check(P + "with 1 minion slot the call is refused: no knight, no Glimmer, no durability", none == null && MinionManager.count(p) == 0
				&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == 0 && st.getDamageValue() == 0, "slots=" + MinionApi.maxSlots(p));
		} else {
			info(P + "test player already has " + MinionApi.maxSlots(p) + " slots; the 1-slot refusal is not checked here");
		}
		slots.addOrUpdateTransientModifier(new AttributeModifier(SLOT_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE));
		int max = MinionApi.maxSlots(p);
		check(P + "test setup: 2 or more minion slots with slot gear", max >= 2, max);
		MinionEntity k1 = relic.summonNow(p, st);
		check(P + "a call summons one Echo Knight for 10 Glimmer and 1 durability, filling 2 slots", k1 instanceof EchoKnight && MinionManager.count(p) == 1
			&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == GearStats.SUMMON_GLIMMER_COST && st.getDamageValue() == 1 && near(MinionSlots.used(p), 2),
			"count=" + MinionManager.count(p) + " spent=" + GlimmerStub.SPENT.get(p.getUUID()) + " dmg=" + st.getDamageValue() + " used=" + MinionSlots.used(p));
		if (k1 == null) return;
		if (max == 2) {
			MinionEntity root = MinionManager.summon(p, SummonerEntities.ROOTLING, 0);
			check(P + "2 slots: a Rootling called after the knight sends the knight (oldest) home", root != null && root.isAlive() && k1.isRemoved()
				&& MinionManager.count(p) == 1, "knight removed=" + k1.isRemoved() + " count=" + MinionManager.count(p));
			MinionEntity k2 = relic.summonNow(p, st);
			check(P + "2 slots: a knight called over a Rootling sends the Rootling home", k2 instanceof EchoKnight && k2.isAlive() && root != null && root.isRemoved()
				&& MinionManager.count(p) == 1, "count=" + MinionManager.count(p));
			slots.removeModifier(SLOT_MOD);
			MinionManager.enforceSlots(p); // the summoner's own slot check (after summons and every 10 ticks)
			check(P + "slot gear taken off (1 slot): the summoner's slot check sends the knight home", k2 != null && k2.isRemoved() && MinionManager.count(p) == 0,
				"count=" + MinionManager.count(p));
			slots.addOrUpdateTransientModifier(new AttributeModifier(SLOT_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE));
		} else {
			info(P + "test player has " + max + " slots; the exact 2-slot replacement cases are not checked here");
		}
		check(P + "weighted rule (pure): [1,2] in 2 -> 1 goes, [2,1] in 2 -> 1, [1,1,2] in 4 -> 0, [2,2] in 3 -> 1, [2] in 1 -> 1",
			MinionSlots.evictions(List.of(1.0, 2.0), 2) == 1 && MinionSlots.evictions(List.of(2.0, 1.0), 2) == 1 && MinionSlots.evictions(List.of(1.0, 1.0, 2.0), 4) == 0
				&& MinionSlots.evictions(List.of(2.0, 2.0), 3) == 1 && MinionSlots.evictions(List.of(2.0), 1) == 1);

		// ---- blows: 40% armor ignored on top of the minion rule, no vibration
		MinionManager.dismissAll(p.getUUID());
		MinionEntity knight = relic.summonNow(p, st);
		check(P + "called again for the combat checks", knight instanceof EchoKnight);
		if (!(knight instanceof EchoKnight k)) return;
		k.snapTo(X + 1, Y, Z, 0, 0);
		Mob husk = mob(l, EntityTypes.HUSK, 2, 0, made);
		AttributeInstance armor = husk.getAttribute(Attributes.ARMOR);
		if (armor != null) armor.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_MOD, 20.0 - armor.getValue(), AttributeModifier.Operation.ADD_VALUE));
		int pierced = EchoKnight.withArmorPierce(husk, husk::getArmorValue);
		check(P + "armor pierce: a 20-armor target counts 12 during a blow and 20 after", husk.getArmorValue() == 20 && pierced == 12, pierced + " / " + husk.getArmorValue());
		float tough = (float) husk.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		DamageSource src = MinionApi.damageSource(l, k, p);
		float before = husk.getHealth();
		int silenced = EchoKnight.silencedHits;
		MinionApi.LAST_HIT_DAMAGE = -1F;
		boolean landed = k.hitTarget(l, p, husk, k.baseDamage());
		float dealt = MinionApi.LAST_HIT_DAMAGE, lost = before - husk.getHealth();
		float want = CombatRules.getDamageAfterAbsorb(husk, dealt, src, 6F, tough), ruleOnly = CombatRules.getDamageAfterAbsorb(husk, dealt, src, 10F, tough);
		check(P + "a blow on 20 armor goes through as 6 armor (x0.5 minion rule x0.6 reliquary), more than the minion rule alone (10)",
			landed && dealt > 0 && Math.abs(lost - want) < 0.05F && lost > ruleOnly + 0.1F, "lost " + lost + ", want " + want + ", minion rule alone " + ruleOnly);
		check(P + "its blow makes no damage vibration (EchoSilenceMixin)", EchoKnight.silencedHits == silenced + 1, EchoKnight.silencedHits - silenced);
		Mob pig = mob(l, EntityTypes.PIG, -2, 0, made);
		int silenced2 = EchoKnight.silencedHits;
		pig.hurtServer(l, l.damageSources().generic(), 1F);
		check(P + "any other hit still vibrates (counter unchanged, vibrates() true)", EchoKnight.silencedHits == silenced2 && EchoKnight.vibrates(pig, l.damageSources().mobAttack(husk)));
		check(P + "hits on a knight make no vibration either", !EchoKnight.vibrates(k, l.damageSources().mobAttack(husk)));

		// ---- kill switch (key echo_reliquary): no call, no trade
		MinionManager.dismissAll(p.getUUID());
		EchoReliquary.enabled = false;
		ItemStack off = new ItemStack(relic);
		p.setItemInHand(InteractionHand.MAIN_HAND, off);
		InteractionResult r = relic.use(l, p, InteractionHand.MAIN_HAND);
		relic.finishUsingItem(off, l, p);
		MinionEntity offCall = relic.summonNow(p, off);
		check(P + "kill switch off: no call, no knight, nothing spent", !r.consumesAction() && offCall == null && MinionManager.count(p) == 0 && off.getDamageValue() == 0, r);
		EchoReliquary.enabled = true;

		// ---- the Mute Archivist's offer
		Identifier archId = ReliquaryTrade.ARCHIVIST;
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(archId) && ReliquaryTrade.item(ReliquaryTrade.RESONANT_SHARD).isPresent()) {
			Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(archId).create(l, EntitySpawnReason.COMMAND);
			if (e instanceof AbstractVillager v) {
				made.add(v);
				v.snapTo(X - 3, Y, Z + 3, 0, 0);
				EchoReliquary.enabled = false;
				boolean offAdds = ReliquaryTrade.ensureOffer(v);
				EchoReliquary.enabled = true;
				boolean first = ReliquaryTrade.ensureOffer(v), second = ReliquaryTrade.ensureOffer(v);
				Item shard = BuiltInRegistries.ITEM.getValue(ReliquaryTrade.RESONANT_SHARD), bell = BuiltInRegistries.ITEM.getValue(ReliquaryTrade.BELL_SHARD);
				MerchantOffer o = null;
				for (MerchantOffer x : v.getOffers()) if (x.getResult().is(relic)) o = x;
				MerchantOffer fo = o;
				check(P + "Mute Archivist sells one for 32 Resonant Shards + 1 Cantor's Bell Shard, once; added once, not when switched off",
					ReliquaryTrade.isArchivist(v) && !offAdds && first && !second && ReliquaryTrade.count(v.getOffers(), relic) == 1 && fo != null
						&& fo.getCostA().is(shard) && fo.getCostA().getCount() == 32 && fo.getCostB().is(bell) && fo.getCostB().getCount() == 1 && fo.getMaxUses() == 1,
					"first=" + first + " second=" + second + " offers=" + ReliquaryTrade.count(v.getOffers(), relic));
			} else {
				check(P + "elsewhere:mute_archivist is a trader", false, e);
				if (e != null) e.discard();
			}
		} else {
			info(P + "Hush module (Mute Archivist, Resonant Shard) not in this environment; the trade is checked in the merged run");
		}

		// ---- assets the client needs
		check(P + "lang: knight name and both action-bar lines", lang("entity.elsewhere.echo_knight") && lang("message.elsewhere.echo_reliquary.slots")
			&& lang("message.elsewhere.echo_reliquary.off"));
		check(P + "Echo Knight textures: body and glow 128x64", png("assets/elsewhere/textures/entity/echo_knight.png", 128, 64)
			&& png("assets/elsewhere/textures/entity/echo_knight_glow.png", 128, 64));
	}

	static Mob mob(ServerLevel l, EntityType<? extends Mob> type, double dx, double dz, List<Entity> made) {
		Mob m = type.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(X + dx, Y, Z + dz, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		made.add(m);
		return m;
	}

	static boolean lang(String key) {
		try {
			for (java.net.URL u : java.util.Collections.list(ReliquarySelfTest.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json"))) {
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

	static boolean png(String path, int w, int h) {
		try (InputStream in = ReliquarySelfTest.class.getResourceAsStream("/" + path)) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img != null && img.getWidth() == w && img.getHeight() == h;
		} catch (Exception e) {
			return false;
		}
	}
}
