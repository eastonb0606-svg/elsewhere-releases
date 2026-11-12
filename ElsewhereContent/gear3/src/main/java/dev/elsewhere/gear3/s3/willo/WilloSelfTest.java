package dev.elsewhere.gear3.s3.willo;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.gear3.SpellItems;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.stub.summoner.GlimmerStub;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

/**
 * s-willolantern self-test ("Will-o'-Lantern: ..." checks), called once from gear3's dev SelfTest. Runs synchronously on its own
 * FakePlayer in a cleared air pocket at (18..36, 128..137, 64..76) inside the forced arena chunks; the wisp's AI is ticked by hand.
 */
public final class WilloSelfTest {
	private WilloSelfTest() {}

	static final String P = "Will-o'-Lantern: ";

	public static void run(MinecraftServer s) {
		ServerLevel l = s.overworld();
		Item lantern = WillOLantern.item();
		check(P + "item registered as a WillOLanternItem calling a Marsh Wisp", lantern instanceof WillOLanternItem w && WillOLantern.WISP != null && w.minionType() == WillOLantern.WISP);
		check(P + "Marsh Wisp entity type registered as elsewhere:marsh_wisp", WillOLantern.WISP != null
			&& Gear3Content.id(WillOLantern.WISP_ID).equals(BuiltInRegistries.ENTITY_TYPE.getKey(WillOLantern.WISP))
			&& WillOLantern.WISP.create(l, EntitySpawnReason.COMMAND) instanceof MarshWisp);
		checkS(P + "marsh_wisp entity texture is a 32x32 PNG", WilloSelfTest::wispTexture);
		checkS(P + "lang: wisp name and the three overlay messages", () -> lang("entity.elsewhere.marsh_wisp") && lang("message.elsewhere.will_o_lantern.off")
			&& lang("message.elsewhere.will_o_lantern.echo") && lang("message.elsewhere.will_o_lantern.nothing"));
		if (!(lantern instanceof WillOLanternItem item)) return;
		ItemStack probe = new ItemStack(item);
		check(P + "calling tags: Caster and Summoner weapon (Ritualist hybrid)", probe.is(tag("elsewhere", "calling_weapon/caster")) && probe.is(tag("elsewhere", "calling_weapon/summoner")));
		check(P + "excluded from the Transmutation Table (boss-only gear)", probe.is(tag("testmeter", "transmutation_excluded")));
		if (!WillOLantern.ENABLED) {
			info("Will-o'-Lantern is switched off (will_o_lantern=false): behaviour checks skipped");
			return;
		}
		Item ember = Gear3Items.BY_ID.get("emberfall_rod"), blink = Gear3Items.BY_ID.get("blinkstone"), lamp = Gear3Items.BY_ID.get("glimmer_lantern"),
			chalice = Gear3Items.BY_ID.get("mirewick_almoners_chalice");
		check(P + "recast list: damage spells only (Emberfall yes; Blinkstone, Glimmer Lantern, Chalice, a stick no)", WillOLantern.recastable(ember)
			&& !WillOLantern.recastable(blink) && !WillOLantern.recastable(lamp) && !WillOLantern.recastable(chalice) && !WillOLantern.recastable(Items.STICK));
		Item wand = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", "amethyst_wand"));
		if (wand != Items.AIR) check(P + "recast list includes arsenal's Amethyst Wand", WillOLantern.recastable(wand));

		// ---- arena, owner, Glimmer
		int queued0 = Gear3Scheduler.pending(); // other blocks' timers are not ours to judge
		int x0 = 18, y0 = 128, z0 = 64;
		for (int x = x0; x <= 36; x++) for (int y = y0; y <= 137; y++) for (int z = z0; z <= 76; z++) l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		BlockPos home = new BlockPos(22, 130, 70);
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("gear3_willo".getBytes(StandardCharsets.UTF_8)), "gear3_willo"));
		p.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, -90F, 0F);
		Function<UUID, ServerPlayer> prevLookup = MinionEntity.testOwnerLookup;
		boolean prevFree = GlimmerStub.testFree, prevDeny = GlimmerStub.devDeny;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : prevLookup.apply(u);
		GlimmerStub.testFree = false;
		GlimmerStub.devDeny = false;
		try {
			body(s, l, p, item, ember, blink, home);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			WillOLantern.rememberForTest(p, null);
			MinionEntity.testOwnerLookup = prevLookup;
			GlimmerStub.testFree = prevFree;
			GlimmerStub.devDeny = prevDeny;
		}
		check(P + "nothing left behind: no live wisp light, nothing of ours queued", WispLights.live() == 0 && Gear3Scheduler.pending() <= queued0,
			WispLights.live() + " live, " + Gear3Scheduler.pending() + " queued (" + queued0 + " before)");
	}

	private static void body(MinecraftServer s, ServerLevel l, FakePlayer p, WillOLanternItem item, Item ember, Item blink, BlockPos home) {
		// ---- the last spell (Spells.ON_CAST)
		WillOLantern.rememberForTest(p, null);
		Spells.afterCast(p, blink);
		boolean ignored = WillOLantern.lastSpell(p) == null;
		Spells.afterCast(p, ember);
		Spells.afterCast(p, blink);
		check(P + "remembers the last damage spell cast; a Blinkstone cast doesn't replace it", ignored && WillOLantern.lastSpell(p) == ember, WillOLantern.lastSpell(p));
		float scale = WillOLantern.recastScale(p);
		check(P + "recast power is 40% x H(caster, summoner), in (0, 0.4]", scale > 0F && scale <= 0.4F + 1e-6F && Math.abs(scale - 0.4F * WillOLantern.hybrid(p)) < 1e-6F, scale);
		info("Will-o'-Lantern: H=" + WillOLantern.hybrid(p) + ", castFree hook " + (WillOLantern.hasCastFree() ? "present (3.0 arsenal)" : "absent (2.x fallback: spell power scaled)"));

		// ---- the call: 10 Glimmer, 1 durability, one wisp per player
		ItemStack stack = new ItemStack(item);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		GlimmerStub.setPool(p, 100F);
		float before = GlimmerStub.pool(p);
		MinionEntity first = item.summonNow(p, stack);
		float after = GlimmerStub.pool(p);
		check(P + "the call summons a Marsh Wisp and costs 1 durability", first instanceof MarshWisp && stack.getDamageValue() == 1, first + ", damage " + stack.getDamageValue());
		if (Float.isNaN(before)) info("Will-o'-Lantern: arsenal Glimmer pool not readable here; Glimmer cost not measured");
		else check(P + "the call costs 10 Glimmer (after spell-cost scaling)", Math.abs((before - after) - GlimmerStub.scaled(p, WillOLantern.SUMMON_COST)) < 1e-3F, before + " -> " + after);
		MinionEntity second = item.summonNow(p, stack);
		long wisps = MinionManager.minions(p).stream().filter(m -> m instanceof MarshWisp).count();
		check(P + "one wisp per player: a new call puts out the old one", first != null && first.isRemoved() && second instanceof MarshWisp && wisps == 1
			&& WillOLantern.wispOf(p) == second, "wisps=" + wisps);
		if (!(second instanceof MarshWisp wisp)) return;

		// ---- the recast
		Mob husk = husk(l, home.getX() + 6.5, home.getY(), home.getZ() + 0.5);
		WillOLantern.rememberForTest(p, ember);
		float yRot = p.getYRot(), xRot = p.getXRot(), head = p.getYHeadRot();
		float want = Spells.damage(p, husk, SpellItems.EMBER_DAMAGE, false, false) * scale, hp = husk.getHealth();
		int n0 = WillOLantern.recasts;
		boolean cast = WillOLantern.recast(l, p, wisp, husk);
		check(P + "recast hits the target with the last spell at the recast power", cast && WillOLantern.recasts == n0 + 1 && Math.abs(Spells.lastDamage - want) < 1e-3F
			&& husk.getHealth() < hp, Spells.lastDamage + " vs " + want + ", hp " + hp + " -> " + husk.getHealth());
		check(P + "the owner's view is restored after the recast", p.getYRot() == yRot && p.getXRot() == xRot && p.getYHeadRot() == head);
		check(P + "a recast does not count as a cast (memory unchanged, no ON_CAST)", WillOLantern.lastSpell(p) == ember);
		checkS(P + "the wisp never intercepts its own echo (floating right on the spell's line)", () -> {
			var mid = p.getEyePosition().add(husk.getBoundingBox().getCenter()).scale(0.5);
			wisp.setPos(mid.x, mid.y - wisp.getBbHeight() * 0.5, mid.z);
			husk.damageCooldownTime = 0; // the hit-immunity frames of the echo just cast (LivingEntity.damageCooldownTime; Entity.setInvulnerableTime is a different timer)
			float h0 = husk.getHealth();
			return WillOLantern.recast(l, p, wisp, husk) && husk.getHealth() < h0 && !wisp.isSpectator();
		});
		WillOLantern.rememberForTest(p, blink);
		boolean noBlink = !WillOLantern.recast(l, p, wisp, husk);
		WillOLantern.rememberForTest(p, null);
		boolean noneYet = !WillOLantern.recast(l, p, wisp, husk);
		check(P + "never echoes a spell off the list, nor anything before your first damage spell", noBlink && noneYet);
		WillOLantern.rememberForTest(p, ember);
		BlockPos wall = home.offset(3, 0, 0);
		l.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
		l.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 2);
		boolean blocked = !WillOLantern.recast(l, p, wisp, husk);
		l.setBlock(wall, Blocks.AIR.defaultBlockState(), 2);
		l.setBlock(wall.above(), Blocks.AIR.defaultBlockState(), 2);
		check(P + "needs the owner to see the target (a wall blocks the echo)", blocked);

		// ---- the AI: recasts every 4 s at the owner's target, and keeps its light
		husk.setHealth(husk.getMaxHealth()); // 3 echoes never kill it
		p.setLastHurtMob(husk);
		int n1 = WillOLantern.recasts;
		for (int i = 0; i < 170 && !wisp.isRemoved(); i++) {
			husk.setInvulnerableTime(0);
			husk.damageCooldownTime = 0;
			wisp.tickCount++;
			wisp.tick();
		}
		int echoes = WillOLantern.recasts - n1;
		check(P + "the wisp recasts at the owner's target every 4 s (2-3 echoes in 170 ticks)", !wisp.isRemoved() && echoes >= 2 && echoes <= 3, echoes + " echoes");
		BlockPos lit = wisp.lightPos();
		check(P + "the wisp lights the air it floats in: one level-10 light, recorded in its chunk", lit != null && WispLights.isOurs(l.getBlockState(lit))
			&& l.getBlockState(lit).getValue(LightBlock.LEVEL) == 10 && WispLights.recorded(l, lit).contains(lit.asLong()) && WispLights.live() == 1, lit);
		if (lit == null) return;
		wisp.setPos(wisp.getX() + 2, wisp.getY(), wisp.getZ());
		WispLights.follow(wisp, l);
		BlockPos moved = wisp.lightPos();
		check(P + "the light follows the wisp and the old one goes out", moved != null && !moved.equals(lit) && l.getBlockState(lit).isAir()
			&& WispLights.isOurs(l.getBlockState(moved)) && !WispLights.recorded(l, lit).contains(lit.asLong()) && WispLights.live() == 1, lit + " -> " + moved);
		if (moved == null) return;
		BlockPos solid = BlockPos.containing(wisp.getX() + 2, wisp.getY() + wisp.getBbHeight() * 0.5, wisp.getZ());
		l.setBlock(solid, Blocks.STONE.defaultBlockState(), 2);
		wisp.setPos(wisp.getX() + 2, wisp.getY(), wisp.getZ());
		WispLights.follow(wisp, l);
		check(P + "never lights inside a block: it keeps its last light instead", moved.equals(wisp.lightPos()) && l.getBlockState(solid).is(Blocks.STONE) && WispLights.isOurs(l.getBlockState(moved)));
		l.setBlock(solid, Blocks.AIR.defaultBlockState(), 2);
		wisp.discard();
		check(P + "a wisp that is sent home or killed puts its light out (block and record)", l.getBlockState(moved).isAir() && WispLights.recorded(l, moved).isEmpty() && WispLights.live() == 0);

		// ---- crash safety (same rules as the Glimmer Lantern lights)
		BlockPos a = home.offset(0, 4, 4), b = home.offset(2, 4, 4), foreign = home.offset(4, 4, 4);
		checkS(P + "a live wisp light survives its chunk loading again", () -> {
			boolean ok = WispLights.place(l, a) && WispLights.onChunkLoad(l, l.getChunkAt(a)) == 0 && WispLights.isOurs(l.getBlockState(a));
			WispLights.clear(l, a);
			return ok && l.getBlockState(a).isAir();
		});
		checkS(P + "after a crash, a stale wisp light is cleared when its chunk loads; another light is never touched", () -> {
			if (!WispLights.place(l, b)) return false;
			WispLights.forgetSessionForTest();
			l.setBlock(foreign, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 2);
			int queued = WispLights.onChunkLoad(l, l.getChunkAt(b));
			Gear3Scheduler.runDueWithin(s, 1);
			boolean ok = queued == 1 && l.getBlockState(b).isAir() && WispLights.recorded(l, b).isEmpty()
				&& l.getBlockState(foreign).is(Blocks.LIGHT) && l.getBlockState(foreign).getValue(LightBlock.LEVEL) == 15;
			l.setBlock(foreign, Blocks.AIR.defaultBlockState(), 2);
			return ok;
		});
		checkS(P + "a wisp unloaded with its chunk leaves its light for that chunk's next load, which clears it", () -> {
			MinionManager.dismissAll(p.getUUID()); // the earlier checks spent the Glimmer pool (20 max, 10 a call) and may hold the slot
			GlimmerStub.setPool(p, 100F);
			MinionEntity m = item.summonNow(p, stack);
			if (!(m instanceof MarshWisp w)) return false;
			w.setPos(home.getX() + 0.5, home.getY() + 5, home.getZ() + 6.5);
			WispLights.follow(w, l);
			BlockPos at = w.lightPos();
			if (at == null || !WispLights.isOurs(l.getBlockState(at))) return false;
			WispLights.release(w, l, Entity.RemovalReason.UNLOADED_TO_CHUNK);
			boolean stays = WispLights.isOurs(l.getBlockState(at)) && WispLights.live() == 0;
			int queued = WispLights.onChunkLoad(l, l.getChunkAt(at));
			Gear3Scheduler.runDueWithin(s, 1);
			MinionManager.dismiss(w);
			return stays && queued == 1 && l.getBlockState(at).isAir() && WispLights.recorded(l, at).isEmpty();
		});

		// ---- sneak-use sends the wisp home
		checkS(P + "sneak-use sends the wisp home", () -> {
			MinionManager.dismissAll(p.getUUID());
			GlimmerStub.setPool(p, 100F);
			MinionEntity m = item.summonNow(p, stack);
			p.setShiftKeyDown(true);
			item.use(l, p, InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
			return m instanceof MarshWisp && m.isRemoved() && WillOLantern.wispOf(p) == null;
		});
		husk.discard();

		// ---- the Chair-holder drop (rival_mirewick soft link)
		check(P + "Chair flag read from chair or chairAtDeath", WillOLantern.chairFlag(new Chair(true, false)) && WillOLantern.chairFlag(new Chair(false, true))
			&& !WillOLantern.chairFlag(new Chair(false, false)) && !WillOLantern.isChairHolder(new Chair(true, true)));
		try {
			Class<?> magnate = Class.forName(WillOLantern.MAGNATE_CLASS);
			boolean fields = false;
			for (Class<?> c = magnate; c != null && !fields; c = c.getSuperclass())
				for (var f : c.getDeclaredFields()) if (f.getType() == boolean.class && (f.getName().equals("chair") || f.getName().equals("chairAtDeath"))) fields = true;
			check(P + "rival_mirewick MagnateBoss still has its chair / chairAtDeath flag (Chair-holder 30% drop)", fields);
		} catch (ClassNotFoundException e) {
			info("Will-o'-Lantern: rival_mirewick not in this environment; the Chair-holder drop hook is registered");
		}
	}

	/** A stand-in with the MagnateBoss flag names (not the MagnateBoss class, so never a Chair-holder itself). */
	@SuppressWarnings("unused")
	static final class Chair {
		final boolean chair, chairAtDeath;

		Chair(boolean chair, boolean chairAtDeath) {
			this.chair = chair;
			this.chairAtDeath = chairAtDeath;
		}
	}

	static Mob husk(ServerLevel l, double x, double y, double z) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, y, z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static TagKey<Item> tag(String ns, String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ns, path));
	}

	/** True if any en_us.json on the classpath (gear3's own, or the merged jar's) has the key. */
	static boolean lang(String key) {
		try {
			Enumeration<URL> urls = WilloSelfTest.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
			while (urls.hasMoreElements()) {
				try (InputStream in = urls.nextElement().openStream()) {
					JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					if (o.has(key)) return true;
				}
			}
		} catch (java.io.IOException | RuntimeException e) {
			info("Will-o'-Lantern: cannot read en_us.json: " + e);
		}
		return false;
	}

	static boolean wispTexture() {
		try (InputStream in = WilloSelfTest.class.getResourceAsStream("/assets/elsewhere/textures/entity/marsh_wisp.png")) {
			var img = in == null ? null : ImageIO.read(in);
			return img != null && img.getWidth() == 32 && img.getHeight() == 32;
		} catch (java.io.IOException e) {
			return false;
		}
	}
}
