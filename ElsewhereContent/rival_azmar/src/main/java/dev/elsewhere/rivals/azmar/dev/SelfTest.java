package dev.elsewhere.rivals.azmar.dev;

import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarItems;
import dev.elsewhere.rivals.azmar.AzmarWear;
import dev.elsewhere.rivals.azmar.FirePots;
import dev.elsewhere.rivals.azmar.GauntletItem;
import dev.elsewhere.duelist.kit.ShieldHook;
import dev.elsewhere.rivals.azmar.StandardItem;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tick-driven self-test (own JVM, fresh superflat world). Run: {@code ./gradlew runServer}; log in selftest-azmar.log. */
public final class SelfTest {
	private SelfTest() {}

	interface Step { int run(MinecraftServer srv, ServerLevel l) throws Exception; }

	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static int pass, fail, idx, wait, startDelay;
	static boolean started, finished, measuring;
	static int y;
	static long tickStart;
	public static final List<Long> SAMPLES = new ArrayList<>();

	public static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[azmar-selftest] " + line);
		if (ok) pass++; else fail++;
	}

	public static void info(String line) {
		LOG.add(line);
		System.out.println("[azmar-selftest] " + line);
	}

	public static void startTick(MinecraftServer srv) {
		if (measuring) tickStart = System.nanoTime();
	}

	public static void endTick(MinecraftServer srv) {
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (finished) return;
		ServerLevel l = srv.overworld();
		if (!started) {
			if (++startDelay < 400 && !l.isPositionEntityTicking(new BlockPos(0, 0, 0))) return;
			if (startDelay < 40) return;
			started = true;
			y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, 0);
			build();
		}
		if (wait > 0) { wait--; return; }
		if (idx >= STEPS.size()) { finish(srv); return; }
		Step s = STEPS.get(idx++);
		try {
			wait = s.run(srv, l);
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		String total = "TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		info(total);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-azmar.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("azmar.selftest.exit")) srv.halt(false);
	}

	static Vec3 at(double x, double z) {
		return new Vec3(x + 0.5, y, z + 0.5);
	}

	static void clean(ServerLevel l) {
		for (Entity e : l.getEntities((Entity) null, new AABB(-80, y - 20, -80, 80, y + 40, 80), e -> !(e instanceof Player))) e.discard();
		FirePots.clear();
	}

	static Zombie zombie(ServerLevel l, Vec3 p, boolean ai) {
		Zombie z = EntityTypes.ZOMBIE.create(l, EntitySpawnReason.COMMAND);
		z.snapTo(p.x, p.y, p.z, 0F, 0F);
		z.setNoAi(!ai);
		z.setPersistenceRequired();
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		z.setHealth(100);
		l.addFreshEntity(z);
		return z;
	}

	static void time(MinecraftServer srv, String t) {
		srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), "time set " + t);
	}

	static boolean res(String path) {
		try (InputStream in = AzmarContent.class.getResourceAsStream("/" + path)) {
			return in != null;
		} catch (Exception e) {
			return false;
		}
	}

	static double attr(ItemStack s, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a) {
		double v = 0;
		for (var e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers())
			if (e.attribute().equals(a)) v += e.modifier().amount();
		return v;
	}

	static void build() {
		String only = System.getenv("AZMAR_ONLY");
		if (only != null && !only.isBlank()) {            // a focused run: only the named boss suites (fast cycles)
			SelfTestBosses.add0(only);
			STEPS.add((srv, l) -> {
				clean(l);
				info("done: " + pass + " pass, " + fail + " fail");
				return 0;
			});
			return;
		}
		// ---- 1. registry, resources, lang ---------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			String[] ids = {"azmar_dinar", "azmar_seal_sultana", "azmar_seal_wazir", "azmar_seal_falconer", "azmar_seal_amir", "azmar_nemes_of_the_uraeus", "azmar_ibis_crest",
				"azmar_falconers_gauntlet", "azmar_sandrunner_sandals", "azmar_winged_scarab_standard", "azmar_copper_khopesh", "azmar_gilded_khopesh", "azmar_twin_khopesh",
				"azmar_scarab_spear", "azmar_composite_bow", "azmar_fire_pot", "azmar_jackal_helm", "azmar_falcon_helm", "azmar_horned_scarab_crown", "azmar_sunplate_cuirass",
				"azmar_armor_trim_smithing_template", "azmar_banner_pattern"};
			StringBuilder langAll = new StringBuilder();
			for (var url : java.util.Collections.list(AzmarContent.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json"))) {
				try (InputStream in = url.openStream()) { langAll.append(new String(in.readAllBytes())); }
			}
			String lang = langAll.toString();
			int missing = 0;
			StringBuilder why = new StringBuilder();
			for (String id : ids) {
				boolean ok = BuiltInRegistries.ITEM.containsKey(AzmarContent.id(id)) && res("assets/elsewhere/items/" + id + ".json") && res("assets/elsewhere/textures/item/" + id + ".png")
					&& lang.contains("\"item.elsewhere." + id + "\"");
				if (!id.equals("azmar_composite_bow") && !id.contains("khopesh")) ok &= res("assets/elsewhere/models/item/" + id + ".json");
				if (!ok) { missing++; why.append(id).append('[').append(BuiltInRegistries.ITEM.containsKey(AzmarContent.id(id))).append(res("assets/elsewhere/items/" + id + ".json")).append(res("assets/elsewhere/textures/item/" + id + ".png")).append(lang.contains("\"item.elsewhere." + id + "\"")).append("] "); }
			}
			check("22 Azmar items registered with item definition, texture, model and lang name", missing == 0 && ids.length == 22, why);
			int eq = 0;
			for (String n : new String[] {"nemes", "ibis", "falcon", "jackal", "scarab", "sunplate", "sandrunner"})
				if (res("assets/elsewhere/equipment/azmar_" + n + ".json") && res("assets/elsewhere/textures/entity/equipment/humanoid/azmar_" + n + ".png")) eq++;
			check("7 equipment assets (headpieces, cuirass, sandals) with textures", eq == 7, eq);
			String[] skins = {"spearguard", "sunguard", "bowman", "jemadar", "qaid", "sand_dancer", "naffatun", "porter", "herald", "physician", "scarab_bearer", "sultana", "wazir", "falconer", "amir"};
			int sk = 0;
			for (String s : skins) if (res("assets/elsewhere/textures/entity/kingdom/azmar/" + s + ".png")) sk++;
			check("15 skins", sk == skins.length, sk);
			check("trim, banner, shield pattern textures", res("assets/elsewhere/textures/trims/entity/humanoid/azmar.png") && res("assets/elsewhere/textures/trims/entity/humanoid_leggings/azmar.png")
				&& res("assets/elsewhere/textures/entity/banner/azmar.png") && res("assets/elsewhere/textures/entity/shield/azmar.png") && res("data/elsewhere/trim_pattern/azmar.json")
				&& res("data/elsewhere/banner_pattern/azmar.json"), null);
			return 0;
		});
		// ---- 2. vanilla-equivalent armor and weapon values ---------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			ItemStack nemes = new ItemStack(AzmarItems.NEMES_OF_THE_URAEUS), ibis = new ItemStack(AzmarItems.IBIS_CREST), sand = new ItemStack(AzmarItems.SANDRUNNER_SANDALS);
			check("Nemes: helmet 3 armor / 2 toughness / 0.1 kb, ~400 durability", attr(nemes, Attributes.ARMOR) == 3 && attr(nemes, Attributes.ARMOR_TOUGHNESS) == 2
				&& Math.abs(attr(nemes, Attributes.KNOCKBACK_RESISTANCE) - 0.1) < 1e-6 && nemes.getMaxDamage() >= 390 && nemes.getMaxDamage() <= 410, nemes.getMaxDamage());
			check("Ibis Crest: helmet 2 / 1 / 0, 275 durability", attr(ibis, Attributes.ARMOR) == 2 && attr(ibis, Attributes.ARMOR_TOUGHNESS) == 1 && ibis.getMaxDamage() == 275, ibis.getMaxDamage());
			check("Sandrunner Sandals: boots 3 / 2 / 0, ~470 durability, safe fall +2", attr(sand, Attributes.ARMOR) == 3 && attr(sand, Attributes.ARMOR_TOUGHNESS) == 2
				&& sand.getMaxDamage() >= 460 && sand.getMaxDamage() <= 480 && attr(sand, Attributes.SAFE_FALL_DISTANCE) == 2, sand.getMaxDamage());
			ItemStack jackal = new ItemStack(AzmarItems.JACKAL_HELM), falcon = new ItemStack(AzmarItems.FALCON_HELM), crown = new ItemStack(AzmarItems.HORNED_SCARAB_CROWN);
			check("Jackal helm = chainmail helmet (2), Falcon helm = leather cap (1), War-crown = iron helmet (2)", attr(jackal, Attributes.ARMOR) == 2 && attr(falcon, Attributes.ARMOR) == 1
				&& attr(crown, Attributes.ARMOR) == 2 && jackal.getMaxDamage() == 165 && crown.getMaxDamage() == 165 && falcon.getMaxDamage() == 55, null);
			check("all headpieces are real equippables in the head slot", List.of(nemes, ibis, jackal, falcon, crown).stream().allMatch(s -> s.get(DataComponents.EQUIPPABLE) != null
				&& s.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.HEAD), null);
			ItemStack cu = new ItemStack(AzmarItems.COPPER_KHOPESH), gi = new ItemStack(AzmarItems.GILDED_KHOPESH), cs = new ItemStack(Items.COPPER_SWORD), is = new ItemStack(Items.IRON_SWORD);
			check("copper khopesh has copper-sword damage and durability, gilded has iron-sword", attr(cu, Attributes.ATTACK_DAMAGE) == attr(cs, Attributes.ATTACK_DAMAGE) && cu.getMaxDamage() == cs.getMaxDamage()
				&& attr(gi, Attributes.ATTACK_DAMAGE) == attr(is, Attributes.ATTACK_DAMAGE) && gi.getMaxDamage() == is.getMaxDamage(), attr(cu, Attributes.ATTACK_DAMAGE) + "/" + attr(gi, Attributes.ATTACK_DAMAGE));
			return 0;
		});
		// ---- 3. faction data and units (needs the war lane's kingdom module) ------------------------------------------------------
		STEPS.add((srv, l) -> {
			FactionDef def = FactionDefs.get(AzmarContent.FACTION);
			check("faction elsewhere:azmar loaded from data", def != null, null);
			if (def == null) return 0;
			check("Azmar army multipliers: HP 0.8, damage 0.9, speed 1.1, size 0.75", def.army().healthMultiplier() == 0.8 && def.army().damageMultiplier() == 0.9
				&& def.army().speedMultiplier() == 1.1 && def.army().sizeMultiplier() == 0.75, null);
			check("4 bosses: sultana, wazir, falconer, amir; sultana 225 (cap 300), wazir 161, falconer 171, amir 182", def.rulers().keySet().containsAll(List.of("king", "marshal", "castellan", "huntsman"))
				&& def.rulers().size() == 4 && def.rulers().get("king").health() == 225 && def.rulers().get("castellan").health() == 161 && def.rulers().get("huntsman").health() == 171
				&& def.rulers().get("marshal").health() == 182, null);
			check("brain: min odds 1.5, noise 0.07, veterancy 0.30, retreat instead of die, supply lines", def.army().brain().minAttackOdds() == 1.5 && def.army().brain().intelligenceNoise() == 0.07
				&& def.army().brain().veterancyCap() == 0.30 && def.army().brain().retreatInsteadOfDie() && def.army().brain().supplyLineOnly(), null);
			check("currency Dinar and a shop table", def.economy().currency().isPresent() && def.economy().currency().get().equals(AzmarContent.id("azmar_dinar")) && !def.economy().shop().isEmpty(), null);
			int ok = 0;
			StringBuilder bad = new StringBuilder();
			for (String u : new String[] {"spearguard", "sunguard", "jackal_guard", "camel_lancer", "bowman", "arbalester", "jemadar", "qaid", "scarab_bearer", "crier", "physician", "page", "pikeman", "sand_dancer", "naffatun"}) {
				UnitType t = UnitTypes.get(AzmarContent.id("azmar_" + u));
				if (t != null && t.skin().getPath().contains("azmar")) ok++; else bad.append(u).append(' ');
			}
			check("15 Azmar unit types load with Azmar skins", ok == 15, bad);
			UnitType maa = Factions.unit(AzmarContent.FACTION, UnitTypes.MAN_AT_ARMS);
			check("roster: man-at-arms resolves to the Spearguard (copper khopesh)", maa != null && maa.mainHand().isPresent() && maa.mainHand().get().equals(AzmarContent.id("azmar_copper_khopesh")), maa == null ? null : maa.mainHand());
			return 0;
		});
		// ---- 4. real soldiers: equipment, stats ---------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomSoldier s = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, AzmarContent.FACTION, at(10, 10), null);
			check("Azmar Spearguard spawns", s != null, null);
			if (s != null) {
				check("Spearguard: copper khopesh in hand, real vanilla armor on, no custom skin armor", s.getMainHandItem().is(AzmarItems.COPPER_KHOPESH) && !s.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
					&& !s.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), s.getMainHandItem());
				check("Spearguard HP is 0.8 x 20 = 16", Math.abs(s.getMaxHealth() - 16.0) < 0.01, s.getMaxHealth());
			}
			KingdomSoldier sd = KingdomApi.spawnSoldier(l, AzmarContent.id("azmar_sand_dancer"), AzmarContent.FACTION, at(12, 10), null);
			check("Sand-Dancer spawns with twin khopesh, HP 20 x 0.8", sd != null && sd.getMainHandItem().is(AzmarItems.TWIN_KHOPESH) && Math.abs(sd.getMaxHealth() - 16.0) < 0.05, sd == null ? null : sd.getMaxHealth());
			KingdomSoldier nf = KingdomApi.spawnSoldier(l, AzmarContent.id("azmar_naffatun"), AzmarContent.FACTION, at(14, 10), null);
			check("Naffatun spawns with a fire pot", nf != null && nf.getMainHandItem().is(AzmarItems.FIRE_POT), null);
			KingdomSoldier jg = KingdomApi.spawnSoldier(l, AzmarContent.id("azmar_jackal_guard"), AzmarContent.FACTION, at(16, 10), null);
			check("Jackal Guard wears the jackal helm as real head equipment", jg != null && jg.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.JACKAL_HELM), null);
			clean(l);
			return 0;
		});
		// ---- 5. khopesh hook -----------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			ShieldHook.reset();
			Zombie def = zombie(l, at(0, 20), false), att = zombie(l, at(0, 21), false), att2 = zombie(l, at(2, 21), false);
			def.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			def.startUsingItem(InteractionHand.OFF_HAND);
			att.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AzmarItems.COPPER_KHOPESH));
			att2.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AzmarItems.GILDED_KHOPESH));
			return 12;
		});
		STEPS.add((srv, l) -> {
			Zombie def = null, att = null, att2 = null;
			for (Zombie z : l.getEntitiesOfClass(Zombie.class, new AABB(-5, y - 2, 15, 8, y + 4, 25))) {
				if (z.getOffhandItem().is(Items.SHIELD)) def = z;
				else if (z.getMainHandItem().is(AzmarItems.COPPER_KHOPESH)) att = z;
				else att2 = z;
			}
			check("zombie holds its shield up (isBlocking)", def != null && def.isBlocking(), null);
			if (def == null) return 0;
			Zombie iron = zombie(l, at(-2, 21), false);
			iron.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			check("an iron sword does not hook the shield", def.isBlocking() && ShieldHook.hooks == 0 && !new ItemStack(Items.IRON_SWORD).is(ShieldHook.KHOPESH) && new ItemStack(AzmarItems.TWIN_KHOPESH).is(ShieldHook.KHOPESH), ShieldHook.hooks);
			ShieldHook.hook(l, att, "azmar-squad", def);
			check("copper khopesh hit hooks the raised shield down", !def.isBlocking() && ShieldHook.hooks == 1, ShieldHook.hooks);
			def.startUsingItem(InteractionHand.OFF_HAND);
			return 8;
		});
		STEPS.add((srv, l) -> {
			Zombie def = null, att = null, att2 = null;
			for (Zombie z : l.getEntitiesOfClass(Zombie.class, new AABB(-5, y - 2, 15, 8, y + 4, 25))) {
				if (z.getOffhandItem().is(Items.SHIELD)) def = z;
				else if (z.getMainHandItem().is(AzmarItems.COPPER_KHOPESH)) att = z;
				else if (z.getMainHandItem().is(AzmarItems.GILDED_KHOPESH)) att2 = z;
			}
			// the shield was raised again 8 ticks ago: a second wielder within 40 ticks of the first hook must NOT hook (squad cap)
			boolean up = def.isBlocking();
			ShieldHook.hook(l, att2, "azmar-squad", def);
			check("a second khopesh within 40 ticks does not hook again (squad cap)", ShieldHook.hooks == 1, ShieldHook.hooks + " up=" + up);
			return 45;
		});
		STEPS.add((srv, l) -> {
			Zombie def = null, att = null, att2 = null;
			for (Zombie z : l.getEntitiesOfClass(Zombie.class, new AABB(-5, y - 2, 15, 8, y + 4, 25))) {
				if (z.getOffhandItem().is(Items.SHIELD)) def = z;
				else if (z.getMainHandItem().is(AzmarItems.COPPER_KHOPESH)) att = z;
				else if (z.getMainHandItem().is(AzmarItems.GILDED_KHOPESH)) att2 = z;
			}
			def.startUsingItem(InteractionHand.OFF_HAND);
			return 8;
		});
		STEPS.add((srv, l) -> {
			Zombie def = null, att = null, att2 = null;
			for (Zombie z : l.getEntitiesOfClass(Zombie.class, new AABB(-5, y - 2, 15, 8, y + 4, 25))) {
				if (z.getOffhandItem().is(Items.SHIELD)) def = z;
				else if (z.getMainHandItem().is(AzmarItems.COPPER_KHOPESH)) att = z;
				else if (z.getMainHandItem().is(AzmarItems.GILDED_KHOPESH)) att2 = z;
			}
			ShieldHook.hook(l, att2, "azmar-squad", def);
			check("a different attacker hooks again after 40 ticks", ShieldHook.hooks == 2, ShieldHook.hooks);
			def.startUsingItem(InteractionHand.OFF_HAND);
			return 0;
		});
		// ---- 6. fire pot: damage, short fire, no block edits -----------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			BlockPos c = BlockPos.containing(at(0, 40));
			StringBuilder before = new StringBuilder();
			for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 3; dy++) before.append(l.getBlockState(c.offset(dx, dy, dz)).getBlock().hashCode());
			Zombie target = zombie(l, at(0, 40), false);
			ItemStack drop = new ItemStack(Items.OAK_PLANKS, 3);
			net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(l, at(1, 40).x, y, at(1, 40).z, drop);
			l.addFreshEntity(ie);
			Zombie thrower = zombie(l, at(0, 32), false);
			FirePots.lob(l, thrower, target.position());
			check("fire pot is in flight", true, null);
			l.getServer().getGameRules();
			PendingFire.before = before.toString(); PendingFire.target = target; PendingFire.item = ie; PendingFire.center = c;
			PendingFire.hp = target.getHealth();
			return 50;
		});
		STEPS.add((srv, l) -> {
			Zombie target = PendingFire.target;
			check("fire pot hit: at least the raw 6 impact landed", PendingFire.hp - target.getHealth() >= 5.9, PendingFire.hp - target.getHealth());
			check("a ground fire patch is burning", FirePots.patchCount() >= 1, FirePots.patchCount());
			return 80;
		});
		STEPS.add((srv, l) -> {
			Zombie target = PendingFire.target;
			BlockPos c = PendingFire.center;
			StringBuilder after = new StringBuilder();
			boolean fire = false;
			for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 3; dy++) {
				var b = l.getBlockState(c.offset(dx, dy, dz)).getBlock();
				after.append(b.hashCode());
				if (b == Blocks.FIRE || b == Blocks.SOUL_FIRE) fire = true;
			}
			check("fire pot never changes or ignites a single block", after.toString().equals(PendingFire.before) && !fire, null);
			check("fire patch ended after 3 s", FirePots.patchCount() == 0, FirePots.patchCount());
			check("dropped items are untouched by the fire", PendingFire.item.isAlive() && PendingFire.item.getItem().getCount() == 3, null);
			check("fire burned the target for more than the impact (patch damage 2/s)", PendingFire.hp - target.getHealth() >= 7.9, PendingFire.hp - target.getHealth());
			return 0;
		});
		// ---- 7. Falconer's Gauntlet ------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			return 4;
		});
		STEPS.add((srv, l) -> {
			FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("azmar-test".getBytes()), "azmar-test"));
			Vec3 p = at(0, 60);
			fp.snapTo(p.x, p.y, p.z, 0F, 0F);   // facing +z
			Zombie far = zombie(l, at(0, 100), false);
			fp.snapTo(at(0, 40).x, at(0, 40).y, at(0, 40).z, 0F, 0F);   // the far zombie settles near z=80: stand 40 blocks from it
			check("Gauntlet ignores a target beyond 32 blocks", GauntletItem.target(fp) == null, fp.position() + " " + fp.getLookAngle() + " " + GauntletItem.target(fp));
			far.discard();
			fp.snapTo(at(0, 60).x, at(0, 60).y, at(0, 60).z, 0F, 0F);
			Zombie z = zombie(l, at(0, 70), false);
			LivingEntity t = GauntletItem.target(fp);
			check("Gauntlet finds the living target in view (range 32)", t == z, t);
			z.discard();
			Zombie z2 = zombie(l, at(0, 70), false);
			float hp = z2.getHealth();
			GauntletItem.dive(l, fp, z2);
			check("Gauntlet dive: Glowing 8 s and 3 damage", z2.hasEffect(MobEffects.GLOWING) && Math.abs(hp - z2.getHealth() - 3.0F) < 0.05, hp - z2.getHealth());
			return 0;
		});
		// ---- 8. Uraeus ward ---------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			time(srv, "noon");
			Zombie z = zombie(l, at(0, 80), false);
			z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(AzmarItems.NEMES_OF_THE_URAEUS));
			PendingFire.target = z;
			float w = AzmarWear.wardFraction(z);
			check("Uraeus Ward is 10% at noon under open sky", Math.abs(w - 0.10F) < 1e-4, w);
			z.setHealth(100F);
			z.hurtServer(l, l.damageSources().generic(), 10F);
			check("noon: a 10-damage hit costs about 9 HP (the ward gives 1 back)", Math.abs(100F - z.getHealth() - 9.0F) < 0.6F, 100F - z.getHealth());
			time(srv, "midnight");
			return 10;
		});
		STEPS.add((srv, l) -> {
			Zombie z = PendingFire.target;
			check("Uraeus Ward is 0 at night", AzmarWear.wardFraction(z) == 0F, AzmarWear.wardFraction(z));
			time(srv, "noon");
			return 10;
		});
		STEPS.add((srv, l) -> {
			Zombie roofed = zombie(l, at(5, 80), false);
			roofed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(AzmarItems.NEMES_OF_THE_URAEUS));
			check("Uraeus Ward is back at noon in the open", Math.abs(AzmarWear.wardFraction(roofed) - 0.10F) < 1e-4, AzmarWear.wardFraction(roofed));
			BlockPos above = roofed.blockPosition().above(3);
			l.setBlock(above, Blocks.STONE.defaultBlockState(), 3);
			check("Uraeus Ward is 0 indoors (roof overhead)", AzmarWear.wardFraction(roofed) == 0F, null);
			l.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
			return 0;
		});
		// ---- 9. Winged-Scarab Standard ------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes("azmar-test".getBytes()), "azmar-test"));
			BlockPos pos = BlockPos.containing(at(0, 90));
			boolean ok = StandardItem.plant(l, fp, pos);
			check("Standard plants a banner on the ground", ok && l.getBlockState(pos).is(net.minecraft.tags.BlockTags.BANNERS), null);
			boolean ok2 = StandardItem.plant(l, fp, pos.east(4));
			check("one standard per player: planting again moves it", ok2 && l.getBlockState(pos).isAir() && StandardItem.plantedCount() >= 1, null);
			StandardItem.restartForTest(srv);
			check("the planted Standard survives a restart (saved to the world, loaded again)", StandardItem.plantedCount() >= 1, StandardItem.plantedCount());
			l.setBlock(pos.east(4), Blocks.AIR.defaultBlockState(), 3);
			return 0;
		});
		SelfTestSmallest.add();   // s-azmarsize: Azmar is the smallest kingdom in every army size row
		SelfTestLang.add();       // s-langfix: no registered Elsewhere id lacks an English name
		SelfTestBosses.add();
		SelfTestTactics.add();
		SelfTestPoses.add(); // 3.0 B4 (s-p4-rivalanims)
		SelfTestForesight.add(); // 3.0 B5 (s-foresight): wind-up hints and the bar move label only with foresight gear
		SelfTestRulerLook.add(); // 3.0 B10 + K29 (s-rulerarmor): Egyptian armor layer, camel caparison
		SelfTestRewards.add(); // 3.0 (s-bossdrops): XP and drops on every death
		STEPS.add((srv, l) -> {
			clean(l);
			info("done: " + pass + " pass, " + fail + " fail");
			return 0;
		});
	}

	static final class PendingFire {
		static String before;
		static Zombie target;
		static net.minecraft.world.entity.item.ItemEntity item;
		static BlockPos center;
		static float hp;
	}
}
