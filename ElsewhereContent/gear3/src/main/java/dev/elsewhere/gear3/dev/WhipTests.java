package dev.elsewhere.gear3.dev;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.s3.whips.Gear3Whips;
import dev.elsewhere.gear3.s3.whips.WhipItem;
import dev.elsewhere.gear3.s3.whips.WhipKind;
import dev.elsewhere.gear3.s3.whips.WhipTags;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.GaleRibbonItem;
import dev.elsewhere.summoner.SummonerEntities;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Whip mechanics self-test (s-whips): one block per whip (mark, minion bonus, crack, sound, cracked model) and the
 * family rules (newest tag wins across whips and the Gale Ribbon, tag timing, D(Summoner), Herd focus, Command bonus
 * hook, clean-up). Synchronous, on FakePlayers and NoAI husks next to the gear3 arena.
 */
final class WhipTests {
	private WhipTests() {}

	private static final UUID BONUS_OWNER = UUID.nameUUIDFromBytes("gear3-whiptest-bonus".getBytes(StandardCharsets.UTF_8));
	private static boolean bonusHooked;

	static Mob husk(ServerLevel l, Vec3 at) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static FakePlayer owner(ServerLevel l, String name, BlockPos at) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(("gear3-whiptest-" + name).getBytes(StandardCharsets.UTF_8)), "gear3_" + name));
		p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return p;
	}

	static long left(FakePlayer p) {
		WhipTags.Tag t = WhipTags.liveTag(p);
		return t == null ? -1 : t.until() - p.level().getGameTime();
	}

	static String name(WhipKind k) {
		return Character.toUpperCase(k.tag.charAt(0)) + k.tag.substring(1);
	}

	static boolean crackModel(String id) {
		JsonObject def = SelfTest.json("assets/elsewhere/items/" + id + ".json");
		JsonObject crack = SelfTest.json("assets/elsewhere/models/item/" + id + "_crack.json");
		if (def == null || crack == null) return false;
		JsonObject m = def.getAsJsonObject("model");
		if (!"minecraft:range_dispatch".equals(m.get("type").getAsString()) || !"minecraft:cooldown".equals(m.get("property").getAsString())) return false;
		if (!m.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("model").get("model").getAsString().equals("elsewhere:item/" + id + "_crack")) return false;
		if (!m.getAsJsonObject("fallback").get("model").getAsString().equals("elsewhere:item/" + id)) return false;
		if (!crack.getAsJsonObject("textures").get("layer0").getAsString().equals("elsewhere:item/" + id + "_crack")) return false;
		try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/item/" + id + "_crack.png")) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			int opaque = 0;
			for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
			return img.getWidth() == 16 && img.getHeight() == 16 && opaque >= 12;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	/** The event is registered and sounds.json (every copy on the classpath) lists it with .ogg files that exist. */
	static boolean sound(WhipKind k) {
		Identifier id = Gear3Content.id(k.soundPath());
		if (!BuiltInRegistries.SOUND_EVENT.containsKey(id) || k.sound() == null) return false;
		JsonObject sj = SelfTest.mergedJson("assets/elsewhere/sounds.json");
		if (sj == null || !sj.has(k.soundPath())) return false;
		JsonObject e = sj.getAsJsonObject(k.soundPath());
		if (!e.has("subtitle") || e.getAsJsonArray("sounds").isEmpty()) return false;
		for (var s : e.getAsJsonArray("sounds")) {
			String n = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
			Identifier f = Identifier.parse(n);
			if (Gear3Content.class.getResource("/assets/" + f.getNamespace() + "/sounds/" + f.getPath() + ".ogg") == null) return false;
		}
		return true;
	}

	static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		BlockPos base = new BlockPos(40, 100, 52);
		FakePlayer a = owner(l, "a", base), b = owner(l, "b", base.offset(2, 0, 0));
		WhipTags.clear(a.getUUID());
		WhipTags.clear(b.getUUID());
		check("whips: kill switch whip_mechanics is on", Gear3Whips.ENABLED);
		MinionEntity minion = SummonerEntities.ROOTLING.create(l, EntitySpawnReason.COMMAND);
		check("whips: test minion (Rootling) created", minion != null);
		if (minion == null) return;
		minion.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 0, 0);
		Mob plain = husk(l, new Vec3(base.getX() + 6.5, base.getY(), base.getZ() + 0.5));
		double plainDmg = MinionApi.minionDamage(a, minion, plain, 10.0);

		int x = 0;
		for (WhipKind k : WhipKind.values()) {
			String id = k.item, mark = name(k);
			Item item = Gear3Items.BY_ID.get(id);
			ItemStack st = item == null ? ItemStack.EMPTY : new ItemStack(item);
			check(id + ": a whip (WhipItem, " + mark + " mark) with the 8-tick lash swing", item instanceof WhipItem w && w.kind() == k
				&& WhipItem.LASH.equals(st.get(DataComponents.ATTACK_ANIMATION)) && WhipItem.LASH.equals(st.get(DataComponents.INTERACT_ANIMATION)));
			if (!(item instanceof WhipItem)) continue;
			Mob t = husk(l, new Vec3(base.getX() + 3.5 + 2 * x++, base.getY(), base.getZ() + 4.5));
			a.setItemInHand(InteractionHand.MAIN_HAND, st);
			item.hurtEnemy(st, t, a);
			check(id + ": a hit marks the target " + mark + " for " + k.ticks / 20 + " s and the owner's minions switch to it",
				WhipTags.kindOn(a, t) == k && left(a) == WhipTags.tagTicks(k, WhipTags.wet(t), 0) /* Tidechain: 7 s if the test world rains */ && GaleRibbonItem.target(a) == t && GaleRibbonItem.isTagged(a, t) && !GaleRibbonItem.isTagged(a, plain), left(a));
			double marked = MinionApi.minionDamage(a, minion, t, 10.0);
			double want = plainDmg * (1.0 + k.pct) + k.flat;
			String bonus = (k.flat > 0 ? "+" + (int) k.flat + " flat" : "+" + Math.round(k.pct * 100) + "%") + (k.armorIgnore > 0 ? " and ignore " + k.armorIgnore + " armor" : "");
			check(id + ": minion hits on the " + mark + " target " + bonus + " (Gale Ribbon's +30% not stacked), others unchanged",
				near(marked, want) && near(MinionApi.minionDamage(a, minion, plain, 10.0), plainDmg), String.format("%.3f vs %.3f, plain %.3f", marked, want, plainDmg));
			checkS(id + ": crack (right-click) keeps the mark alive, 0.5 s cooldown, cracked-lash model while it runs", () -> {
				var cd = a.getCooldowns();
				cd.removeCooldown(cd.getCooldownGroup(st));
				var r = item.use(l, a, InteractionHand.MAIN_HAND);
				boolean ok = r.consumesAction() && cd.isOnCooldown(st) && WhipTags.kindOn(a, t) == k && WhipTags.liveTag(a).lastSwing() == l.getGameTime();
				boolean again = !item.use(l, a, InteractionHand.MAIN_HAND).consumesAction(); // no crack spam while cooling down
				cd.removeCooldown(cd.getCooldownGroup(st));
				return ok && again && crackModel(id);
			});
			checkS(id + ": crack sound elsewhere:" + k.soundPath() + " registered, in sounds.json with its .ogg files", () -> sound(k));
			if (k == WhipKind.SUNSCOURGE) {
				checkS(id + ": Venom: the owner's minion hits ignore 3 armor (10 -> 7; other sources and unmarked targets keep 10)", () -> {
					DamageSource ms = MinionApi.damageSource(l, minion, a);
					return near(Gear3Whips.armorAgainst(t, ms, 10F), 7F) && near(Gear3Whips.armorAgainst(plain, ms, 10F), 10F)
						&& near(Gear3Whips.armorAgainst(t, l.damageSources().generic(), 10F), 10F) && near(Gear3Whips.armorAgainst(t, ms, 2F), 0F);
				});
				checkS(id + ": Venom in a real hit: armor 10 husk takes the 7-armor damage from a minion", () -> {
					var armor = t.getAttribute(Attributes.ARMOR);
					Identifier aid = Gear3Content.id("whiptest.armor");
					armor.addOrUpdateTransientModifier(new AttributeModifier(aid, 10.0 - armor.getValue(), AttributeModifier.Operation.ADD_VALUE));
					DamageSource ms = MinionApi.damageSource(l, minion, a);
					float tough = (float) t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
					t.setHealth(t.getMaxHealth());
					t.damageCooldownTime = 0;
					t.hurtServer(l, ms, 8F);
					float lost = t.getMaxHealth() - t.getHealth();
					armor.removeModifier(aid);
					t.setHealth(t.getMaxHealth());
					t.damageCooldownTime = 0;
					float want7 = CombatRules.getDamageAfterAbsorb(t, 8F, ms, 7F, tough);
					SelfTest.info(id + ": Venom hit lost " + lost + " (armor 7: " + want7 + ", armor 10: " + CombatRules.getDamageAfterAbsorb(t, 8F, ms, 10F, tough) + ")");
					return Math.abs(lost - want7) < 0.05F;
				});
			}
			if (k == WhipKind.TIDECHAIN) {
				checkS(id + ": Shackled lasts 7 s on a wet target (standing in water)", () -> {
					BlockPos wp = BlockPos.containing(base.getX() + 0.5, base.getY(), base.getZ() + 8.5);
					l.setBlock(wp, Blocks.WATER.defaultBlockState(), 3);
					Mob wetOne = husk(l, Vec3.atBottomCenterOf(wp));
					item.hurtEnemy(st, wetOne, a);
					boolean ok = WhipTags.wet(wetOne) && WhipTags.kindOn(a, wetOne) == k && left(a) == k.wetTicks;
					wetOne.discard();
					WhipTags.clearTarget(wetOne.getUUID());
					l.setBlock(wp, Blocks.AIR.defaultBlockState(), 3);
					return ok && k.wetTicks == 140;
				});
			}
			WhipTags.clear(a.getUUID());
			t.discard();
		}

		// ---- family rules
		Mob t1 = husk(l, new Vec3(base.getX() + 3.5, base.getY(), base.getZ() + 10.5)), t2 = husk(l, new Vec3(base.getX() + 6.5, base.getY(), base.getZ() + 10.5));
		ItemStack thorn = new ItemStack(Gear3Items.get("thornlash")), echo = new ItemStack(Gear3Items.get("echo_chord"));
		checkS("whips: one tag per owner (a new target takes the mark) and per target (newest wins, any owner, any whip)", () -> {
			thorn.getItem().hurtEnemy(thorn, t1, a);
			thorn.getItem().hurtEnemy(thorn, t2, a);
			boolean moved = WhipTags.kindOn(a, t1) == null && WhipTags.kindOn(a, t2) == WhipKind.THORNLASH;
			echo.getItem().hurtEnemy(echo, t2, b);
			boolean newest = WhipTags.kindOn(b, t2) == WhipKind.ECHO_CHORD && WhipTags.kindOn(a, t2) == null && WhipTags.liveTag(a) == null && WhipTags.size() == 1;
			return moved && newest;
		});
		checkS("whips: a Gale Ribbon hit replaces the gear3 mark on its target; a gear3 hit clears the owner's Gale Ribbon tag", () -> {
			GaleRibbonItem.tag(a, t2); // b's Echo Chord mark on t2 goes
			boolean ribbonWins = WhipTags.rawOn(t2.getUUID()) == null && WhipTags.liveTag(b) == null && GaleRibbonItem.isTagged(a, t2);
			// the Ribbon's own +30% still applies to its tag (no gear3 mark there)
			boolean thirty = near(MinionApi.minionDamage(a, minion, t2, 10.0), plainDmg * 1.3);
			thorn.getItem().hurtEnemy(thorn, t1, a);
			boolean whipWins = !GaleRibbonItem.isTagged(a, t2) && WhipTags.kindOn(a, t1) == WhipKind.THORNLASH;
			WhipTags.clear(a.getUUID());
			GaleRibbonItem.clear(a.getUUID());
			return ribbonWins && thirty && whipWins;
		});
		checkS("whips: a mark ends at its time, and 3 s after the owner's last swing (hit or crack)", () -> {
			WhipTags.Tag tg = new WhipTags.Tag(a.getUUID(), t1.getUUID(), WhipKind.THORNLASH, 1100L, 1000L, WhipTags.IDLE_TICKS);
			return tg.alive(1000) && tg.alive(1060) && !tg.alive(1061) && new WhipTags.Tag(a.getUUID(), t1.getUUID(), WhipKind.THORNLASH, 1100L, 1050L, 60).alive(1100)
				&& !new WhipTags.Tag(a.getUUID(), t1.getUUID(), WhipKind.THORNLASH, 1100L, 1090L, 60).alive(1101);
		});
		checkS("whips: set-bonus hook adds tag time to gear3 marks and to the Gale Ribbon (Marshal's Command +3 s)", () -> {
			FakePlayer c = owner(l, "bonus", base.offset(4, 0, 0));
			if (!c.getUUID().equals(BONUS_OWNER)) return false;
			if (!bonusHooked) { // once per JVM: a second self-test run must not stack another +60
				bonusHooked = true;
				WhipTags.addBonusTicks(p -> BONUS_OWNER.equals(p.getUUID()) ? 60 : 0);
			}
			thorn.getItem().hurtEnemy(thorn, t1, c);
			boolean whip = left(c) == WhipKind.THORNLASH.ticks + 60 && WhipTags.liveTag(c).idle() == WhipTags.IDLE_TICKS + 60 && left(a) == -1;
			WhipTags.clear(c.getUUID());
			GaleRibbonItem.tag(c, t1);
			long until;
			try {
				var f = GaleRibbonItem.class.getDeclaredField("TAGS");
				f.setAccessible(true);
				Object tag = ((java.util.Map<?, ?>) f.get(null)).get(c.getUUID());
				var m = tag.getClass().getDeclaredMethod("until");
				m.setAccessible(true);
				until = (Long) m.invoke(tag);
			} catch (ReflectiveOperationException e) {
				SelfTest.info("Gale Ribbon tag not readable: " + e);
				return false;
			}
			GaleRibbonItem.clear(c.getUUID());
			return whip && until - l.getGameTime() == 100 + 60;
		});
		checkS("whips: whip melee x D(Summoner) against mobs, x1.0 against players and for non-whips", () -> {
			a.setItemInHand(InteractionHand.MAIN_HAND, thorn);
			DamageSource hit = l.damageSources().playerAttack(a);
			float d = Calling.damageMult(a, Calling.Path.SUMMONER, t1);
			boolean whip = near(Gear3Whips.whipMelee(t1, hit, 10F), 10F * d) && near(Gear3Whips.whipMelee(b, hit, 10F), 10F);
			a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			boolean sword = near(Gear3Whips.whipMelee(t1, hit, 10F), 10F);
			SelfTest.info("whips: D(Summoner) for the test owner = " + d);
			return whip && sword;
		});
		checkS("whips: Herd focus is lifted for a whip attack and put back after it; a sword attack keeps it", () -> {
			var inst = a.getAttribute(Attributes.ATTACK_DAMAGE);
			AttributeModifier herd = new AttributeModifier(Gear3Whips.HERD_FOCUS, -0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			inst.addOrUpdateTransientModifier(herd);
			a.setItemInHand(InteractionHand.MAIN_HAND, thorn);
			AttributeModifier lifted = Gear3Whips.liftHerdFocus(a);
			boolean during = lifted != null && inst.getModifier(Gear3Whips.HERD_FOCUS) == null;
			Gear3Whips.restoreHerdFocus(a, lifted);
			boolean back = inst.getModifier(Gear3Whips.HERD_FOCUS) != null;
			t1.damageCooldownTime = 0;
			a.attack(t1); // through the Player.attack wrapper
			boolean afterAttack = inst.getModifier(Gear3Whips.HERD_FOCUS) != null && near(inst.getModifier(Gear3Whips.HERD_FOCUS).amount(), -0.20);
			a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			boolean sword = Gear3Whips.liftHerdFocus(a) == null && inst.getModifier(Gear3Whips.HERD_FOCUS) != null;
			inst.removeModifier(Gear3Whips.HERD_FOCUS);
			a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			WhipTags.clear(a.getUUID());
			return during && back && afterAttack && sword;
		});
		checkS("whips: a mark is dropped when its target dies", () -> {
			thorn.getItem().hurtEnemy(thorn, t2, b);
			boolean on = WhipTags.rawOn(t2.getUUID()) != null;
			t2.hurtServer(l, l.damageSources().genericKill(), Float.MAX_VALUE);
			return on && !t2.isAlive() && WhipTags.rawOn(t2.getUUID()) == null && WhipTags.liveTag(b) == null;
		});
		WhipTags.clear(a.getUUID());
		WhipTags.clear(b.getUUID());
		for (LivingEntity e : new LivingEntity[] {t1, t2, plain}) e.discard();
		minion.discard();
		check("whips: test left no marks behind", WhipTags.size() == 0, WhipTags.size());
		SelfTest.info("whip blocks done");
	}
}
