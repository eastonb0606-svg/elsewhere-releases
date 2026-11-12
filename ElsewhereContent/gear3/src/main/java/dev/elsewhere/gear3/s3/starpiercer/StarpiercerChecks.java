package dev.elsewhere.gear3.s3.starpiercer;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Scheduler;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Starpiercer self-test block (gear3 self-test, called from SelfTest.runChecks). Synchronous, on a FakePlayer in the forced
 * test chunks; every arrow it makes is discarded at the end. Check names start with "Starpiercer:".
 */
public final class StarpiercerChecks {
	private StarpiercerChecks() {}

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item bow = Starpiercer.item();
		check("Starpiercer: registered as a bow (StarpiercerItem)", bow instanceof StarpiercerItem && bow instanceof BowItem, bow);
		if (!(bow instanceof StarpiercerItem sp)) return;
		FakePlayer p = FakePlayer.get(l);
		BlockPos base = new BlockPos(40, 100, 40);

		// ---- registration, data, assets
		check("Starpiercer: arrow entity elsewhere:starpiercer_arrow registered and in #minecraft:arrows (Power, Punch)",
			BuiltInRegistries.ENTITY_TYPE.getValue(Gear3Content.id("starpiercer_arrow")) == Starpiercer.ARROW
				&& Starpiercer.ARROW.builtInRegistryHolder().is(EntityTypeTags.ARROWS));
		checkS("Starpiercer: enchantable like a bow (#minecraft:enchantable/bow and /durability, enchantability 15)", () -> {
			ItemStack st = new ItemStack(bow);
			return st.is(tag("minecraft:enchantable/bow")) && st.is(tag("minecraft:enchantable/durability")) && st.get(DataComponents.ENCHANTABLE) != null
				&& st.get(DataComponents.ENCHANTABLE).value() == 15 && st.isDamageableItem();
		});
		checkS("Starpiercer: Marksman calling item (#elsewhere:calling_weapon/marksman)", () -> new ItemStack(bow).is(tag("elsewhere:calling_weapon/marksman")));
		checkS("Starpiercer: 4 sound events registered, in sounds.json, with subtitles", () -> {
			JsonObject sounds = merged("assets/elsewhere/sounds.json"), lang = merged("assets/elsewhere/lang/en_us.json");
			for (var h : List.of(Starpiercer.SHOOT, Starpiercer.DRAW_FULL, Starpiercer.CANCEL, Starpiercer.HIT)) {
				String path = h.unwrapKey().orElseThrow().identifier().getPath();
				if (sounds == null || !sounds.has(path) || lang == null) return false;
				String sub = sounds.getAsJsonObject(path).get("subtitle").getAsString();
				if (!lang.has(sub)) return false;
			}
			return lang.has("entity.elsewhere.starpiercer_arrow");
		});
		checkS("Starpiercer: pull models (3 frames on use_duration over 1.6 s) and 16x16 frame textures", () -> {
			JsonObject def = json("assets/elsewhere/items/" + Starpiercer.ID + ".json");
			if (def == null) return false;
			String t = def.toString();
			if (!t.contains("minecraft:using_item") || !t.contains("minecraft:use_duration") || !t.contains("0.03125")) return false;
			for (int i = 0; i < 3; i++) {
				String f = Starpiercer.ID + "_pulling_" + i;
				JsonObject m = json("assets/elsewhere/models/item/" + f + ".json");
				if (!t.contains("elsewhere:item/" + f) || m == null || !m.getAsJsonObject("textures").get("layer0").getAsString().equals("elsewhere:item/" + f)) return false;
				if (!png16("assets/elsewhere/textures/item/" + f + ".png")) return false;
			}
			return true;
		});
		checkS("Starpiercer: arrow texture (32x32, the vanilla arrow layout)", () -> {
			try (InputStream in = Gear3Content.class.getResourceAsStream("/assets/elsewhere/textures/entity/projectiles/starpiercer_arrow.png")) {
				if (in == null) return false;
				var img = ImageIO.read(in);
				return img.getWidth() == 32 && img.getHeight() == 32 && (img.getRGB(10, 2) >>> 24) != 0 && (img.getRGB(2, 7) >>> 24) != 0;
			} catch (java.io.IOException e) {
				return false;
			}
		});

