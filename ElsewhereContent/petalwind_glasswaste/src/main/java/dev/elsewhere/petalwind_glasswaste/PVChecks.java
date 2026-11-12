package dev.elsewhere.petalwind_glasswaste;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.boss.kit.KitStore;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.glasswaste.GlasswasteItems;
import dev.elsewhere.glasswaste.Heat;
import dev.elsewhere.glasswaste.SunwrightPerks;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.petalwind.PetalwindItems;
import dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** 2.0 dims-pv checks: boss kit, endgame lines, live Heat toggle, events, trims, trades, smithing. */
final class PVChecks {
	private PVChecks() {}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	private static void setSlot(FakePlayer p, EquipmentSlot slot, net.minecraft.world.item.Item item) {
		p.getItemBySlot(slot).forEachModifier(slot, (attr, mod) -> {
			var inst = p.getAttribute(attr);
			if (inst != null) inst.removeModifier(mod.id());
		});
		ItemStack st = item == null ? ItemStack.EMPTY : new ItemStack(item);
		p.setItemSlot(slot, st);
		st.forEachModifier(slot, (attr, mod) -> {
			var inst = p.getAttribute(attr);
			if (inst != null && !inst.hasModifier(mod.id())) inst.addTransientModifier(mod);
		});
	}

	private static void wear(FakePlayer p, net.minecraft.world.item.Item h, net.minecraft.world.item.Item c, net.minecraft.world.item.Item l, net.minecraft.world.item.Item f) {
		setSlot(p, EquipmentSlot.HEAD, h);
		setSlot(p, EquipmentSlot.CHEST, c);
		setSlot(p, EquipmentSlot.LEGS, l);
		setSlot(p, EquipmentSlot.FEET, f);
	}

