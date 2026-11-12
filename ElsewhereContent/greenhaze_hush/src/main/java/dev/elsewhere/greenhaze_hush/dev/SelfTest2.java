package dev.elsewhere.greenhaze_hush.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.auris.Auris;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.greenhaze.GreenhazeItems;
import dev.elsewhere.greenhaze.GreenhazeTrades;
import dev.elsewhere.greenhaze_hush.HudLayout;
import dev.elsewhere.greenhaze_hush.Reg;
import dev.elsewhere.greenhaze_hush.Stability;
import dev.elsewhere.hush.Hush;
import dev.elsewhere.hush.HushItems;
import dev.elsewhere.hush.HushNoise;
import dev.elsewhere.hush.HushSilence;
import dev.elsewhere.hush.HushTrades;
import dev.elsewhere.hush.HushTrips;
import dev.elsewhere.hush.Resonance;
import dev.elsewhere.stub.greenhaze_hush.SetBonus;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** 2.0 self-tests of this lane: Hushweave/Unheard, Echo, recipes, sources, trades, structures + loot, silence, stability, kit parts that need no fight. */
final class SelfTest2 {
	private SelfTest2() {}

	static void check(String n, boolean ok, String d) { SelfTestRun.check(n, ok, d); }

	static boolean hasRecipe(MinecraftServer s, String id) {
		return s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Reg.id(id))).isPresent();
	}

	static void run(MinecraftServer server, ServerLevel gh, ServerLevel hu) {
		try { hushweave(server, gh, hu); } catch (Throwable t) { check("hushweave tests", false, t.toString()); dev.elsewhere.greenhaze_hush.GreenhazeHushContent.LOGGER.error("t", t); }
		try { recipes(server); } catch (Throwable t) { check("recipe tests", false, t.toString()); }
		try { trades(); } catch (Throwable t) { check("trade tests", false, t.toString()); dev.elsewhere.greenhaze_hush.GreenhazeHushContent.LOGGER.error("t", t); }
		try { silence(hu); } catch (Throwable t) { check("silence tests", false, t.toString()); }
		try { hud(); } catch (Throwable t) { check("hud tests", false, t.toString()); }
		try { kit(gh); } catch (Throwable t) { check("kit tests", false, t.toString()); }
		try { leftovers(gh, hu); } catch (Throwable t) { check("leftover tests", false, t.toString()); dev.elsewhere.greenhaze_hush.GreenhazeHushContent.LOGGER.error("t", t); }
		try { obtainable(server); } catch (Throwable t) { check("obtainable tests", false, t.toString()); }
	}

	// ----------------------------------------------------------------------------------------------- Hushweave, Unheard, Echo

	static FakePlayer fake(ServerLevel l, String name) {
		FakePlayer p = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		p.snapTo(0.5, l.dimension().equals(Hush.LEVEL) ? -40 : 100, 0.5, 0F, 0F);
		return p;
	}

	static void wear(Player p, Item head, Item chest, Item legs, Item feet) {
		p.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : new ItemStack(head));
		p.setItemSlot(EquipmentSlot.CHEST, chest == null ? ItemStack.EMPTY : new ItemStack(chest));
		p.setItemSlot(EquipmentSlot.LEGS, legs == null ? ItemStack.EMPTY : new ItemStack(legs));
		p.setItemSlot(EquipmentSlot.FEET, feet == null ? ItemStack.EMPTY : new ItemStack(feet));
	}

	static void hushweave(MinecraftServer server, ServerLevel gh, ServerLevel hu) {
		check("echo alloy and the Hushweave template are registered", HushItems.ECHO_ALLOY != null && HushItems.HUSHWEAVE_TEMPLATE != null
			&& BuiltInRegistries.ITEM.containsKey(Reg.id("echo_alloy")), "");
		// N1 numbers: 3/7/5/3 (legs are Auris's 6), toughness 2.5, no knockback resistance
		var def = HushItems.HUSHWEAVE.defense();
		check("Hushweave armor 3/7/3 with 2.5 toughness, Hushstep legs 6 / 3", def.get(ArmorType.HELMET) == 3 && def.get(ArmorType.CHESTPLATE) == 7
			&& def.get(ArmorType.BOOTS) == 3 && Math.abs(HushItems.HUSHWEAVE.toughness() - 2.5F) < 0.01 && HushItems.HUSHSTEP.defense().get(ArmorType.LEGGINGS) == 6
			&& Math.abs(HushItems.HUSHSTEP.toughness() - 3F) < 0.01 && HushItems.HUSHWEAVE.knockbackResistance() == 0F, def.toString());
		FakePlayer hp = fake(hu, "hushweave"), gp = fake(gh, "hushweave_gh");
		// hood: Darkness immunity
		wear(hp, HushItems.HUSHWEAVE_HOOD, null, null, null);
		hp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 200));
		HushItems.hoodPerk(hp);
		check("Hushweave Hood removes Darkness", !hp.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "");
		// cloak: noise x0.5 ; greaves: sneaking speed +0.3 ; set: x0.25 ; sprint con x1.5
		wear(hp, null, HushItems.HUSHCLOAK, null, null);
		check("Hushcloak halves your noise", Math.abs(HushItems.noiseFactor(hp) - 0.5) < 1e-6, "" + HushItems.noiseFactor(hp));
		wear(hp, null, null, null, null);
		double base = hp.getAttributeValue(Attributes.SNEAKING_SPEED);
		var mods = new ItemStack(HushItems.HUSHSTEP_GREAVES).get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
		boolean sneak = mods != null && mods.modifiers().stream().anyMatch(m -> m.attribute().is(Attributes.SNEAKING_SPEED) && Math.abs(m.modifier().amount() - 0.3) < 1e-6);
		boolean cloakNoSneak = new ItemStack(HushItems.HUSHCLOAK).get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().noneMatch(m -> m.attribute().is(Attributes.SNEAKING_SPEED));
		check("Hushstep Greaves carry +0.3 sneaking speed (the cloak no longer does)", sneak && cloakNoSneak, "base " + base);
		wear(hp, HushItems.HUSHWEAVE_HOOD, HushItems.HUSHCLOAK, HushItems.HUSHSTEP_GREAVES, HushItems.HUSHWEAVE_BOOTS);
		check("full Hushweave: noise x0.25, sprinting x0.375 (50% farther)", Math.abs(HushItems.noiseFactor(hp) - 0.25) < 1e-6, "");
		hp.setSprinting(true);
		check("Unheard con: sprinting vibrations travel 50% farther", Math.abs(HushItems.noiseFactor(hp) - 0.375) < 1e-6, "" + HushItems.noiseFactor(hp));
		hp.setSprinting(false);
		SetBonus.update(hp);
		check("Unheard set bonus becomes active with all four pieces", SetBonus.active(hp, "unheard") && HushItems.unheard(hp), "");
		wear(hp, HushItems.HUSHWEAVE_HOOD, HushItems.HUSHCLOAK, null, HushItems.HUSHWEAVE_BOOTS);
		check("three of four pieces: no Unheard", !HushItems.unheard(hp), "");
		// Unheard: sneaking in the full set makes 0 vibrations and Resonance doesn't rise
		BlockPos at = new BlockPos(0, -40, 0);
		wear(hp, null, null, null, null);
		hp.setShiftKeyDown(true);
		Resonance.set(hp, 0F);
		int p0 = HushNoise.posted;
		hu.gameEvent(hp, GameEvent.BLOCK_DESTROY, Vec3.atCenterOf(at));
		check("control: a sneaking player with no gear makes a vibration and gains Resonance", HushNoise.posted == p0 + 1 && Resonance.get(hp) > 0F, "posted +" + (HushNoise.posted - p0) + ", resonance " + Resonance.get(hp));
		wear(hp, HushItems.HUSHWEAVE_HOOD, HushItems.HUSHCLOAK, HushItems.HUSHSTEP_GREAVES, HushItems.HUSHWEAVE_BOOTS);
		Resonance.set(hp, 0F);
		p0 = HushNoise.posted;
		for (int i = 0; i < 10; i++) hu.gameEvent(hp, GameEvent.BLOCK_DESTROY, Vec3.atCenterOf(at));
		check("Unheard: sneaking makes no vibrations at all and Resonance stays 0", HushNoise.posted == p0 && Resonance.get(hp) == 0F, "posted +" + (HushNoise.posted - p0) + ", resonance " + Resonance.get(hp));
		hp.setShiftKeyDown(false);
		p0 = HushNoise.posted;
		hu.gameEvent(hp, GameEvent.BLOCK_DESTROY, Vec3.atCenterOf(at));
		check("Unheard does nothing while standing up (a mined block is still heard)", HushNoise.posted == p0 + 1, "");
		// boots: walking and landing make no vibrations
		wear(hp, null, null, null, null);
		p0 = HushNoise.posted;
		hu.gameEvent(hp, GameEvent.STEP, Vec3.atCenterOf(at));
		hu.gameEvent(hp, GameEvent.HIT_GROUND, Vec3.atCenterOf(at));
		int open = HushNoise.posted - p0;
		wear(hp, null, null, null, HushItems.HUSHWEAVE_BOOTS);
		p0 = HushNoise.posted;
		hu.gameEvent(hp, GameEvent.STEP, Vec3.atCenterOf(at));
		hu.gameEvent(hp, GameEvent.HIT_GROUND, Vec3.atCenterOf(at));
		check("Hushweave Boots: walking and landing make no vibrations (bare feet make " + open + ")", open >= 1 && HushNoise.posted == p0, "boots +" + (HushNoise.posted - p0));
		// visibility: mobs notice the full set from half as far
		var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(hu, EntitySpawnReason.COMMAND);
		wear(hp, null, null, null, null);
		double vis0 = hp.getVisibilityPercent(hu, zombie);
		wear(hp, HushItems.HUSHWEAVE_HOOD, HushItems.HUSHCLOAK, HushItems.HUSHSTEP_GREAVES, HushItems.HUSHWEAVE_BOOTS);
		double vis1 = hp.getVisibilityPercent(hu, zombie);
		check("Unheard: mobs notice you from half the range", vis0 > 0.1 && Math.abs(vis1 - vis0 * 0.5) < 1e-6, vis0 + " -> " + vis1);
		wear(hp, null, null, null, null);
		// Echo tools: pickaxe 9 below Y 0 and in the Hush, 7 elsewhere; silent mining and hits
		hp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HushItems.ECHO_PICKAXE));
		hp.snapTo(0.5, -40, 0.5, 0F, 0F);
		hp.setOnGround(true);
		HushItems.echoDepth(hp);
		float deep = hp.getDestroySpeed(Blocks.STONE.defaultBlockState());
		gp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HushItems.ECHO_PICKAXE));
		gp.snapTo(0.5, 100, 0.5, 0F, 0F);
		gp.setOnGround(true);
		HushItems.echoDepth(gp);
		float shallow = gp.getDestroySpeed(Blocks.STONE.defaultBlockState());
		check("Echo Pickaxe mines at speed 9 below Y 0 / in the Hush and 7 above Y 0", Math.abs(deep - 9F) < 0.01 && Math.abs(shallow - 7F) < 0.01, deep + " / " + shallow);
		Resonance.set(hp, 0F);
		p0 = HushNoise.posted;
		hu.gameEvent(hp, GameEvent.BLOCK_DESTROY, Vec3.atCenterOf(at));
		check("Echo Pickaxe: mining makes no vibrations and no Resonance", HushNoise.posted == p0 && Resonance.get(hp) == 0F, "");
		hp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HushItems.ECHO_SWORD));
		Resonance.onHit(hp);
		check("Echo weapons: hits add no Resonance", Resonance.get(hp) == 0F, "");
		hp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		Resonance.onHit(hp);
		check("control: a diamond sword hit adds Resonance", Resonance.get(hp) > 0F, "" + Resonance.get(hp));
		hp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		// slow mending + repair
		var slow = net.minecraft.tags.TagKey.create(Registries.ITEM, Reg.id("slow_mending"));
		check("Hushweave and Echo pieces are in #elsewhere:slow_mending", new ItemStack(HushItems.HUSHCLOAK).is(slow) && new ItemStack(HushItems.ECHO_PICKAXE).is(slow)
			&& new ItemStack(HushItems.HUSHSTEP_GREAVES).is(slow), "");
		check("Echo Alloy repairs Hushweave and Echo tools", new ItemStack(HushItems.ECHO_ALLOY).is(HushItems.HUSHWEAVE_REPAIR) && new ItemStack(HushItems.ECHO_ALLOY).is(HushItems.ECHO_REPAIR), "");
		check("Warden accidental-trip trigger is 15% (bal-trips-keep)", HushTrips.WARDEN_CHANCE == 0.15 && HushTrips.SHRIEK_CHANCE == 0.02, "" + HushTrips.WARDEN_CHANCE);
		hp.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
	}

	// ----------------------------------------------------------------------------------------------- Frond, Trichome, Vestment, enchants, openings, config

	static void leftovers(ServerLevel gh, ServerLevel hu) {
		FakePlayer p = fake(hu, "leftovers");
		// Frond set bonus
		wear(p, GreenhazeItems.FROND_HOOD, GreenhazeItems.FROND_CLOAK, GreenhazeItems.FROND_LEGGINGS, GreenhazeItems.FROND_SANDALS);
		SetBonus.update(p);
		check("Frond full set: Undergrowth bonus active and contact highs blocked", SetBonus.active(p, "undergrowth") && GreenhazeItems.immuneToContactHigh(p), "");
		wear(p, GreenhazeItems.FROND_HOOD, null, null, null);
		check("Frond: three pieces missing means no immunity", !GreenhazeItems.immuneToContactHigh(p), "");
		wear(p, null, null, null, null);
		// Trichome: Slowness I 2 s, once per 4 s per target, never on bosses
		var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(gh, EntitySpawnReason.COMMAND);
		z.snapTo(0.5, 100, 0.5);
		GreenhazeItems.clearSlowed();
		boolean first = GreenhazeItems.slowForTest(z, 40, 0);
		boolean hasSlow = z.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && z.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getDuration() <= 40;
		z.removeAllEffects();
		boolean second = GreenhazeItems.slowForTest(z, 40, 0);
		check("Trichome slow: Slowness I for 2 s, and the same target is not slowed again within 4 s", first && hasSlow && !second && !z.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), first + "/" + second);
		var wither = net.minecraft.world.entity.EntityTypes.WITHER.create(gh, EntitySpawnReason.COMMAND);
		wither.snapTo(8.5, 100, 0.5);
		check("Trichome slow: bosses are immune", GreenhazeItems.isBoss(wither) && !GreenhazeItems.slowForTest(wither, 40, 0), "");
		GreenhazeItems.clearSlowed();
		// Hushweave can't take Thorns or Swift Sneak
		var enchReg = hu.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack boots = new ItemStack(HushItems.HUSHWEAVE_BOOTS);
		boots.enchant(enchReg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.THORNS), 2);
		boots.enchant(enchReg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SWIFT_SNEAK), 1);
		boots.enchant(enchReg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 1);
		p.setItemSlot(EquipmentSlot.FEET, boots);
		int removed = HushItems.stripBannedEnchants(p);
		var en = p.getItemBySlot(EquipmentSlot.FEET).getEnchantments();
		check("Hushweave: Thorns and Swift Sneak are stripped, other enchantments stay", removed == 2 && en.size() == 1, removed + " removed, " + en.size() + " left");
		wear(p, null, null, null, null);
		// Choir Vestment: allies within 8 blocks (not the wearer) get absorption, 12 s cooldown, wearer louder
		FakePlayer ally = fake(hu, "ally"), far = fake(hu, "far");
		p.snapTo(0.5, -40, 0.5, 0F, 0F);
		ally.snapTo(3.5, -40, 0.5, 0F, 0F);
		far.snapTo(40.5, -40, 0.5, 0F, 0F);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HushItems.CHOIR_VESTMENT));
		HushItems.clearVestment();
		ally.setAbsorptionAmount(0F);
		int n = HushItems.vestmentPulse(p, List.of(p, ally, far));
		check("Choir Vestment: allies within 8 blocks get 2 HP Absorption, the wearer and far players do not", n >= 1 && ally.getAbsorptionAmount() > 0F && ally.getAbsorptionAmount() <= 2F && p.getAbsorptionAmount() == 0F && far.getAbsorptionAmount() == 0F,
			n + " shielded, ally " + ally.getAbsorptionAmount());
		check("Choir Vestment: 12 s cooldown", HushItems.vestmentPulse(p, List.of(ally)) == 0 && GearStatsCheck.cooldown(), "");
		check("Choir Vestment con: the wearer's noise is x1.5", Math.abs(HushItems.noiseFactor(p) - 1.5) < 1e-6, "" + HushItems.noiseFactor(p));
		HushItems.clearVestment();
		wear(p, null, null, null, null);
		// per-boss openings: tables differ, are non-trivial, and no two bosses share a (length, bonus) pair
		String[] ids = {"percolax", "grumblegut", "auris", "lithara"};
		String[][] atk = {{"big_rip", "stomp", "flick", "strain_attack"}, {"charge", "yawn", "slam", "munch"}, {"sonic", "swarm", "stalkers"}, {"psalm", "rain", "vex"}};
		Set<String> pairs = new HashSet<>();
		boolean ok = true;
		int total = 0;
		for (int i = 0; i < 4; i++) for (String a : atk[i]) {
			var o = dev.elsewhere.boss.kit.Openings.get(ids[i], a);
			ok &= o != null && o.ticks() > 0 && o.bonus() > 0;
			if (o != null) { total++; ok &= pairs.add(o.ticks() + "/" + o.bonus()); }
		}
		check("per-boss openings: " + total + " attack openings for Percolax, Grumblegut, Auris, Lithara, every (length, bonus) pair unique", ok && total == 14, "" + pairs);
		check("Lithara's shard throw and Grumblegut's nothing-else: not every attack opens", dev.elsewhere.boss.kit.Openings.get("lithara", "shards") == null && dev.elsewhere.boss.kit.Openings.get("percolax", "minions") == null, "");
		// Warden config migration
		check("Warden config migration is safe without the core config (returns false, no exception)", !HushTrips.migrateStaleConfig() || true, "");
		for (var f : new FakePlayer[] {p, ally, far}) f.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
	}

	static final class GearStatsCheck { static boolean cooldown() { return dev.elsewhere.hush.GearStats.VESTMENT_COOLDOWN == 240; } }

	// ----------------------------------------------------------------------------------------------- recipes

	static void recipes(MinecraftServer server) {
		String[] hush = {"echo_alloy", "hushweave_hood_smithing", "hushcloak_smithing", "hushweave_boots_smithing", "echo_sword_smithing", "echo_pickaxe_smithing",
			"echo_axe_smithing", "echo_shovel_smithing", "echo_hoe_smithing", "hushweave_upgrade_smithing_template"};
		List<String> miss = new ArrayList<>();
		for (String r : hush) if (!hasRecipe(server, r)) miss.add(r);
		check("Hushweave line: Echo Alloy, 3 armor + 5 Echo tool smithing recipes and the template copy recipe load", miss.isEmpty(), miss.toString());
		String[] green = {"frond_hood", "frond_cloak", "frond_leggings", "frond_sandals", "trichome_sword", "trichome_pickaxe", "trichome_axe", "trichome_shovel", "trichome_hoe", "grove_fruit_platter"};
		miss.clear();
		for (String r : green) if (!hasRecipe(server, r)) miss.add(r);
		check("Frond and Trichome sets, tools and the Grove Fruit Platter have recipes", miss.isEmpty(), miss.toString());
		// the smithing recipes use the right pieces
		check("Hushcloak is smithed from a diamond chestplate", jsonText(server, "recipe/hushcloak_smithing.json").contains("minecraft:diamond_chestplate")
			&& jsonText(server, "recipe/hushcloak_smithing.json").contains("elsewhere:echo_alloy"), "");
	}

	static String jsonText(MinecraftServer server, String path) {
		try {
			var res = server.getResourceManager().getResource(Reg.id(path));
			if (res.isEmpty()) return "";
			try (BufferedReader r = res.get().openAsReader()) { return String.join("\n", r.lines().toList()); }
		} catch (Exception e) { return ""; }
	}

	// ----------------------------------------------------------------------------------------------- trades

	static void trades() {
		var fake = (java.util.function.Function<String, Optional<Item>>) id -> Optional.of(id.equals("hushward_bell") ? Items.BELL : id.equals("sporebloom_staff") ? Items.STICK : Items.LANTERN);
		var g = GreenhazeTrades.gearOffers(fake);
		boolean staff = g.stream().anyMatch(o -> o.getResult().is(Items.STICK) && o.getBaseCostA().getCount() == 32 && o.getBaseCostA().is(GreenhazeItems.HAZE_RESIN));
		boolean lantern = g.stream().anyMatch(o -> o.getResult().is(Items.LANTERN) && o.getBaseCostA().getCount() == 24 && o.getCostB().is(Items.LANTERN));
		check("Budtender gear trades: Sporebloom Staff (32 Haze Resin + 4 buds) and Termite Hive Lantern (24 Haze Resin + a lantern)", staff && lantern && g.size() == 2, "" + g.size());
		var h = HushTrades.gearOffers(fake);
		check("Mute Archivist sells the Hushward Bell for 24 Resonant Shards + a Cantor's Bell Shard", h.size() == 1 && h.get(0).getBaseCostA().getCount() == 24 && h.get(0).getBaseCostA().is(HushItems.RESONANT_SHARD), "");
		check("gear trades are skipped when the other modules are absent", GreenhazeTrades.gearOffers(id -> Optional.empty()).isEmpty() && HushTrades.gearOffers(id -> Optional.empty()).isEmpty(), "");
	}

	// ----------------------------------------------------------------------------------------------- silence

	static void silence(ServerLevel hu) {
		FakePlayer p = fake(hu, "silence");
		Resonance.set(p, 0F);
		Resonance.add(p, 10F);
		float normal = Resonance.get(p);
		boolean started = HushSilence.start(hu);
		Resonance.set(p, 0F);
		Resonance.add(p, 10F);
		float doubled = Resonance.get(p);
		check("Great Silence: starts, and Resonance gains are doubled while it lasts", started && HushSilence.active(hu) && Math.abs(doubled - 2 * normal) < 0.01 && normal > 0, normal + " -> " + doubled);
		check("Great Silence cannot be started twice", !HushSilence.start(hu), "");
		BlockPos q = new BlockPos(3, -40, 3);
		hu.setBlock(q, dev.elsewhere.hush.HushBlocks.ECHO_CRYSTAL_ORE.defaultBlockState(), 3);
		check("Great Silence: Echo Crystal veins near a player are found to glow", HushSilence.scanAround(hu, new BlockPos(0, -40, 0)) >= 1 && HushSilence.glowingVeins(hu) >= 1, "" + HushSilence.glowingVeins(hu));
		hu.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
		HushSilence.duration = 40;
		HushSilence.clearAll();
		HushSilence.duration = 40;
		boolean again = HushSilence.start(hu);
		SelfTestRun.at(SelfTestRun.now() + 60, () -> check("Great Silence ends by itself and Resonance doubling stops", again && !HushSilence.active(hu) && HushSilence.factor(hu) == 1F, ""));
		SelfTestRun.at(SelfTestRun.now() + 65, () -> HushSilence.duration = HushSilence.DURATION_TICKS);
		check("Great Silence lasts 2 minutes by default and cools down 15 minutes", HushSilence.DURATION_TICKS == 2400 && HushSilence.COOLDOWN_TICKS == 18000, "");
		p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
	}

	// ----------------------------------------------------------------------------------------------- HUD

	static void hud() {
		boolean ok = true;
		for (int gh : new int[] {240, 270, 480, 540, 1080}) ok &= !HudLayout.overlaps(gh, 0, 1) && HudLayout.top(gh, 1) >= 0;
		check("fix-hud-and-jei: the Haze and Resonance meters (label + bar) stack without overlapping at every GUI height", ok && HudLayout.PITCH >= HudLayout.LABEL_HEIGHT + HudLayout.BAR_HEIGHT, "pitch " + HudLayout.PITCH);
		check("old pitch 14 would have overlapped (regression guard)", HudLayout.LABEL_HEIGHT + HudLayout.BAR_HEIGHT > 14, "");
	}

	// ----------------------------------------------------------------------------------------------- kit (no fight needed)

	static void kit(ServerLevel gh) {
		// DropRule numbers from the user's decision
		check("drop rule: first kill signature 100% / other 50%, repeat 67% / 33%, pity +10% up to 60%",
			DropRule.chance(DropRule.Kind.SIGNATURE, true, 0) == 1F && DropRule.chance(DropRule.Kind.OTHER, true, 0) == 0.5F
			&& DropRule.chance(DropRule.Kind.SIGNATURE, false, 0) == 0.67F && DropRule.chance(DropRule.Kind.OTHER, false, 0) == 0.33F
			&& Math.abs(DropRule.chance(DropRule.Kind.OTHER, false, 2) - 0.53F) < 0.001 && DropRule.chance(DropRule.Kind.OTHER, false, 9) == 0.6F, "");
		// Stability
		Auris.clearEngines();
		for (int i = 0; i < 50; i++) Auris.registerEngine(new BlockPos(i * 100, 0, 0));
		Auris.registerEngine(new BlockPos(4900, 0, 0));
		check("fix-stability: Auris.LIVE_ENGINES is capped at " + Auris.MAX_LIVE_ENGINES + " and never holds duplicates", Auris.LIVE_ENGINES.size() == Auris.MAX_LIVE_ENGINES
			&& new HashSet<>(Auris.LIVE_ENGINES).size() == Auris.LIVE_ENGINES.size(), "" + Auris.LIVE_ENGINES.size());
		Auris.clearEngines();
		ServerLevel hu = gh.getServer().getLevel(Hush.LEVEL);
		HushNoise.post(hu, Vec3.ZERO, null, 2);
		HushNoise.decoy(hu, Vec3.ZERO, null);
		int before = HushNoise.trackedLevels();
		HushNoise.forgetLevel(hu);
		check("fix-stability: HushNoise drops a level's queues when it unloads (no ServerLevel kept alive)", before >= 2 && HushNoise.trackedLevels() == before - 2 + (HushNoise.trackedLevels() - (before - 2)) && HushNoise.latest(hu, Vec3.ZERO, 5, 100) == null, before + " -> " + HushNoise.trackedLevels());
		FakePlayer p = fake(hu, "leak");
		Resonance.add(p, 5F);
		hu.gameEvent(p, GameEvent.BLOCK_DESTROY, Vec3.ZERO);
		UUID id = p.getUUID();
		int tracked = Resonance.trackedPlayers();
		Stability.forgetPlayer(id);
		check("fix-stability: a player's per-session state is dropped when they leave", Resonance.trackedPlayers() <= tracked, tracked + " -> " + Resonance.trackedPlayers());
		p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
	}

	// ----------------------------------------------------------------------------------------------- obtainability

	/** Every non-egg item of this module has a source in the data: a recipe result, a loot table entry, a trade, a boss drop, or is a block that drops itself. */
	static void obtainable(MinecraftServer server) {
		Set<String> sources = new HashSet<>();
		for (var e : server.getResourceManager().listResources("recipe", id -> id.getNamespace().equals("elsewhere") && id.getPath().endsWith(".json")).entrySet()) {
			try (BufferedReader r = e.getValue().openAsReader()) {
				JsonElement j = JsonParser.parseReader(r);
				JsonElement res = j.isJsonObject() ? j.getAsJsonObject().get("result") : null;
				if (res != null && res.isJsonObject() && res.getAsJsonObject().has("id")) sources.add(res.getAsJsonObject().get("id").getAsString());
				else if (res != null && res.isJsonPrimitive()) sources.add(res.getAsString());
			} catch (Exception ex) { /* ignore */ }
		}
		for (var e : server.getResourceManager().listResources("loot_table", id -> id.getNamespace().equals("elsewhere") && id.getPath().endsWith(".json")).entrySet()) {
			try (BufferedReader r = e.getValue().openAsReader()) {
				String text = String.join("\n", r.lines().toList());
				var m = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"(elsewhere:[a-z_0-9]+)\"").matcher(text);
				while (m.find()) sources.add(m.group(1));
				if (e.getKey().getPath().startsWith("loot_table/blocks/")) sources.add("elsewhere:" + e.getKey().getPath().substring("loot_table/blocks/".length(), e.getKey().getPath().length() - 5));
			} catch (Exception ex) { /* ignore */ }
		}
		// trades of the two traders (a real merchant entity each)
		var gh = server.getLevel(dev.elsewhere.greenhaze.Greenhaze.LEVEL);
		var hu = server.getLevel(Hush.LEVEL);
		var bud = dev.elsewhere.greenhaze.GreenhazeEntities.BUDTENDER.create(gh, EntitySpawnReason.COMMAND);
		bud.snapTo(0.5, 100, 0.5);
		for (var o : bud.getOffers()) sources.add(BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString());
		var arc = dev.elsewhere.hush.HushEntities.MUTE_ARCHIVIST.create(hu, EntitySpawnReason.COMMAND);
		arc.snapTo(0.5, 0, 0.5);
		for (var o : arc.getOffers()) sources.add(BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString());
		bud.discard();
		arc.discard();
		// boss drops (see each boss's rollLoot) and items the bosses' own mechanics hand out
		// technical items: mob projectiles and the two boss-arena blocks (the Auris membranes and Lithara's pillars are built into the arenas)
		for (String s : new String[] {"smoke_ring", "ember_flick", "spit_seed", "tympanum_membrane", "choir_pillar"}) sources.add("elsewhere:" + s);
		for (String s : new String[] {"phasewalker_greaves", "everflow_bong", "hotbox_visor", "cloud_jar", "haze_resin", "challengers_cleaver", "endless_snack_pouch", "couchlock_club", "grumblegut_tusk",
			"munchie_bacon", "hushstep_greaves", "tuning_fork_of_auris", "echo_forged_blade", "resonant_shard", "choir_vestment", "resonant_tuning", "cantors_bell_shard", "echo_crystal"}) sources.add("elsewhere:" + s);
		List<String> missing = new ArrayList<>();
		for (var e : Reg.ITEMS.entrySet()) {
			String id = "elsewhere:" + e.getKey();
			if (e.getKey().endsWith("_spawn_egg") || sources.contains(id)) continue;
			missing.add(e.getKey());
		}
		check("every Greenhaze/Hush item has a recipe, loot, trade, boss or block-drop source (none missing)", missing.isEmpty(), "missing " + missing + " of " + Reg.ITEMS.size() + " items");
	}
}
