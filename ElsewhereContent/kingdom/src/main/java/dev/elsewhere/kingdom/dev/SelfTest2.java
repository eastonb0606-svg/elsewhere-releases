package dev.elsewhere.kingdom.dev;

import static dev.elsewhere.kingdom.dev.SelfTest.STEPS;
import static dev.elsewhere.kingdom.dev.SelfTest.at;
import static dev.elsewhere.kingdom.dev.SelfTest.check;
import static dev.elsewhere.kingdom.dev.SelfTest.clean;
import static dev.elsewhere.kingdom.dev.SelfTest.soldier;
import static dev.elsewhere.kingdom.dev.SelfTest.zombie;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomAggression;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomEvents;
import dev.elsewhere.kingdom.KingdomLod;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.item.BodkinArrow;
import dev.elsewhere.kingdom.item.FletchersBundleItem;
import dev.elsewhere.kingdom.item.KingdomItems;
import dev.elsewhere.kingdom.item.SergeantsHornItem;
import dev.elsewhere.kingdom.item.WhetstoneItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** 0.2.0 checks (K-A army lane): items, drops, look, combat tuning, abilities, morale, caps, LOD, aggression, routines. */
final class SelfTest2 {
	private SelfTest2() {}

	static FakePlayer fp(ServerLevel l, String name) {
		return FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
	}

