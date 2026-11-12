package dev.elsewhere.gear3.s3.meadhorn;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.MeadHornItem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mead-Horn honey-bottle refill self-test (gear3 self-test group "MeadHornTests"). Synchronous on the server thread, on its
 * own FakePlayer at y 140, x 40, z 66 (nothing is spawned). Check names start with "Mead-Horn refill: ".
 */
public final class MeadHornTests {
	private MeadHornTests() {}

	static final String P = "Mead-Horn refill: ";

	static FakePlayer fake(ServerLevel l) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("s-meadhorn".getBytes(StandardCharsets.UTF_8)), "s-meadhorn"));
		p.snapTo(40.0, 140.0, 66.0, 0.0F, 0.0F);
		p.setNoGravity(true);
		p.setHealth(p.getMaxHealth());
		p.getInventory().clearContent();
		p.removeAllEffects();
		return p;
	}

	static ItemStack emptyHorn(Item horn) {
		ItemStack st = new ItemStack(horn);
		MeadHornRefill.setEmpty(st, true);
		return st;
	}

	static int count(FakePlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	static void reset(FakePlayer p, Item horn) {
		p.getInventory().clearContent();
		p.removeAllEffects();
		p.getAbilities().instabuild = false;
		p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(new ItemStack(horn)));
	}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item horn = Gear3Items.BY_ID.get("hrafnholt_mead_horn");
		boolean was = MeadHornRefill.ENABLED;
		if (horn == null) {
			check(P + "item hrafnholt_mead_horn registered", false);
			return;
		}
		FakePlayer p = fake(l);
		try {
			MeadHornRefill.ENABLED = true; // forced on for the behaviour checks
			info("Mead-Horn refill: kill switch " + MeadHornRefill.KILL_KEY + "=" + was + " (forced on for the behaviour checks)");
			check(P + "a fresh horn is full (existing horns stay full)", !MeadHornRefill.isEmpty(new ItemStack(horn)));
			checkS(P + "drinking empties the horn (heal + cooldown as before), and an empty horn cannot be drunk", () -> {
				reset(p, horn);
				ItemStack st = new ItemStack(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				horn.use(l, p, InteractionHand.MAIN_HAND);
				boolean ok = MeadHornRefill.isEmpty(st) && p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
				p.removeAllEffects();
				p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(st));
				InteractionResult again = horn.use(l, p, InteractionHand.MAIN_HAND); // empty, no bottle
				ok &= !p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) && again == InteractionResult.PASS && MeadHornRefill.isEmpty(st);
				reset(p, horn);
				return ok;
			});
			checkS(P + "refill works: horn in main hand, honey bottle in the off hand (horn use)", () -> {
				reset(p, horn);
				ItemStack st = emptyHorn(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 3));
				InteractionResult r = horn.use(l, p, InteractionHand.MAIN_HAND);
				boolean ok = r == InteractionResult.SUCCESS && !MeadHornRefill.isEmpty(st);
				reset(p, horn);
				return ok;
			});
			checkS(P + "refill works with the horn in the OFF hand and the bottle in the main hand (use-item event, no honey drunk)", () -> {
				reset(p, horn);
				ItemStack st = emptyHorn(horn);
				p.setItemInHand(InteractionHand.OFF_HAND, st);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
				InteractionResult r = UseItemCallback.EVENT.invoker().interact(p, l, InteractionHand.MAIN_HAND);
				boolean ok = r == InteractionResult.SUCCESS && !MeadHornRefill.isEmpty(st) && !p.isUsingItem();
				reset(p, horn);
				return ok;
			});
			checkS(P + "refill works with the horn in the main hand via the use-item event too", () -> {
				reset(p, horn);
				ItemStack st = emptyHorn(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE));
				InteractionResult r = UseItemCallback.EVENT.invoker().interact(p, l, InteractionHand.MAIN_HAND);
				boolean ok = r == InteractionResult.SUCCESS && !MeadHornRefill.isEmpty(st);
				reset(p, horn);
				return ok;
			});
			checkS(P + "one bottle is consumed and one glass bottle returned to the inventory (stack of 3 -> 2 + 1 glass), no dupes", () -> {
				reset(p, horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, emptyHorn(horn));
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 3));
				MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND);
				boolean ok = p.getItemInHand(InteractionHand.OFF_HAND).is(Items.HONEY_BOTTLE) && p.getItemInHand(InteractionHand.OFF_HAND).getCount() == 2
					&& count(p, Items.GLASS_BOTTLE) == 1 && count(p, Items.HONEY_BOTTLE) == 2;
				boolean second = MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND); // now full: nothing more happens
				ok &= !second && count(p, Items.GLASS_BOTTLE) == 1 && count(p, Items.HONEY_BOTTLE) == 2;
				reset(p, horn);
				return ok;
			});
			checkS(P + "the last bottle turns into the glass bottle in the same hand", () -> {
				reset(p, horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, emptyHorn(horn));
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE));
				MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND);
				boolean ok = p.getItemInHand(InteractionHand.OFF_HAND).is(Items.GLASS_BOTTLE) && p.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1
					&& count(p, Items.HONEY_BOTTLE) == 0 && count(p, Items.GLASS_BOTTLE) == 1;
				reset(p, horn);
				return ok;
			});
			checkS(P + "full inventory: the glass bottle drops at the player, nothing is lost", () -> {
				reset(p, horn);
				for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
				p.setItemInHand(InteractionHand.MAIN_HAND, emptyHorn(horn));
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
				var box = p.getBoundingBox().inflate(3.0);
				for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) e.discard();
				boolean refilled = MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND);
				var drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> e.getItem().is(Items.GLASS_BOTTLE));
				int glass = 0;
				for (var e : drops) {
					glass += e.getItem().getCount();
					e.discard();
				}
				boolean ok = refilled && glass == 1 && p.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1;
				reset(p, horn);
				return ok;
			});
			checkS(P + "no refill when full: the bottle stays, the event passes (honey is then drunk as usual)", () -> {
				reset(p, horn);
				ItemStack st = new ItemStack(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
				boolean r = MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND);
				InteractionResult ev = UseItemCallback.EVENT.invoker().interact(p, l, InteractionHand.OFF_HAND);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
				p.setItemInHand(InteractionHand.OFF_HAND, st);
				InteractionResult ev2 = UseItemCallback.EVENT.invoker().interact(p, l, InteractionHand.MAIN_HAND);
				boolean ok = !r && ev == InteractionResult.PASS && ev2 == InteractionResult.PASS && count(p, Items.HONEY_BOTTLE) == 2
					&& count(p, Items.GLASS_BOTTLE) == 0 && !MeadHornRefill.isEmpty(st);
				reset(p, horn);
				return ok;
			});
			checkS(P + "an empty horn without a honey bottle stays empty (other items do not refill it)", () -> {
				reset(p, horn);
				ItemStack st = emptyHorn(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS_BOTTLE));
				boolean ok = !MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND) && MeadHornRefill.isEmpty(st);
				reset(p, horn);
				return ok;
			});
			checkS(P + "creative: refills and keeps the honey bottle, no glass bottle appears", () -> {
				reset(p, horn);
				p.getAbilities().instabuild = true;
				ItemStack st = emptyHorn(horn);
				p.setItemInHand(InteractionHand.MAIN_HAND, st);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 1));
				boolean r = MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND);
				boolean ok = r && !MeadHornRefill.isEmpty(st) && p.getItemInHand(InteractionHand.OFF_HAND).is(Items.HONEY_BOTTLE)
					&& p.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1 && count(p, Items.GLASS_BOTTLE) == 0;
				reset(p, horn);
				return ok;
			});
			checkS(P + "kill switch off: refill is blocked, honey untouched, and the horn never empties (old reusable horn)", () -> {
				reset(p, horn);
				MeadHornRefill.ENABLED = false;
				try {
					ItemStack st = new ItemStack(horn);
					p.setItemInHand(InteractionHand.MAIN_HAND, st);
					horn.use(l, p, InteractionHand.MAIN_HAND);
					boolean neverEmpties = !MeadHornRefill.isEmpty(st) && p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
					reset(p, horn);
					ItemStack flagged = emptyHorn(horn); // even a flagged horn counts as full while off (no world is ever locked out)
					p.setItemInHand(InteractionHand.MAIN_HAND, flagged);
					p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
					boolean blocked = !MeadHornRefill.tryRefill(p, InteractionHand.MAIN_HAND)
						&& UseItemCallback.EVENT.invoker().interact(p, l, InteractionHand.MAIN_HAND) == InteractionResult.PASS
						&& count(p, Items.HONEY_BOTTLE) == 2 && count(p, Items.GLASS_BOTTLE) == 0;
					return neverEmpties && blocked;
				} finally {
					MeadHornRefill.ENABLED = true;
					reset(p, horn);
				}
			});
			checkS(P + "horn class is MeadHornItem and the lang lines + JEI line exist", () -> {
				if (!(horn instanceof MeadHornItem)) return false;
				JsonObject lang = json("/assets/elsewhere/lang/en_us.json");
				JsonObject jei = json("/elsewhere_gear3/jei_info.json");
				if (lang == null || jei == null) return false;
				if (!lang.has(MeadHornRefill.LANG_REFILLED) || !lang.has(MeadHornRefill.LANG_EMPTY)) return false;
				var arr = jei.getAsJsonArray("elsewhere:hrafnholt_mead_horn");
				if (arr == null) return false;
				for (var e : arr) if (e.getAsString().toLowerCase().contains("honey bottle")) return true;
				return false;
			});
		} finally {
			MeadHornRefill.ENABLED = was;
			reset(p, horn);
		}
	}

	/**
	 * Every copy of a resource on the classpath, merged (first wins). The core jar ships its own
	 * assets/elsewhere/lang/en_us.json, so a single getResourceAsStream can return the core file instead of gear3's.
	 */
	static JsonObject json(String path) {
		String rel = path.startsWith("/") ? path.substring(1) : path;
		JsonObject out = new JsonObject();
		boolean any = false;
		try {
			for (java.net.URL u : java.util.Collections.list(Gear3Content.class.getClassLoader().getResources(rel))) {
				try (InputStream in = u.openStream()) {
					for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
						if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
					any = true;
				}
			}
		} catch (Exception e) {
			return null;
		}
		return any ? out : null;
	}
}