	static void run(MinecraftServer server) {
		ServerLevel gw = server.getLevel(GlasswasteDimension.LEVEL), pw = server.getLevel(PetalwindDimension.LEVEL), ow = server.overworld();
		var rm = server.getRecipeManager();
		var reg = server.registryAccess();

		// ---- boss kit: drop rules, XP, refund, persistent re-arm cooldown
		var rnd = ow.getRandom();
		int sig = 0, oth = 0;
		for (int i = 0; i < 400; i++) {
			var out = DropRule.roll(true, new HashMap<>(), List.of(DropRule.signature("a", () -> new ItemStack(Items.STICK)), DropRule.other("b", () -> new ItemStack(Items.DIRT))), rnd);
			if (out.stream().anyMatch(s -> s.is(Items.STICK))) sig++;
			if (out.stream().anyMatch(s -> s.is(Items.DIRT))) oth++;
		}
		SelfTest.check(sig == 400 && oth > 150 && oth < 250, "drop rule first kill: signature 400/400, other " + oth + "/400 (50%)");
		check2(DropRule.chance(DropRule.Kind.SIGNATURE, false, 0) == 0.67F && DropRule.chance(DropRule.Kind.OTHER, false, 0) == 0.33F, "repeat kills: signature 67%, other 33%");
		FakePlayer fp = FakePlayer.get(ow, new GameProfile(UUID.randomUUID(), "selftest_pv"));
		fp.setPos(8.5, 200, 8.5);
		// the orbs only exist if their chunk is loaded; the merged world does not always keep the overworld origin loaded
		ow.getChunk(0, 0);
		ow.setChunkForced(0, 0, true);
		int before = ow.getEntitiesOfClass(ExperienceOrb.class, new AABB(fp.blockPosition()).inflate(8)).size();
		int xp = BossRewards.awardXp(ow, fp.position(), 0);
		int sum = ow.getEntitiesOfClass(ExperienceOrb.class, new AABB(fp.blockPosition()).inflate(8)).stream().mapToInt(ExperienceOrb::getValue).sum();
		SelfTest.check(xp >= 400 && sum >= 400, "boss XP: " + xp + " awarded, " + sum + " in orbs (orbs before " + before + ")");
		ow.setChunkForced(0, 0, false);
		ow.getEntitiesOfClass(ExperienceOrb.class, new AABB(fp.blockPosition()).inflate(8)).forEach(net.minecraft.world.entity.Entity::discard);
		KitStore store = KitStore.of(server);
		UUID boss = UUID.randomUUID();
		int refunds = store.data().refunds.size();
		store.fightStart(boss, "kazehana", fp.getUUID(), new ItemStack(PGStructures.BLOSSOM_SEAL_CHARM), "elsewhere:petalwind", 0, 100, 0);
		boolean active = store.fightActive(boss);
		store.fightWiped(boss, "selftest");
		SelfTest.check(active && store.data().refunds.size() == refunds + 1 && store.data().refunds.get(store.data().refunds.size() - 1).item.equals("elsewhere:blossom_seal_charm"),
			"retry refund: a wiped fight queues the Blossom Seal Charm for the summoner");
		store.data().refunds.removeIf(r -> "selftest".equals(r.reason));
		store.fightStart(boss, "tempestra", fp.getUUID(), new ItemStack(Items.WIND_CHARGE, 3), "elsewhere:petalwind", 0, 100, 0);
		store.fightWiped(boss, "selftest");
		SelfTest.check(store.data().refunds.stream().anyMatch(r -> "selftest".equals(r.reason) && r.count == 3 && r.item.equals("minecraft:wind_charge")), "retry refund: Tempestra's 3 wind charges come back");
		store.data().refunds.removeIf(r -> "selftest".equals(r.reason));
		BlockPos arena = new BlockPos(777, 100, 777);
		boolean readyBefore = PGStructures.rearmReady(ow, arena);
		PGStructures.rearmStart(ow, arena);
		var reread = new KitStore(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("elsewhere_boss_kit.json"), () -> ow.getGameTime());
		SelfTest.check(readyBefore && !PGStructures.rearmReady(ow, arena) && reread.cooldownRemaining(PGStructures.rearmKey(arena)) > 11000,
			"re-arm cooldown is in the persistent KitStore (a fresh load of the file still has " + reread.cooldownRemaining(PGStructures.rearmKey(arena)) + " ticks left); LAST_KILL map gone");
		store.data().cooldowns.remove(PGStructures.rearmKey(arena));
		store.save();

		// ---- Sunwright line
		check2(GlasswasteItems.SUNWRIGHT_INGOT != null && GlasswasteItems.SUNWRIGHT_TEMPLATE != null && GlasswasteItems.SUNBURST_TRIM_TEMPLATE != null && PetalwindItems.GALE_TRIM_TEMPLATE != null,
			"new ids registered: sunwright_ingot, sunwright_upgrade_smithing_template, sunburst + gale trim templates");
		int missing = 0;
		for (String n : new String[] {"sunwright_helmet", "sunwright_chestplate", "sunwright_leggings", "sunwright_boots", "sunstone_sword", "sunstone_pickaxe", "sunstone_axe", "sunstone_shovel",
			"sunstone_hoe", "sunwright_ingot", "sunwright_template_duplicate", "gale_armor_trim_smithing_template_smithing_trim", "gale_armor_trim_smithing_template_duplicate",
			"sunburst_armor_trim_smithing_template_smithing_trim", "sunburst_armor_trim_smithing_template_duplicate"}) {
			if (rm.byKey(ResourceKey.create(Registries.RECIPE, Reg.id(n))).isEmpty()) {
				missing++;
				SelfTest.check(false, "recipe missing: " + n);
			}
		}
		var helmRecipe = rm.byKey(ResourceKey.create(Registries.RECIPE, Reg.id("sunwright_helmet")));
		SelfTest.check(missing == 0 && helmRecipe.isPresent() && helmRecipe.get().value() instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe,
			"Sunwright armor and Sunstone tools are smithed (G9): 15 recipes present, helmet is a smithing_transform");
		var chestKey = ResourceKey.create(Registries.LOOT_TABLE, Reg.id("chests/sunwright_ziggurat"));
		var table = server.reloadableRegistries().getLootTable(chestKey);
		var ctx = new net.minecraft.world.level.storage.loot.LootParams.Builder(ow).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
		int tmpl = 0, trials = 400;
		for (int i = 0; i < trials; i++) for (ItemStack st : table.getRandomItems(ctx, 1000L + i)) if (st.is(GlasswasteItems.SUNWRIGHT_TEMPLATE)) tmpl++;
		PetalwindGlasswasteContent.LOGGER.info("[selftest] chest sample {}", table.getRandomItems(ctx, 5L));
		SelfTest.check(tmpl > 80 && tmpl < 200, "Sunwright Ziggurat chest loot: template in " + tmpl + "/" + trials + " rolls (rare, about 35%)");
		for (Object[] t : new Object[][] {{"chests/breeze_temple_vault", PetalwindItems.GALE_TRIM_TEMPLATE, "gale_armor_trim_smithing_template_duplicate"},
			{"chests/sunwright_ziggurat", GlasswasteItems.SUNBURST_TRIM_TEMPLATE, "sunburst_armor_trim_smithing_template_duplicate"}}) {
			var tt = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Reg.id((String)t[0])));
			int hit = 0, tr = 1500;
			for (int i = 0; i < tr; i++) for (ItemStack st : tt.getRandomItems(ctx, 7000L + i)) if (st.is((net.minecraft.world.item.Item)t[1])) hit++;
			SelfTest.check(hit >= 40 && hit <= 250, "trim template in " + t[0] + ": " + hit + "/" + tr + " rolls (rare, about 8%)");
			SelfTest.check(rm.byKey(ResourceKey.create(Registries.RECIPE, Reg.id((String)t[2]))).isPresent(), "trim template duplication recipe exists: " + t[2]);
		}
		var pats = reg.lookupOrThrow(Registries.TRIM_PATTERN);
		SelfTest.check(pats.get(ResourceKey.create(Registries.TRIM_PATTERN, Reg.id("gale"))).isPresent() && pats.get(ResourceKey.create(Registries.TRIM_PATTERN, Reg.id("sunburst"))).isPresent()
			&& SelfTest.class.getResource("/assets/elsewhere/textures/trims/entity/humanoid/gale.png") != null && SelfTest.class.getResource("/assets/elsewhere/textures/trims/entity/humanoid_leggings/sunburst.png") != null,
			"trims: Gale (Petalwind) and Sunburst (Glasswaste) patterns registered with textures");
		SelfTest.check(new ItemStack(GlasswasteItems.SUNWRIGHT_HELMET).is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR) && new ItemStack(PetalwindItems.GALEWEAVE_BOOTS).is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR),
			"trimmable_armor tag has the Sunwright and Galeweave pieces");

		FakePlayer p = FakePlayer.get(gw, new GameProfile(UUID.randomUUID(), "selftest_sunwright"));
		p.setPos(8.5, 250, 8.5);
		wear(p, GlasswasteItems.SUNWRIGHT_HELMET, GlasswasteItems.SUNWRIGHT_CHESTPLATE, GlasswasteItems.SUNWRIGHT_LEGGINGS, GlasswasteItems.SUNWRIGHT_BOOTS);
		double armor = p.getAttributeValue(Attributes.ARMOR), tough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), kb = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		SelfTest.check(near(armor, 20) && near(tough, 8) && near(kb, 0) && near(p.getAttributeValue(Attributes.BURNING_TIME), 0.7),
			f2("Sunwright set: armor %.0f (netherite 20), toughness %.0f (netherite 12), KB res %.1f, burning x%.2f (chest -30%%)", armor, tough, kb, p.getAttributeValue(Attributes.BURNING_TIME)));
		var src = ow.damageSources();
		SelfTest.check(SunwrightPerks.noonfire(p) && SunwrightPerks.blocks(p, src.lava()) && SunwrightPerks.blocks(p, src.inFire()) && SunwrightPerks.blocks(p, src.onFire()) && SunwrightPerks.blocks(p, src.hotFloor())
			&& !SunwrightPerks.blocks(p, src.mobAttack(p)) && !SunwrightPerks.blocks(p, src.fireball(null, null)),
			"Noonfire: lava, fire, burning and magma are cancelled; mobs and fireball hits still hurt");
		p.igniteForSeconds(5);
		SunwrightPerks.tickPlayer(p);
		SelfTest.check(!p.isOnFire(), "Noonfire: burning is put out on the next tick");
		SelfTest.check(near(Heat.heatGainMultiplier(p), 0.0), "Noonfire: Heat never builds (gain x0)");
		wear(p, GlasswasteItems.SUNWRIGHT_HELMET, null, null, null);
		SelfTest.check(near(Heat.heatGainMultiplier(p), 0.75) && !SunwrightPerks.noonfire(p) && !SunwrightPerks.blocks(p, src.lava()) && Heat.glareImmune(p),
			"one Sunwright piece: Heat gain -25%, no Noonfire, lava still hurts; Helm: Glare immune");
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 100));
		SunwrightPerks.tickPlayer(p);
		SelfTest.check(!p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "Sunwright Helm: Blindness immunity");
		wear(p, null, null, GlasswasteItems.SUNWRIGHT_LEGGINGS, null);
		SelfTest.check(SunwrightPerks.blocks(p, src.hotFloor()) && SunwrightPerks.blocks(p, src.campfire()) && !SunwrightPerks.blocks(p, src.lava()),
			"Sunwright Legs: magma blocks and campfires don't hurt; lava does");
		wear(p, null, null, null, GlasswasteItems.SUNWRIGHT_BOOTS);
		gw.setBlockAndUpdate(new BlockPos(8, 249, 8), Blocks.RED_SAND.defaultBlockState());
		gw.setBlockAndUpdate(new BlockPos(8, 250, 8), Blocks.AIR.defaultBlockState());
		p.setPos(8.5, 250, 8.5);
		p.setOnGround(true);
		SunwrightPerks.tickPlayer(p);
		var sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean sandFast = sp != null && sp.hasModifier(Reg.id("sunwright_sand_speed"));
		gw.setBlockAndUpdate(new BlockPos(8, 249, 8), Blocks.STONE.defaultBlockState());
		SunwrightPerks.tickPlayer(p);
		SelfTest.check(sandFast && !sp.hasModifier(Reg.id("sunwright_sand_speed")), "Sunwright Boots: +15% speed on red sand, gone on stone");
		var fireProt = reg.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION);
		var prot = reg.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
		SelfTest.check(SunwrightPerks.bannedEnchant(fireProt, new ItemStack(GlasswasteItems.SUNWRIGHT_CHESTPLATE)) && !SunwrightPerks.bannedEnchant(prot, new ItemStack(GlasswasteItems.SUNWRIGHT_CHESTPLATE))
			&& new ItemStack(GlasswasteItems.SUNWRIGHT_CHESTPLATE).is(ResourceKeyTag.slow()), "Sunwright con: Fire Protection refused, Protection allowed, slow Mending tag");
		// Plate
		wear(p, null, GlasswasteItems.SUNWRIGHT_PLATE, null, null);
		SelfTest.check(near(p.getAttributeValue(Attributes.ARMOR), 8) && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 3) && near(Heat.heatGainMultiplier(p), 0.0),
			"Heliodor armor (Sunwright Plate): 8 armor / 3 toughness, Heat immunity, counts for the set");
		wear(p, GlasswasteItems.SUNWRIGHT_HELMET, GlasswasteItems.SUNWRIGHT_PLATE, GlasswasteItems.SUNWRIGHT_LEGGINGS, GlasswasteItems.SUNWRIGHT_BOOTS);
		SelfTest.check(SunwrightPerks.noonfire(p), "Noonfire works with the Plate in the chest slot");
		// Sunstone tools
		setSlot(p, EquipmentSlot.MAINHAND, GlasswasteItems.SUNSTONE_SWORD);
		p.setPos(8.5, 250, 8.5);
		SunwrightPerks.tickPlayer(p);
		boolean sunny = Heat.inDirectSun(gw, p);
		double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		SelfTest.check(near(dmg, sunny ? 8 : 8 - GearStats.SUNSTONE_DARK_PENALTY), f2("Sunstone sword: %.0f damage %s (8 in daylight under open sky, %.0f otherwise)", dmg, sunny ? "in the sun" : "in shade", 8 - GearStats.SUNSTONE_DARK_PENALTY));
		p.setPos(8.5, 5, 8.5);
		SunwrightPerks.tickPlayer(p);
		SelfTest.check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 8 - GearStats.SUNSTONE_DARK_PENALTY), f2("Sunstone sword underground: -%.0f damage (3.0 rebalance: no dark penalty)", GearStats.SUNSTONE_DARK_PENALTY));
		setSlot(p, EquipmentSlot.MAINHAND, null);
		SunwrightPerks.tickPlayer(p);
		SelfTest.check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1), "Sunstone modifiers are removed when the tool is put away");

		// ---- Galeweave per piece
		FakePlayer g = FakePlayer.get(pw, new GameProfile(UUID.randomUUID(), "selftest_gale"));
		wear(g, PetalwindItems.GALEWEAVE_HOOD, null, null, null);
		double one = g.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		wear(g, PetalwindItems.GALEWEAVE_HOOD, PetalwindItems.GALEWEAVE_ROBE, PetalwindItems.GALEWEAVE_LEGGINGS, PetalwindItems.GALEWEAVE_BOOTS);
		SelfTest.check(near(one, 0.9) && near(g.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.6), f2("Galeweave: fall damage x%.2f with one piece, x%.2f with four (-10%% per piece)", one, g.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));

		// ---- live Heat toggle
		ClientSettingsStub.setLive(k -> ClientSettingsStub.Mode.HIDDEN);
		boolean hidden = ClientSettingsStub.meter("heat") == ClientSettingsStub.Mode.HIDDEN;
		ClientSettingsStub.setLive(k -> ClientSettingsStub.Mode.ALWAYS);
		boolean always = ClientSettingsStub.meter("heat") == ClientSettingsStub.Mode.ALWAYS;
		ClientSettingsStub.setLive(null);
		SelfTest.check(hidden && always, "Heat meter follows the live setting on the very next read (no file polling): Hidden then Always");

		// ---- events
		if (gw != null) {
			p.setPos(8.5, 250, 8.5);
			wear(p, null, null, null, null);
			PGEvents.startGlassStorm(gw);
			boolean storm = PGEvents.glassStorm(gw), exposed = PGEvents.stormExposed(gw, p);
			wear(p, Items.IRON_HELMET, null, null, null);
			boolean covered = !PGEvents.stormExposed(gw, p);
			SelfTest.check(storm && exposed && covered && !PGEvents.glassStorm(ow), "glass storm: active in the Glasswaste only; open sky without a helmet is exposed, a helmet is cover");
		}
		if (pw != null) {
			PGEvents.startUpdrafts(pw);
			BlockPos base = new BlockPos(40, 200, 40);
			pw.getChunk(base);
			pw.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
			var pig = net.minecraft.world.entity.EntityTypes.PIG.create(pw, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			pig.setPos(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
			pw.addFreshEntity(pig);
			pig.setDeltaMovement(0, 0, 0);
			PGEvents.testLift(pw, base);
			double vy = pig.getDeltaMovement().y;
			pig.discard();
			SelfTest.check(PGEvents.updraftSeason(pw) && vy > 0.1, f2("updraft season: active in Petalwind, a column lifts what stands in it (vy %.2f)", vy));
		}
		var ww = ModEntities.WINDWRIGHT.create(ow, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		boolean rapier = ArsenalHooks.item("gale_rapier").isPresent(), ribbon = ArsenalHooks.item("gale_ribbon").isPresent();
		SelfTest.check(ww != null && ww.getOffers().stream().noneMatch(o -> o.getResult().isEmpty()) && ww.getOffers().stream().anyMatch(o -> o.getResult().is(Items.AIR)) == false,
			"Windwright: Gale Rapier " + (rapier ? "sold" : "absent, skipped") + ", Gale Ribbon " + (ribbon ? "sold" : "absent, skipped") + " (by id)");
		if (ww != null) ww.discard();
	}

	private static void check2(boolean ok, String what) {
		SelfTest.check(ok, what);
	}

	private static String f2(String fmt, Object... a) {
		return SelfTest.f(fmt, a);
	}

	private static final class ResourceKeyTag {
		static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> slow() {
			return net.minecraft.tags.TagKey.create(Registries.ITEM, Reg.id("slow_mending"));
		}
	}
}
