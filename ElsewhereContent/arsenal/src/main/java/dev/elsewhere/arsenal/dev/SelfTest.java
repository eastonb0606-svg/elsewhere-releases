package dev.elsewhere.arsenal.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalEffects;
import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.combat.AttributeHooks;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.mixin.LivingEntityInvoker;
import dev.elsewhere.arsenal.spell.ArsenalEntities;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.arsenal.spell.CasterItems;
import dev.elsewhere.arsenal.spell.GlimmerTonicItem;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.arsenal.trinket.Trinkets;
import dev.elsewhere.stub.arsenal.SetBonus;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Arsenal server self-test (-Darsenal.selftest=true): logs PASS/FAIL lines and a total, writes run/selftest-1.log. */
public final class SelfTest {
	private SelfTest() {}

	static final List<String> LOG = new ArrayList<>();
	static int pass, fail;

	static void check(String name, boolean ok) {
		check(name, ok, null);
	}

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[arsenal-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void checkS(String name, BooleanSupplier s) {
		try {
			check(name, s.getAsBoolean(), null);
		} catch (Throwable t) {
			check(name, false, t);
		}
	}

	static float exhaustion(FakePlayer p) {
		try {
			var f = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
			f.setAccessible(true);
			return f.getFloat(p.getFoodData());
		} catch (ReflectiveOperationException e) {
			return Float.NaN;
		}
	}

	/** Clears a mob's invulnerability frames (the test never lets game time pass between hits). */
	static void iframes(Mob m) {
		m.damageCooldownTime = 0;
	}

	static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	/** Platform probes (corners and centre of the arena box below): every one must sit in an entity-ticking chunk. */
	static final int[][] PROBES = {{8, 0}, {-4, -6}, {17, -6}, {-4, 7}, {17, 7}};

	static boolean platformTicking(ServerLevel l) {
		for (int[] q : PROBES) if (!l.isPositionEntityTicking(new BlockPos(q[0], 100, q[1]))) return false;
		return true;
	}

	/**
	 * Entry for every harness (the lane's DevEntry and the merged jar's SERVER_STARTED hook alike). Entities only become
	 * visible to level queries inside entity-ticking chunks, and a chunk force ticket is only promoted on a later server
	 * tick, so on SERVER_STARTED this forces the platform chunks and starts the checks once they tick (at most 400 ticks).
	 * Run synchronously inside SERVER_STARTED, the husks, bolts and motes were added but invisible (merged 77/84).
	 */
	public static void run(MinecraftServer server) {
		ServerLevel l = server.overworld();
		if (platformTicking(l)) {
			runNow(server);
			return;
		}
		for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) l.setChunkForced(cx, cz, true);
		int[] ticks = {0};
		boolean[] ran = {false};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (ran[0] || srv != server) return;
			if (++ticks[0] < 400 && !platformTicking(l)) return;
			ran[0] = true;
			if (ticks[0] >= 400) System.out.println("[arsenal-selftest] platform chunks still not entity-ticking after 400 ticks");
			runNow(server);
		});
	}

	static void runNow(MinecraftServer server) {
		try {
			body(server);
		} catch (Throwable t) {
			check("selftest crashed", false, t);
			t.printStackTrace();
		}
		String total = "TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		LOG.add(total);
		System.out.println("[arsenal-selftest] " + total);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-1.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("arsenal.selftest.exit")) server.halt(false);
	}

	static void equip(FakePlayer p, EquipmentSlot s, ItemStack st) {
		p.setItemSlot(s, st);
		((LivingEntityInvoker) p).elsewhere$detectEquipmentUpdates();
		Calling.recompute(p, true); // a FakePlayer never ticks; settle the derived Callings at once for the test
	}

	static final boolean V3 = dev.elsewhere.arsenal.Overhaul3.CALLINGS;

	static void summonerLegacy(boolean on) {
		try {
			Class.forName("dev.elsewhere.stub.summoner.CallingStub").getField("forceLegacy").setBoolean(null, on);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			// summoner module absent
		}
	}

	static Mob mob(ServerLevel l, double x, double y, double z) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(x, y, z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static void body(MinecraftServer server) {
		ServerLevel l = server.overworld();
		// ---- 3.0 balance calculator (BUILD-3.0 test 7) and Calling v3 math
		if (dev.elsewhere.arsenal.Overhaul3.BALANCE_CALC) for (String s : BalanceCalc.run((n, ok) -> check(n, ok))) {
			LOG.add("INFO " + s);
			System.out.println("[arsenal-selftest] INFO " + s);
		}
		check("calling: Primary M 1.00, Secondary 0.60..0.80, Open 0.25..0.45, Unattuned 0.40..0.60",
			near(Calling.masteryOf(Calling.Rank.PRIMARY, 0), 1.0) && near(Calling.masteryOf(Calling.Rank.SECONDARY, 4), 0.8) && near(Calling.masteryOf(Calling.Rank.SECONDARY, 1), 0.65)
				&& near(Calling.masteryOf(Calling.Rank.OPEN, 4), 0.45) && near(Calling.masteryOf(Calling.Rank.UNATTUNED, 0), 0.4) && near(Calling.masteryOf(Calling.Rank.UNATTUNED, 4), 0.6));
		check("calling: minion slot cap Primary 4, Secondary 2, Unattuned 1, Open 1", Calling.slotCapOf(1F) == 4 && Calling.slotCapOf(0.6F) == 2 && Calling.slotCapOf(0.8F) == 3
			&& Calling.slotCapOf(0.4F) == 1 && Calling.slotCapOf(0.25F) == 1, Calling.slotCapOf(1F) + "/" + Calling.slotCapOf(0.6F) + "/" + Calling.slotCapOf(0.4F));
		// ---- registries
		String[] attrs = {"max_glimmer", "glimmer_regen", "spell_power", "spell_cost", "projectile_pierce", "minion_slots", "minion_damage", "dodge_chance", "kill_heal"};
		int found = 0;
		for (String a : attrs) if (BuiltInRegistries.ATTRIBUTE.containsKey(dev.elsewhere.arsenal.ArsenalContent.id(a))) found++;
		check("9 attributes registered", found == 9, found);
		check("items registered", ArsenalItems.ALL.size() >= 18, ArsenalItems.ALL.size());
		check("magic bolt entity", BuiltInRegistries.ENTITY_TYPE.containsKey(dev.elsewhere.arsenal.ArsenalContent.id("magic_bolt")) && ArsenalEntities.MAGIC_BOLT != null);
		checkS("effects clarity + dazed", () -> ArsenalEffects.CLARITY != null && ArsenalEffects.DAZED != null);
		checkS("spell damage type is a projectile", () -> l.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Spells.SPELL).is(DamageTypeTags.IS_PROJECTILE));
		checkS("brewing recipes loaded", () -> server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
			dev.elsewhere.arsenal.ArsenalContent.id("brewing/glimmer_tonic"))).isPresent());

		// Own test platform high above spawn (works on normal, flat and amplified worlds; the old y -60 spot was inside stone
		// on a normal world): clear an air box, lay a floor, restore every block afterwards.
		BlockPos base = new BlockPos(0, Math.min(l.getMaxY() - 12, 240), 0);
		buildPlatform(l, base);
		float rain0 = l.getRainLevel(1F), thunder0 = l.getThunderLevel(1F);
		l.setRainLevel(0F); // the Stormcoil "dry" and Sunlance checks need clear weather at the platform
		l.setThunderLevel(0F);
		try {
			arena(server, l, base);
		} finally {
			l.setRainLevel(rain0);
			l.setThunderLevel(thunder0);
			restorePlatform(l);
		}
	}

	static final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> SAVED = new java.util.LinkedHashMap<>();

	static void buildPlatform(ServerLevel l, BlockPos base) {
		for (int cx = (base.getX() - 4) >> 4; cx <= (base.getX() + 17) >> 4; cx++)
			for (int cz = (base.getZ() - 6) >> 4; cz <= (base.getZ() + 7) >> 4; cz++) {
				l.getChunk(cx, cz);
				l.setChunkForced(cx, cz, true); // entity lookups only see entity-ticking chunks; the platform is far from spawn
			}
		for (int x = -4; x <= 17; x++)
			for (int z = -6; z <= 7; z++)
				for (int y = -1; y <= 4; y++) {
					BlockPos q = base.offset(x, y, z);
					SAVED.put(q.immutable(), l.getBlockState(q));
					l.setBlock(q, y == -1 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
				}
	}

	static void restorePlatform(ServerLevel l) {
		for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) l.setChunkForced(cx, cz, false);
		SAVED.forEach((q, st) -> l.setBlock(q, st, 2 | 16));
		SAVED.clear();
	}

	/** Summoner present (combined jar): the real minion manager replaces the dev stand-in {@link Glimmer#devMinions}. */
	static boolean realMinions() {
		try {
			Class.forName("dev.elsewhere.minion.MinionApi");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Summons one real Rootling for the owner through the summoner module (reflection; no compile dependency), free of Glimmer. */
	static net.minecraft.world.entity.Entity summonReal(FakePlayer p) {
		try {
			var type = BuiltInRegistries.ENTITY_TYPE.getValue(dev.elsewhere.arsenal.ArsenalContent.id("rootling"));
			var m = Class.forName("dev.elsewhere.minion.MinionManager").getMethod("summon", net.minecraft.server.level.ServerPlayer.class,
				net.minecraft.world.entity.EntityType.class, int.class);
			return (net.minecraft.world.entity.Entity) m.invoke(null, p, type, 0);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[arsenal-selftest] real summon failed: " + e);
			return null;
		}
	}

	static void dismissReal(FakePlayer p) {
		try {
			Class.forName("dev.elsewhere.minion.MinionManager").getMethod("dismissAll", UUID.class).invoke(null, p.getUUID());
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[arsenal-selftest] dismissAll failed: " + e);
		}
		try {
			Class.forName("dev.elsewhere.summoner.SummonerPerks").getMethod("updateHerdFocus", net.minecraft.server.level.ServerPlayer.class).invoke(null, p);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[arsenal-selftest] updateHerdFocus failed: " + e);
		}
	}

	static void arena(MinecraftServer server, ServerLevel l, BlockPos base) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("arsenal".getBytes()), "ArsenalTester"));
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
		p.getFoodData().setFoodLevel(20);

		// ---- Glimmer pool
		check("base max 20", near(Glimmer.attrMax(p), 20), Glimmer.attrMax(p));
		Glimmer.setForTest(p, 20);
		Calling.recompute(p, true);
		final float c10 = V3 ? 10.2F : 11.5F, left = 20 - c10;
		check(V3 ? "3.0 costs: Unattuned wand (starter, M 0.60) 2.0, 10 at M 0.40 = 10.2" : "rev4 costs: wand (starter, Calling 2) 2.1, 10 at Calling 0 = 11.5",
			near(Glimmer.cost(p, 2, true), V3 ? 2.0 : 2.1) && near(Glimmer.cost(p, 10), c10), Glimmer.cost(p, 2, true) + "/" + Glimmer.cost(p, 10));
		check("trySpend " + c10, Glimmer.trySpend(p, 10) && near(Glimmer.current(p), left), Glimmer.current(p));
		check("trySpend fails when short", !Glimmer.trySpend(p, 10) && near(Glimmer.current(p), left) && near(Glimmer.lastFailNeed, c10));
		Glimmer.markSpentForTest(p, 5);
		Glimmer.tickPlayer(p);
		check("no regen within 1 s delay", near(Glimmer.current(p), left), Glimmer.current(p));
		Glimmer.markSpentForTest(p, 25);
		float ex0 = p.getFoodData().getSaturationLevel() * 0 + exhaustion(p);
		for (int i = 0; i < 20; i++) Glimmer.tickPlayer(p);
		check("regen 3/s after delay", near(Glimmer.current(p), left + 3), Glimmer.current(p));
		check("regen costs 0.05 exhaustion per Glimmer", l.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || near(exhaustion(p) - ex0, 0.15),
			exhaustion(p) - ex0);
		p.getFoodData().setFoodLevel(6);
		for (int i = 0; i < 20; i++) Glimmer.tickPlayer(p);
		check("no regen at hunger 6", near(Glimmer.current(p), left + 3), Glimmer.current(p));
		p.getFoodData().setFoodLevel(20);
		List<net.minecraft.world.entity.Entity> real = new ArrayList<>();
		boolean useReal = realMinions();
		summonerLegacy(true); // the real-minion block below uses the summoner's 2.1 slot/reserve tuning (its own self-test's rule)
		if (useReal) { // two real Rootlings (summoner module); slots raised to 2 for the test
			p.getAttribute(ArsenalAttributes.MINION_SLOTS).setBaseValue(2);
			for (int i = 0; i < 2; i++) {
				var m = summonReal(p);
				if (m != null) real.add(m);
			}
		} else Glimmer.devMinions = 2;
		String how = useReal ? "real MinionApi, " + real.size() + " Rootlings, " : "dev stand-in, ";
		int resv = Glimmer.reserved(p); // 2.1: 6 each; 3.0 Unattuned: 6 - 2 * 0.40 = 5.2 each (the summoner module rounds per minion: 5)
		check("2 minions reserve " + (V3 ? "10" : "12") + " at Summoner M 0.40 / Calling 0", Glimmer.minions(p) == 2 && (V3 ? resv == 10 || resv == 12 : resv == 12) && near(Glimmer.max(p), 20 - resv), how + resv);
		check("minions slow regen 10% each", near(Glimmer.regenPerSecond(p), 2.4), how + Glimmer.regenPerSecond(p));
		Glimmer.devMinions = 0;
		real.forEach(net.minecraft.world.entity.Entity::discard);
		if (useReal) {
			p.getAttribute(ArsenalAttributes.MINION_SLOTS).setBaseValue(ArsenalStats.MINION_SLOTS[0]);
			dismissReal(p); // also clears the summoner's Herd Focus own-damage/spell-power modifier from the tester
		}
		summonerLegacy(false);
		check("minions gone after the check", Glimmer.minions(p) == 0 && Glimmer.reserved(p) == 0, Glimmer.minions(p));

		// ---- Calling + Moonsilk
		check("Calling 0", Calling.caster(p) == 0 && Calling.casterFor(p, true) == 2);
		equip(p, EquipmentSlot.HEAD, new ItemStack(ArsenalItems.MOONSILK_HOOD));
		equip(p, EquipmentSlot.CHEST, new ItemStack(ArsenalItems.MOONSILK_ROBE));
		equip(p, EquipmentSlot.LEGS, new ItemStack(ArsenalItems.MOONSILK_LEGGINGS));
		equip(p, EquipmentSlot.FEET, new ItemStack(ArsenalItems.MOONSILK_BOOTS));
		check("full Moonsilk = Caster Calling 4", Calling.caster(p) == 4, Calling.caster(p));
		final float primaryBonus = V3 ? 12 : 0; // 3.0: full Moonsilk makes Caster Primary (M 1.00): max Glimmer +20 (1.00 - 0.40) = +12
		check("full Moonsilk max " + (32 + primaryBonus) + " (20+6+6" + (V3 ? "+12 Primary" : "") + ")", near(Glimmer.attrMax(p), 32 + primaryBonus), Glimmer.attrMax(p));
		if (V3) check("full Moonsilk = Caster Primary, mastery 1.00, others Open", Calling.rank(p, Calling.Path.CASTER) == Calling.Rank.PRIMARY && near(Calling.mastery(p, Calling.Path.CASTER), 1.0)
			&& Calling.rank(p, Calling.Path.VANGUARD) == Calling.Rank.OPEN && near(Calling.spellMult(p, false), 1.15) && near(Calling.costMult(p, false), 0.90), Calling.mastery(p, Calling.Path.CASTER));
		if (V3) { // settle: a gear change waits SETTLE_TICKS; putting the gear back cancels it (no tick involved)
			ItemStack h = p.getItemBySlot(EquipmentSlot.HEAD), lg = p.getItemBySlot(EquipmentSlot.LEGS), ft = p.getItemBySlot(EquipmentSlot.FEET);
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			Calling.recompute(p, false);
			check("calling settle: 1 caster piece left derives Unattuned, but Caster stays Primary for 30 s", Calling.derive(p)[0] == -1
				&& Calling.rank(p, Calling.Path.CASTER) == Calling.Rank.PRIMARY && Calling.ticksUntilSettled(p) == Calling.SETTLE_TICKS, Calling.ticksUntilSettled(p));
			p.setItemSlot(EquipmentSlot.HEAD, h);
			p.setItemSlot(EquipmentSlot.LEGS, lg);
			p.setItemSlot(EquipmentSlot.FEET, ft);
			Calling.recompute(p, false);
			check("calling settle: putting the set back cancels the pending change", Calling.rank(p, Calling.Path.CASTER) == Calling.Rank.PRIMARY && Calling.ticksUntilSettled(p) == 0);
			check("calling: the /calling readout names Caster Primary", dev.elsewhere.arsenal.spell.CallingCommand.readout(p).contains("Caster: Primary 100%"), dev.elsewhere.arsenal.spell.CallingCommand.readout(p));
		}
		check("Moonsilk regen x1.3, power x1.1, cost x0.9, pierce 1", near(ArsenalAttributes.value(p, ArsenalAttributes.GLIMMER_REGEN), 1.3)
			&& near(ArsenalAttributes.value(p, ArsenalAttributes.SPELL_POWER), 1.1) && near(ArsenalAttributes.value(p, ArsenalAttributes.SPELL_COST), 0.9)
			&& near(ArsenalAttributes.value(p, ArsenalAttributes.PROJECTILE_PIERCE), 1));
		check("Moonlit set bonus active", SetBonus.moonlit(p));
		check(V3 ? "3.0: caster pieces no longer cut melee (attack damage 1)" : "caster pieces cut melee 5% each (attack damage 1 -> 0.95^4)", near(ArsenalAttributes.value(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), V3 ? 1.0 : Math.pow(0.95, 4)),
			ArsenalAttributes.value(p, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
		check(V3 ? "Sculk Resonator cost at Primary = 10*0.9*0.9 = 8.1" : "Sculk Resonator cost at Calling 4 = 10*0.9 = 9.0", near(Glimmer.cost(p, 10), V3 ? 8.1 : 9), Glimmer.cost(p, 10));
		equip(p, EquipmentSlot.HEAD, new ItemStack(ArsenalItems.CANTORS_DIADEM));
		check("Cantor's Diadem keeps Calling 4, breaks Moonlit, max 34", Calling.caster(p) == 4 && !SetBonus.moonlit(p) && near(Glimmer.attrMax(p), 34 + primaryBonus), Glimmer.attrMax(p));
		equip(p, EquipmentSlot.HEAD, new ItemStack(ArsenalItems.LINED_MOONSILK_HOOD));
		check("Lined Moonsilk hood counts for the set", SetBonus.moonlit(p) && Calling.caster(p) == 4);

		// ---- trinkets
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ArsenalItems.GLIMMERSTONE_FOCUS));
		Trinkets.update(p);
		check("Glimmerstone Focus +8 max, +25% regen", near(Glimmer.attrMax(p), 40 + primaryBonus) && near(ArsenalAttributes.value(p, ArsenalAttributes.GLIMMER_REGEN), 1.55),
			Glimmer.attrMax(p));
		checkS("trinket limit 2", () -> {
			p.getInventory().setItem(0, new ItemStack(ArsenalItems.GLIMMERSTONE_FOCUS));
			return Trinkets.active(p).size() == 1 && Trinkets.activeSlots(p).size() == 1;
		});
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(0, ItemStack.EMPTY);
		Trinkets.update(p);
		check("Focus removed", near(Glimmer.attrMax(p), 32 + primaryBonus), Glimmer.attrMax(p));

		// ---- spells
		float dmg = Spells.damage(p, p, 10, false, false);
		final float sp = V3 ? 1.15F : 1.0F; // 3.0 Caster Primary D(1.00) = 1.15
		check("spell damage at " + (V3 ? "Caster Primary x1.15" : "Calling 4") + " x spell_power 1.1 = " + 11 * sp, near(dmg, 11 * sp), dmg);
		check("Moonlit +50%", near(Spells.damage(p, p, 10, false, true), 16.5 * sp), Spells.damage(p, p, 10, false, true));
		if (!dev.elsewhere.arsenal.compat.TestMeterCompat.present()) check("Test Meter half bonus without Test Meter = 1.0", near(Spells.testMeterHalf(p), 1.0));
		SelfTestTestMeter.run(l, base); // s-compat-testmeter: spell bolts land the half bonus once (passes with or without Test Meter)
		if (V3 && dev.elsewhere.arsenal.Overhaul3.CALLING_WEAPONS) {
			// z-gear 3.0.0: Vanguard multiplies the full enchanted melee damage; Marksman arrow damage is non-player only
			Mob vt = mob(l, base.getX() + 20.5, base.getY(), base.getZ() + 20.5);
			float vm = Calling.damageMult(p, Calling.Path.VANGUARD, vt);
			check("vanguard: bonus multiplies the full melee damage (Sharpness included): 10 + 3 Sharpness = 13 * " + vm, !near(vm, 1.0) && near(Calling.meleeScaled(p, vt, 13F, true), 13F * vm) && near(Calling.meleeScaled(p, vt, 13F, false), 13F),
				Calling.meleeScaled(p, vt, 13F, true));
			check("vanguard: 1.0 against players (PvP calling-neutral)", near(Calling.meleeScaled(p, p, 13F, true), 13F));
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArsenalItems.CLOCKWORK_REPEATER));
			net.minecraft.world.entity.projectile.arrow.Arrow ar = EntityTypes.ARROW.create(l, EntitySpawnReason.COMMAND);
			ar.setOwner(p);
			dev.elsewhere.arsenal.combat.AttributeHooks.adjustArrow(ar, p);
			float mm = Calling.marksmanMult(p);
			net.minecraft.world.damagesource.DamageSource arrowSrc = l.damageSources().arrow(ar, p);
			check("marksman: arrow bonus x" + mm + " applies to mobs at hit time", !near(mm, 1.0) && near(Calling.scaleHurt(vt, arrowSrc, 8F), 8F * mm), Calling.scaleHurt(vt, arrowSrc, 8F));
			check("marksman: arrow hitting a player is unscaled (PvP neutral)", near(Calling.scaleHurt(p, arrowSrc, 8F), 8F), Calling.scaleHurt(p, arrowSrc, 8F));
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			vt.discard();
		}
		// Sunlance: three husks in a line (+X looking along yaw -90)
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, -90, 0);
		p.setYHeadRot(-90);
		Glimmer.setForTest(p, 32);
		Mob a = mob(l, base.getX() + 4.5, base.getY(), base.getZ() + 0.5), b = mob(l, base.getX() + 7.5, base.getY(), base.getZ() + 0.5),
			c = mob(l, base.getX() + 10.5, base.getY(), base.getZ() + 0.5), d = mob(l, base.getX() + 13.5, base.getY(), base.getZ() + 0.5);
		dev.elsewhere.arsenal.spell.SpellLines.trace(l, p, 24, 0, false); // first query after spawning in the same tick
		CasterItem sun = (CasterItem) ArsenalItems.SUNLANCE_STAFF;
		ItemStack sunStack = new ItemStack(sun);
		p.setItemInHand(InteractionHand.MAIN_HAND, sunStack);
		boolean cast = sun.castNow(l, p, sunStack, InteractionHand.MAIN_HAND);
		check("Sunlance casts, hits 3 of 4 in a line, burns", cast && a.getHealth() < 20 && b.getHealth() < 20 && c.getHealth() < 20 && d.getHealth() >= 20 && a.isOnFire(),
			a.getHealth() + "/" + b.getHealth() + "/" + c.getHealth() + "/" + d.getHealth() + " targets=" + CasterItems.SunlanceStaff.lastTargets + " hurt=" + CasterItems.SunlanceStaff.lastHurt
			+ " seen=" + l.getEntities(null, a.getBoundingBox().inflate(1)).size() + " byUuid=" + (l.getEntity(a.getUUID()) != null) + " tick=" + l.isPositionEntityTicking(a.blockPosition()) + " inLevel=" + l.getAllEntities().iterator().hasNext() + " removed=" + a.isRemoved() + " pos=" + a.blockPosition() + " p=" + p.blockPosition() + " look=" + p.getLookAngle());
		check("Sunlance used durability and Glimmer", sunStack.getDamageValue() == 1 && Glimmer.current(p) < 32, Glimmer.current(p));
		for (Mob m : List.of(a, b, c, d)) {
			m.damageCooldownTime = 0;
			m.setHealth(20);
			m.clearFire();
		}
		// Stormcoil: first target a, arcs to 2 neighbours (dry)
		CasterItem storm = (CasterItem) ArsenalItems.STORMCOIL_STAFF;
		ItemStack stormStack = new ItemStack(storm);
		Glimmer.setForTest(p, 32);
		Mob e1 = mob(l, base.getX() + 4.5, base.getY(), base.getZ() + 3.5), e2 = mob(l, base.getX() + 4.5, base.getY(), base.getZ() - 2.5);
		boolean sc = storm.castNow(l, p, stormStack, InteractionHand.MAIN_HAND);
		check("Stormcoil chains to 2 more mobs when dry", sc && CasterItems.StormcoilStaff.lastArcs == 2 && a.getHealth() < 20, CasterItems.StormcoilStaff.lastArcs);
		for (Mob m : List.of(a, b, c, d, e1, e2)) {
			m.damageCooldownTime = 0;
			m.setHealth(20);
		}
		// Resonator: line through all, half armor ignored
		CasterItem res = (CasterItem) ArsenalItems.SCULK_RESONATOR;
		ItemStack resStack = new ItemStack(res);
		Glimmer.setForTest(p, 32);
		boolean rc = res.castNow(l, p, resStack, InteractionHand.MAIN_HAND);
		check("Sculk Resonator hits the whole line", rc && a.getHealth() < 20 && d.getHealth() < 20, a.getHealth() + "/" + d.getHealth());
		// Amethyst Wand bolt spawns
		CasterItem wand = (CasterItem) ArsenalItems.AMETHYST_WAND;
		ItemStack wandStack = new ItemStack(wand);
		Glimmer.setForTest(p, 32);
		int before = l.getEntities(ArsenalEntities.MAGIC_BOLT, x -> true).size();
		boolean wc = wand.castNow(l, p, wandStack, InteractionHand.MAIN_HAND);
		check("Amethyst Wand fires a bolt", wc && l.getEntities(ArsenalEntities.MAGIC_BOLT, x -> true).size() == before + 1);
		Glimmer.setForTest(p, 0);
		check("cast fails with no Glimmer", !wand.castNow(l, p, wandStack, InteractionHand.MAIN_HAND));

		// ---- Phase 3 gear: Mirrorglass Prism, Hushward Bell
		check("Mirrorglass Prism, Hushward Bell registered", ArsenalItems.MIRRORGLASS_PRISM != null && ArsenalItems.HUSHWARD_BELL != null
			&& BuiltInRegistries.ITEM.containsKey(dev.elsewhere.arsenal.ArsenalContent.id("mirrorglass_prism")) && BuiltInRegistries.ITEM.containsKey(dev.elsewhere.arsenal.ArsenalContent.id("hushward_bell")));
		for (Mob m : List.of(a, b, c, d, e1, e2)) m.discard(); // the earlier targets are dead or hurt; use fresh ones
		Mob pa = mob(l, base.getX() + 4.5, base.getY(), base.getZ() + 0.5), pb = mob(l, base.getX() + 8.5, base.getY(), base.getZ() + 0.5);
		pa.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
		dev.elsewhere.arsenal.spell.SpellLines.trace(l, p, 24, 0, false); // warm-up query
		pa.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
		pa.setHealth(500);
		var prism = (dev.elsewhere.arsenal.spell.GearV2.MirrorglassPrism) ArsenalItems.MIRRORGLASS_PRISM;
		ItemStack prismStack = new ItemStack(prism);
		Glimmer.setForTest(p, 100);
		float g0 = Glimmer.current(p);
		float hp0 = pa.getHealth();
		iframes(pa);
		boolean go = prism.channelTick(p, prismStack, 0, InteractionHand.MAIN_HAND);
		float first = hp0 - pa.getHealth();
		check("Prism beam hits only the first target, Glimmer spent", go && first > 0 && pb.getHealth() == 20 && Glimmer.current(p) < g0, first + " / " + Glimmer.current(p));
		for (int t = 1; t < 70; t++) {
			iframes(pa);
			prism.channelTick(p, prismStack, t, InteractionHand.MAIN_HAND);
		}
		iframes(pa);
		float hp1 = pa.getHealth();
		prism.channelTick(p, prismStack, 70, InteractionHand.MAIN_HAND);
		float late = hp1 - pa.getHealth();
		check("Prism ramps from " + dev.elsewhere.arsenal.spell.GearV2.PRISM_DPS_LOW + "/s to 12/s after " + dev.elsewhere.arsenal.spell.GearV2.PRISM_RAMP_TICKS + " ticks on one target", first > 0
			&& Math.abs(late - first * dev.elsewhere.arsenal.spell.GearV2.PRISM_DPS_HIGH / dev.elsewhere.arsenal.spell.GearV2.PRISM_DPS_LOW) < 0.05, first + " -> " + late);
		check("Prism spends its Glimmer cost each second", g0 - Glimmer.current(p) > 3 * Glimmer.cost(p, dev.elsewhere.arsenal.spell.GearV2.PRISM_COST) - 0.01, g0 - Glimmer.current(p) + " vs 3 x " + Glimmer.cost(p, dev.elsewhere.arsenal.spell.GearV2.PRISM_COST));
		pb.setHealth(20);
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 90, 0); // look away: no target, the ramp resets
		p.setYHeadRot(90);
		prism.channelTick(p, prismStack, 80, InteractionHand.MAIN_HAND);
		check("Prism ramp resets when the line is lost", dev.elsewhere.arsenal.spell.GearV2.MirrorglassPrism.ticksOnTarget(p) == 0);
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, -90, 0);
		p.setYHeadRot(-90);
		Glimmer.setForTest(p, 0);
		check("Prism stops with no Glimmer", !prism.channelTick(p, prismStack, 100, InteractionHand.MAIN_HAND));
		pa.discard();
		pb.discard();
		var bell = (dev.elsewhere.arsenal.spell.GearV2.HushwardBell) ArsenalItems.HUSHWARD_BELL;
		ItemStack bellStack = new ItemStack(bell);
		Glimmer.setForTest(p, 32);
		boolean ring = bell.castNow(l, p, bellStack, InteractionHand.MAIN_HAND);
		float afterRing = Glimmer.current(p);
		check("Hushward Bell raises a 20-point ward", ring && afterRing < 32 && p.getAbsorptionAmount() >= 20F && dev.elsewhere.arsenal.spell.GearV2.wardActive(p), p.getAbsorptionAmount());
		check("the ward silences its area", dev.elsewhere.arsenal.spell.GearV2.covers(p, p.position().add(5, 0, 0)) && !dev.elsewhere.arsenal.spell.GearV2.covers(p, p.position().add(40, 0, 0)));
		check("reusing the Bell while the ward is up is refused", !bell.castNow(l, p, bellStack, InteractionHand.MAIN_HAND) && Glimmer.current(p) == afterRing);
		dev.elsewhere.arsenal.spell.GearV2.clearWard(p);
		p.removeAllEffects();
		p.setAbsorptionAmount(0);
		check("Prism and Bell loot tables exist (gear pools added by the modify event)", l.getServer().reloadableRegistries().getLootTable(dev.elsewhere.arsenal.ArsenalLoot.table("heliodor")) != net.minecraft.world.level.storage.loot.LootTable.EMPTY
			&& l.getServer().reloadableRegistries().getLootTable(dev.elsewhere.arsenal.ArsenalLoot.table("auris")) != net.minecraft.world.level.storage.loot.LootTable.EMPTY);
		check("heliodor unique uses the shared drop rule: 50% first, 33% repeat, pity cap 60%", dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, true, 0) == 0.5F && dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, false, 0) == 0.33F && dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, false, 9) == 0.6F);
		{
			var kit = dev.elsewhere.boss.kit.KitStore.of(l.getServer());
			kit.data().kills.remove(p.getUUID().toString());
			boolean wasKilled = kit.hasKilled(p.getUUID(), "heliodor");
			int n = 0;
			for (int k = 0; k < 40; k++) n += dev.elsewhere.arsenal.ArsenalLoot.rollFor(p, l, p, "heliodor", () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)).size();
			check("heliodor roll records the kill and drops sometimes (40 rolls, got " + n + ")", !wasKilled && kit.hasKilled(p.getUUID(), "heliodor") && n >= 8 && n <= 40);
			kit.data().kills.remove(p.getUUID().toString());
			kit.data().pity.keySet().removeIf(x -> x.contains("heliodor"));
			l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8)).forEach(net.minecraft.world.entity.Entity::discard);
		}
		// ---- new-glimmer-motes, new-repeater, Prism recipe
		{
			var zombie = (net.minecraft.world.entity.LivingEntity) BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie")).create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			var spellSrc = dev.elsewhere.arsenal.spell.Spells.source(l, p, p);
			check("a spell kill drops a mote on a roll under 20%, not above", dev.elsewhere.arsenal.spell.GlimmerMotes.shouldDrop(spellSrc, zombie, 0.1F) && !dev.elsewhere.arsenal.spell.GlimmerMotes.shouldDrop(spellSrc, zombie, 0.3F));
			check("a plain melee kill or a player victim drops no mote", !dev.elsewhere.arsenal.spell.GlimmerMotes.shouldDrop(p.damageSources().playerAttack(p), zombie, 0.0F) && !dev.elsewhere.arsenal.spell.GlimmerMotes.shouldDrop(spellSrc, p, 0.0F));
			Glimmer.setForTest(p, 0);
			var moteEntity = new net.minecraft.world.entity.item.ItemEntity(l, p.getX(), p.getY(), p.getZ(), new ItemStack(ArsenalItems.GLIMMER_MOTE));
			moteEntity.setPickUpDelay(0);
			l.addFreshEntity(moteEntity);
			int got = dev.elsewhere.arsenal.spell.GlimmerMotes.pickup(p);
			check("picking up a mote restores 3 Glimmer (got " + got + ", Glimmer " + Glimmer.current(p) + ")", got == 1 && Math.abs(Glimmer.current(p) - 3F) < 0.6F && moteEntity.isRemoved());
			Glimmer.setForTest(p, 0);
			var rep = new ItemStack(ArsenalItems.CLOCKWORK_REPEATER);
			p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.ARROW, 5));
			ArsenalItems.CLOCKWORK_REPEATER.finishUsingItem(rep, l, p);
			int arrowsLeft = 0;
			for (ItemStack st : p.getInventory().getNonEquipmentItems()) if (st.is(net.minecraft.world.item.Items.ARROW)) arrowsLeft += st.getCount();
			check("Repeater reload loads 3 arrows and uses 3 (loaded " + dev.elsewhere.arsenal.spell.RepeaterItem.loaded(rep) + ", " + arrowsLeft + " arrows left of 5)", dev.elsewhere.arsenal.spell.RepeaterItem.loaded(rep) == 3 && arrowsLeft == 2);
			check("Repeater reload is slow (" + dev.elsewhere.arsenal.spell.RepeaterItem.RELOAD_TICKS + " ticks, a crossbow is 25)", ArsenalItems.CLOCKWORK_REPEATER.getUseDuration(rep, p) == dev.elsewhere.arsenal.spell.RepeaterItem.RELOAD_TICKS);
			dev.elsewhere.arsenal.spell.RepeaterItem.fireBurst(p, rep);
			check("Repeater fires a burst of 3 and empties", dev.elsewhere.arsenal.spell.RepeaterItem.loaded(rep) == 0 && dev.elsewhere.arsenal.spell.RepeaterItem.bursting(p));
			var ench = l.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			check("Repeater cannot take Multishot (but takes Unbreaking)", !ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT).value().isSupportedItem(rep) && ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING).value().isSupportedItem(rep));
			var rm = l.getServer().getRecipeManager();
			check("Mirrorglass Prism and Clockwork Repeater have survival recipes", rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, dev.elsewhere.arsenal.ArsenalContent.id("mirrorglass_prism"))).isPresent()
				&& rm.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, dev.elsewhere.arsenal.ArsenalContent.id("clockwork_repeater"))).isPresent());
		}
		Glimmer.setForTest(p, 0);

		// ---- consumables
		Glimmer.setForTest(p, 0);
		GlimmerTonicItem.drink(p, ArsenalStats.TONIC_RESTORE);
		check("Glimmer Tonic restores 12 and Dazes 10 s", near(Glimmer.current(p), 12) && p.hasEffect(ArsenalEffects.DAZED)
			&& p.getEffect(ArsenalEffects.DAZED).getDuration() == ArsenalStats.DAZED_TICKS, Glimmer.current(p));
		GlimmerTonicItem.drink(p, ArsenalStats.STRONG_TONIC_RESTORE);
		check("2nd tonic stacks Dazed to 20 s", p.getEffect(ArsenalEffects.DAZED).getDuration() == 2 * ArsenalStats.DAZED_TICKS);
		check("Dazed cuts spell damage 25%", near(Spells.damage(p, p, 10, false, false), 10 * Calling.spellMult(p, false) * 1.1 * 0.75), Spells.damage(p, p, 10, false, false));
		if (dev.elsewhere.arsenal.Overhaul3.SPELL_HOOKS) {
			float hookBase = Spells.damage(p, p, 10, false, false);
			float[] hookScaled = new float[1];
			Spells.withPowerScale(0.4F, () -> hookScaled[0] = Spells.damage(p, p, 10, false, false));
			java.util.function.ToDoubleBiFunction<net.minecraft.server.level.ServerPlayer, net.minecraft.world.entity.LivingEntity> hookDm = (hO, hT) -> 1.25, hookCc = (hO, hT) -> 1.3;
			Spells.DAMAGE_MULT.add(hookDm);
			Spells.CC_MULT.add(hookCc);
			float hookDmg = Spells.damage(p, p, 10, false, false);
			int hookTicks = Spells.ccTicks(p, p, 60);
			Spells.DAMAGE_MULT.remove(hookDm);
			Spells.CC_MULT.remove(hookCc);
			check("spell hooks: 40% recast scale, DAMAGE_MULT x1.25, CC_MULT 60 -> 78 ticks, scale resets", near(hookScaled[0], hookBase * 0.4) && near(hookDmg, hookBase * 1.25)
				&& hookTicks == 78 && Spells.powerScale() == 1F && near(Spells.damage(p, p, 10, false, false), hookBase), hookScaled[0] + " / " + hookDmg + " / " + hookTicks);
		}
		check("tonic surge doubles regen", Glimmer.regenPerSecond(p) > 3 * 1.3 * 1.9);
		p.removeAllEffects();
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(ArsenalEffects.CLARITY, ArsenalStats.CLARITY_TICKS));
		check("Clarity: spell_cost -25%, regen +25%", near(ArsenalAttributes.value(p, ArsenalAttributes.SPELL_COST), 0.65)
			&& near(ArsenalAttributes.value(p, ArsenalAttributes.GLIMMER_REGEN), 1.55), ArsenalAttributes.value(p, ArsenalAttributes.SPELL_COST));

		// ---- dodge
		p.getAttribute(ArsenalAttributes.DODGE_CHANCE).setBaseValue(0.10);
		AttributeHooks.forceRoll = 0.05;
		check("dodge a mob melee hit", AttributeHooks.dodges(p, l.damageSources().mobAttack(a)));
		check("never dodge a projectile/magic", !AttributeHooks.dodges(p, l.damageSources().magic()));
		AttributeHooks.forceRoll = 0.5;
		check("roll above chance -> hit", !AttributeHooks.dodges(p, l.damageSources().mobAttack(a)));
		AttributeHooks.forceRoll = null;
		check("Stormsage Mantle registered as chest caster piece", new ItemStack(ArsenalItems.STORMSAGE_MANTLE).is(Calling.CASTER_ARMOR));
		check("Glimmer Tonic is not Items.POTION", ArsenalItems.GLIMMER_TONIC != Items.POTION);
		for (Mob m : List.of(a, b, c, d, e1, e2)) m.discard();
	}
}