	static void cmd(MinecraftServer srv, String c) {
		srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), c);
	}

	static Squad sq, sq2;
	static KingdomSoldier a, b, c, d, e, f;
	static Zombie z1, z2;
	static List<KingdomSoldier> list = new ArrayList<>();
	static int n0;
	static long t0, t1;
	static UUID id0, id1;
	static Vec3 v0;
	static int hornEvents;
	static boolean hooked;

	static int count(FakePlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
		return n;
	}

	static double rate(int hits, int n) {
		return hits / (double) n;
	}

	static void add() {
		if (!hooked) {
			hooked = true;
			KingdomEvents.HORN_BLOWN.register((lv, h, n) -> hornEvents++);
		}
		// ---- items, tags, data ---------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			Item[] items = {KingdomItems.ALDERMARK_CROWN, KingdomItems.BODKIN_BOLT, KingdomItems.WHETSTONE, KingdomItems.FLETCHERS_BUNDLE, KingdomItems.SERGEANTS_HORN,
				KingdomItems.OATHKEEPERS_SEAL, KingdomItems.ALDERMARK_TRIM_TEMPLATE, KingdomItems.ALDERMARK_BANNER_PATTERN};
			boolean all = true;
			for (Item i : items) all &= i != null && BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("elsewhere");
			check("8 kingdom items registered (elsewhere:aldermark_crown ...)", all && BuiltInRegistries.ITEM.getKey(KingdomItems.ALDERMARK_CROWN).getPath().equals("aldermark_crown"), null);
			ItemStack crown = new ItemStack(KingdomItems.ALDERMARK_CROWN);
			check("Crown: stackable currency in testmeter:transmutation_excluded", crown.getMaxStackSize() == 64
				&& crown.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("testmeter", "transmutation_excluded"))), null);
			check("Bodkin Bolt is an arrow (#minecraft:arrows)", new ItemStack(KingdomItems.BODKIN_BOLT).is(ItemTags.ARROWS), null);
			var reg = l.registryAccess();
			var provides = new ItemStack(KingdomItems.ALDERMARK_BANNER_PATTERN).get(DataComponents.PROVIDES_BANNER_PATTERNS);
			check("banner pattern elsewhere:aldermark loaded and provided by the pattern item", reg.lookupOrThrow(Registries.BANNER_PATTERN).get(KingdomItems.ALDERMARK_PATTERN).isPresent()
				&& provides != null && provides.size() == 1, provides == null ? "no component" : provides.size());
			check("trim pattern elsewhere:aldermark loaded", reg.lookupOrThrow(Registries.TRIM_PATTERN).get(KingdomItems.ALDERMARK_TRIM).isPresent(), null);
			check("trim template recipes loaded (smithing trim + duplication)",
				srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, KingdomContent.id("aldermark_armor_trim_smithing_template_smithing_trim"))).isPresent()
				&& srv.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, KingdomContent.id("aldermark_armor_trim_smithing_template"))).isPresent(), null);
			return 1;
		});
		// ---- loot tables -----------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			FakePlayer killer = fp(l, "kingdom-loot");
			killer.snapTo(at(0, 0).x, at(0, 0).y, at(0, 0).z, 0, 0);
			KingdomSoldier victim = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(0, 4));
			victim.setNoAi(true);
			String[] units = {"man_at_arms", "knight", "lancer", "mounted_swordsman", "archer", "crossbowman", "pikeman", "squire", "herald", "surgeon", "sergeant", "captain", "standard_bearer"};
			int loaded = 0;
			StringBuilder missing = new StringBuilder();
			for (String u : units) {
				LootTable t = srv.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, KingdomContent.id("entities/kingdom_" + u)));
				if (t == LootTable.EMPTY) missing.append(u).append(' ');
				else loaded++;
			}
			check("13 soldier loot tables load", loaded == 13, missing.length() == 0 ? null : missing);
			var src = l.damageSources().playerAttack(killer);
			LootParams p = new LootParams.Builder(l).withParameter(LootContextParams.THIS_ENTITY, victim).withParameter(LootContextParams.ORIGIN, victim.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, src).withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer).withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
				.create(LootContextParamSets.ENTITY);
			int N = 2000;
			String[][] cases = {{"man_at_arms", "whetstone", "0.03", "0.075"}, {"crossbowman", "bodkin_bolt", "0.12", "0.18"}, {"archer", "fletchers_bundle", "0.08", "0.125"},
				{"sergeant", "sergeants_horn", "0.17", "0.235"}, {"captain", "aldermark_armor_trim_smithing_template", "0.06", "0.105"}, {"standard_bearer", "aldermark_banner_pattern", "0.999", "1.01"}};
			for (String[] cs : cases) {
				LootTable t = srv.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, KingdomContent.id("entities/kingdom_" + cs[0])));
				Item uniq = BuiltInRegistries.ITEM.getValue(KingdomContent.id(cs[1]));
				int crowns = 0, ems = 0, hits = 0, seals = 0;
				for (int i = 0; i < N; i++) {
					boolean hit = false;
					for (ItemStack s : t.getRandomItems(p)) {
						if (s.is(KingdomItems.ALDERMARK_CROWN)) crowns += s.getCount();
						if (s.is(Items.EMERALD)) ems += s.getCount();
						if (s.is(KingdomItems.OATHKEEPERS_SEAL)) seals++;
						if (s.is(uniq)) hit = true;
					}
					if (hit) hits++;
				}
				boolean officer = cs[0].equals("sergeant") || cs[0].equals("captain") || cs[0].equals("standard_bearer");
				double cr = crowns / (double) N, em = ems / (double) N, ur = rate(hits, N), sr = rate(seals, N);
				boolean currency = officer ? (cr > 5.3 && cr < 6.7 && em > 3.1 && em < 3.9) : (cr > 1.8 && cr < 2.2 && em > 0.85 && em < 1.15);
				boolean uniqueOk = ur >= Double.parseDouble(cs[2]) && ur <= Double.parseDouble(cs[3]);
				boolean sealOk = officer ? (sr > 0.025 && sr < 0.06) : sr == 0;
				check("drops " + cs[0] + ": " + (officer ? "2-5 emeralds, 4-8 Crowns" : "0-2 emeralds, 1-3 Crowns") + ", " + cs[1] + (officer ? ", 4% Seal" : ""),
					currency && uniqueOk && sealOk, String.format("crowns %.2f, emeralds %.2f, %s %.1f%%, seal %.1f%%", cr, em, cs[1], ur * 100, sr * 100));
			}
			clean(l);
			return 1;
		});
		// ---- look: real armor, trims, heraldry, drop chances ---------------------------------------------------------
		STEPS.add((srv, l) -> {
			list.clear();
			Identifier[] ids = {UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.LANCER, UnitTypes.MOUNTED_SWORDSMAN, UnitTypes.ARCHER, UnitTypes.CROSSBOWMAN, UnitTypes.SERGEANT,
				UnitTypes.CAPTAIN, UnitTypes.STANDARD_BEARER, UnitTypes.HERALD, UnitTypes.SURGEON, UnitTypes.SQUIRE, UnitTypes.PIKEMAN};
			for (int i = 0; i < ids.length; i++) list.add(soldier(l, ids[i], Factions.ALDERMARK, at(-36 + i * 6, 40)));
			boolean spawned = list.stream().allMatch(s -> s != null);
			check("all 13 units spawn (6 new: captain, standard-bearer, herald, surgeon, squire, pikeman)", spawned, null);
			if (!spawned) return 1;
			StringBuilder noArmor = new StringBuilder(), noTrim = new StringBuilder(), badDrop = new StringBuilder();
			EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
			for (KingdomSoldier s : list) {
				for (EquipmentSlot sl : armor) {
					ItemStack st = s.getItemBySlot(sl);
					boolean banner = st.is(ItemTags.BANNERS);
					if (st.isEmpty() || (!banner && !st.has(DataComponents.EQUIPPABLE))) noArmor.append(s.unitId().getPath()).append(':').append(sl.getName()).append(' ');
					if (!banner && !st.has(DataComponents.TRIM)) noTrim.append(s.unitId().getPath()).append(':').append(sl.getName()).append(' ');
					if (Math.abs(s.getDropChances().byEquipment(sl) - 0.02F) > 1e-4) badDrop.append(s.unitId().getPath()).append(' ');
				}
				for (EquipmentSlot sl : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
					if (Math.abs(s.getDropChances().byEquipment(sl) - 0.04F) > 1e-4) badDrop.append(s.unitId().getPath()).append(' ');
			}
			check("every unit wears real vanilla armor in all 4 slots (standard-bearer: banner helm)", noArmor.length() == 0, noArmor.length() == 0 ? null : noArmor);
			check("armor trims applied to every armor piece", noTrim.length() == 0, noTrim.length() == 0 ? null : noTrim);
			check("drop chances: armor 2% per piece, weapons/shields 4%", badDrop.length() == 0, badDrop.length() == 0 ? null : badDrop);
			ArmorTrim tMa = list.get(0).getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM), tKn = list.get(1).getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM),
				tSg = list.get(6).getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM), tCp = list.get(7).getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM);
			check("trim material by rank: iron men-at-arms, lapis knights, gold sergeant and captain", tMa != null && tMa.material().is(TrimMaterials.IRON) && tKn != null && tKn.material().is(TrimMaterials.LAPIS)
				&& tSg != null && tSg.material().is(TrimMaterials.GOLD) && tCp != null && tCp.material().is(TrimMaterials.GOLD), null);
			ItemStack shield = list.get(0).getOffhandItem();
			var layers = shield.get(DataComponents.BANNER_PATTERNS);
			check("Aldermark shield: blue base, red chief + tower pattern", shield.is(Items.SHIELD) && shield.get(DataComponents.BASE_COLOR) == DyeColor.BLUE && layers != null && layers.layers().size() == 2
				&& layers.layers().get(1).pattern().is(KingdomItems.ALDERMARK_PATTERN), layers);
			KingdomSoldier cap = list.get(7), sb = list.get(8), pk = list.get(12);
			ItemStack helm = sb.getItemBySlot(EquipmentSlot.HEAD);
			check("captain: 30 hp, Sharpness sword, captain skin; standard-bearer: heraldic banner; pikeman: spear, no shield",
				cap.getMaxHealth() == 30F && cap.getMainHandItem().isEnchanted() && cap.skin().getPath().contains("captain") && helm.is(ItemTags.BANNERS)
					&& helm.has(DataComponents.BANNER_PATTERNS) && pk.getMainHandItem().is(Items.IRON_SPEAR) && pk.getOffhandItem().isEmpty(), cap.getMaxHealth());
			check("support units: herald horn, surgeon potion, squire 16 hp", list.get(9).getOffhandItem().is(Items.GOAT_HORN) && list.get(10).getOffhandItem().is(Items.SPLASH_POTION)
				&& list.get(11).getMaxHealth() == 16F, null);
			clean(l);
			list.clear();
			for (int i = 0; i < 12; i++) list.add(soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(-30 + i * 3, 30)));
			Set<Object> pats = new HashSet<>();
			for (KingdomSoldier s : list) if (s != null && s.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM) != null)
				pats.add(s.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.TRIM).pattern().unwrapKey().orElse(null));
			check("a few looks per unit: 12 men-at-arms show 2+ trim patterns", pats.size() >= 2, pats.size());
			clean(l);
			return 1;
		});
		// ---- combat tuning ----------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			UnitType.Combat ma = UnitTypes.get(UnitTypes.MAN_AT_ARMS).combat(), kn = UnitTypes.get(UnitTypes.KNIGHT).combat();
			check("rank and file: rare crits (<=12% per attack, >=8 s cooldown), light strafe, chance-based retreat and sprint",
				ma.critChance() <= 0.12F && ma.critCooldown() >= 160 && kn.critChance() <= 0.12F && ma.strafe() <= 0.5F && ma.retreatChance() < 0.5F && ma.sprintChance() <= 0.3F, ma);
			UnitType.Combat old = new UnitType.Combat(0.5F, 0.5F, 40, 0.3F);
			check("0.1.0 combat constructor keeps full player-like defaults (for Lords/King)", old.strafe() == 1F && old.sprintChance() == 1F && old.retreatChance() == 1F && old.mountedBonus() == 1F, null);
			check("human health ladder (RIVALS-DECISIONS 14): man-at-arms 20, knight 24, archer 20, crossbowman 20", UnitTypes.get(UnitTypes.MAN_AT_ARMS).stats().health() == 20 && UnitTypes.get(UnitTypes.KNIGHT).stats().health() == 24
				&& UnitTypes.get(UnitTypes.ARCHER).stats().health() == 20 && UnitTypes.get(UnitTypes.CROSSBOWMAN).stats().health() == 20, null);
			list.clear();
			for (int i = 0; i < 30; i++) {
				KingdomSoldier s = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(-28 + (i % 10) * 3, -40 + (i / 10) * 3));
				if (s != null) {
					s.setHealth(3F);
					list.add(s);
				}
			}
			return 15;
		});
		STEPS.add((srv, l) -> {
			long dec = list.stream().filter(s -> s.retreatDecided != null).count(), yes = list.stream().filter(KingdomSoldier::wantsRetreat).count();
			check("low-health retreat is a chance (~35%), rolled once", dec == list.size() && list.size() == 30 && yes > 2 && yes < 22, yes + "/" + dec + " retreat");
			clean(l);
			// pikeman bonus vs mounted
			a = soldier(l, UnitTypes.PIKEMAN, Factions.ALDERMARK, at(0, 20));
			a.setNoAi(true);
			z1 = zombie(l, at(1, 21), false);
			z2 = zombie(l, at(-1, 21), false);
			AbstractHorse h = EntityTypes.HORSE.create(l, EntitySpawnReason.COMMAND);
			h.snapTo(at(-1, 21).x, at(-1, 21).y, at(-1, 21).z, 0, 0);
			h.setNoAi(true);
			l.addFreshEntity(h);
			z2.startRiding(h, true, false);
			return 2;
		});
		STEPS.add((srv, l) -> {
			a.chargeAttack();
			float h1 = z1.getHealth();
			a.strike(l, z1);
			float d1 = h1 - z1.getHealth();
			a.chargeAttack();
			float h2 = z2.getHealth();
			a.strike(l, z2);
			float d2 = h2 - z2.getHealth();
			check("pikemen deal double damage to riders", z2.isPassenger() && d1 > 0 && Math.abs(d2 / d1 - 2F) < 0.15F, "ground " + d1 + ", mounted " + d2);
			clean(l);
			return 1;
		});
		// ---- role formation + sergeant's shield wall ------------------------------------------------------------
		STEPS.add((srv, l) -> {
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -30)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.KNIGHT, UnitTypes.KNIGHT, UnitTypes.PIKEMAN, UnitTypes.PIKEMAN, UnitTypes.ARCHER, UnitTypes.ARCHER));
			sq = s.get(0).squad();
			sq.setObjective(BlockPos.containing(at(0, -16)));
			return 70;
		});
		STEPS.add((srv, l) -> {
			Mob lead = sq.leaderEntity(l);
			boolean front = true, mid = true, back = true;
			StringBuilder det = new StringBuilder();
			for (KingdomSoldier m : sq.memberEntities(l)) {
				if (m.formationTarget == null || lead == null) {
					front = false;
					continue;
				}
				double rad = Math.toRadians(m.formationYaw);
				double fwd = (m.formationTarget.x - lead.getX()) * -Math.sin(rad) + (m.formationTarget.z - lead.getZ()) * Math.cos(rad);
				det.append(m.unitId().getPath()).append(String.format("=%.1f ", fwd));
				if (m.role().ranged()) back &= fwd < -3.0;
				else if (m.hasShield()) front &= fwd > 0.0;
				else mid &= fwd < -0.2 && fwd > -3.0;
			}
			check("role formation: shield-bearers in front, pikemen between, archers back", front && mid && back, det);
			Vec3 lp = lead.position();
			net.minecraft.world.entity.monster.skeleton.Skeleton sk = EntityTypes.SKELETON.create(l, EntitySpawnReason.COMMAND);
			sk.snapTo(lp.x, lp.y, lp.z + 7, 180F, 0F);
			sk.setNoAi(true);
			sk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
			sk.setHealth(300F);
			sk.setPersistenceRequired();
			l.addFreshEntity(sk);
			return 30;
		});
		STEPS.add((srv, l) -> {
			boolean wall = true, archersFree = true;
			for (KingdomSoldier m : sq.memberEntities(l)) {
				if (m.role().ranged()) archersFree &= !m.wallUp;
				else if (m.hasShield()) wall &= m.wallUp;
			}
			check("the sergeant calls the shield wall against archers (LINE squad)", wall && archersFree && !sq.enemies().isEmpty(), sq.enemies().size() + " enemies");
			clean(l);
			return 1;
		});
		// ---- morale ---------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			KingdomConfig.routChanceOnOfficerDeath = 0.0;
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, BlockPos.containing(at(-20, -30)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.STANDARD_BEARER, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS));
			sq = s.get(0).squad();
			n0 = sq.morale();
			s.get(1).kill(l);
			return 2;
		});
		STEPS.add((srv, l) -> {
			// -25 at the death; the squad's 5 s morale regen (+2) may land inside the 2-tick wait
			check("the standard-bearer's death costs the squad 25 morale", sq.morale() >= n0 - 25 && sq.morale() <= n0 - 23, n0 + " -> " + sq.morale());
			// Deterministic: set the morale, kill, and read the result in the same tick (the death hook is synchronous), so the squad's timed morale regen
			// (+2/+5 every 5 s) and the rout timer cannot land between the kill and the check.
			sq.setMorale(22);
			sq.memberEntities(l).get(0).kill(l);
			List<KingdomSoldier> all = new ArrayList<>(sq.memberEntities(l));
			if (sq.leaderEntity(l) instanceof KingdomSoldier ks) all.add(ks);
			long routed = all.stream().filter(KingdomSoldier::isRouted).count();
			check("morale under 20 breaks the squad (all flee), then it regroups at 40 (read in the same tick as the kill)", routed == all.size() && all.size() == 5 && sq.morale() == 40, routed + "/" + all.size() + ", morale " + sq.morale());
			return 1;
		});
		STEPS.add((srv, l) -> {
			KingdomConfig.routChanceOnOfficerDeath = 0.5;
			clean(l);
			return 1;
		});
		// ---- abilities ------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			a = soldier(l, UnitTypes.STANDARD_BEARER, Factions.ALDERMARK, at(0, 20));
			b = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(3, 20));
			b.setNoAi(true);
			c = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(20, 20));
			c.setNoAi(true);
			d = soldier(l, UnitTypes.CAPTAIN, Factions.ALDERMARK, at(0, 34));
			e = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(3, 34));
			e.setNoAi(true);
			Zombie zc = zombie(l, at(0, 39), false);
			zc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
			zc.setHealth(300F);
			d.setTarget(zc);
			KingdomSoldier sur = soldier(l, UnitTypes.SURGEON, Factions.ALDERMARK, at(-20, 20));
			f = soldier(l, UnitTypes.SERGEANT, Factions.ALDERMARK, at(-17, 20));
			f.setNoAi(true);
			f.setHealth(10F);
			KingdomSoldier squ = soldier(l, UnitTypes.SQUIRE, Factions.ALDERMARK, at(-34, 34));
			list.clear();
			list.add(soldier(l, UnitTypes.SERGEANT, Factions.ALDERMARK, at(-31, 34)));
			list.get(0).setNoAi(true);
			list.get(0).setHealth(10F);
			t0 = KingdomSoldier.totalWarCries;
			return 30;
		});
		STEPS.add((srv, l) -> {
			check("standard-bearer: allies within 12 blocks get the +10% aura, farther ones don't", b.standardBuffUntil >= l.getGameTime() && c.standardBuffUntil < l.getGameTime(),
				b.standardBuffUntil - l.getGameTime());
			z1 = zombie(l, at(5, 22), false);
			z2 = zombie(l, at(5, 18), false);
			b.standardBuffUntil = 0;
			b.chargeAttack();
			float h1 = z1.getHealth();
			b.strike(l, z1);
			float d1 = h1 - z1.getHealth();
			b.standardBuffUntil = l.getGameTime() + 20;
			b.chargeAttack();
			float h2 = z2.getHealth();
			b.strike(l, z2);
			float d2 = h2 - z2.getHealth();
			check("the standard's aura adds 10% melee damage", d1 > 0 && Math.abs(d2 / d1 - 1.1F) < 0.03F, d1 + " -> " + d2);
			MobEffectInstance str = e.getEffect(MobEffects.STRENGTH);
			check("captain's war cry: Strength I for allies within 16", KingdomSoldier.totalWarCries > t0 && str != null && str.getAmplifier() == 0, str);
			check("surgeon heals a wounded officer", f.getHealth() > 10F, f.getHealth());
			check("squire patches up a wounded officer", list.get(0).getHealth() > 10F, list.get(0).getHealth());
			clean(l);
			return 1;
		});
		STEPS.add((srv, l) -> {
			t0 = KingdomSoldier.totalHorns;
			hornEvents = 0;
			sq2 = KingdomApi.createVirtualSquad(l, BlockPos.containing(at(30, -30)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
			List<KingdomSoldier> hs = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -30)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.HERALD, UnitTypes.MAN_AT_ARMS));
			a = hs.get(1);
			Zombie zh = zombie(l, at(0, -25), false);
			zh.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
			zh.setHealth(300F);
			return 60;
		});
		STEPS.add((srv, l) -> {
			check("herald blows the horn once when its squad engages", KingdomSoldier.totalHorns == t0 + 1 && a.hornUsed && hornEvents == 1, (KingdomSoldier.totalHorns - t0) + " horns");
			check("the horn calls the nearest virtual squad: it turns real and marches to the herald", !sq2.isVirtual() && sq2.memberEntities(l).size() == 3 && sq2.objective() != null,
				sq2.isVirtual() + " " + sq2.memberEntities(l).size());
			clean(l);
			return 1;
		});
		// ---- items in use ----------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			ItemStack sw = new ItemStack(Items.IRON_SWORD);
			sw.setDamageValue(200);
			int rep = WhetstoneItem.sharpen(sw);
			check("Whetstone restores 15% durability (iron sword: 38 of 250)", rep == 38 && sw.getDamageValue() == 162, rep);
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE), axe = new ItemStack(Items.IRON_AXE), pick = new ItemStack(Items.DIAMOND_PICKAXE);
			chest.setDamageValue(20);
			axe.setDamageValue(20);
			pick.setDamageValue(20);
			check("Whetstone only on damaged swords, axes and tools", !WhetstoneItem.canSharpen(chest) && !WhetstoneItem.canSharpen(new ItemStack(Items.IRON_SWORD))
				&& WhetstoneItem.canSharpen(axe) && WhetstoneItem.canSharpen(pick), null);
			FakePlayer p = fp(l, "kingdom-items");
			p.snapTo(at(0, 0).x, at(0, 0).y, at(0, 0).z, 0, 0);
			p.getInventory().clearContent();
			FletchersBundleItem.unpack(p);
			check("Fletcher's Bundle: 8 arrows + 1 spectral arrow", count(p, Items.ARROW) == 8 && count(p, Items.SPECTRAL_ARROW) == 1, count(p, Items.ARROW) + "+" + count(p, Items.SPECTRAL_ARROW));
			p.removeAllEffects();
			Wolf near = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND), far = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND);
			near.snapTo(at(5, 0).x, at(5, 0).y, at(5, 0).z, 0, 0);
			far.snapTo(at(20, 0).x, at(20, 0).y, at(20, 0).z, 0, 0);
			for (Wolf w : List.of(near, far)) {
				w.setNoAi(true);
				l.addFreshEntity(w);
				w.setTame(true, false);
				w.setOwner(p);
			}
			SergeantsHornItem.sound(p);
			MobEffectInstance st = p.getEffect(MobEffects.STRENGTH);
			check("Sergeant's Horn: Strength I for 8 s to you and your pets within 12", st != null && st.getAmplifier() == 0 && st.getDuration() > 150 && st.getDuration() <= 160
				&& near.hasEffect(MobEffects.STRENGTH) && !far.hasEffect(MobEffects.STRENGTH), st);
			ItemStack horn = new ItemStack(KingdomItems.SERGEANTS_HORN);
			p.setItemInHand(InteractionHand.MAIN_HAND, horn);
			horn.use(l, p, InteractionHand.MAIN_HAND);
			check("Sergeant's Horn: 90 s cooldown", p.getCooldowns().isOnCooldown(horn), null);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KingdomItems.OATHKEEPERS_SEAL));
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setHealth(1F);
			boolean fired = KingdomItems.trySeal(p, l.damageSources().generic());
			MobEffectInstance res = p.getEffect(MobEffects.RESISTANCE);
			check("Oathkeeper's Seal: instead of dying, 3 hearts + Resistance II for 2 s, consumed", fired && p.getHealth() == 6F && res != null && res.getAmplifier() == 1
				&& res.getDuration() <= 40 && p.getMainHandItem().isEmpty(), p.getHealth() + " " + res);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KingdomItems.OATHKEEPERS_SEAL));
			check("the Seal shares the 30 s death-protection lockout", !KingdomItems.trySeal(p, l.damageSources().generic()) && !p.getMainHandItem().isEmpty(), null);
			FakePlayer p2 = fp(l, "kingdom-items2");
			p2.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KingdomItems.OATHKEEPERS_SEAL));
			p2.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			check("a real totem in hand goes first (the Seal is kept)", !KingdomItems.trySeal(p2, l.damageSources().generic()) && !p2.getMainHandItem().isEmpty(), null);
			float k = BodkinArrow.pierceFactor(6F, 20F, 8F), k0 = BodkinArrow.pierceFactor(6F, 0F, 0F);
			check("Bodkin Bolt: treats armor as 25% lower (6 dmg vs full diamond: x" + String.format("%.2f", k) + ")", k > 1.3F && k < 2.0F && k0 == 1F, k);
			z1 = zombie(l, at(10, 40), false);
			z2 = zombie(l, at(16, 40), false);
			for (Zombie z : List.of(z1, z2)) {
				z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			}
			Arrow plain = new Arrow(l, z1.getX(), z1.getY() + 1.0, z1.getZ() - 2.5, new ItemStack(Items.ARROW), null);
			BodkinArrow bod = new BodkinArrow(l, z2.getX(), z2.getY() + 1.0, z2.getZ() - 2.5, new ItemStack(KingdomItems.BODKIN_BOLT), null);
			for (Arrow ar : List.of(plain, bod)) {
				ar.setDeltaMovement(0, 0, 2.5);
				l.addFreshEntity(ar);
			}
			return 6;
		});
		STEPS.add((srv, l) -> {
			float d1 = 20F - z1.getHealth(), d2 = 20F - z2.getHealth();
			check("Bodkin Bolt hits an iron-armored zombie harder than an arrow", d1 > 0 && d2 > d1, "arrow " + d1 + ", bodkin " + d2);
			clean(l);
			return 1;
		});
		// ---- caps, company, composition ---------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			KingdomPerf.forceActive = false;
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(0, 0));
			List<Squad> co = KingdomApi.spawnCompany(l, BlockPos.containing(at(0, 4)), Factions.ALDERMARK);
			int n = KingdomPerf.loaded(l).size();
			boolean capLead = co.size() > 0 && co.get(0).leaderEntity(l) instanceof KingdomSoldier ks && ks.unitId().equals(UnitTypes.CAPTAIN);
			check("captain's company: captain + 2 sergeants + 12 = 15; squad B follows the captain", co.size() == 2 && n == 15 && capLead && co.get(1).commander() != null, n);
			int extra = KingdomApi.spawnPatrol(l, BlockPos.containing(at(6, 6)), Factions.ALDERMARK, 8).size();
			check("15 real soldiers per player: a lone player's area refuses more", extra == 0 && KingdomPerf.loaded(l).size() == 15, extra);
			KingdomPerf.testObservers.add(at(4, 0));
			for (int i = 0; i < 3; i++) KingdomApi.spawnPatrol(l, BlockPos.containing(at(-6 + i * 4, -6)), Factions.ALDERMARK, 8);
			int two = KingdomPerf.loaded(l).size();
			check("two players there: up to 30", two == 30, two);
			KingdomPerf.testObservers.clear();
			KingdomPerf.forceActive = true;
			int beyond = KingdomApi.spawnPatrol(l, BlockPos.containing(at(0, 10)), Factions.ALDERMARK, 8).size();
			check("30 per area: no more soldiers in the same 48-block area", beyond == 0, beyond);
			RandomSource r = RandomSource.create(42);
			int mounted = 0;
			for (int i = 0; i < 10000; i++) if (ArmyComposer.isMounted(ArmyComposer.roll(r, true))) mounted++;
			check("army composition: cavalry about 10%", mounted > 850 && mounted < 1150, mounted / 100.0 + "%");
			clean(l);
			return 1;
		});
		// ---- LOD: virtual squads ---------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			KingdomPerf.forceActive = false;
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(-60, -60));
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, BlockPos.containing(at(50, 50)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER));
			sq = s.get(0).squad();
			s.get(1).setHealth(7F);
			id0 = s.get(1).getUUID();
			id1 = s.get(3).getUUID();
			s.get(3).kill(l);
			return KingdomLod.VIRTUALIZE_DELAY + 30;
		});
		STEPS.add((srv, l) -> {
			check("a squad out of every player's range turns virtual (entities removed, kept as data)", sq.isVirtual() && l.getEntity(id0) == null && sq.virtualMembers().size() == 3,
				sq.isVirtual() + ", " + sq.virtualMembers().size() + " stored");
			float hp = sq.virtualMembers().stream().filter(v -> v.uuid().equals(id0)).map(Squad.VirtualSoldier::health).findFirst().orElse(-1F);
			check("the virtual record keeps saved health (no free healing)", hp == 7F, hp);
			sq.setObjective(BlockPos.containing(at(30, 50)));
			v0 = sq.anchor();
			return 45;
		});
		STEPS.add((srv, l) -> {
			check("a virtual squad marches as a point", sq.anchor() != null && v0 != null && sq.anchor().distanceTo(v0) > 2.0, sq.anchor());
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(20, 50));
			return 30;
		});
		STEPS.add((srv, l) -> {
			Entity v = l.getEntity(id0);
			check("a player comes near: real again with the same UUIDs and saved health; the dead stay dead", !sq.isVirtual() && v instanceof KingdomSoldier ks && Math.abs(ks.getHealth() - 7F) < 0.01F
				&& l.getEntity(id1) == null && sq.size() == 3, v == null ? "missing" : ((KingdomSoldier) v).getHealth());
			sq2 = KingdomApi.createVirtualSquad(l, BlockPos.containing(at(10, 50)), Factions.ALDERMARK, Squad.Formation.COLUMN, UnitTypes.SERGEANT, List.of(UnitTypes.PIKEMAN, UnitTypes.SQUIRE));
			return 30;
		});
		STEPS.add((srv, l) -> {
			check("createVirtualSquad: a data-only squad spawns when a player comes near", !sq2.isVirtual() && sq2.memberEntities(l).size() == 2 && sq2.leaderEntity(l) != null, sq2.isVirtual());
			sq2.dismiss();
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(at(-60, -60));
			return KingdomLod.VIRTUALIZE_DELAY + 30;
		});
		STEPS.add((srv, l) -> {
			check("a dismissed squad (finished hunt) is removed out of sight, not stored", KingdomState.get(l).squad(sq2.id()) == null && sq2.memberEntities(l).isEmpty(), null);
			KingdomPerf.testObservers.clear();
			KingdomPerf.forceActive = true;
			clean(l);
			return 1;
		});
		// ---- aggression and hunts ----------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			FakePlayer p = fp(l, "kingdom-aggr");
			p.snapTo(at(0, 0).x, at(0, 0).y, at(0, 0).z, 0, 0);
			KingdomAggression.set(l, p.getUUID(), 0F);
			KingdomSoldier sg = soldier(l, UnitTypes.SERGEANT, Factions.ALDERMARK, at(0, 10));
			sg.setNoAi(true);
			sg.hurtServer(l, l.damageSources().playerAttack(p), 1000F);
			check("killing a sergeant adds 2 aggression to the killer", Math.abs(KingdomAggression.get(l, p.getUUID()) - 2F) < 0.01F, KingdomAggression.get(l, p.getUUID()));
			KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, false);
			KingdomSoldier w = soldier(l, UnitTypes.MAN_AT_ARMS, Factions.ALDERMARK, at(5, 10));
			w.setNoAi(true);
			boolean before = KingdomTargeting.canAttack(w, p);
			KingdomAggression.set(l, p.getUUID(), 4F);
			boolean after = KingdomTargeting.canAttack(w, p);
			check("aggression 4+: a neutral Aldermark attacks the player on sight (below: not)", !before && after, null);
			KingdomAggression.set(l, p.getUUID(), 8F);
			sq = KingdomAggression.startHunt(l, p, 8F);
			Mob hl = sq == null ? null : sq.leaderEntity(l);
			double dist = hl == null ? -1 : Math.sqrt(hl.distanceToSqr(p));
			check("aggression 6+: a hunting squad sets out 40-56 blocks away and marches on the player", sq != null && p.getUUID().equals(sq.huntTarget()) && dist > 35 && dist < 62
				&& sq.objective() != null && sq.objective().closerToCenterThan(p.position(), 3), String.format("%.0f blocks, %d soldiers", dist, sq == null ? 0 : sq.size()));
			check("hunters fight the hunted player even while the faction is neutral", sq != null && KingdomTargeting.canAttack(Factions.ALDERMARK, sq, null, p), null);
			check("aggression decays and is capped (mild)", KingdomAggression.MAX <= 20F && KingdomAggression.DECAY_PER_DAY >= 1F && KingdomAggression.HUNT_COOLDOWN >= 36000, null);
			KingdomApi.setFactionHostileToPlayers(srv, Factions.ALDERMARK, true);
			KingdomAggression.set(l, p.getUUID(), 0F);
			clean(l);
			return 1;
		});
		// ---- daily routine ----------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			cmd(srv, "time set 18000");
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, BlockPos.containing(at(-30, 0)), Factions.ALDERMARK, Squad.Formation.LINE, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS));
			sq = s.get(0).squad();
			sq.setRoutine(Squad.Routine.GUARD, BlockPos.containing(at(-30, 0)), List.of());
			return 50;
		});
		STEPS.add((srv, l) -> {
			long resting = sq.memberEntities(l).stream().filter(KingdomSoldier::isResting).count();
			check("night: half the guard squad rests at home, the rest keep watch", resting == 2, resting + "/" + sq.memberEntities(l).size() + " at clock " + l.getOverworldClockTime() % 24000);
			z1 = zombie(l, at(-30, 8), false);
			for (KingdomSoldier m : sq.memberEntities(l)) if (m.isResting()) {
				m.hurtServer(l, l.damageSources().mobAttack(z1), 1F);
				break;
			}
			return 2;
		});
		STEPS.add((srv, l) -> {
			check("an attack wakes the whole squad", sq.memberEntities(l).stream().noneMatch(KingdomSoldier::isResting), null);
			z1.discard();
			cmd(srv, "time set 1000");
			sq.setRoutine(Squad.Routine.PATROL, BlockPos.containing(at(-30, 0)), List.of(BlockPos.containing(at(-30, 24)), BlockPos.containing(at(-10, 24))));
			return 30;
		});
		STEPS.add((srv, l) -> {
			BlockPos o = sq.objective();
			check("day: the patrol walks its waypoints", o != null && (o.equals(BlockPos.containing(at(-30, 24))) || o.equals(BlockPos.containing(at(-10, 24)))), o);
			clean(l);
			return 1;
		});
	}
}
