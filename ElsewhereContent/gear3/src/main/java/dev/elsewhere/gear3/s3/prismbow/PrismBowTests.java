package dev.elsewhere.gear3.s3.prismbow;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.MagicBolt;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Loot;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Prism Bow self-test block (gear3 self-test, called from SelfTest.runChecks). Synchronous on the server thread, on a FakePlayer
 * of its own at (24.5, 175, 20.5) facing south: the light arrow's 24 blocks end at z 44.5, inside the forced test chunks and
 * clear of the other blocks' corners. Entities are ticked by hand ({@code tickCount++; tick()}); everything it spawns is
 * removed. Check names start with "Prism Bow:".
 */
public final class PrismBowTests {
	private PrismBowTests() {}

	static final double X = 24.5, Y = 175.0, Z = 20.5;
	static final AABB AREA = new AABB(X - 6, Y - 6, Z - 4, X + 6, Y + 8, Z + 30);

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item item = BuiltInRegistries.ITEM.getValue(Gear3Content.id(PrismBowItem.ID));
		check("Prism Bow: registered as a bow (PrismBowItem)", item instanceof PrismBowItem, item);
		if (!(item instanceof PrismBowItem bow)) return;

		// ---- registration, data, assets
		checkS("Prism Bow: enchantable and repairable like a bow (#minecraft:enchantable/bow and /durability, 640 durability)", () -> {
			ItemStack st = new ItemStack(bow);
			return st.is(tag("minecraft:enchantable/bow")) && st.is(tag("minecraft:enchantable/durability")) && st.get(DataComponents.ENCHANTABLE) != null
				&& st.get(DataComponents.REPAIRABLE) != null && st.isDamageableItem() && st.getMaxDamage() == 640;
		});
		checkS("Prism Bow: calling item for Marksman and Caster (M+C hybrid, read as Marksman first)", () -> {
			ItemStack st = new ItemStack(bow);
			return st.is(Calling.MARKSMAN_WEAPON) && st.is(Calling.CASTER_ITEM) && Calling.itemPath(st) == Calling.Path.MARKSMAN;
		});
		checkS("Prism Bow: pull models (vanilla 3 stages over 1.0 s), upright-sprite hand display, 16x16 frame textures", () -> {
			JsonObject def = json("assets/elsewhere/items/prism_bow.json"), base = json("assets/elsewhere/models/item/prism_bow.json");
			if (def == null || base == null) return false;
			String t = def.toString();
			if (!t.contains("minecraft:using_item") || !t.contains("minecraft:use_duration") || !t.contains("0.05")) return false;
			JsonArray rot = base.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand").getAsJsonArray("rotation");
			if (!"minecraft:item/bow".equals(base.get("parent").getAsString()) || rot.get(2).getAsDouble() != -85.0) return false;
			if (!png16("assets/elsewhere/textures/item/prism_bow.png")) return false;
			for (int i = 0; i < 3; i++) {
				String f = "prism_bow_pulling_" + i;
				JsonObject m = json("assets/elsewhere/models/item/" + f + ".json");
				if (!t.contains("elsewhere:item/" + f) || m == null || !m.getAsJsonObject("textures").get("layer0").getAsString().equals("elsewhere:item/" + f)
					|| !png16("assets/elsewhere/textures/item/" + f + ".png")) return false;
			}
			return true;
		});
		checkS("Prism Bow: name, 4 lore lines and JEI lines (how to get, the light arrow, the other-hand rule)", () -> {
			JsonObject lang = merged("assets/elsewhere/lang/en_us.json"), jei = merged("elsewhere_gear3/jei_info.json");
			if (lang == null || jei == null || !lang.has("item.elsewhere.prism_bow") || !jei.has("elsewhere:prism_bow")) return false;
			for (int i = 1; i <= 4; i++) if (!lang.has("item.elsewhere.prism_bow.lore" + i)) return false;
			String lines = jei.get("elsewhere:prism_bow").toString();
			return "Prism Bow".equals(lang.get("item.elsewhere.prism_bow").getAsString()) && lines.contains("Heliodor") && lines.contains("light arrow")
				&& lines.contains("other hand");
		});
		checkS("Prism Bow: recipe (7 Sunglass Shards + Heliodor Mirror Core + bow) loads exactly when both parts exist", () -> {
			boolean parts = BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("elsewhere", "sunglass_shard"))
				&& BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("elsewhere", "heliodor_mirror_core"));
			boolean loaded = s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Gear3Content.id(PrismBowItem.ID))).isPresent();
			info("Prism Bow: recipe parts " + (parts ? "present" : "absent (Glasswaste or gear2 off)") + ", recipe " + (loaded ? "loaded" : "not loaded"));
			return parts == loaded;
		});
		checkS("Prism Bow: Heliodor drops it (30%)", () -> {
			List<Gear3Loot.Entry> drops = Gear3Loot.DROPS.get("elsewhere:entities/heliodor");
			return drops != null && drops.stream().anyMatch(e -> e.item() == bow && Math.abs(e.drop().chance() - 0.3F) < 1.0E-6F);
		});
		check("Prism Bow: light arrow numbers (2 Glimmer, 8 x H, speed 3.0, lifetime 7 = 24 blocks, full draw at 20 ticks)",
			PrismBowItem.GLIMMER_COST == 2 && PrismBowItem.LIGHT_DAMAGE == 8.0F && PrismBowItem.LIGHT_LIFETIME == 7
				&& Math.abs((PrismBowItem.LIGHT_LIFETIME + 1) * PrismBowItem.LIGHT_SPEED - PrismBowItem.LIGHT_RANGE) < 1.0E-6, PrismBowItem.LIGHT_LIFETIME);

		// ---- behaviour (the kill switch is forced on for these and restored after; its configured state is reported)
		info("Prism Bow: kill switch prism_bow=" + PrismBowItem.ENABLED + " (forced on for the behaviour checks)");
		boolean was = PrismBowItem.ENABLED;
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("elsewhere-prism-bow-test".getBytes(StandardCharsets.UTF_8)), "prism_bow_test"));
		float glimmer0 = Glimmer.current(p);
		try {
			PrismBowItem.ENABLED = true;
			check("Prism Bow: the full draw is exactly 20 ticks (1.0 s, no Quiver)", fullAt(bow, p, 20) && !fullAt(bow, p, 19));

			checkS("Prism Bow: full draw + Glimmer = one light arrow (8 x H, white-gold, weightless); Glimmer spent, arrow kept, bow worn 1", () -> {
				ItemStack st = reset(l, p, bow);
				float max = Glimmer.max(p), cost = Glimmer.cost(p, PrismBowItem.GLIMMER_COST);
				Glimmer.setForTest(p, max);
				boolean shot = bow.releaseUsing(st, l, p, bow.getUseDuration(st, p) - 20);
				List<MagicBolt> bolts = bolts(l);
				boolean ok = shot && bolts.size() == 1 && arrows(l).isEmpty() && Math.abs(Glimmer.current(p) - (max - cost)) < 1.0E-3F
					&& p.getInventory().getItem(9).getCount() == 4 && st.getDamageValue() == 1;
				if (ok) {
					MagicBolt b = bolts.get(0);
					ok = b.color() == PrismBowItem.LIGHT_COLOR && Math.abs(b.damage - PrismBowItem.LIGHT_DAMAGE * PrismBowItem.hybrid(p)) < 1.0E-4F && b.gravity == 0F
						&& b.isNoGravity() && b.lifetime == PrismBowItem.LIGHT_LIFETIME && b.getOwner() == p && Math.abs(b.getDeltaMovement().length() - PrismBowItem.LIGHT_SPEED) < 0.01;
				}
				info("Prism Bow: H(Marksman, Caster) for the test player = " + PrismBowItem.hybrid(p) + ", Glimmer cost " + cost + " of " + max);
				clear(l);
				return ok;
			});
			checkS("Prism Bow: the light arrow flies dead straight and fades after 24 blocks", () -> {
				ItemStack st = reset(l, p, bow);
				MagicBolt b = bow.loose(l, p, st);
				double y0 = b.getY(), z0 = b.getZ();
				boolean straight = true;
				int moves = 0;
				while (!b.isRemoved() && moves < 20) {
					b.tickCount++;
					b.tick();
					moves++;
					straight &= Math.abs(b.getY() - y0) < 1.0E-6;
				}
				double flown = b.getZ() - z0;
				info("Prism Bow: light arrow flew " + flown + " blocks in " + moves + " ticks");
				clear(l);
				return b.isRemoved() && straight && moves == PrismBowItem.LIGHT_LIFETIME + 1 && Math.abs(flown - PrismBowItem.LIGHT_RANGE) < 0.05;
			});
			checkS("Prism Bow: a light arrow hit deals Spells.damage(8 x H) as spell damage", () -> {
				ItemStack st = reset(l, p, bow);
				Mob pig = EntityTypes.PIG.create(l, EntitySpawnReason.COMMAND);
				if (pig == null) return false;
				MagicBolt b = bow.loose(l, p, st);
				pig.snapTo(X, b.getY() - 0.45, Z + 8, 0.0F, 0.0F);
				pig.setNoAi(true);
				pig.setNoGravity(true);
				l.addFreshEntity(pig);
				l.getEntitiesOfClass(Mob.class, AREA); // warm-up query (GOTCHAS: the first query after addFreshEntity can miss)
				float hp0 = pig.getHealth();
				float want = Spells.damage(p, pig, b.damage * b.scale, false, b.moonlit);
				for (int t = 0; t < 8 && !b.isRemoved(); t++) {
					b.tickCount++;
					b.tick();
				}
				float lost = hp0 - pig.getHealth();
				boolean spell = pig.getLastDamageSource() != null && pig.getLastDamageSource().is(Spells.SPELL);
				info("Prism Bow: light arrow hit a pig for " + lost + " (Spells.damage " + want + ")");
				pig.discard();
				clear(l);
				return b.isRemoved() && spell && want > 0 && Math.abs(lost - want) < 0.05F;
			});
			checkS("Prism Bow: without the Glimmer it shoots the arrow (arrow used, nothing spent, no light arrow)", () -> {
				ItemStack st = reset(l, p, bow);
				Glimmer.setForTest(p, 0F);
				boolean shot = bow.releaseUsing(st, l, p, bow.getUseDuration(st, p) - 20);
				boolean ok = shot && arrows(l).size() == 1 && bolts(l).isEmpty() && p.getInventory().getItem(9).getCount() == 3 && Glimmer.current(p) == 0F;
				clear(l);
				return ok;
			});
			checkS("Prism Bow: a short draw shoots the arrow and spends no Glimmer", () -> {
				ItemStack st = reset(l, p, bow);
				float max = Glimmer.max(p);
				Glimmer.setForTest(p, max);
				boolean shot = bow.releaseUsing(st, l, p, bow.getUseDuration(st, p) - 10);
				boolean ok = shot && arrows(l).size() == 1 && bolts(l).isEmpty() && Math.abs(Glimmer.current(p) - max) < 1.0E-4F;
				clear(l);
				return ok;
			});
			checkS("Prism Bow: arrows in the other hand are always loosed as arrows (spectral from the off hand, no Glimmer)", () -> {
				ItemStack st = reset(l, p, bow);
				float max = Glimmer.max(p);
				Glimmer.setForTest(p, max);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SPECTRAL_ARROW, 4));
				boolean shot = bow.releaseUsing(st, l, p, bow.getUseDuration(st, p) - 20);
				List<AbstractArrow> a = arrows(l);
				boolean ok = shot && a.size() == 1 && a.get(0) instanceof SpectralArrow && bolts(l).isEmpty() && p.getOffhandItem().getCount() == 3
					&& p.getInventory().getItem(9).getCount() == 4 && Math.abs(Glimmer.current(p) - max) < 1.0E-4F;
				clear(l);
				return ok;
			});
			checkS("Prism Bow: kill switch off = a plain bow (full draw shoots the arrow, no Glimmer)", () -> {
				ItemStack st = reset(l, p, bow);
				float max = Glimmer.max(p);
				Glimmer.setForTest(p, max);
				PrismBowItem.ENABLED = false;
				boolean shot = bow.releaseUsing(st, l, p, bow.getUseDuration(st, p) - 20);
				PrismBowItem.ENABLED = true;
				boolean ok = shot && arrows(l).size() == 1 && bolts(l).isEmpty() && p.getInventory().getItem(9).getCount() == 3 && Math.abs(Glimmer.current(p) - max) < 1.0E-4F;
				clear(l);
				return ok;
			});
		} finally {
			PrismBowItem.ENABLED = was;
			clear(l);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.getInventory().setItem(9, ItemStack.EMPTY);
			Glimmer.setForTest(p, glimmer0);
		}
	}

	static boolean fullAt(PrismBowItem bow, FakePlayer p, int held) {
		ItemStack st = new ItemStack(bow);
		return bow.fullDraw(st, p, bow.getUseDuration(st, p) - held);
	}

	/** The test player at its spot facing south, a fresh bow in the main hand, 4 arrows in slot 9, empty off hand, no strays. */
	static ItemStack reset(ServerLevel l, FakePlayer p, PrismBowItem bow) {
		clear(l);
		p.snapTo(X, Y, Z, 0.0F, 0.0F);
		p.setYHeadRot(0.0F);
		p.setNoGravity(true);
		p.setDeltaMovement(0, 0, 0);
		ItemStack st = new ItemStack(bow);
		p.setItemInHand(InteractionHand.MAIN_HAND, st);
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
		return st;
	}

	static List<MagicBolt> bolts(ServerLevel l) {
		l.getEntitiesOfClass(MagicBolt.class, AREA); // warm-up query (GOTCHAS)
		return l.getEntitiesOfClass(MagicBolt.class, AREA, Entity::isAlive);
	}

	static List<AbstractArrow> arrows(ServerLevel l) {
		l.getEntitiesOfClass(AbstractArrow.class, AREA); // warm-up query (GOTCHAS)
		return l.getEntitiesOfClass(AbstractArrow.class, AREA, Entity::isAlive);
	}

	static void clear(ServerLevel l) {
		for (MagicBolt b : l.getEntitiesOfClass(MagicBolt.class, AREA)) b.discard();
		for (AbstractArrow a : l.getEntitiesOfClass(AbstractArrow.class, AREA)) a.discard();
	}

	static TagKey<Item> tag(String id) {
		return TagKey.create(Registries.ITEM, Identifier.parse(id));
	}

	static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Every copy on the classpath merged, first copy wins per key (GOTCHAS: devlibs bring their own lang / JEI files). */
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
			return null;
		}
		return out;
	}

	static boolean png16(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return false;
			var img = ImageIO.read(in);
			return img != null && img.getWidth() == 16 && img.getHeight() == 16;
		} catch (java.io.IOException e) {
			return false;
		}
	}
}
