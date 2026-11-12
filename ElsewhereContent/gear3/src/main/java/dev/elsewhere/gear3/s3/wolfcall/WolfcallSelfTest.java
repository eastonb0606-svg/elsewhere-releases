package dev.elsewhere.gear3.s3.wolfcall;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.summoner.GearStats;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Wolf-Call Horn self-test block (called from the gear3 self-test). Synchronous, on its own FakePlayer: the Warg's numbers, the
 * call through the summoner minion API (Glimmer, slot cap, oldest leaves, Herd focus), the howl bonus in and out of range and
 * when the pack leaves, a bite's minion damage, the drop table, and the assets (sounds, mono howl, Warg texture, lang, horn model).
 */
public final class WolfcallSelfTest {
	private WolfcallSelfTest() {}

	static final String P = "Wolf-Call Horn: ";

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item horn = Wolfcall.item();
		check(P + "catalogue item registered with its staff class", horn instanceof WolfcallHornItem, horn);
		check(P + "kill switch 'wolfcall_horn' is on by default", Wolfcall.ENABLED);
		if (!(horn instanceof WolfcallHornItem staff) || !Wolfcall.ENABLED) return;

		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_wolfcall".getBytes(StandardCharsets.UTF_8)), "gear3_wolfcall"));
		p.snapTo(64.5, 100, 64.5, 0, 0);
		Function<UUID, ServerPlayer> lookup0 = MinionEntity.testOwnerLookup;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : lookup0.apply(u);
		boolean free0 = GlimmerStub.testFree, deny0 = GlimmerStub.devDeny;
		AttributeInstance slots = SummonerAttributes.slots() == null ? null : p.getAttribute(SummonerAttributes.slots());
		Identifier slotMod = Gear3Content.id("selftest.wolfcall_slots");
		List<Mob> extra = new ArrayList<>();
		try {
			GlimmerStub.testFree = true;
			GlimmerStub.devDeny = false;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionManager.dismissAll(p.getUUID());

			// ---- the Warg and the horn
			checkS(P + "Warg: 26 HP, armor 6, bites 5 every 20 ticks (1 s), reach 2, ground minion", () -> {
				WargEntity w = Wolfcall.WARG.create(l, EntitySpawnReason.COMMAND);
				boolean ok = w != null && near(w.getMaxHealth(), 26) && near(w.getAttributeValue(Attributes.ARMOR), 6) && near(w.baseDamage(), 5)
					&& w.attackIntervalTicks() == 20 && near(w.attackReach(), 2) && !w.isFlyingMinion();
				if (w != null) w.discard();
				return ok && BuiltInRegistries.ENTITY_TYPE.getKey(Wolfcall.WARG).equals(Gear3Content.id("hrafnholt_warg"));
			});
			check(P + "a summoner staff that calls the Warg, blown with the goat-horn toot pose",
				staff.minionType() == Wolfcall.WARG && staff.getUseAnimation(new ItemStack(horn)) == ItemUseAnimation.TOOT_HORN && staff.getUseDuration(new ItemStack(horn), p) == GearStats.SUMMON_CALL_TICKS);

			// ---- call: 10 Glimmer through MinionManager.summon
			ItemStack st = new ItemStack(horn);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			MinionEntity first = staff.summonNow(p, st);
			check(P + "blowing it calls one Warg for 10 Glimmer and 1 durability (summoner minion API)", first instanceof WargEntity && MinionManager.count(p) == 1
				&& GlimmerStub.SPENT.getOrDefault(p.getUUID(), 0) == GearStats.SUMMON_GLIMMER_COST && st.getDamageValue() == 1,
				"count=" + MinionManager.count(p) + " spent=" + GlimmerStub.SPENT.get(p.getUUID()) + " dmg=" + st.getDamageValue());
			GlimmerStub.devDeny = true;
			check(P + "no Glimmer, no Warg", staff.summonNow(p, st) == null && MinionManager.count(p) == 1);
			GlimmerStub.devDeny = false;

			// ---- slot cap: over the cap the oldest leaves
			int cap1 = MinionApi.maxSlots(p);
			for (int i = 0; i < cap1; i++) staff.summonNow(p, st);
			check(P + "slot cap " + cap1 + ": one Warg per slot, the oldest leaves when over", MinionManager.count(p) == cap1 && first.isRemoved(),
				"count=" + MinionManager.count(p) + " cap=" + cap1);
			if (slots != null) slots.addOrUpdateTransientModifier(new AttributeModifier(slotMod, 3, AttributeModifier.Operation.ADD_VALUE));
			int cap2 = MinionApi.maxSlots(p);
			for (int i = 0; i < cap2 + 1; i++) staff.summonNow(p, st);
			check(P + "a pack: " + cap2 + " Wargs out with slot gear, never more than the cap", cap2 >= 2 && MinionManager.count(p) == cap2,
				"count=" + MinionManager.count(p) + " cap=" + cap2);

			// ---- Herd focus (the summoner rule): -10% own damage per minion beyond the first
			SummonerPerks.updateHerdFocus(p);
			int n = MinionManager.count(p);
			double focus = GearStats.HERD_FOCUS_PER_EXTRA_MINION * Math.max(0, n - 1);
			check(P + "Herd focus applies to the pack: own melee x" + String.format("%.2f", 1 + focus) + " with " + n + " Wargs",
				near(SummonerPerks.herdFocus(p), focus) && near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) * (1 + focus)),
				p.getAttributeValue(Attributes.ATTACK_DAMAGE));

			// ---- howl: +10% x H attack speed within 8 blocks
			List<WargEntity> pack = new ArrayList<>();
			for (MinionEntity m : MinionManager.minions(p)) if (m instanceof WargEntity w) pack.add(w);
			double h = Wolfcall.hybrid(p);
			for (int i = 0; i < pack.size(); i++) pack.get(i).snapTo(p.getX() + 2 + i, p.getY(), p.getZ(), 0, 0);
			Wolfcall.refreshHowl(p, null);
			double amt = Wolfcall.howlAmount(p);
			AttributeModifier mod = p.getAttribute(Attributes.ATTACK_SPEED).getModifier(Wolfcall.HOWL_MOD);
			check(P + "howl: +10% x H attack speed within 8 blocks of a Warg (H=" + String.format("%.2f", h) + ")",
				h > 0 && h <= 1 && near(amt, 0.10 * h) && mod != null && mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE, amt);
			check(P + "howl does not stack per Warg", near(Wolfcall.howlAmount(p), 0.10 * h) && pack.size() >= 2);
			for (int i = 0; i < pack.size(); i++) pack.get(i).snapTo(p.getX() + 10 + i, p.getY(), p.getZ(), 0, 0);
			Wolfcall.refreshHowl(p, null);
			check(P + "howl: gone beyond 8 blocks", Wolfcall.howlAmount(p) == 0, Wolfcall.howlAmount(p));
			pack.get(0).snapTo(p.getX() + 7.5, p.getY(), p.getZ(), 0, 0);
			Wolfcall.refreshHowl(p, null);
			check(P + "howl: back at 7.5 blocks", near(Wolfcall.howlAmount(p), 0.10 * h));

			// ---- a bite is the owner's minion damage
			Mob husk = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
			husk.snapTo(p.getX() + 3.5, p.getY(), p.getZ(), 0, 0);
			husk.setNoAi(true);
			l.addFreshEntity(husk);
			extra.add(husk);
			WargEntity biter = pack.get(0);
			biter.snapTo(p.getX() + 2.5, p.getY(), p.getZ(), 0, 0);
			double want = MinionApi.minionDamage(p, biter, husk, Wolfcall.WARG_DAMAGE);
			float hp0 = husk.getHealth();
			husk.damageCooldownTime = 0;
			boolean bit = biter.bite(l, p, husk);
			check(P + "Warg bite deals 5 x the owner's minion multipliers (" + String.format("%.2f", want) + ")",
				bit && husk.getHealth() < hp0 && near(MinionApi.LAST_HIT_DAMAGE, want), "hp " + hp0 + " -> " + husk.getHealth() + ", hit " + MinionApi.LAST_HIT_DAMAGE);
			check(P + "a Warg hunting a target is aggressive (client: hackles up)", setTargetAggressive(biter, husk));

			// ---- the pack goes home: howl and Herd focus clear
			staff.use(l, sneaking(p, true), InteractionHand.MAIN_HAND);
			sneaking(p, false);
			SummonerPerks.updateHerdFocus(p);
			check(P + "sneak-use sends the pack home; the howl bonus and Herd focus clear", MinionManager.count(p) == 0 && Wolfcall.howlAmount(p) == 0
				&& near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)), "count=" + MinionManager.count(p));

			// ---- drops: Gudrun 30%, Wolf-Handler 5%, player kills only
			check(P + "drop chances: Gudrun 30% (and Risen), Wolf-Handler 5%, nothing else", Wolfcall.dropChance(Wolfcall.GUDRUN) == 0.30F
				&& Wolfcall.dropChance(Wolfcall.GUDRUN_RISEN) == 0.30F && Wolfcall.dropChance(Wolfcall.WOLF_HANDLER) == 0.05F && Wolfcall.dropChance("minecraft:zombie") == 0F);
			RandomSource rnd = RandomSource.create(20261112L);
			int hits = 0;
			for (int i = 0; i < 4000; i++) if (Wolfcall.rollDrop(Wolfcall.GUDRUN, rnd)) hits++;
			check(P + "Gudrun drop rate about 30% (4000 rolls)", hits > 1080 && hits < 1320, hits);
			Mob untouched = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND); // never hurt by a player, never added to the world
			check(P + "drops need a player kill", untouched != null && !Wolfcall.killedByPlayer(untouched, l.damageSources().generic())
				&& Wolfcall.killedByPlayer(untouched, l.damageSources().playerAttack(p)));
			Identifier chief = Identifier.parse(Wolfcall.GUDRUN);
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(chief))
				check(P + "Gudrun's entity type is hooked", Wolfcall.dropChance(BuiltInRegistries.ENTITY_TYPE.getValue(chief)) == 0.30F);
			else info(P + "Hrafnholt is not in this environment; Gudrun / Wolf-Handler hooks are keyed by id");

			// ---- assets
			checkS(P + "sounds.json defines the horn blast and the Warg's 6 sounds, each with a subtitle", () -> {
				JsonObject snd = merged("assets/elsewhere/sounds.json");
				for (String k : new String[] {"item.hrafnholt_wolfcall_horn.blow", "entity.hrafnholt_warg.howl", "entity.hrafnholt_warg.ambient", "entity.hrafnholt_warg.growl",
					"entity.hrafnholt_warg.hurt", "entity.hrafnholt_warg.death", "entity.hrafnholt_warg.pant"}) {
					if (!snd.has(k) || !snd.getAsJsonObject(k).has("subtitle")) return false;
					if (!BuiltInRegistries.SOUND_EVENT.containsKey(Gear3Content.id(k))) return false;
				}
				return true;
			});
			checkS(P + "the howl is an original mono Vorbis OGG (distance attenuation works)", () -> monoOgg("/assets/elsewhere/sounds/gear3/wolfcall/warg_howl.ogg"));
			checkS(P + "Warg texture is a 64x32 wolf-layout PNG", () -> wargTexture("/assets/elsewhere/textures/entity/hrafnholt_warg.png"));
			checkS(P + "lang: Warg name, 7 subtitles and the switched-off message", () -> {
				JsonObject lang = merged("assets/elsewhere/lang/en_us.json");
				String[] keys = {"entity.elsewhere.hrafnholt_warg", "subtitles.elsewhere.hrafnholt_wolfcall_horn.blow", "subtitles.elsewhere.hrafnholt_warg.howl",
					"subtitles.elsewhere.hrafnholt_warg.ambient", "subtitles.elsewhere.hrafnholt_warg.growl", "subtitles.elsewhere.hrafnholt_warg.hurt",
					"subtitles.elsewhere.hrafnholt_warg.death", "subtitles.elsewhere.hrafnholt_warg.pant", "message.elsewhere.hrafnholt_wolfcall_horn.disabled"};
				for (String k : keys) if (!lang.has(k)) return false;
				return true;
			});
			checkS(P + "held like a goat horn and raised to the mouth while blown (using_item model)", () -> {
				JsonObject def = json("assets/elsewhere/items/" + Wolfcall.ID + ".json");
				JsonObject toot = json("assets/elsewhere/models/item/" + Wolfcall.ID + "_tooting.json");
				return def != null && toot != null && def.toString().contains("minecraft:using_item") && def.toString().contains("elsewhere:item/" + Wolfcall.ID + "_tooting")
					&& "minecraft:item/tooting_goat_horn".equals(toot.get("parent").getAsString());
			});
		} finally {
			MinionManager.dismissAll(p.getUUID());
			for (Mob m : extra) m.discard();
			if (slots != null) slots.removeModifier(slotMod);
			GlimmerStub.testFree = free0;
			GlimmerStub.devDeny = deny0;
			GlimmerStub.SPENT.remove(p.getUUID());
			MinionEntity.testOwnerLookup = lookup0;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		info(P + "done");
	}

	private static boolean setTargetAggressive(WargEntity w, Mob target) {
		w.setTarget(target);
		boolean on = w.isAggressive();
		w.setTarget(null);
		return on && !w.isAggressive();
	}

	private static ServerPlayer sneaking(ServerPlayer p, boolean on) {
		p.setShiftKeyDown(on);
		return p;
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

	/** Ogg page 0 carries the Vorbis identification header: "\1vorbis", version (4 bytes), channels (1 byte). */
	static boolean monoOgg(String res) {
		try (InputStream in = Gear3Content.class.getResourceAsStream(res)) {
			if (in == null) return false;
			byte[] b = in.readNBytes(128);
			String s = new String(b, StandardCharsets.ISO_8859_1);
			int i = s.indexOf("\u0001vorbis");
			return s.startsWith("OggS") && i > 0 && i + 11 < b.length && b[i + 11] == 1;
		} catch (java.io.IOException e) {
			return false;
		}
	}

	/** The Warg skin: 64x32 (the vanilla wolf UV layout), with paint on the head front and the body. */
	static boolean wargTexture(String res) {
		try (InputStream in = Gear3Content.class.getResourceAsStream(res)) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img != null && img.getWidth() == 64 && img.getHeight() == 32 && (img.getRGB(6, 6) >>> 24) != 0 && (img.getRGB(25, 20) >>> 24) != 0;
		} catch (java.io.IOException e) {
			return false;
		}
	}
}
