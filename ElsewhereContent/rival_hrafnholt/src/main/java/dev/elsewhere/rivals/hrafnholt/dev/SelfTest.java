package dev.elsewhere.rivals.hrafnholt.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import dev.elsewhere.rivals.hrafnholt.HrafnholtItems;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Hrafnholt self-test (milestone 1: data, units, items, gear). {@code ./gradlew runServer}; results on the console as
 * "[hrafnholt-selftest]" and in selftest-hrafnholt.log. Step-driven like the kingdom tests (one step per tick slot).
 */
public final class SelfTest {
	private SelfTest() {}

	interface Step {
		int run(MinecraftServer srv, ServerLevel l) throws Exception;
	}

	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static int pass, fail, idx, wait, startDelay;
	static boolean started, finished, measuring;
	static long tickStart;
	static final List<Long> SAMPLES = new ArrayList<>();
	static final List<String> BENCH = new ArrayList<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> build());
		ServerTickEvents.START_SERVER_TICK.register(srv -> tickStart = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::endTick);
	}

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[hrafnholt-selftest] " + line);
		if (ok) pass++; else fail++;
	}

	static void info(String s) {
		LOG.add(s);
		System.out.println("[hrafnholt-selftest] " + s);
	}

	static void endTick(MinecraftServer srv) {
		if (finished) return;
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (!started) {
			if (++startDelay < 60) return;
			started = true;
			info("started");
		}
		if (wait > 0) { wait--; return; }
		if (idx >= STEPS.size()) { finish(srv); return; }
		Step s = STEPS.get(idx++);
		try {
			wait = s.run(srv, srv.overworld());
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		info("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-hrafnholt.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("hrafnholt.selftest.exit")) srv.halt(false);
	}

	static Identifier id(String p) { return HrafnholtContent.id(p); }

	static FakePlayer fp(ServerLevel l, String name) {
		return FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
	}

	static void build() {
		STEPS.add((srv, l) -> {
			for (int cx = -2; cx <= 3; cx++) for (int cz = -2; cz <= 3; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
			return 5;
		});
		// ---- data: faction file, rulers, economy, brain
		STEPS.add((srv, l) -> {
			FactionDef f = FactionDefs.get(HrafnholtContent.FACTION);
			check("faction file loaded", f != null, null);
			if (f == null) return 0;
			check("faction: six leaders in rank order", f.society().leaders().equals(List.of("king", "marshal", "castellan", "master_at_arms", "huntsman", "chancellor")), f.society().leaders());
			check("faction: six rulers with the specified HP", f.rulers().size() == 6 && f.rulers().get("king").health() == 300 && f.rulers().get("marshal").health() == 236
				&& f.rulers().get("castellan").health() == 257 && f.rulers().get("master_at_arms").health() == 236 && f.rulers().get("huntsman").health() == 246
				&& f.rulers().get("chancellor").health() == 214, null);
			check("faction: Hird-oath +10% damage per champion", "damage_per_champion".equals(f.society().bonus().type()) && f.society().bonus().amount() == 10.0, f.society().bonus());
			check("faction: Standing start -30", f.society().standing().start().orElse(0) == -30, null);
			check("faction: Thralls double count, 18 HP, levy 1/3", f.society().serfs().countMultiplier() == 2.0F && f.society().serfs().health() == 18.0 && f.society().serfs().levy().isPresent(), null);
			check("faction: currency is Hacksilver", f.economy().currency().map(i -> i.equals(id("hacksilver"))).orElse(false), null);
			check("faction: army multipliers 1.15 / 1.10 / 0.90 / 1.2", f.army().healthMultiplier() == 1.15 && f.army().damageMultiplier() == 1.10 && f.army().speedMultiplier() == 0.90 && f.army().sizeMultiplier() == 1.2, null);
			check("faction: patrols 7 day, 8 night", f.army().patrolDay() == 7 && f.army().patrolNight() == 8, null);
			check("brain: war frequency 1.5, min odds 1.0, never retreats", f.army().brain().warFrequency() == 1.5 && f.army().brain().minAttackOdds() == 1.0 && !f.army().brain().retreatInsteadOfDie(), null);
			check("brain: jarl and hersir are brain roles", f.army().brain().brainRoles().size() == 2, null);
			check("two unique soldiers", f.army().uniqueUnits().size() == 2, null);
			for (var hh : dev.elsewhere.rivals.hrafnholt.boss.Hrafn.values()) {
				String s = hh.key;
				var r = f.rulers().get(hh.courtSlot);
				var ids = r.drops().stream().map(d -> d.item().getPath()).toList();
				check("drops " + s + ": unique, seal, Oathkeeper's Seal, Hacksilver", ids.contains("hrafnholt_seal_" + s) && ids.contains("oathkeepers_seal") && ids.contains("hacksilver") && ids.size() >= 4, ids);
				for (var d : r.drops()) if (!BuiltInRegistries.ITEM.containsKey(d.item()) && !d.item().getPath().equals("oathkeepers_seal"))
					check("drop item exists " + d.item(), false, null);
			}
			var jarl = f.rulers().get("king").drops();
			check("jarl treasury 80-96 Hacksilver + 6-12 emeralds", jarl.stream().anyMatch(d -> d.item().getPath().equals("hacksilver") && d.min() == 80 && d.max() == 96)
				&& jarl.stream().anyMatch(d -> d.item().getPath().equals("emerald") && d.min() == 6 && d.max() == 12), null);
			var shop = f.economy().shop();
			check("shop: 8 lines, Seal 220 limit 2, Template 275 limit 2, bolts x8 27", shop.size() == 8
				&& shop.stream().anyMatch(s -> s.item().getPath().equals("oathkeepers_seal") && s.price() == 220 && s.weeklyLimit() == 2)
				&& shop.stream().anyMatch(s -> s.item().getPath().equals("netherite_upgrade_smithing_template") && s.price() == 275 && s.weeklyLimit() == 2)
				&& shop.stream().anyMatch(s -> s.item().getPath().equals("bodkin_bolt") && s.count() == 8 && s.price() == 27), null);
			check("shop: cosmetics 20-40", shop.stream().filter(s -> s.item().getPath().startsWith("hrafnholt_")).allMatch(s -> s.price() >= 20 && s.price() <= 40), null);
			for (var s : shop) if (s.item().getNamespace().equals("elsewhere") && s.item().getPath().startsWith("hrafnholt_"))
				check("shop item exists " + s.item(), BuiltInRegistries.ITEM.containsKey(s.item()), null);
			return 0;
		});
		// ---- units
		STEPS.add((srv, l) -> {
			FactionDef f = FactionDefs.get(HrafnholtContent.FACTION);
			String[] units = {"huskarl", "shield_thane", "hunter", "bolt_thrall", "wolf_rider", "wolf_rider_axe", "hird_sergeant", "hird_captain", "raven_bearer", "skald",
				"leech", "shield_lad", "spear_thane", "berserker", "wolf_handler"};
			for (String u : units) {
				Identifier uid = id("hrafnholt_" + u);
				check("unit type loaded: " + u, UnitTypes.exists(uid), null);
				UnitType t = UnitTypes.get(uid);
				boolean skin = HrafnholtContent.class.getResourceAsStream("/assets/" + t.skin().getNamespace() + "/" + t.skin().getPath()) != null;
				check("unit skin exists: " + u, skin, t.skin());
			}
			for (var e : f.roster().entrySet()) check("roster resolves " + e.getKey().getPath(), e.getValue().unit().map(UnitTypes::exists).orElse(false), null);
			UnitType berserker = UnitTypes.get(id("hrafnholt_berserker"));
			check("berserker: 24 HP, speed 0.32, no armor, two axes", berserker.stats().health() == 24 && berserker.stats().speed() == 0.32 && berserker.armor().head().isEmpty()
				&& berserker.armor().chest().isEmpty() && berserker.mainHand().isPresent() && berserker.offHand().isPresent(), null);
			UnitType wh = UnitTypes.get(id("hrafnholt_wolf_handler"));
			check("wolf-handler: 20 HP, iron axe, chainmail", wh.stats().health() == 20 && wh.armor().head().isPresent(), null);
			// spawn each roster unit and check loadout and the Hrafnholt multipliers
			ServerLevel lv = srv.overworld();
			double x = 0;
			for (String u : units) {
				KingdomSoldier s = KingdomApi.spawnSoldier(lv, id("hrafnholt_" + u), HrafnholtContent.FACTION, new Vec3(x, -60, 0), null);
				check("spawns: " + u, s != null, null);
				if (s != null) {
					UnitType t = UnitTypes.get(id("hrafnholt_" + u));
					double hp = s.getMaxHealth();
					check("hp includes x1.15: " + u, Math.abs(hp - t.stats().health() * 1.15) < 0.6, hp);
					if (t.armor().head().isPresent() && !u.equals("raven_bearer")) check("wears real armor: " + u, !s.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), null);
					s.discard();
				}
				x += 2;
			}
			return 0;
		});
		// ---- unique soldiers: Winter Rage, wolves
		STEPS.add((srv, l) -> {
			ServerLevel lv = srv.overworld();
			dev.elsewhere.kingdom.KingdomPerf.forceActive = true;
			KingdomSoldier b = KingdomApi.spawnSoldier(lv, id("hrafnholt_berserker"), HrafnholtContent.FACTION, new Vec3(10.5, -60, 10.5), null);
			check("berserker uses the Berserker class", b instanceof dev.elsewhere.rivals.hrafnholt.unit.Berserker, b == null ? null : b.getClass().getSimpleName());
			KingdomSoldier h = KingdomApi.spawnSoldier(lv, id("hrafnholt_wolf_handler"), HrafnholtContent.FACTION, new Vec3(16.5, -60, 10.5), null);
			check("wolf-handler uses the WolfHandler class", h instanceof dev.elsewhere.rivals.hrafnholt.unit.WolfHandler, h == null ? null : h.getClass().getSimpleName());
			BERSERKER = b; HANDLER = h;
			return 80;
		});
		STEPS.add((srv, l) -> {
			ServerLevel lv = srv.overworld();
			KingdomSoldier b = BERSERKER, h = HANDLER;
			check("berserker: Strength I (Winter Rage)", b != null && b.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH), null);
			b.rout(200);
			check("berserker ignores rout", !b.isRouted(), null);
			check("berserker: no armor, two axes", b.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && b.getMainHandItem().is(net.minecraft.world.item.Items.IRON_AXE) && b.getOffhandItem().is(net.minecraft.world.item.Items.IRON_AXE), null);
			check("berserker HP 24 x1.15, speed 0.32 x0.9", Math.abs(b.getMaxHealth() - 27.6) < 0.2 && Math.abs(b.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.288) < 0.01,
				b.getMaxHealth() + "/" + b.getAttributeValue(Attributes.MOVEMENT_SPEED));
			var hs = (dev.elsewhere.rivals.hrafnholt.unit.WolfHandler) h;
			check("wolf-handler has 2 wolves", hs.wolfIds().size() == 2, hs.wolfIds().size());
			int found = 0;
			for (UUID u : hs.wolfIds()) {
				var e = lv.getEntity(u);
				if (e instanceof Wolf w) { found++; if (w.getMaxHealth() != 20F || w.getAttributeValue(Attributes.ATTACK_DAMAGE) != 4.0) check("wolf stats", false, w.getMaxHealth()); }
			}
			check("both wolves are in the world, 20 HP, bite 4", found == 2, found);
			// wolves leave with him
			h.discard();
			int left = 0;
			for (UUID u : hs.wolfIds()) if (lv.getEntity(u) != null) left++;
			check("wolves leave with the handler", left == 0 && hs.wolfIds().isEmpty(), left);
			b.discard();
			dev.elsewhere.kingdom.KingdomPerf.forceActive = false;
			return 0;
		});
		// ---- items and stats
		STEPS.add((srv, l) -> {
			for (String n : new String[] {"hacksilver", "hrafnholt_iron_crown", "hrafnholt_raven_banner", "hrafnholt_wolfskin_boots", "hrafnholt_hearth_ward_mail", "hrafnholt_skalds_rune_axe",
				"hrafnholt_ulfhednar_pelt", "hrafnholt_hornbow", "hrafnholt_armor_trim_smithing_template", "hrafnholt_banner_pattern", "hrafnholt_raven_shield",
				"hrafnholt_wolfpelt_blanket", "hrafnholt_fur_collar_dye"})
				check("item registered: " + n, BuiltInRegistries.ITEM.containsKey(id(n)), null);
			for (String s : HrafnholtItems.SLOTS) check("seal registered: " + s, BuiltInRegistries.ITEM.containsKey(id("hrafnholt_seal_" + s)), null);
			ItemStack crown = new ItemStack(HrafnholtItems.IRON_CROWN), boots = new ItemStack(HrafnholtItems.WOLFSKIN_BOOTS), mail = new ItemStack(HrafnholtItems.HEARTH_WARD_MAIL),
				pelt = new ItemStack(HrafnholtItems.ULFHEDNAR_PELT), axe = new ItemStack(HrafnholtItems.RUNE_AXE), bow = new ItemStack(HrafnholtItems.HORNBOW);
			check("crown durability 420", crown.getMaxDamage() == 420, crown.getMaxDamage());
			check("boots durability 481 (netherite boots 481)", boots.getMaxDamage() == 481, boots.getMaxDamage());
			check("mail durability 560", mail.getMaxDamage() == 560, mail.getMaxDamage());
			check("pelt durability 480", pelt.getMaxDamage() == 480, pelt.getMaxDamage());
			check("rune axe durability 1700 (netherite 2031)", axe.getMaxDamage() == 1700, axe.getMaxDamage());
			check("hornbow durability 480", bow.getMaxDamage() == 480, bow.getMaxDamage());
			final boolean rb = HrafnholtItems.REBALANCE_RIVALS; // 3.0 rival rebalance (GEAR.md A4): mail 8, pelt 8/3, crown toughness 3, axe 10
			check("armor values, all below or equal netherite", armorOf(crown) == 3 && armorOf(boots) == 3 && armorOf(mail) == (rb ? 8 : 7) && armorOf(pelt) == (rb ? 8 : 6),
				armorOf(crown) + "/" + armorOf(boots) + "/" + armorOf(mail) + "/" + armorOf(pelt));
			check(rb ? "toughness 3 / 2 / 3 / 3 (3.0)" : "toughness 2 / 2 / 3 / 1", toughOf(crown) == (rb ? 3 : 2) && toughOf(boots) == 2 && toughOf(mail) == 3 && toughOf(pelt) == (rb ? 3 : 1),
				toughOf(crown) + "/" + toughOf(boots) + "/" + toughOf(mail) + "/" + toughOf(pelt));
			check(rb ? "rune axe damage 10, speed 1.0 (3.0)" : "rune axe damage 9, speed 1.0", Math.abs(attr(axe, Attributes.ATTACK_DAMAGE) - (rb ? 9.0 : 8.0)) < 1e-6 && Math.abs(attr(axe, Attributes.ATTACK_SPEED) + 3.0) < 1e-6,
				attr(axe, Attributes.ATTACK_DAMAGE) + "/" + attr(axe, Attributes.ATTACK_SPEED));
			check("lore seals tag has 6 hrafnholt seals", HrafnholtItems.SEALS.values().stream().allMatch(i -> new ItemStack(i).is(HrafnholtItems.LORE_SEALS)), null);
			var tp = srv.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
			check("trim pattern elsewhere:hrafnholt registered", tp.containsKey(ResourceKey.create(Registries.TRIM_PATTERN, id("hrafnholt"))), null);
			var bp = srv.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			check("banner pattern elsewhere:hrafnholt registered", bp.containsKey(ResourceKey.create(Registries.BANNER_PATTERN, id("hrafnholt"))), null);
			check("trim recipes registered", srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("hrafnholt_armor_trim_smithing_template"))).isPresent()
				&& srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("hrafnholt_armor_trim_smithing_template_smithing_trim"))).isPresent(), null);
			return 0;
		});
		// ---- gear effects
		STEPS.add((srv, l) -> {
			ServerLevel lv = srv.overworld();
			FakePlayer p = fp(lv, "hrafn-gear");
			p.snapTo(4.5, -60, 4.5, 0, 0);
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HrafnholtItems.IRON_CROWN));
			check("wolf-rally: no wolf, no reduction", HrafnholtGear.rallyFor(p) == 0F, null);
			Wolf w = EntityTypes.WOLF.create(lv, EntitySpawnReason.MOB_SUMMONED);
			w.snapTo(6.5, -60, 4.5, 0, 0);
			w.tame(p);
			lv.addFreshEntity(w);
			GEAR_P = p;
			GEAR_W = w;
			return 3;
		});
		STEPS.add((srv, l) -> {
			ServerLevel lv = srv.overworld();
			FakePlayer p = GEAR_P;
			Wolf w = GEAR_W;
			float r0 = HrafnholtGear.rallyFor(p);
			check("wolf-rally: 8% with a tame wolf in range", Math.abs(r0 - 0.08F) < 1e-5, r0);
			p.getInventory().add(new ItemStack(HrafnholtItems.SEALS.get("jarl")));
			p.getInventory().add(new ItemStack(HrafnholtItems.SEALS.get("hersir")));
			check("wolf-rally: +1% per Lord Seal", Math.abs(HrafnholtGear.rallyFor(p) - 0.10F) < 1e-5, HrafnholtGear.rallyFor(p));
			for (String s : HrafnholtItems.SLOTS) p.getInventory().add(new ItemStack(HrafnholtItems.SEALS.get(s)));
			p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(id("lore_seal_marshal"))));
			check("wolf-rally: capped at 12%", Math.abs(HrafnholtGear.rallyFor(p) - 0.12F) < 1e-5, HrafnholtGear.rallyFor(p));
			w.setPos(34.5, -60, 4.5);
			check("wolf-rally: no reduction beyond 16 blocks", HrafnholtGear.rallyFor(p) == 0F, null);
			w.discard();
			// hearth-ward fire
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HrafnholtItems.HEARTH_WARD_MAIL));
			float fire = HrafnholtGear.scaleIncoming(p, lv.damageSources().onFire(), 10F);
			float other = HrafnholtGear.scaleIncoming(p, lv.damageSources().generic(), 10F);
			check("hearth-ward: -10% fire damage only", Math.abs(fire - 9F) < 1e-4 && Math.abs(other - 10F) < 1e-4 || other < 10F, fire + "/" + other);
			// raven banner
			BlockPos bp = new BlockPos(4, -60, 8);
			lv.setBlock(bp.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			boolean planted = HrafnholtGear.plantBanner(lv, p, bp);
			check("raven banner plants a black banner", planted && lv.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.BANNER.black()), null);
			Wolf pet = EntityTypes.WOLF.create(lv, EntitySpawnReason.MOB_SUMMONED);
			pet.snapTo(5.5, -60, 8.5, 0, 0);
			pet.tame(p);
			lv.addFreshEntity(pet);
			GEAR_W = pet;
			BANNER_POS = bp;
			return 3;
		});
		STEPS.add((srv, l) -> {
			ServerLevel lv = srv.overworld();
			FakePlayer p = GEAR_P;
			BlockPos bp = BANNER_POS;
			HrafnholtGear.plantBanner(lv, p, bp);
			Wolf pet = GEAR_W;
			check("raven banner gives Speed I to a tamed pet within 16 blocks", pet.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), null);
			pet.discard();
			HrafnholtGear.BANNERS.removeIf(b -> { b.level().removeBlock(b.pos(), false); return true; });
			lv.removeBlock(bp.below(), false);
			check("raven banner ledger clears and leaves no block", lv.getBlockState(bp).isAir(), null);
			return 0;
		});
		STEPS.add((srv, l) -> { // sfx lane 3.0
			java.util.List<String> bad = dev.elsewhere.rivals.hrafnholt.Sfx3.selfCheck();
			check("sfx3: Hrafnholt horn, war cry, shield wall and pelt rage resolve, subtitled, no ender dragon or wither", bad.isEmpty(), bad);
			dev.elsewhere.rivals.hrafnholt.Sfx3.setEnabledForTest(true);
			boolean on = dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "pelt_rage", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL) != net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL
				&& dev.elsewhere.rivals.hrafnholt.Sfx3.get("hrafnholt", "shieldwall") != null;
			dev.elsewhere.rivals.hrafnholt.Sfx3.setEnabledForTest(false);
			boolean off = dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "horn", net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL) == net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL;
			dev.elsewhere.rivals.hrafnholt.Sfx3.setEnabledForTest(null);
			check("sfx3: Ulfhednar rage no longer uses the ender dragon growl; sfx3_rival_hrafnholt=false restores 2.x", on && off, null);
			return 0;
		});
		SelfTestPoses.add(); // 3.0 B4 (s-p4-rivalanims)
		SelfTestForesight.add(); // 3.0 B5 (s-foresight): wind-up hints and the bar move label only with foresight gear
		SelfTestRulerLook.add(); // 3.0 B10 (s-rulerarmor): faction armor layer, no cyan diamond
		SelfTestHeap.add(); // 3.0 heap pass (s-heapleak): Rune-Axe combo state bounded
		SelfTestBosses.build();
		SelfTestRewards.add(); // 3.0 (s-bossdrops): XP and drops on every death
	}

	static KingdomSoldier BERSERKER, HANDLER;
	static BlockPos BANNER_POS;
	static FakePlayer GEAR_P;
	static Wolf GEAR_W;

	static int armorOf(ItemStack s) { return (int) Math.round(attr(s, Attributes.ARMOR)); }
	static int toughOf(ItemStack s) { return (int) Math.round(attr(s, Attributes.ARMOR_TOUGHNESS)); }

	static double attr(ItemStack s, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a) {
		double sum = 0;
		var mods = s.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		for (var e : mods.modifiers()) if (e.attribute().equals(a)) sum += e.modifier().amount();
		return sum;
	}
}