		// ---- draw
		check("Starpiercer: full draw takes 1.6 s (32 ticks count as the bow's 20)", Starpiercer.effectiveHeld(32) == 20 && BowItem.getPowerForTime(Starpiercer.effectiveHeld(31)) < 1F
			&& BowItem.getPowerForTime(Starpiercer.effectiveHeld(32)) == 1F, Starpiercer.effectiveHeld(31) + "/" + Starpiercer.effectiveHeld(32));
		check("Starpiercer: full-draw chime at tick 32 (no Quiver)", !sp.fullAt(31, p) && sp.fullAt(32, p));
		check("Starpiercer: launch speed x1.6 (4.8 blocks/tick at full draw)", Math.abs(Starpiercer.velocity(1F) - 4.8F) < 1.0E-4);
		check("Starpiercer: 32 weightless blocks = 7 ticks at full draw", Starpiercer.straightTicks(4.8) == 7 && Starpiercer.straightTicks(0.2) == Starpiercer.MAX_STRAIGHT_TICKS,
			Starpiercer.straightTicks(4.8));
		check("Starpiercer: longbow zoom 1x idle, 1.5x full draw, 2x sneaking, eased",
			Math.abs(Starpiercer.zoom(0, 0) - 1F) < 1e-4 && Math.abs(Starpiercer.zoom(1, 0) - 1.5F) < 1e-4 && Math.abs(Starpiercer.zoom(1, 1) - 2F) < 1e-4
				&& Math.abs(Starpiercer.zoom(0.5F, 0) - 1.125F) < 1e-4 && Starpiercer.zoom(5, 5) <= 2F && Math.abs(Starpiercer.drawProgress(16) - 0.5F) < 1e-4);

		// ---- sprinting cancels the draw
		checkS("Starpiercer: sprinting stops a draw in progress and blocks a new one", () -> {
			p.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
			ItemStack st = new ItemStack(bow);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			p.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
			p.setSprinting(false);
			p.startUsingItem(InteractionHand.MAIN_HAND);
			boolean drawing = p.isUsingItem();
			p.setSprinting(true);
			st.onUseTick(l, p, p.getUseItemRemainingTicks());
			boolean cancelled = !p.isUsingItem();
			boolean refused = !sp.use(l, p, InteractionHand.MAIN_HAND).consumesAction() && !p.isUsingItem();
			p.setSprinting(false);
			return drawing && cancelled && refused;
		});

		// ---- a full-draw shot with a plain arrow
		checkS("Starpiercer: plain arrow -> starpiercer_arrow at x1.6 speed, pierce +2, crit, weightless, arrow used", () -> {
			clear(l, base);
			p.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
			ItemStack st = new ItemStack(bow);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			p.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
			boolean shot = sp.releaseUsing(st, l, p, sp.getUseDuration(st, p) - Starpiercer.DRAW_TICKS);
			List<AbstractArrow> a = arrows(l, base);
			if (!shot || a.size() != 1 || !(a.get(0) instanceof StarpiercerArrow arrow)) return false;
			double v = arrow.getDeltaMovement().length();
			boolean ok = v > 4.6 && v < 5.0 && arrow.getPierceLevel() == Starpiercer.PIERCE && arrow.isCritArrow() && arrow.isNoGravity()
				&& arrow.launchPoint() != null && arrow.pickup == AbstractArrow.Pickup.ALLOWED && p.getInventory().getItem(9).getCount() == 3
				&& st.getDamageValue() == 1 && arrow.getOwner() == p;
			clear(l, base);
			return ok;
		});
		checkS("Starpiercer: a short draw shoots slower (power curve over 1.6 s)", () -> {
			clear(l, base);
			p.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
			ItemStack st = new ItemStack(bow);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			p.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
			sp.releaseUsing(st, l, p, sp.getUseDuration(st, p) - 16);
			List<AbstractArrow> a = arrows(l, base);
			float expect = Starpiercer.velocity(BowItem.getPowerForTime(Starpiercer.effectiveHeld(16)));
			boolean ok = a.size() == 1 && Math.abs(a.get(0).getDeltaMovement().length() - expect) < 0.1 && !a.get(0).isCritArrow();
			clear(l, base);
			return ok;
		});

		// ---- flight: weightless for 32 blocks, then gravity
		checkS("Starpiercer: no gravity for the first 32 blocks, then it falls", () -> {
			clear(l, base);
			double z0 = base.getZ() - 19.5; // the whole ~46-block flight stays in the forced test chunks (z 16..79)
			p.snapTo(base.getX() + 0.5, base.getY() + 1, z0, 0, 0);
			StarpiercerArrow arrow = new StarpiercerArrow(l, p, new ItemStack(Items.ARROW), new ItemStack(bow));
			arrow.shoot(0, 0, 1, Starpiercer.velocity(1F), 0F);
			l.addFreshEntity(arrow);
			Starpiercer.fly(arrow);
			double vy0 = arrow.getDeltaMovement().y, y0 = arrow.getY(), startZ = arrow.getZ();
			boolean launched = arrow.isNoGravity() && arrow.launchPoint() != null;
			boolean straight = true;
			double restoredAt = -1;
			for (int t = 0; t < 14 && restoredAt < 0; t++) {
				arrow.tickCount++;
				arrow.tick();
				if (arrow.isNoGravity()) straight &= Math.abs(arrow.getY() - y0) < 1.0E-6 && Math.abs(arrow.getDeltaMovement().y - vy0) < 1.0E-6;
				else restoredAt = arrow.getZ() - startZ;
			}
			double vyRestore = arrow.getDeltaMovement().y;
			for (int t = 0; t < 3; t++) {
				arrow.tickCount++;
				arrow.tick();
			}
			boolean falls = arrow.getDeltaMovement().y < vyRestore - 0.1 && arrow.getY() < y0;
			arrow.discard();
			return launched && straight && restoredAt >= Starpiercer.STRAIGHT_BLOCKS && restoredAt < Starpiercer.STRAIGHT_BLOCKS + 5 && falls;
		});

