package dev.elsewhere.gear2.dev;

import com.mojang.serialization.JsonOps;
import dev.elsewhere.gear2.Capacitor;
import dev.elsewhere.gear2.Gear2Content;
import dev.elsewhere.gear2.Gear2Hooks;
import dev.elsewhere.gear2.Gear2Items;
import dev.elsewhere.gear2.Gear2Loot;
import dev.elsewhere.gear2.Gear2Stats;
import dev.elsewhere.gear2.SeersHourglass;
import dev.elsewhere.gear2.Termites;
import dev.elsewhere.gear2.item.MirrorboltArbalest;
import dev.elsewhere.gear2.item.ProspectorsPendulum;
import dev.elsewhere.gear2.item.SteamAuger;
import dev.elsewhere.gear2.item.VoidTetherSpool;
import dev.elsewhere.gear2.mixin.Gear2ArrowAccess;
import dev.elsewhere.gear2.mixin.Gear2EquipmentInvoker;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gear2 server self-test (-Dgear2.selftest=true): one block of checks per item; logs PASS/FAIL, writes selftest-gear2.log. */
public final class SelfTest {
	private SelfTest() {}

	static final List<String> LOG = new ArrayList<>();
	static int pass, fail;
	static final List<BlockPos> PLACED = new ArrayList<>();
	static final List<Mob> MOBS = new ArrayList<>();

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[gear2-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void check(String name, boolean ok) {
		check(name, ok, null);
	}

	static void checkS(String name, BooleanSupplier s) {
		try {
			check(name, s.getAsBoolean(), null);
		} catch (Throwable t) {
			check(name, false, t);
		}
	}

	static void info(String s) {
		LOG.add("INFO " + s);
		System.out.println("[gear2-selftest] INFO " + s);
	}

	static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	static boolean has(String id) {
		return BuiltInRegistries.ITEM.containsKey(Identifier.parse(id));
	}

	static TagKey<Item> itag(String id) {
		return TagKey.create(Registries.ITEM, Identifier.parse(id));
	}

	static boolean recipe(MinecraftServer s, String path) {
		return s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Gear2Content.id(path))).isPresent();
	}

	/** The table exists and (after the modify event) names the item. */
	static boolean loot(MinecraftServer s, String table, Item item) {
		LootTable t = s.reloadableRegistries().getLootTable(Gear2Loot.table(table));
		if (t == LootTable.EMPTY) return false;
		String json = LootTable.DIRECT_CODEC.encodeStart(s.registryAccess().createSerializationContext(JsonOps.INSTANCE), t).getOrThrow().toString();
		return json.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	static boolean asset(String path) {
		return Gear2Content.class.getResource("/assets/elsewhere/" + path) != null;
	}

	static void equip(FakePlayer p, EquipmentSlot s, ItemStack st) {
		p.setItemSlot(s, st);
		((Gear2EquipmentInvoker) p).gear2$detectEquipmentUpdates();
	}

	static void clearPlayer(FakePlayer p) {
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) p.setItemSlot(s, ItemStack.EMPTY);
		p.getInventory().clearContent();
		((Gear2EquipmentInvoker) p).gear2$detectEquipmentUpdates();
		p.setLastClientInput(Input.EMPTY);
		p.setKnownMovement(Vec3.ZERO);
		p.setSprinting(false);
		p.setShiftKeyDown(false);
	}

	static Mob mob(ServerLevel l, Vec3 at) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		MOBS.add(m);
		return m;
	}

	static void set(ServerLevel l, BlockPos p, BlockState s) {
		l.setBlock(p, s, 3);
		PLACED.add(p.immutable());
	}

	static boolean enchantable(ItemStack st, ServerLevel l, ResourceKey<Enchantment> e) {
		var h = l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(e);
		return st.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE);
	}

	static boolean repairs(Item tool, Item material) {
		var r = new ItemStack(tool).get(DataComponents.REPAIRABLE);
		return r != null && r.isValidRepairItem(new ItemStack(material));
	}

	// ============================================================================================================

	/** Merge entry (modules.json post_init, behind -Dgear2.selftest=true): forces the arena chunks, waits until they tick, runs. */
	public static void register() {
		int[] ticks = {0};
		boolean[] ran = {false};
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = 1; cx <= 4; cx++) for (int cz = 1; cz <= 4; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (ran[0]) return;
			if (++ticks[0] < 400 && !srv.overworld().isPositionEntityTicking(new BlockPos(40, 100, 40))) return;
			ran[0] = true;
			start(srv);
		});
	}

	public static void start(MinecraftServer server) {
		ServerLevel l = server.overworld();
		BlockPos base = new BlockPos(40, Math.min(l.getMaxY() - 30, 200), 40);
		FakePlayer p = FakePlayer.get(l);
		int[] wait = {-1};
		try {
			p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
			body(server, l, p, base);
			SelfTestTooltips.run(l, p); // s-p5-tooltips: tooltip numbers = item stats and the real hooks
			wait[0] = 0;
		} catch (Throwable t) {
			check("selftest crashed (sync part)", false, t);
			t.printStackTrace();
			finish(server, l);
			return;
		}
		// Tick-driven part: termites (2 s) and Capacitor fade (8 s).
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (wait[0] < 0) return;
			wait[0]++;
			try {
				if (wait[0] == Gear2Stats.TERMITE_SPREAD_TICKS + 10) termiteResult(l);
				if (wait[0] == Gear2Stats.CAPACITOR_FADE_TICKS + 10) {
					checkS("Coilmail: a stored charge fades after 8 s", () -> Capacitor.charges(p) == 0);
					wait[0] = -1;
					finish(server, l);
				}
			} catch (Throwable t) {
				check("selftest crashed (tick part)", false, t);
				wait[0] = -1;
				finish(server, l);
			}
		});
	}

	static void finish(MinecraftServer server, ServerLevel l) {
		for (BlockPos b : PLACED) l.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		for (Mob m : MOBS) m.discard();
		for (int cx = 1; cx <= 4; cx++) for (int cz = 1; cz <= 4; cz++) l.setChunkForced(cx, cz, false);
		String total = "TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL");
		LOG.add(total);
		System.out.println("[gear2-selftest] " + total);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-gear2.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("gear2.selftest.exit")) server.halt(false);
	}

	static BlockPos termiteCenter, termiteOre, termitePlank;

	static void termiteResult(ServerLevel l) {
		int eaten = 0;
		for (BlockPos b : Termites.face(termiteCenter, net.minecraft.core.Direction.Axis.Z)) if (l.getBlockState(b).isAir()) eaten++;
		final int e = eaten;
		check("Termite Lantern: termites ate the 3x3x1 face within 2 s (7 of 8 neighbours; the ore stays)", e == 7, e + " eaten");
		check("Termite Lantern con: ores are never eaten", l.getBlockState(termiteOre).is(Blocks.IRON_ORE));
		check("Termite Lantern con: termites chew builds (planks in the face were eaten)", l.getBlockState(termitePlank).isAir());
	}

	static void body(MinecraftServer s, ServerLevel l, FakePlayer p, BlockPos base) {
		info("elsewhere jar present: " + has("elsewhere:skyiron") + ", boss kit present: " + classPresent("dev.elsewhere.boss.kit.ForesightGear"));
		DamageSource melee = l.damageSources().playerAttack(p);
		Vec3 c = Vec3.atBottomCenterOf(base);

		// ---------------------------------------------------------------- shared
		check("all 22 gear2 items registered", Gear2Items.ALL.size() == 22, Gear2Items.ALL.size());
		dev.elsewhere.gear2.anim.UseAnimAssets.run(SelfTest::check); // s-p6-useanims: flail/rapier weapon state models
		checkS("every item has a name, item model and a non-missing texture", () -> {
			for (Item i : Gear2Items.ALL) {
				String path = BuiltInRegistries.ITEM.getKey(i).getPath();
				if (!Language.getInstance().has("item.elsewhere." + path)) throw new IllegalStateException("lang " + path);
				if (!asset("items/" + path + ".json")) throw new IllegalStateException("items/" + path);
				if (!asset("textures/item/" + path + ".png")) throw new IllegalStateException("texture " + path);
			}
			return asset("textures/entity/equipment/humanoid/galvanic_coilmail.png") && asset("textures/entity/equipment/humanoid_leggings/galvanic_coilmail.png")
				&& asset("textures/entity/equipment/humanoid/amberstill.png") && asset("textures/entity/equipment/humanoid_leggings/amberstill.png");
		});
		checkS("Calling: no gear2 item counts as caster/summoner armor (Calling 0, like netherite)", () -> {
			for (Item i : Gear2Items.ALL) {
				ItemStack st = new ItemStack(i);
				if (st.is(itag("elsewhere:caster_armor")) || st.is(itag("elsewhere:summoner_armor")) || st.is(itag("elsewhere:calling/caster"))) return false;
			}
			return true;
		});

		// ---------------------------------------------------------------- gear-armor-b Galvanic Coilmail
		clearPlayer(p);
		Item[] coil = {Gear2Items.COILMAIL_HELMET, Gear2Items.COILMAIL_CHESTPLATE, Gear2Items.COILMAIL_LEGGINGS, Gear2Items.COILMAIL_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) equip(p, slots[i], new ItemStack(coil[i]));
		check("Coilmail stats: armor " + java.util.Arrays.stream(dev.elsewhere.gear2.Gear2Stats.COILMAIL_ARMOR).sum() + ", toughness 12, knockback resistance " + (4 * dev.elsewhere.gear2.Gear2Stats.COILMAIL_KB), near(p.getAttributeValue(Attributes.ARMOR), java.util.Arrays.stream(dev.elsewhere.gear2.Gear2Stats.COILMAIL_ARMOR).sum())
			&& near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12) && near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 4 * dev.elsewhere.gear2.Gear2Stats.COILMAIL_KB),
			p.getAttributeValue(Attributes.ARMOR) + "/" + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) + "/" + p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		check("Coilmail set bonus active with 4 pieces", Capacitor.wearing(p));
		Mob attacker = mob(l, c.add(3, 0, 0));
		DamageSource mobHit = l.damageSources().mobAttack(attacker);
		for (int i = 0; i < 7; i++) Capacitor.onHurt(p, mobHit);
		check("Coilmail Capacitor: hits taken store charges, max 5", Capacitor.charges(p) == 5, Capacitor.charges(p));
		Mob target = mob(l, c.add(0, 0, 3));
		List<Mob> others = new ArrayList<>();
		for (int i = 0; i < 4; i++) others.add(mob(l, c.add(-1.5 + i, 0, 4.5)));
		Mob far = mob(l, c.add(0, 0, 12));
		l.getEntities((net.minecraft.world.entity.Entity) null, new AABB(base).inflate(20), e -> true); // warm-up query (GOTCHAS)
		float[] before = new float[4];
		for (int i = 0; i < 4; i++) before[i] = others.get(i).getHealth();
		float farBefore = far.getHealth();
		Gear2Hooks.modifyIncoming(target, melee, 5.0F);
		int hit10 = 0;
		for (int i = 0; i < 4; i++) if (near(before[i] - others.get(i).getHealth(), 10.0)) hit10++;
		check("Coilmail Capacitor: next melee hit discharges 2 x 5 = 10 into 3 other mobs within 4 blocks, ignoring armor", hit10 == 3, hit10 + " mobs took 10");
		check("Coilmail Capacitor: mobs farther than 4 blocks are not hit, charges spent", near(far.getHealth(), farBefore) && Capacitor.charges(p) == 0);
		DamageSource bolt = l.damageSources().lightningBolt();
		check("Coilmail con: lightning hurts 50% more with the full set", near(Gear2Hooks.modifyIncoming(p, bolt, 10.0F), 15.0F));
		clearPlayer(p);
		check("Coilmail con: no lightning penalty without the armor", near(Gear2Hooks.modifyIncoming(p, bolt, 10.0F), 10.0F));
		checkS("Coilmail con: slow Mending tag on all 4 pieces", () -> {
			for (Item i : coil) if (!new ItemStack(i).is(itag("elsewhere:slow_mending"))) return false;
			return true;
		});
		checkS("Coilmail: repaired with Pearlite", () -> !has("elsewhere:pearlite") || repairs(coil[0], item("elsewhere:pearlite")));
		checkS("Coilmail recipes: 4 smithing (template + diamond piece + Storm Coil) + template copy", () -> recipe(s, "galvanic_coilmail_helmet_smithing")
			&& recipe(s, "galvanic_coilmail_chestplate_smithing") && recipe(s, "galvanic_coilmail_leggings_smithing") && recipe(s, "galvanic_coilmail_boots_smithing")
			&& recipe(s, "galvanic_upgrade_smithing_template"));
		checkS("Coilmail source: Galvanox drops the template (loot modify)", () -> loot(s, "entities/galvanox", Gear2Items.GALVANIC_TEMPLATE));
		for (Mob m : others) m.discard();
		far.discard();
		attacker.discard();

		// ---------------------------------------------------------------- gear-weapon1-b Ironclad Flail
		clearPlayer(p);
		equip(p, EquipmentSlot.MAINHAND, new ItemStack(Gear2Items.IRONCLAD_FLAIL));
		check("Flail stats: 7 damage, " + dev.elsewhere.gear2.Gear2Stats.FLAIL_SPEED + " speed, durability 1700", near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 7) && near(p.getAttributeValue(Attributes.ATTACK_SPEED), dev.elsewhere.gear2.Gear2Stats.FLAIL_SPEED)
			&& new ItemStack(Gear2Items.IRONCLAD_FLAIL).getMaxDamage() == 1700, p.getAttributeValue(Attributes.ATTACK_DAMAGE) + "/" + p.getAttributeValue(Attributes.ATTACK_SPEED));
		checkS("Flail: repaired with Skyiron", () -> !has("elsewhere:skyiron") || repairs(Gear2Items.IRONCLAD_FLAIL, item("elsewhere:skyiron")));
		Mob a = mob(l, c.add(2, 0, 0)), b = mob(l, c.add(-2, 0, 0));
		float[] mult = new float[7];
		for (int i = 0; i < 7; i++) mult[i] = Gear2Hooks.modifyIncoming(a, melee, 7.0F) / 7.0F;
		check("Flail ability: same-target chain +" + Math.round(dev.elsewhere.gear2.Gear2Stats.FLAIL_CHAIN_STEP * 100) + "% per hit, 5 hits max", near(mult[0], 1.0) && near(mult[1], 1 + dev.elsewhere.gear2.Gear2Stats.FLAIL_CHAIN_STEP) && near(mult[5], 1 + 5 * dev.elsewhere.gear2.Gear2Stats.FLAIL_CHAIN_STEP) && near(mult[6], 1 + 5 * dev.elsewhere.gear2.Gear2Stats.FLAIL_CHAIN_STEP),
			java.util.Arrays.toString(mult));
		check("Flail con: switching target resets the chain", near(Gear2Hooks.modifyIncoming(b, melee, 7.0F), 7.0F));
		Gear2Hooks.modifyIncoming(b, melee, 7.0F);
		Gear2Hooks.onPunch(p);
		check("Flail con: a miss (punch at air) resets the chain", near(Gear2Hooks.modifyIncoming(b, melee, 7.0F), 7.0F));
		float hb = a.getHealth();
		a.damageCooldownTime = 0;
		p.attack(a);
		check("Flail end-to-end: a real attack goes through the damage hook (chain started)", a.getHealth() < hb && Gear2Hooks.flailChain(p) == 0, hb + "->" + a.getHealth());
		check("Flail con: no Sweeping Edge (not a sword, not sweeping-enchantable)", !new ItemStack(Gear2Items.IRONCLAD_FLAIL).is(ItemTags.SWORDS)
			&& !new ItemStack(Gear2Items.IRONCLAD_FLAIL).is(ItemTags.SWEEPING_ENCHANTABLE));
		double sp0 = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		p.setLastClientInput(new Input(false, true, false, false, false, false, false));
		Gear2Hooks.flailBackward(p);
		double sp1 = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		p.setLastClientInput(Input.EMPTY);
		Gear2Hooks.flailBackward(p);
		check("Flail con: 20% slower while moving backward (and back to normal after)", near(sp1, sp0 * 0.8) && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), sp0), sp0 + "->" + sp1);
		checkS("Flail recipe: Ironclad Chain + diamond sword + Skyiron (smithing)", () -> recipe(s, "ironclad_flail_smithing"));
		checkS("Flail source: Thurgrim drops the Ironclad Chain (loot modify)", () -> loot(s, "entities/thurgrim", Gear2Items.IRONCLAD_CHAIN));

		// ---------------------------------------------------------------- gear-weapon2-a Gale Rapier
		clearPlayer(p);
		equip(p, EquipmentSlot.MAINHAND, new ItemStack(Gear2Items.GALE_RAPIER));
		check("Rapier stats: 5 damage, 2.4 speed, durability 1100", near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 5) && near(p.getAttributeValue(Attributes.ATTACK_SPEED), 2.4)
			&& new ItemStack(Gear2Items.GALE_RAPIER).getMaxDamage() == 1100);
		checkS("Rapier: repaired with Zephyrite", () -> !has("elsewhere:zephyrite") || repairs(Gear2Items.GALE_RAPIER, item("elsewhere:zephyrite")));
		p.setSprinting(true);
		p.setDeltaMovement(Vec3.ZERO);
		float lunge = Gear2Hooks.modifyIncoming(a, melee, 5.0F);
		check("Rapier ability: sprinting hit adds a 3-damage lunge and a forward dash", near(lunge, 8.0F) && p.getDeltaMovement().horizontalDistance() > 0.3, lunge + ", dash " + p.getDeltaMovement());
		p.setSprinting(false);
		check("Rapier con: -" + Math.round((1 - dev.elsewhere.gear2.Gear2Stats.RAPIER_STILL_MULT) * 100) + "% damage while standing still", near(Gear2Hooks.modifyIncoming(a, melee, 5.0F), 5.0F * dev.elsewhere.gear2.Gear2Stats.RAPIER_STILL_MULT));
		p.setLastClientInput(new Input(true, false, false, false, false, false, false));
		check("Rapier: full damage while moving (not sprinting)", near(Gear2Hooks.modifyIncoming(a, melee, 5.0F), 5.0F));
		check("Rapier con: no Sweeping Edge", !new ItemStack(Gear2Items.GALE_RAPIER).is(ItemTags.SWORDS) && !new ItemStack(Gear2Items.GALE_RAPIER).is(ItemTags.SWEEPING_ENCHANTABLE));
		checkS("Rapier source: Tempestra drops it (loot modify)", () -> loot(s, "entities/tempestra", Gear2Items.GALE_RAPIER));
		a.discard();
		b.discard();

		// ---------------------------------------------------------------- gear-ranged-a Mirrorbolt Arbalest
		clearPlayer(p);
		ItemStack arb = new ItemStack(Gear2Items.MIRRORBOLT_ARBALEST);
		equip(p, EquipmentSlot.MAINHAND, arb);
		check("Arbalest stats: durability 700, reload " + dev.elsewhere.gear2.Gear2Stats.ARBALEST_RELOAD_SECONDS + " s (vanilla crossbow 1.25 s)", arb.getMaxDamage() == 700 && MirrorboltArbalest.chargeTicks(arb, p) == net.minecraft.util.Mth.floor(dev.elsewhere.gear2.Gear2Stats.ARBALEST_RELOAD_SECONDS * 20.0F)
			&& CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), p) == 25, MirrorboltArbalest.chargeTicks(arb, p));
		check("Arbalest: takes Multishot and Quick Charge, refuses Piercing (built in) and Infinity", enchantable(arb, l, Enchantments.MULTISHOT)
			&& enchantable(arb, l, Enchantments.QUICK_CHARGE) && !enchantable(arb, l, Enchantments.PIERCING) && !enchantable(arb, l, Enchantments.INFINITY));
		check("Arbalest con: normal arrows only (no fireworks)", ((MirrorboltArbalest) Gear2Items.MIRRORBOLT_ARBALEST).getAllSupportedProjectiles().test(new ItemStack(Items.ARROW))
			&& !((MirrorboltArbalest) Gear2Items.MIRRORBOLT_ARBALEST).getAllSupportedProjectiles().test(new ItemStack(Items.FIREWORK_ROCKET)));
		p.snapTo(c.x, c.y + 20, c.z, 0, 0);
		arb.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
		((CrossbowItem) Gear2Items.MIRRORBOLT_ARBALEST).performShooting(l, p, InteractionHand.MAIN_HAND, arb, Gear2Stats.ARBALEST_POWER, 1.0F, null);
		AABB box = p.getBoundingBox().inflate(8);
		l.getEntitiesOfClass(AbstractArrow.class, box);
		List<AbstractArrow> bolts = l.getEntitiesOfClass(AbstractArrow.class, box);
		check("Arbalest ability: a fired bolt pierces 3 mobs and hits for 14 (no random crit)", bolts.size() == 1 && bolts.get(0).getPierceLevel() == Gear2Stats.ARBALEST_PIERCE
			&& !bolts.get(0).isCritArrow() && (int) Math.ceil(((Gear2ArrowAccess) bolts.get(0)).gear2$baseDamage() * Gear2Stats.ARBALEST_POWER) == 14,
			bolts.isEmpty() ? "no bolt" : "pierce " + bolts.get(0).getPierceLevel() + ", dmg " + ((Gear2ArrowAccess) bolts.get(0)).gear2$baseDamage() * Gear2Stats.ARBALEST_POWER);
		bolts.forEach(net.minecraft.world.entity.Entity::discard);
		p.snapTo(c.x, c.y, c.z, 0, 0);
		checkS("Arbalest recipe: Sunglass Shards + Heliodor Mirror Core + crossbow + diamonds", () -> recipe(s, "mirrorbolt_arbalest"));
		checkS("Arbalest source: Heliodor drops the Mirror Core (loot modify)", () -> loot(s, "entities/heliodor", Gear2Items.HELIODOR_MIRROR_CORE));

		// ---------------------------------------------------------------- gear-drill-a Steam Auger
		clearPlayer(p);
		ItemStack aug = new ItemStack(Gear2Items.STEAM_AUGER);
		equip(p, EquipmentSlot.MAINHAND, aug);
		BlockState stone = Blocks.STONE.defaultBlockState();
		check("Auger stats: durability 1200, reach 2 blocks (pickaxe 4.5)", aug.getMaxDamage() == 1200 && near(p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 2.0),
			p.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));
		p.getInventory().add(new ItemStack(Items.COAL, 1));
		boolean refuelled = SteamAuger.refuel(p, aug);
		check("Auger: burns 1 coal from the inventory for 100 blocks", refuelled && SteamAuger.fuel(aug) == 100 && p.getInventory().countItem(Items.COAL) == 0);
		float speed = aug.getDestroySpeed(stone), netherite = new ItemStack(Items.NETHERITE_PICKAXE).getDestroySpeed(stone);
		check("Auger ability: 2.5x netherite pickaxe speed on stone, ores and deepslate", near(speed, netherite * 2.5) && near(aug.getDestroySpeed(Blocks.DEEPSLATE.defaultBlockState()), speed)
			&& near(aug.getDestroySpeed(Blocks.IRON_ORE.defaultBlockState()), speed), speed + " vs " + netherite);
		BlockPos dig = base.offset(0, 0, 1);
		set(l, dig, stone);
		boolean broke = p.gameMode.destroyBlock(dig);
		check("Auger: mining a block uses 1 of the 100 blocks of steam", broke && SteamAuger.fuel(p.getMainHandItem()) == 99, SteamAuger.fuel(p.getMainHandItem()));
		aug = p.getMainHandItem();
		aug.set(Gear2Items.AUGER_FUEL, 0);
		check("Auger con: no coal, no steam (mines like a bare hand)", !SteamAuger.refuel(p, aug) && near(aug.getDestroySpeed(stone), 1.0));
		check("Auger con: Silk Touch yes, Fortune and Mending no", enchantable(aug, l, Enchantments.SILK_TOUCH) && !enchantable(aug, l, Enchantments.FORTUNE)
			&& !enchantable(aug, l, Enchantments.MENDING));
		checkS("Auger: repaired with Skyiron", () -> !has("elsewhere:skyiron") || repairs(Gear2Items.STEAM_AUGER, item("elsewhere:skyiron")));
		checkS("Auger recipe: Auger Bit + diamond pickaxe + Skyiron (smithing)", () -> recipe(s, "steam_auger_smithing"));
		checkS("Auger source: Thurgrim drops the Auger Bit (loot modify)", () -> loot(s, "entities/thurgrim", Gear2Items.STEAM_AUGER_BIT));

		// ---------------------------------------------------------------- gear-areaminer-b Termite Hive Lantern
		clearPlayer(p);
		ItemStack lan = new ItemStack(Gear2Items.TERMITE_HIVE_LANTERN);
		check("Termite Lantern stats: durability 800, 1/3 netherite pickaxe speed on stone, dirt and wood", lan.getMaxDamage() == 800
			&& near(lan.getDestroySpeed(stone), 3.0) && near(lan.getDestroySpeed(Blocks.DIRT.defaultBlockState()), 3.0) && near(lan.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()), 3.0));
		check("Termite Lantern con: does nothing to ores and ancient debris", near(lan.getDestroySpeed(Blocks.IRON_ORE.defaultBlockState()), 1.0)
			&& !lan.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) && !lan.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()));
		checkS("Termite Lantern: repaired with Amber (tag, optional until Amber exists)", () -> new ItemStack(Gear2Items.TERMITE_HIVE_LANTERN).get(DataComponents.REPAIRABLE) != null);
		FakePlayer tp = FakePlayer.get(l, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("6e61b0c2-2f2a-4b8e-9d7e-0a7e2f1c9a01"), "gear2-termites"));
		equip(tp, EquipmentSlot.MAINHAND, lan);
		tp.snapTo(c.x + 8, c.y, c.z - 3, 0, 0); // yaw 0, pitch 0: looking +Z, so the face is the XY plane
		termiteCenter = base.offset(8, 2, 0);
		for (BlockPos f : Termites.face(termiteCenter, net.minecraft.core.Direction.Axis.Z)) set(l, f, Blocks.DIRT.defaultBlockState());
		termiteOre = termiteCenter.offset(1, 1, 0);
		termitePlank = termiteCenter.offset(-1, -1, 0);
		set(l, termiteOre, Blocks.IRON_ORE.defaultBlockState());
		set(l, termitePlank, Blocks.OAK_PLANKS.defaultBlockState());
		set(l, termiteCenter, Blocks.DIRT.defaultBlockState());
		boolean t = tp.gameMode.destroyBlock(termiteCenter);
		check("Termite Lantern ability: mining a block sends termites to the 8 face neighbours", t && Termites.pending() == 8, Termites.pending());
		checkS("Termite Lantern recipe: Termite Queen + Amber + lantern", () -> recipe(s, "termite_hive_lantern"));
		checkS("Termite Lantern source: Mother Creak drops the Termite Queen (loot modify)", () -> loot(s, "entities/mother_creak", Gear2Items.TERMITE_QUEEN));

		// ---------------------------------------------------------------- gear-trinket1-a Seer's Hourglass
		clearPlayer(p);
		ItemStack hg = new ItemStack(Gear2Items.SEERS_HOURGLASS);
		check("Hourglass: registered as elsewhere:seers_hourglass, in #elsewhere:trinket and #elsewhere:foresight_gear",
			BuiltInRegistries.ITEM.getKey(Gear2Items.SEERS_HOURGLASS).toString().equals("elsewhere:seers_hourglass")
			&& hg.is(itag("elsewhere:trinket")) && hg.is(itag("elsewhere:foresight_gear")));
		check("Hourglass stats: none (no attribute modifiers)", hg.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
			net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty());
		p.getInventory().setItem(4, hg.copy());
		p.getInventory().setSelectedSlot(0);
		check("Hourglass ability: active from the hotbar, boss warnings 0.15 s (3 ticks) earlier", SeersHourglass.active(p) && SeersHourglass.leadTicks(p) == 3);
		Object sees = foresight(p);
		if (sees == null) info("boss kit ForesightGear not in this jar: hotbar-foresight mixin not exercised");
		else check("Hourglass ability: the bosses' ForesightGear.sees() is true with it in the hotbar (mixin)", Boolean.TRUE.equals(sees));
		Item third = has("elsewhere:glimmerstone_focus") ? item("elsewhere:glimmerstone_focus") : Gear2Items.PROSPECTORS_PENDULUM;
		p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Gear2Items.PROSPECTORS_PENDULUM));
		p.getInventory().setItem(1, new ItemStack(third == Gear2Items.PROSPECTORS_PENDULUM ? Items.AIR : third));
		boolean full = third != Gear2Items.PROSPECTORS_PENDULUM;
		check("Hourglass con: takes one of the 2 trinket places (a 3rd trinket stays inactive)", !full || (!SeersHourglass.active(p) && SeersHourglass.leadTicks(p) == 0),
			full ? "with " + BuiltInRegistries.ITEM.getKey(third) : "no 3rd trinket in this jar");
		p.getInventory().clearContent();
		p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		Object seesNone = foresight(p);
		check("Hourglass con: does nothing when not carried as a trinket", !SeersHourglass.active(p) && (seesNone == null || Boolean.FALSE.equals(seesNone)));
		checkS("Hourglass source: Selenne drops it (loot modify)", () -> loot(s, "entities/selenne", Gear2Items.SEERS_HOURGLASS));

		// ---------------------------------------------------------------- gear-trinket2-b Prospector's Pendulum
		clearPlayer(p);
		BlockPos pc = base.offset(0, 50, 0);
		p.snapTo(pc.getX() + 0.5, pc.getY(), pc.getZ() + 0.5, 0, 0);
		ItemStack pen = new ItemStack(Gear2Items.PROSPECTORS_PENDULUM);
		check("Pendulum stats: trinket, durability 300", pen.is(itag("elsewhere:trinket")) && pen.getMaxDamage() == 300);
		set(l, pc.offset(6, 0, 0), Blocks.IRON_ORE.defaultBlockState());
		set(l, pc.offset(0, 0, -10), Blocks.DEEPSLATE_COAL_ORE.defaultBlockState());
		set(l, pc.offset(0, 3, 0), Blocks.DIAMOND_ORE.defaultBlockState());
		set(l, pc.offset(-4, 0, 0), Blocks.ANCIENT_DEBRIS.defaultBlockState());
		set(l, pc.offset(0, 0, 9), Blocks.GOLD_ORE.defaultBlockState());
		List<ProspectorsPendulum.Find> f = ProspectorsPendulum.scan(l, pc);
		check("Pendulum ability: finds the nearest ore, up to 2 types (iron 6, gold 9)", f.size() == 2 && f.get(0).block() == Blocks.IRON_ORE && f.get(1).block() == Blocks.GOLD_ORE,
			f.toString());
		check("Pendulum con: diamond ore and ancient debris are never found", f.stream().noneMatch(x -> x.block() == Blocks.DIAMOND_ORE || x.block() == Blocks.ANCIENT_DEBRIS));
		for (BlockPos o : List.of(pc.offset(6, 0, 0), pc.offset(0, 0, 9), pc.offset(0, 0, -10))) l.setBlock(o, Blocks.AIR.defaultBlockState(), 3);
		set(l, pc.offset(26, 0, 0), Blocks.IRON_ORE.defaultBlockState());
		check("Pendulum con: short range (an ore 26 blocks away is not found)", ProspectorsPendulum.scan(l, pc).isEmpty());
		set(l, pc.offset(5, 0, 0), Blocks.COPPER_ORE.defaultBlockState());
		p.setItemSlot(EquipmentSlot.OFFHAND, pen);
		List<ProspectorsPendulum.Find> ping = Gear2Hooks.pendulumPing(p);
		check("Pendulum ability: as an active trinket it swings toward ore", ping.size() == 1 && ping.get(0).block() == Blocks.COPPER_ORE);
		check("Pendulum con: wears out (1 durability per swing that finds ore)", p.getOffhandItem().getDamageValue() == 1, p.getOffhandItem().getDamageValue());
		checkS("Pendulum source: Thurgrim and Ironpeak cave chests (loot modify)", () -> loot(s, "entities/thurgrim", Gear2Items.PROSPECTORS_PENDULUM)
			&& (!has("elsewhere:skyiron") || (loot(s, "chests/climbers_cairn", Gear2Items.PROSPECTORS_PENDULUM) && loot(s, "chests/clan_hold", Gear2Items.PROSPECTORS_PENDULUM))));
		p.snapTo(c.x, c.y, c.z, 0, 0);

		// ---------------------------------------------------------------- gear-trinket3-b Void Tether Spool
		clearPlayer(p);
		ItemStack spool = new ItemStack(Gear2Items.VOID_TETHER_SPOOL);
		p.setItemSlot(EquipmentSlot.MAINHAND, spool);
		p.getFoodData().setFoodLevel(20);
		VoidTetherSpool.Result r1 = VoidTetherSpool.act(p, spool, false);
		BlockPos anchor = p.blockPosition();
		p.snapTo(c.x + 30, c.y, c.z, 0, 0);
		VoidTetherSpool.Result r2 = VoidTetherSpool.act(p, spool, false);
		check("Spool ability: set an anchor, then snap back to it from 30 blocks", r1 == VoidTetherSpool.Result.ANCHORED && r2 == VoidTetherSpool.Result.SNAPPED
			&& p.blockPosition().equals(anchor), r1 + "/" + r2 + " at " + p.blockPosition() + " anchor " + anchor);
		check("Spool con: costs 4 hunger points and starts a 3-minute cooldown", p.getFoodData().getFoodLevel() == 16 && p.getCooldowns().isOnCooldown(spool)
			&& near(p.getCooldowns().getCooldownPercent(spool, 0), 1.0));
		p.snapTo(c.x + 70, c.y, c.z, 0, 0);
		check("Spool con: only within 64 blocks", VoidTetherSpool.act(p, spool, false) == VoidTetherSpool.Result.TOO_FAR);
		spool.set(Gear2Items.TETHER_ANCHOR, GlobalPos.of(Level.NETHER, anchor));
		check("Spool con: cannot cross dimensions", VoidTetherSpool.act(p, spool, false) == VoidTetherSpool.Result.OTHER_DIMENSION);
		spool.set(Gear2Items.TETHER_ANCHOR, GlobalPos.of(l.dimension(), anchor));
		p.snapTo(c.x + 5, c.y, c.z, 0, 0);
		p.getFoodData().setFoodLevel(3);
		check("Spool con: too hungry (under 4 hunger points) does nothing", VoidTetherSpool.act(p, spool, false) == VoidTetherSpool.Result.TOO_HUNGRY);
		p.getFoodData().setFoodLevel(20);
		p.snapTo(c.x, c.y, c.z, 0, 0);
		checkS("Spool source: Aphelion and Ossyra drop it (loot modify)", () -> loot(s, "entities/aphelion", Gear2Items.VOID_TETHER_SPOOL)
			&& loot(s, "entities/ossyra", Gear2Items.VOID_TETHER_SPOOL));

		// ---------------------------------------------------------------- gear-armor-a Amberstill Plate
		clearPlayer(p);
		Item[] amb = {Gear2Items.AMBERSTILL_HELMET, Gear2Items.AMBERSTILL_CHESTPLATE, Gear2Items.AMBERSTILL_LEGGINGS, Gear2Items.AMBERSTILL_BOOTS};
		for (int i = 0; i < 4; i++) equip(p, slots[i], new ItemStack(amb[i]));
		check("Amberstill stats: armor 20, toughness 8, knockback resistance 0.4, not fire-proof", near(p.getAttributeValue(Attributes.ARMOR), 20)
			&& near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 8) && near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.4)
			&& !new ItemStack(amb[1]).has(DataComponents.DAMAGE_RESISTANT), p.getAttributeValue(Attributes.ARMOR) + "/" + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		long t0 = l.getGameTime();
		final boolean brace = dev.elsewhere.gear2.Overhaul3.REBALANCE_GEAR2; // 3.0 Brace: only sprinting breaks Stillness (and restarts the 2 s)
		if (brace) {
			p.setSprinting(true);
			dev.elsewhere.gear2.Amberstill.update(p, t0);
			p.setSprinting(false);
		} else dev.elsewhere.gear2.Amberstill.onAction(p, t0);
		dev.elsewhere.gear2.Amberstill.update(p, t0 + 39);
		double tough39 = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		dev.elsewhere.gear2.Amberstill.update(p, t0 + 40);
		check("Amberstill ability: Stillness after 2 s without sprinting or attacking: toughness 12 (netherite's)", near(tough39, 8) && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12),
			tough39 + "->" + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		p.setSprinting(true);
		dev.elsewhere.gear2.Amberstill.update(p, t0 + 41);
		double sprint = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		p.setSprinting(false);
		dev.elsewhere.gear2.Amberstill.update(p, t0 + 100);
		boolean back = near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12);
		Mob dummy = mob(l, c.add(0, 0, 2));
		Gear2Hooks.modifyIncoming(dummy, melee, 1.0F);
		check(brace ? "Amberstill Brace (3.0): sprinting drops toughness to 8, a hit keeps Stillness (12)" : "Amberstill con: toughness drops to 8 the moment you sprint or hit",
			near(sprint, 8) && back && near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), brace ? 12 : 8), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		Gear2Hooks.onPunch(p);
		dev.elsewhere.gear2.Amberstill.update(p, brace ? t0 + 101 : l.getGameTime() + 20);
		check(brace ? "Amberstill Brace (3.0): a swing at air keeps Stillness" : "Amberstill con: a swing at air also restarts the 2 s",
			near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), brace ? 12 : 8), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		checkS("Amberstill con: slow Mending tag on all 4 pieces", () -> {
			for (Item i : amb) if (!new ItemStack(i).is(itag("elsewhere:slow_mending"))) return false;
			return true;
		});
		checkS("Amberstill recipes: 4 smithing (template + diamond piece + Amber ingot) + template copy", () -> recipe(s, "amberstill_helmet_smithing")
			&& recipe(s, "amberstill_chestplate_smithing") && recipe(s, "amberstill_leggings_smithing") && recipe(s, "amberstill_boots_smithing")
			&& recipe(s, "amberstill_upgrade_smithing_template"));
		checkS("Amberstill source: Mother Creak drops the template (loot modify)", () -> loot(s, "entities/mother_creak", Gear2Items.AMBERSTILL_TEMPLATE));
		checkS("Amberstill: no Calling (not caster/summoner armor)", () -> !new ItemStack(amb[0]).is(itag("elsewhere:caster_armor")) && !new ItemStack(amb[0]).is(itag("elsewhere:summoner_armor")));

		// ---------------------------------------------------------------- netherite stays a safe, equal choice
		check("Netherite check: Coilmail armor at most netherite (20)", java.util.Arrays.stream(dev.elsewhere.gear2.Gear2Stats.COILMAIL_ARMOR).sum() <= 20);
		clearPlayer(p);
		target.discard();
		// Capacitor fade needs a charge on a wearer: re-equip for the tick part.
		for (int i = 0; i < 4; i++) equip(p, slots[i], new ItemStack(coil[i]));
		Capacitor.onHurt(p, l.damageSources().mobAttack(mob(l, c.add(0, 0, -3))));
		check("Coilmail: one charge stored before the 8 s wait", Capacitor.charges(p) == 1);
	}

	static boolean classPresent(String name) {
		try {
			Class.forName(name);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Calls ForesightGear.sees(p) reflectively; null if the boss kit isn't in this jar. */
	static Object foresight(net.minecraft.world.entity.player.Player p) {
		try {
			return Class.forName("dev.elsewhere.boss.kit.ForesightGear").getMethod("sees", net.minecraft.world.entity.player.Player.class).invoke(null, p);
		} catch (Throwable t) {
			return null;
		}
	}
}