		// ---- other ammo keeps its own entity and gets the flight rules
		checkS("Starpiercer: spectral arrow stays spectral, gets pierce +2 and a 7-tick weightless window", () -> {
			clear(l, base);
			p.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
			ItemStack st = new ItemStack(bow);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			p.getInventory().setItem(9, new ItemStack(Items.SPECTRAL_ARROW, 4));
			sp.releaseUsing(st, l, p, sp.getUseDuration(st, p) - Starpiercer.DRAW_TICKS);
			List<AbstractArrow> a = arrows(l, base);
			if (a.size() != 1 || !(a.get(0) instanceof SpectralArrow arrow)) return false;
			boolean before = arrow.isNoGravity() && arrow.entityTags().contains(Starpiercer.TAG) && Starpiercer.pending(arrow) && arrow.getPierceLevel() == Starpiercer.PIERCE
				&& arrow.getDeltaMovement().length() > 4.6;
			Gear3Scheduler.runDueWithin(s, Starpiercer.straightTicks(arrow.getDeltaMovement().length()) + 1);
			boolean after = !arrow.isNoGravity() && !arrow.entityTags().contains(Starpiercer.TAG) && !Starpiercer.pending(arrow);
			clear(l, base);
			return before && after;
		});
		checkS("Starpiercer: tipped arrow stays a tipped vanilla arrow with its potion", () -> {
			clear(l, base);
			p.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 0);
			ItemStack st = new ItemStack(bow);
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 4);
			tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SLOWNESS));
			p.getInventory().setItem(9, tipped);
			sp.releaseUsing(st, l, p, sp.getUseDuration(st, p) - Starpiercer.DRAW_TICKS);
			List<AbstractArrow> a = arrows(l, base);
			boolean ok = a.size() == 1 && a.get(0) instanceof Arrow arrow && arrow.getColor() > 0 && arrow.isNoGravity();
			Gear3Scheduler.runDueWithin(s, 10);
			clear(l, base);
			return ok;
		});
		checkS("Starpiercer: a weightless foreign arrow loaded after a restart gets its gravity back", () -> {
			clear(l, base);
			SpectralArrow arrow = new SpectralArrow(l, p, new ItemStack(Items.SPECTRAL_ARROW), null);
			arrow.setNoGravity(true);
			arrow.addTag(Starpiercer.TAG);
			l.addFreshEntity(arrow); // ENTITY_LOAD: tagged, not pending in this session
			boolean ok = !arrow.isNoGravity() && !arrow.entityTags().contains(Starpiercer.TAG);
			arrow.discard();
			return ok;
		});

		// ---- Aphelion drop safety
		checkS("Starpiercer: a dropped Starpiercer on solid ground stays put and glows (void columns move it to the killer)", () -> {
			Vec3 at = new Vec3(base.getX() + 0.5, base.getY() + 2, base.getZ() + 0.5);
			ItemEntity drop = new ItemEntity(l, at.x, at.y, at.z, new ItemStack(bow));
			l.addFreshEntity(drop);
			l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2)); // warm-up query (GOTCHAS: first query after addFreshEntity)
			boolean grounded = !Starpiercer.overVoid(l, at);
			int moved = Starpiercer.secureDrops(l, at, p);
			boolean ok = grounded && moved == 0 && drop.position().distanceTo(at) < 0.01 && drop.hasGlowingTag();
			drop.discard();
			return ok;
		});
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(9, ItemStack.EMPTY);
	}

	private static List<AbstractArrow> arrows(ServerLevel l, BlockPos base) {
		AABB box = new AABB(base).inflate(8);
		l.getEntitiesOfClass(AbstractArrow.class, box); // warm-up query (GOTCHAS: the first query after addFreshEntity can miss)
		return l.getEntitiesOfClass(AbstractArrow.class, box, e -> e.isAlive());
	}

	private static void clear(ServerLevel l, BlockPos base) {
		for (AbstractArrow a : l.getEntitiesOfClass(AbstractArrow.class, new AABB(base).inflate(48))) a.discard();
	}

	private static TagKey<Item> tag(String id) {
		return TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.parse(id));
	}

	private static boolean png16(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
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

	private static JsonObject json(String path) {
		try (InputStream in = Gear3Content.class.getResourceAsStream("/" + path)) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Every copy on the classpath merged, first copy wins per key (GOTCHAS: devlibs bring their own sounds.json / lang). */
	private static JsonObject merged(String path) {
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
