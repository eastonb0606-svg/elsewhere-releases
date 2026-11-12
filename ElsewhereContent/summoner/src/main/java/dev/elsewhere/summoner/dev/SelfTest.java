package dev.elsewhere.summoner.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionStaffItem;
import dev.elsewhere.minion.MinionTargeting;
import dev.elsewhere.stub.summoner.GlimmerStub;
import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.summoner.Brassback;
import dev.elsewhere.summoner.GearStats;
import dev.elsewhere.summoner.PaperKite;
import dev.elsewhere.summoner.Rootling;
import dev.elsewhere.summoner.SummonerEffects;
import dev.elsewhere.summoner.SummonerEntities;
import dev.elsewhere.summoner.SummonerItems;
import dev.elsewhere.summoner.SummonerPerks;
import dev.elsewhere.summoner.TrailBiscuitItem;
import dev.elsewhere.summoner.mixin.AbstractArrowAccessor;
import dev.elsewhere.summoner.mixin.LivingEntityInvoker;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server self-test (summoner.selftest=true): staffs, slots, follow/teleport, targeting, protection, despawn rules,
 * fervor, armor/trinket/consumable effects. Entities are ticked by hand so the run is deterministic. PASS/FAIL + total.
 */
public final class SelfTest {
	private SelfTest() {}

	private static final Logger LOG = LoggerFactory.getLogger("elsewhere_summoner");
	private static int passes, failures;
	private static final Map<UUID, ServerPlayer> OWNERS = new HashMap<>();
	private static ServerLevel level;
	private static FakePlayer owner;
	private static final int Y = 100;

	private static void check(boolean ok, String what) {
		LOG.info("[selftest] {} {}", ok ? "PASS" : "FAIL", what);
		if (ok) passes++;
		else failures++;
	}

	private static String f(String s, Object... a) {
		return String.format(Locale.ROOT, s, a);
	}

	public static void run(MinecraftServer server) {
		passes = failures = 0;
		level = server.overworld();
		dev.elsewhere.stub.summoner.CallingStub.forceLegacy = true; // this test's numbers are the 2.1 tuning; arsenal's self-test covers 3.0 Callings
		try {
			setup();
			registration();
			summoning();
			slots();
			movement();
			targeting();
			protection();
			killsAndLoot();
			despawn();
			fervor();
			armor();
			perks();
			consumableAndTrinket();
			minionKinds();
			v2Balance();
			v2Gear();
			calling();
			herding();
			herdFocus();
			orders();
			griefing();
		} catch (Throwable t) {
			LOG.error("[selftest] crashed", t);
			failures++;
		}
		dev.elsewhere.stub.summoner.CallingStub.forceLegacy = false;
		LOG.info("[selftest] {} passed, {} failed: {}", passes, failures, failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
		if (Boolean.getBoolean("summoner.selftest.exit")) server.halt(false);
	}

	// ------------------------------------------------------------------ helpers

	private static void setup() {
		for (int cx = -2; cx <= 6; cx++) for (int cz = -2; cz <= 2; cz++) level.getChunk(cx, cz);
		for (int x = -24; x <= 90; x++) for (int z = -24; z <= 24; z++) {
			level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 2);
			for (int y = Y; y < Y + 6; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		}
		owner = fake("summoner_owner");
		MinionEntity.testOwnerLookup = OWNERS::get;
	}

	private static FakePlayer fake(String name) {
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		p.setPos(0.5, Y, 0.5);
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4));
		OWNERS.put(p.getUUID(), p);
		return p;
	}

	private static void equip(Player0 p, EquipmentSlot slot, ItemStack s) {
		p.p.setItemSlot(slot, s);
		((LivingEntityInvoker) p.p).elsewhere_summoner$detectEquipmentUpdates();
	}

	/** tiny wrapper so equip() reads well */
	private record Player0(ServerPlayer p) {}

	private static void equip(EquipmentSlot slot, ItemStack s) {
		equip(new Player0(owner), slot, s);
	}

	private static void unequipAll() {
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) equip(s, ItemStack.EMPTY);
		owner.getInventory().clearContent();
		((LivingEntityInvoker) owner).elsewhere_summoner$detectEquipmentUpdates();
		SummonerPerks.updateTrinketSlots(owner);
	}

	private static void tick(int n, Entity... extra) {
		for (int i = 0; i < n; i++) {
			for (MinionEntity m : MinionManager.minions(owner)) {
				m.tickCount++;
				m.tick();
			}
			for (Entity e : extra) {
				if (e.isRemoved()) continue;
				e.tickCount++;
				e.tick();
			}
		}
	}

	private static void reset() {
		MinionManager.dismissAll(owner.getUUID());
		unequipAll();
		owner.setPos(0.5, Y, 0.5);
		owner.setShiftKeyDown(false);
		owner.setLastHurtMob(null);
		owner.setLastHurtByMob(null);
		GlimmerStub.devDeny = false;
		// combined jar: keep the long test off arsenal's real pool (summoning() checks the real pool itself)
		GlimmerStub.testFree = true;
		GlimmerStub.SPENT.clear();
		GlimmerStub.REFUNDED.clear();
		dev.elsewhere.summoner.SummonerConfig.setMaxMinionsPerPlayer(GearStats.MAX_MINIONS_PER_PLAYER);
		SummonerAttributes.slots();
		var inst = owner.getAttribute(SummonerAttributes.slots());
		if (inst != null) inst.setBaseValue(GearStats.BASE_MINION_SLOTS);
	}

	private static MinionEntity summon(EntityType<? extends MinionEntity> type) {
		MinionStaffItem staff = type == SummonerEntities.ROOTLING ? (MinionStaffItem) SummonerItems.ROOTCALL_STAFF
			: type == SummonerEntities.SPORELING ? (MinionStaffItem) SummonerItems.SPOREBLOOM_STAFF
			: type == SummonerEntities.PAPER_KITE ? (MinionStaffItem) SummonerItems.KITESTRING_STAFF : (MinionStaffItem) SummonerItems.BRASSBACK_KEY;
		ItemStack st = new ItemStack(staff);
		owner.setItemInHand(InteractionHand.MAIN_HAND, st);
		return staff.summonNow(owner, st);
	}

	private static <T extends Mob> T spawn(EntityType<T> type, double x, double z, boolean noAi) {
		T m = type.create(level, EntitySpawnReason.COMMAND);
		m.snapTo(x, Y, z, 0, 0);
		m.setNoAi(noAi);
		m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0));
		level.addFreshEntity(m);
		return m;
	}

	// ------------------------------------------------------------------ tests

	private static void registration() {
		String[] items = {"herding_crook", "rootcall_staff", "kitestring_staff", "brassback_key", "herdcaller_cap", "herdcaller_vest", "herdcaller_leggings", "herdcaller_boots",
			"lined_herdcaller_cap", "lined_herdcaller_vest", "lined_herdcaller_leggings", "lined_herdcaller_boots", "elder_bark_crown", "engineers_harness",
			"gorger_fluff", "bell_of_the_flock", "trail_biscuit"};
		int ok = 0;
		for (String i : items) if (BuiltInRegistries.ITEM.containsKey(dev.elsewhere.summoner.SummonerContent.id(i))) ok++;
		check(ok == items.length, f("all %d summoner items registered (%d found)", items.length, ok));
		check(BuiltInRegistries.ENTITY_TYPE.containsKey(dev.elsewhere.summoner.SummonerContent.id("rootling"))
			&& BuiltInRegistries.ENTITY_TYPE.containsKey(dev.elsewhere.summoner.SummonerContent.id("paper_kite"))
			&& BuiltInRegistries.ENTITY_TYPE.containsKey(dev.elsewhere.summoner.SummonerContent.id("brassback")), "rootling, paper_kite, brassback entity types registered");
		check(SummonerEffects.WELL_FED != null, "effect elsewhere:well_fed registered");
		check(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(MinionApi.MINION_DAMAGE).isPresent(), "damage type elsewhere:minion loaded");
		var slots = owner.getAttribute(SummonerAttributes.slots());
		var dmg = owner.getAttribute(SummonerAttributes.damage());
		check(slots != null && dmg != null && slots.getValue() == 1.0 && dmg.getValue() == 1.0, "players carry minion_slots (base 1) and minion_damage (base 1.0)");
		check(!SummonerEntities.ROOTLING.canSerialize() && !SummonerEntities.PAPER_KITE.canSerialize() && !SummonerEntities.BRASSBACK.canSerialize(), "minions are never saved with the world");
	}

	private static void summoning() {
		reset();
		// spend through the real API: arsenal's Glimmer pool in the combined jar, the dev record without it
		GlimmerStub.testFree = false;
		boolean realPool = GlimmerStub.arsenalPresent();
		if (realPool) GlimmerStub.setPool(owner, 1000F);
		float pool0 = GlimmerStub.pool(owner);
		ItemStack st = new ItemStack(SummonerItems.ROOTCALL_STAFF);
		owner.setItemInHand(InteractionHand.MAIN_HAND, st);
		MinionStaffItem staff = (MinionStaffItem) SummonerItems.ROOTCALL_STAFF;
		check(staff.getUseDuration(st, owner) == 20, "staff call takes 1.0 s (20 ticks, bow-draw slow)");
		var r = staff.use(level, owner, InteractionHand.MAIN_HAND);
		check(owner.isUsingItem() && MinionManager.count(owner) == 0, "right-click starts the call without summoning yet (" + r + ")");
		owner.releaseUsingItem();
		owner.stopUsingItem();
		staff.finishUsingItem(st, level, owner);
		MinionEntity a = MinionManager.minions(owner).isEmpty() ? null : MinionManager.minions(owner).get(0);
		check(a instanceof Rootling, "finishing the call summons a Rootling");
		float spent = realPool ? pool0 - GlimmerStub.pool(owner) : GlimmerStub.SPENT.getOrDefault(owner.getUUID(), 0);
		check(Math.abs(spent - GearStats.SUMMON_GLIMMER_COST) < 0.01, f("summon spends %d Glimmer (spent %.1f from the %s)", GearStats.SUMMON_GLIMMER_COST, spent, realPool ? "real arsenal pool" : "dev stub"));
		check(st.getDamageValue() == 1 && st.getMaxDamage() == GearStats.STAFF_DURABILITY, "summon costs 1 staff durability (128 max)");
		check(MinionApi.reservedGlimmer(owner) == GearStats.ATTUNE_RESERVE[0], f("a live minion reserves %d max Glimmer at Summoner Calling 0 (%d)", GearStats.ATTUNE_RESERVE[0], MinionApi.reservedGlimmer(owner)));
		check(!(a instanceof Enemy), "minions are not Enemy (no Test Meter health bonus)");
		// interrupt: damage stops the call
		owner.setItemInHand(InteractionHand.MAIN_HAND, st);
		staff.use(level, owner, InteractionHand.MAIN_HAND);
		var husk = spawn(EntityTypes.HUSK, 2.5, 0.5, true);
		// a FakePlayer is invulnerable (its connection never "loaded"), so fire the damage event the way a real hit does
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(owner, owner.damageSources().mobAttack(husk), 2F, 2F, false);
		check(!owner.isUsingItem(), "taking damage interrupts the 1 s call");
		husk.discard();
		// not enough Glimmer
		if (realPool) GlimmerStub.setPool(owner, 0F);
		else GlimmerStub.devDeny = true;
		int before = MinionManager.count(owner);
		MinionEntity none = summon(SummonerEntities.PAPER_KITE);
		check(none == null && MinionManager.count(owner) == before, "not enough Glimmer: nothing is summoned" + (realPool ? " (real pool at 0)" : ""));
		GlimmerStub.devDeny = false;
		if (realPool) GlimmerStub.setPool(owner, 1000F);
		GlimmerStub.testFree = true;
	}

	private static void slots() {
		reset();
		MinionEntity a = summon(SummonerEntities.ROOTLING);
		MinionEntity b = summon(SummonerEntities.ROOTLING);
		check(a != null && a.isRemoved() && b != null && !b.isRemoved() && MinionManager.count(owner) == 1, "base 1 slot: a second summon replaces the oldest");
		equip(EquipmentSlot.HEAD, new ItemStack(SummonerItems.HERDCALLER_CAP));
		equip(EquipmentSlot.CHEST, new ItemStack(SummonerItems.HERDCALLER_VEST));
		check(MinionApi.maxSlots(owner) == 3, f("Herdcaller cap + vest: 3 slots (got %d)", MinionApi.maxSlots(owner)));
		owner.getInventory().setItem(3, new ItemStack(SummonerItems.BELL_OF_THE_FLOCK));
		SummonerPerks.updateTrinketSlots(owner);
		check(MinionApi.maxSlots(owner) == 4, f("+ Bell of the Flock in the hotbar: 4 slots (got %d)", MinionApi.maxSlots(owner)));
		var mg = SummonerAttributes.maxGlimmer();
		check(mg != null && Math.abs(owner.getAttributeValue(mg) - (20 + GearStats.BELL_MAX_GLIMMER)) < 0.001, f("Bell of the Flock (summoner piece worn): max Glimmer +6 (%.0f)", mg == null ? -1 : owner.getAttributeValue(mg)));
		for (int i = 0; i < 5; i++) summon(i == 4 ? SummonerEntities.BRASSBACK : SummonerEntities.ROOTLING);
		List<MinionEntity> l = MinionManager.minions(owner);
		check(l.size() == 4 && l.get(3) instanceof Brassback && b.isRemoved(), f("5 summons at 4 slots: 4 out, oldest gone (%d out)", l.size()));
		// server config maxMinionsPerPlayer (0..4) can only lower the limit
		dev.elsewhere.summoner.SummonerConfig.setMaxMinionsPerPlayer(9);
		boolean noRaise = dev.elsewhere.summoner.SummonerConfig.maxMinionsPerPlayer() == 4 && MinionApi.maxSlots(owner) == 4;
		dev.elsewhere.summoner.SummonerConfig.setMaxMinionsPerPlayer(2);
		int capped = MinionApi.maxSlots(owner);
		MinionManager.enforceSlots(owner);
		int left = MinionManager.count(owner);
		dev.elsewhere.summoner.SummonerConfig.setMaxMinionsPerPlayer(0);
		MinionEntity off = summon(SummonerEntities.ROOTLING);
		int offSlots = MinionApi.maxSlots(owner);
		dev.elsewhere.summoner.SummonerConfig.setMaxMinionsPerPlayer(GearStats.MAX_MINIONS_PER_PLAYER);
		check(noRaise && capped == 2 && left == 2 && off == null && offSlots == 0 && MinionApi.maxSlots(owner) == 4,
			f("server config maxMinionsPerPlayer: 9 clamps to 4, 2 caps 4 slots to %d (%d left out), 0 turns summoning off (slots %d)", capped, left, offSlots));
		var inst = owner.getAttribute(SummonerAttributes.slots());
		inst.setBaseValue(10);
		check(inst.getValue() <= GearStats.HARD_SLOT_CAP && MinionApi.maxSlots(owner) == GearStats.HARD_SLOT_CAP, f("hard cap: slots clamp to %d (attr %.0f)", GearStats.HARD_SLOT_CAP, inst.getValue()));
		inst.setBaseValue(GearStats.BASE_MINION_SLOTS);
		equip(EquipmentSlot.HEAD, ItemStack.EMPTY);
		equip(EquipmentSlot.CHEST, ItemStack.EMPTY);
		SummonerPerks.updateTrinketSlots(owner);
		check(MinionApi.maxSlots(owner) == 1, f("bell gives nothing without a summoner armor piece (slots %d)", MinionApi.maxSlots(owner)));
		MinionManager.enforceSlots(owner);
		l = MinionManager.minions(owner);
		check(l.size() == 1 && l.get(0) instanceof Brassback, "taking gear off drops the oldest down to the new limit");
		// sneak + use dismisses only that staff's minions
		reset();
		inst.setBaseValue(2);
		summon(SummonerEntities.ROOTLING);
		summon(SummonerEntities.PAPER_KITE);
		owner.setShiftKeyDown(true);
		ItemStack st = new ItemStack(SummonerItems.ROOTCALL_STAFF);
		owner.setItemInHand(InteractionHand.MAIN_HAND, st);
		SummonerItems.ROOTCALL_STAFF.use(level, owner, InteractionHand.MAIN_HAND);
		l = MinionManager.minions(owner);
		check(l.size() == 1 && l.get(0) instanceof PaperKite, "sneak + use dismisses only that staff's minions");
		owner.setShiftKeyDown(false);
	}

	private static void movement() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		for (int i = 0; i < 20; i++) {
			owner.setPos(owner.getX() + 0.5, Y, 0.5);
			tick(1);
		}
		tick(100);
		check(r.distanceTo(owner) <= GearStats.FOLLOW_MAX + 0.5, f("Rootling follows its owner on foot (dist %.1f after the owner walked 10 blocks)", r.distanceTo(owner)));
		owner.setPos(owner.getX() + 30, Y, 0.5);
		tick(2);
		check(r.distanceTo(owner) <= 4, f("owner teleports 30 blocks: the minion appears next to them (dist %.1f)", r.distanceTo(owner)));
		reset();
		MinionEntity k = summon(SummonerEntities.PAPER_KITE);
		for (int i = 0; i < 20; i++) {
			owner.setPos(owner.getX() + 0.4, Y, 0.5);
			tick(1);
		}
		tick(80);
		double dy = k.getY() - owner.getY();
		double dh = Math.hypot(k.getX() - owner.getX(), k.getZ() - owner.getZ());
		check(dy >= 2.0 && dy <= 6.5 && dh <= 4, f("Paper Kite hovers above the owner's head (dy %.1f, horizontal %.1f)", dy, dh));
	}

	private static void targeting() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		Mob husk = spawn(EntityTypes.HUSK, 3.5, 0.5, true);
		Mob cow = spawn(EntityTypes.COW, -3.5, 0.5, true);
		owner.setLastHurtMob(husk);
		float hp0 = husk.getHealth();
		tick(60, husk);
		check(husk.getHealth() < hp0, f("Rootling attacks what its owner hit (husk %.1f -> %.1f)", hp0, husk.getHealth()));
		check(Math.abs(MinionApi.LAST_HIT_DAMAGE - GearStats.ROOTLING_DAMAGE * GearStats.ATTUNE_DAMAGE[0]) < 0.01, f("Rootling hit = 4 x %.2f (Summoner Calling 0) = %.2f", GearStats.ATTUNE_DAMAGE[0], MinionApi.LAST_HIT_DAMAGE));
		check(husk.getLastHurtByMob() == owner || husk.getLastHurtByPlayer() == owner, "minion hits are credited to the owner");
		check(Rootling.rootsApplied > 0 && Rootling.rootsApplied <= 1, f("Rootling hits root non-boss targets (Slowness II), once per 4 s (%d roots in 3 s)", Rootling.rootsApplied));
		husk.discard();
		// what hurt the owner
		owner.setLastHurtMob(null);
		Mob husk2 = spawn(EntityTypes.HUSK, 0.5, 4.5, true);
		owner.setLastHurtByMob(husk2);
		tick(12, husk2);
		check(r.getTarget() == husk2, "targets what hurt its owner");
		husk2.discard();
		owner.setLastHurtByMob(null);
		// nearest Enemy targeting the owner
		Mob z = spawn(EntityTypes.HUSK, -4.5, 0.5, true);
		z.setTarget(owner);
		tick(12, z);
		check(r.getTarget() == z, "targets the nearest Enemy that is targeting its owner");
		z.discard();
		// passive mobs never, even when the owner hits them
		owner.setLastHurtMob(cow);
		float c0 = cow.getHealth();
		tick(40, cow);
		check(cow.getHealth() == c0 && r.getTarget() != cow, "never targets passive mobs");
		cow.discard();
	}

	private static void protection() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		FakePlayer other = fake("summoner_other");
		other.setPos(2.5, Y, 0.5);
		Mob villager = spawn(EntityTypes.VILLAGER, -2.5, 0.5, true);
		var wolf = spawn(EntityTypes.WOLF, 0.5, 2.5, true);
		wolf.tame(owner);
		Mob golem = spawn(EntityTypes.IRON_GOLEM, 0.5, -3.5, true);
		LivingEntity[] prot = {other, villager, wolf, golem};
		boolean safe = true;
		for (LivingEntity v : prot) {
			owner.setLastHurtMob(v);
			float h = v.getHealth();
			tick(30, v);
			if (v.getHealth() < h || r.getTarget() == v) safe = false;
			if (r.hitTarget(level, owner, v, 10)) safe = false;
			if (v.hurtServer(level, MinionApi.damageSource(level, r, owner), 5F)) safe = false;
		}
		check(safe, "never targets or damages players, tamed pets, villagers or golems (AI, direct hit and damage guard)");
		MinionEntity r2 = summonFor(other);
		check(r2 != null && !MinionTargeting.isValidTarget(owner, r, r2, MinionTargeting.Reason.OWNER_HIT) && !r2.hurtServer(level, MinionApi.damageSource(level, r, owner), 5F),
			"never targets or damages other minions");
		check(!r.hurtServer(level, owner.damageSources().playerAttack(owner), 5F), "its owner's swings don't hurt it (and it isn't pickable)");
		MinionManager.dismissAll(other.getUUID());
		villager.discard();
		wolf.discard();
		golem.discard();
	}

	/** User decisions 20:35/20:40: automatic targeting only for hostiles (+ self-defence); everything else only by a Crook order. */
	private static void orders() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		// passive: the owner's hit doesn't redirect minions, a Crook mark does
		Mob cow = spawn(EntityTypes.COW, 2.5, 0.5, true);
		owner.setLastHurtMob(cow);
		float c0 = cow.getHealth();
		tick(30, cow);
		check(cow.getHealth() == c0 && r.getTarget() != cow, "owner hits a cow: minions ignore it");
		dev.elsewhere.minion.Herding.mark(owner, cow);
		tick(30, cow);
		check(cow.getHealth() < c0, f("Crook-marked cow: minions attack it (%.1f -> %.1f)", c0, cow.getHealth()));
		dev.elsewhere.minion.Herding.callOff(owner);
		owner.setLastHurtMob(null);
		cow.discard();
		// neutral: ignored when the owner hits them
		Mob wolf = spawn(EntityTypes.WOLF, 2.5, 0.5, true);
		Mob ender = spawn(EntityTypes.ENDERMAN, -2.5, 0.5, true);
		boolean ign = true;
		for (Mob n : new Mob[]{wolf, ender}) {
			owner.setLastHurtMob(n);
			float h = n.getHealth();
			tick(30, n);
			if (n.getHealth() < h || r.getTarget() == n) ign = false;
		}
		check(ign, "owner hits a wolf or an enderman (neutral): minions ignore them");
		owner.setLastHurtMob(null);
		wolf.discard();
		// self-defence: a neutral mob attacking the owner
		ender.setTarget(owner);
		tick(20, ender);
		check(r.getTarget() == ender, "an enderman attacking its owner: minions defend");
		ender.setTarget(null);
		tick(2, ender);
		check(r.getTarget() != ender, "...and let it go once it stops attacking");
		ender.discard();
		// hostile: automatic
		Mob zombie = spawn(EntityTypes.ZOMBIE, 2.5, -1.5, true);
		zombie.setTarget(owner);
		float z0 = zombie.getHealth();
		tick(30, zombie);
		check(zombie.getHealth() < z0, "a hostile zombie targeting its owner is attacked automatically");
		zombie.discard();
		// villager and someone else's pet: default safe, Crook order attacks
		FakePlayer other = fake("summoner_other_orders");
		other.setPos(6.5, Y, 6.5);
		Mob villager = spawn(EntityTypes.VILLAGER, 2.5, 0.5, true);
		var pet = spawn(EntityTypes.WOLF, -2.5, 0.5, true);
		pet.tame(other);
		boolean safe = true, ordered = true;
		for (Mob v : new Mob[]{villager, pet}) {
			owner.setLastHurtMob(v);
			float h = v.getHealth();
			tick(20, v);
			if (v.getHealth() < h) safe = false;
			dev.elsewhere.minion.Herding.mark(owner, v);
			tick(30, v);
			if (!(v.getHealth() < h)) ordered = false;
			dev.elsewhere.minion.Herding.callOff(owner);
			owner.setLastHurtMob(null);
		}
		check(safe, "owner hits a villager or someone's pet: minions ignore them");
		check(ordered, "Crook-marked villager and someone else's pet: minions attack them");
		// call-off
		villager.setHealth(villager.getMaxHealth());
		dev.elsewhere.minion.Herding.mark(owner, villager);
		tick(10, villager);
		dev.elsewhere.minion.Herding.callOff(owner);
		tick(1, villager);
		float v1 = villager.getHealth();
		tick(30, villager);
		check(!dev.elsewhere.minion.Herding.isHerdedBy(owner, villager) && r.getTarget() != villager && villager.getHealth() == v1, "sneak-use of the Crook calls the minions off");
		villager.discard();
		pet.discard();
		// players: only with pvp on (FakePlayers ignore damage, so check the rules)
		var rules = level.getGameRules();
		boolean pvp0 = rules.get(net.minecraft.world.level.gamerules.GameRules.PVP);
		rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, true, level.getServer());
		owner.setLastHurtMob(other);
		boolean auto = MinionTargeting.pick(owner, r) == other;
		dev.elsewhere.minion.Herding.mark(owner, other);
		boolean on = MinionTargeting.pick(owner, r) == other && MinionTargeting.mayDamage(owner, other);
		rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, false, level.getServer());
		boolean off = !MinionTargeting.isValidTarget(owner, r, other, MinionTargeting.Reason.KEEP) && !MinionTargeting.mayDamage(owner, other);
		rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, pvp0, level.getServer());
		dev.elsewhere.minion.Herding.callOff(owner);
		owner.setLastHurtMob(null);
		check(!auto && on && off, f("Crook-marked player: attacked only while pvp is on (auto %s, pvp on %s, pvp off refused %s)", auto, on, off));
		// never the owner or the owner's own pets
		var myPet = spawn(EntityTypes.WOLF, 0.5, 2.5, true);
		myPet.tame(owner);
		dev.elsewhere.minion.Herding.mark(owner, myPet);
		float p0 = myPet.getHealth();
		tick(30, myPet);
		check(myPet.getHealth() == p0 && !MinionTargeting.mayDamage(owner, myPet) && !MinionTargeting.mayDamage(owner, owner) && !MinionTargeting.ordered(owner, owner),
			"never the owner or the owner's own pets, even Crook-marked");
		dev.elsewhere.minion.Herding.callOff(owner);
		myPet.discard();
	}

	/** ARSENAL rev 4 Herd focus: own damage -10% per minion out beyond the first; clears when the herd leaves. */
	private static void herdFocus() {
		reset();
		var inst = owner.getAttribute(SummonerAttributes.slots());
		net.minecraft.resources.Identifier id = dev.elsewhere.summoner.SummonerContent.id("selftest.herd_slots");
		inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, 3, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		summon(SummonerEntities.ROOTLING);
		SummonerPerks.updateHerdFocus(owner);
		check(Math.abs(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 0.001, "Herd focus: one minion out is free (own damage x1.00)");
		summon(SummonerEntities.ROOTLING);
		summon(SummonerEntities.ROOTLING);
		SummonerPerks.updateHerdFocus(owner);
		double d = owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
		check(MinionManager.count(owner) == 3 && Math.abs(d - 0.8) < 0.001 && Math.abs(SummonerPerks.arrowMultiplier(owner) - 0.8) < 0.001,
			f("Herd focus: 3 minions out -> own melee and arrows -20%% (%.2f)", d));
		MinionManager.dismissAll(owner.getUUID());
		SummonerPerks.updateHerdFocus(owner);
		check(Math.abs(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 0.001, "Herd focus clears when the herd goes home");
		inst.removeModifier(id);
	}

	private static MinionEntity summonFor(ServerPlayer p) {
		ItemStack st = new ItemStack(SummonerItems.ROOTCALL_STAFF);
		p.setItemInHand(InteractionHand.MAIN_HAND, st);
		return ((MinionStaffItem) SummonerItems.ROOTCALL_STAFF).summonNow(p, st);
	}

	private static void killsAndLoot() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		Mob s = spawn(EntityTypes.SILVERFISH, 2.5, 0.5, true);
		owner.setLastHurtMob(s);
		tick(120, s);
		AABB box = new AABB(s.blockPosition()).inflate(6);
		int orbs = level.getEntitiesOfClass(ExperienceOrb.class, box).size();
		check(!s.isAlive() && orbs > 0, f("a minion kill counts as the owner's (dead=%s, %d XP orbs)", !s.isAlive(), orbs));
		level.getEntitiesOfClass(ExperienceOrb.class, box.inflate(10)).forEach(Entity::discard);
		level.getEntitiesOfClass(ItemEntity.class, box.inflate(10)).forEach(Entity::discard);
		BlockPos at = r.blockPosition();
		r.hurtServer(level, level.damageSources().generic(), 1000F);
		for (int i = 0; i < 30; i++) {
			r.tickCount++;
			if (!r.isRemoved()) r.tick();
		}
		AABB b2 = new AABB(at).inflate(4);
		check(level.getEntitiesOfClass(ExperienceOrb.class, b2).isEmpty() && level.getEntitiesOfClass(ItemEntity.class, b2).isEmpty(), "a dead minion drops no loot and no XP");
	}

	private static void despawn() {
		reset();
		summon(SummonerEntities.ROOTLING);
		ServerPlayerEvents.LEAVE.invoker().onLeave(owner);
		check(MinionManager.count(owner) == 0, "owner logout: minions go away");
		summon(SummonerEntities.ROOTLING);
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(owner, owner.damageSources().generic());
		check(MinionManager.count(owner) == 0, "owner death: minions go away");
		MinionEntity m = summon(SummonerEntities.ROOTLING);
		owner.setHealth(0F);
		tick(2);
		check(m.isRemoved(), "a dead owner's minion leaves on its next tick");
		owner.setHealth(owner.getMaxHealth());
		summon(SummonerEntities.ROOTLING);
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.invoker().afterChangeLevel(owner, level, nether == null ? level : nether);
		check(MinionManager.count(owner) == 0, "owner changes dimension: minions go away");
		summon(SummonerEntities.ROOTLING);
		EntitySleepEvents.START_SLEEPING.invoker().onStartSleeping(owner, owner.blockPosition());
		check(MinionManager.count(owner) == 0, "owner sleeps: minions go home");
		m = summon(SummonerEntities.ROOTLING);
		OWNERS.remove(owner.getUUID());
		tick(1);
		m.tickCount++;
		m.tick();
		OWNERS.put(owner.getUUID(), owner);
		check(m.isRemoved(), "owner no longer online: the minion removes itself");
	}

	private static void fervor() {
		reset();
		MinionEntity m = summon(SummonerEntities.ROOTLING);
		int t0 = GearStats.ATTUNE_FERVOR_TICKS[0];
		check(m.fervorTicks == t0, f("battle timer starts at %d s at Summoner Calling 0 (%d ticks)", t0 / 20, m.fervorTicks));
		m.fervorTicks = 100;
		Mob husk = spawn(EntityTypes.HUSK, 6.5, 6.5, true);
		husk.hurtServer(level, owner.damageSources().playerAttack(owner), 1F);
		check(m.fervorTicks == t0, "owner hitting a hostile mob resets the battle timer");
		m.fervorTicks = 100;
		ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(owner, owner.damageSources().mobAttack(husk), 1F);
		check(m.fervorTicks == t0, "a hostile mob hitting the owner resets the battle timer");
		husk.discard();
		m.fervorTicks = 3;
		owner.setLastHurtMob(null);
		tick(5);
		check(m.isRemoved(), "fervor runs out: the minion goes home");
		reset();
		m = summon(SummonerEntities.ROOTLING);
		m.fervorTicks = 100;
		TrailBiscuitItem.feedMinions(owner);
		check(m.fervorTicks <= 100, "Trail Biscuit does not extend the fervor timer");
	}

	private static void armor() {
		reset();
		Object[][] sets = {
			{new ItemStack[]{new ItemStack(SummonerItems.HERDCALLER_CAP), new ItemStack(SummonerItems.HERDCALLER_VEST), new ItemStack(SummonerItems.HERDCALLER_LEGGINGS), new ItemStack(SummonerItems.HERDCALLER_BOOTS)}, 20.0, 8.0, 0.0, "Herdcaller"},
			{new ItemStack[]{new ItemStack(SummonerItems.LINED_HERDCALLER_CAP), new ItemStack(SummonerItems.LINED_HERDCALLER_VEST), new ItemStack(SummonerItems.LINED_HERDCALLER_LEGGINGS), new ItemStack(SummonerItems.LINED_HERDCALLER_BOOTS)}, 20.0, 12.0, 0.2, "Lined Herdcaller"},
		};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (Object[] set : sets) {
			ItemStack[] st = (ItemStack[]) set[0];
			for (int i = 0; i < 4; i++) equip(slots[i], st[i]);
			double armor = owner.getAttributeValue(Attributes.ARMOR), tough = owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS), kb = owner.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			check(Math.abs(armor - (Double) set[1]) < 0.01 && Math.abs(tough - (Double) set[2]) < 0.01 && Math.abs(kb - (Double) set[3]) < 0.01,
				f("%s: armor %.0f / toughness %.0f / KB %.2f", set[4], armor, tough, kb));
			check(MinionApi.maxSlots(owner) == 3 && Math.abs(MinionApi.damageMultiplier(owner) - 1.15) < 0.001,
				f("%s: 3 slots, minion_damage x%.2f (leggings +15%%)", set[4], MinionApi.damageMultiplier(owner)));
			check(Math.abs(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1 + 4 * GearStats.OWN_DAMAGE_PER_PIECE)) < 0.001, f("%s: own attack damage %+.0f%% per piece (%.2f of base 1 with 4 pieces)", set[4], GearStats.OWN_DAMAGE_PER_PIECE * 100, owner.getAttributeValue(Attributes.ATTACK_DAMAGE)));
			check(SummonerPerks.packBond(owner), set[4] + ": full set gives Pack Bond");
			check(SummonerPerks.teleportDistance(owner) == GearStats.TELEPORT_DISTANCE_BOOTS && Math.abs(SummonerPerks.speedMultiplier(owner) - 1.15) < 0.001, set[4] + " boots: minions teleport from 16 blocks and move 15% faster");
		}
		Arrow arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
		double base = ((AbstractArrowAccessor) arrow).elsewhere_summoner$getBaseDamage();
		level.addFreshEntity(arrow);
		double after = ((AbstractArrowAccessor) arrow).elsewhere_summoner$getBaseDamage();
		check(Math.abs(after - base * (1 + 4 * GearStats.OWN_DAMAGE_PER_PIECE)) < 0.001, f("arrows of a full summoner kit are cut the same way (%.2f -> %.2f)", base, after));
		arrow.discard();
		unequipAll();
		equip(EquipmentSlot.HEAD, new ItemStack(SummonerItems.ELDER_BARK_CROWN));
		equip(EquipmentSlot.CHEST, new ItemStack(SummonerItems.ENGINEERS_HARNESS));
		check(MinionApi.maxSlots(owner) == 3 && Math.abs(MinionApi.damageMultiplier(owner) - 1.15) < 0.001 && owner.getAttributeValue(Attributes.ARMOR) == 11
			&& owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 4, f("Elder Bark Crown + Engineer's Harness: 3 slots, minion_damage x1.15, armor 11 / toughness 4 (%.0f / %.0f)",
			owner.getAttributeValue(Attributes.ARMOR), owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
		check(!SummonerPerks.packBond(owner), "boss pieces don't count for Pack Bond");
	}

	private static void perks() {
		reset();
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		var set = new net.minecraft.world.item.Item[]{SummonerItems.HERDCALLER_CAP, SummonerItems.HERDCALLER_VEST, SummonerItems.HERDCALLER_LEGGINGS, SummonerItems.HERDCALLER_BOOTS};
		for (int i = 0; i < 4; i++) equip(slots[i], new ItemStack(set[i]));
		MinionEntity m = summon(SummonerEntities.ROOTLING);
		m.hurtServer(level, level.damageSources().generic(), 6F);
		float h0 = m.getHealth();
		tick(21);
		check(m.getHealth() > h0 && m.getAttributeValue(Attributes.ARMOR) == GearStats.ROOTLING_ARMOR + GearStats.PACK_BOND_ARMOR,
			f("Pack Bond: minions nearby heal 1 HP/s and get +4 armor (hp %.0f -> %.0f, armor %.0f)", h0, m.getHealth(), m.getAttributeValue(Attributes.ARMOR)));
		m.hurtServer(level, level.damageSources().generic(), 1000F);
		check(Math.abs(GlimmerStub.REFUNDED.getOrDefault(owner.getUUID(), 0F) - GearStats.PACK_BOND_REFUND) < 0.01, "Pack Bond: a dying minion refunds 10 Glimmer");
		// minion damage with leggings
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		Mob husk = spawn(EntityTypes.HUSK, 2.5, 0.5, true);
		r.hitTarget(level, owner, husk, GearStats.ROOTLING_DAMAGE);
		check(Math.abs(MinionApi.LAST_HIT_DAMAGE - 4 * 1.15 * GearStats.ATTUNE_DAMAGE[4]) < 0.01, f("minion_damage +15%% at Calling 4: Rootling hit 4 -> %.2f", MinionApi.LAST_HIT_DAMAGE));
		husk.discard();
		unequipAll();
		// Elder Bark Crown + Engineer's Harness
		equip(EquipmentSlot.HEAD, new ItemStack(SummonerItems.ELDER_BARK_CROWN));
		equip(EquipmentSlot.CHEST, new ItemStack(SummonerItems.ENGINEERS_HARNESS));
		MinionEntity rc = summon(SummonerEntities.ROOTLING);
		tick(21);
		check(Math.abs(rc.getMaxHealth() - GearStats.ROOTLING_HP * 1.5) < 0.01, f("Elder Bark Crown: Rootlings +50%% HP (%.0f)", rc.getMaxHealth()));
		check(!rc.hurtServer(level, level.damageSources().inFire(), 5F) && !rc.hurtServer(level, level.damageSources().lightningBolt(), 5F), "Engineer's Harness: minions immune to fire and lightning");
		unequipAll();
		MinionEntity rn = summon(SummonerEntities.ROOTLING);
		check(rn.hurtServer(level, level.damageSources().inFire(), 1F), "without the harness, fire hurts minions");
	}

	private static void consumableAndTrinket() {
		reset();
		MinionEntity m = summon(SummonerEntities.ROOTLING);
		m.setHealth(5F);
		ItemStack biscuit = new ItemStack(SummonerItems.TRAIL_BISCUIT);
		owner.setItemInHand(InteractionHand.MAIN_HAND, biscuit);
		biscuit.getItem().finishUsingItem(biscuit, level, owner);
		check(m.hasEffect(SummonerEffects.WELL_FED) && m.getEffect(SummonerEffects.WELL_FED).getDuration() == GearStats.WELL_FED_TICKS, "Trail Biscuit: minions get Well Fed for 3 min");
		check(Math.abs(m.getHealth() - 15F) < 0.01, f("Trail Biscuit heals minions 50%% (5 -> %.0f)", m.getHealth()));
		check(m.getAttributeValue(Attributes.ARMOR) == GearStats.ROOTLING_ARMOR + GearStats.WELL_FED_ARMOR, "Well Fed: +4 armor");
		Mob husk = spawn(EntityTypes.HUSK, 2.5, 0.5, true);
		m.hitTarget(level, owner, husk, GearStats.ROOTLING_DAMAGE);
		check(Math.abs(MinionApi.LAST_HIT_DAMAGE - 4 * GearStats.ATTUNE_DAMAGE[0] * 1.2) < 0.01, f("Well Fed: +20%% damage (4 x Calling 0 x 1.2 -> %.2f)", MinionApi.LAST_HIT_DAMAGE));
		husk.discard();
		TrailBiscuitItem.feedMinions(owner);
		check(m.getEffect(SummonerEffects.WELL_FED).getAmplifier() == 0, "a second biscuit refreshes Well Fed, never stacks");
		ItemStack fresh = new ItemStack(SummonerItems.TRAIL_BISCUIT);
		check(fresh.getMaxStackSize() == 16 && fresh.has(net.minecraft.core.component.DataComponents.FOOD), "Trail Biscuit stacks to 16 and can be eaten");
		// bell recall
		m.teleportTo(10.5, Y, 8.5);
		ItemStack bell = new ItemStack(SummonerItems.BELL_OF_THE_FLOCK);
		owner.setItemInHand(InteractionHand.MAIN_HAND, bell);
		SummonerItems.BELL_OF_THE_FLOCK.use(level, owner, InteractionHand.MAIN_HAND);
		check(m.distanceTo(owner) <= 4, f("ringing the Bell of the Flock calls minions to your side (dist %.1f)", m.distanceTo(owner)));
		check(bell.is(dev.elsewhere.stub.summoner.TrinketStub.TRINKET), "Bell of the Flock is tagged elsewhere:trinket");
	}

	private static Mob dummy(double x, double armor) {
		Mob m = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		m.snapTo(owner.getX() + x, Y, owner.getZ(), 0, 0);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(armor);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		level.addFreshEntity(m);
		m.setHealth(200);
		return m;
	}

	/** Phase 3 gear: Sporebloom Staff (Sporeling) and Gale Ribbon. */
	private static void v2Gear() {
		reset();
		owner.getAttribute(SummonerAttributes.slots()).setBaseValue(3);
		check(BuiltInRegistries.ITEM.containsKey(dev.elsewhere.summoner.SummonerContent.id("sporebloom_staff"))
			&& BuiltInRegistries.ITEM.containsKey(dev.elsewhere.summoner.SummonerContent.id("gale_ribbon"))
			&& BuiltInRegistries.ENTITY_TYPE.containsKey(dev.elsewhere.summoner.SummonerContent.id("sporeling")), "Sporebloom Staff, Gale Ribbon and Sporeling registered");
		check(Math.abs(dev.elsewhere.summoner.Sporeling.strength(0) - 1.0 / 3) < 0.001 && Math.abs(dev.elsewhere.summoner.Sporeling.strength(4) - 1.0) < 0.001,
			"Sporeling heal strength: 1/3 at Calling 0, full at Calling 4");
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		MinionEntity sp = summon(SummonerEntities.SPORELING);
		check(sp instanceof dev.elsewhere.summoner.Sporeling && MinionApi.reservedGlimmer(owner) == 12, f("Sporeling reserves 6 Glimmer (total with a Rootling at Calling 0 is %d)", MinionApi.reservedGlimmer(owner)));
		owner.setHealth(10F);
		r.setHealth(5F);
		((dev.elsewhere.summoner.Sporeling) sp).pulse(level, owner);
		check(Math.abs(owner.getHealth() - (10 + 4F / 3)) < 0.05 && Math.abs(r.getHealth() - (5 + 4F / 3)) < 0.05,
			f("Sporeling pulse heals the owner and other minions 4 x 1/3 (owner %.2f, rootling %.2f)", owner.getHealth(), r.getHealth()));
		r.discard(); // the Rootling would attack the husk
		Mob husk = dummy(5, 0);
		owner.setLastHurtMob(husk);
		tick(45, husk);
		check(husk.getHealth() == 200, "Sporeling never attacks");
		husk.discard();
		r = summon(SummonerEntities.ROOTLING);
		// Gale Ribbon: +30% minion damage on the tagged target only
		Mob t1 = dummy(3, 0), t2 = dummy(4, 0);
		r.hitTarget(level, owner, t1, 10);
		float plain = MinionApi.LAST_HIT_DAMAGE;
		dev.elsewhere.summoner.GaleRibbonItem.tag(owner, t1);
		check(dev.elsewhere.summoner.GaleRibbonItem.isTagged(owner, t1) && !dev.elsewhere.summoner.GaleRibbonItem.isTagged(owner, t2), "Gale Ribbon tags one target");
		t1.hurtServer(level, level.damageSources().generic(), 0F);
		r.hitTarget(level, owner, t1, 10);
		float tagged = MinionApi.LAST_HIT_DAMAGE;
		r.hitTarget(level, owner, t2, 10);
		check(Math.abs(tagged - plain * 1.3F) < 0.02 && Math.abs(MinionApi.LAST_HIT_DAMAGE - plain) < 0.02, f("tagged target takes +30%% (%.2f vs %.2f), others do not", tagged, plain));
		dev.elsewhere.summoner.GaleRibbonItem.clear(owner.getUUID());
		check(!dev.elsewhere.summoner.GaleRibbonItem.isTagged(owner, t1), "the tag can be cleared");
		t1.discard();
		t2.discard();
		check(((net.minecraft.world.item.component.ItemAttributeModifiers) new net.minecraft.world.item.ItemStack(SummonerItems.GALE_RIBBON).getComponents().get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS))
			.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && e.modifier().amount() == GearStats.RIBBON_REACH), "Gale Ribbon has +3 reach (6 blocks)");
		check(level.getServer().reloadableRegistries().getLootTable(dev.elsewhere.summoner.SummonerLoot.table("percolax")) != net.minecraft.world.level.storage.loot.LootTable.EMPTY
			&& level.getServer().reloadableRegistries().getLootTable(dev.elsewhere.summoner.SummonerLoot.table("kazehana")) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "Percolax and Kazehana loot tables exist (gear pools added by the modify event)");
		check(dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, true, 0) == 0.5F && dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, false, 0) == 0.33F && dev.elsewhere.boss.kit.DropRule.chance(dev.elsewhere.boss.kit.DropRule.Kind.OTHER, false, 9) == 0.6F, "percolax unique uses the shared drop rule: 50% first, 33% repeat, pity cap 60%");
		{
			var kit = dev.elsewhere.boss.kit.KitStore.of(level.getServer());
			kit.data().kills.remove(owner.getUUID().toString());
			boolean before = kit.hasKilled(owner.getUUID(), "percolax");
			int n = 0;
			for (int k = 0; k < 40; k++) n += dev.elsewhere.summoner.SummonerLoot.rollFor(owner, level, owner, "percolax", () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)).size();
			check(!before && kit.hasKilled(owner.getUUID(), "percolax") && n >= 8 && n <= 40, "percolax roll records the kill and drops sometimes (40 rolls, got " + n + ")");
			kit.data().kills.remove(owner.getUUID().toString());
			kit.data().pity.keySet().removeIf(x -> x.contains("percolax"));
			level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, owner.getBoundingBox().inflate(8)).forEach(net.minecraft.world.entity.Entity::discard);
		}
	}

	/** bal-minion-armor, bal-minion-survive, bal-kite-entry. */
	private static void v2Balance() {
		reset();
		owner.getAttribute(SummonerAttributes.slots()).setBaseValue(3);
		check(GearStats.KITE_DAMAGE == 4, "Paper Kite base damage is 4 (bal-kite-entry)");
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		Mob a = dummy(3, 20), b = dummy(5, 20);
		boolean hit = r.hitTarget(level, owner, a, 10);
		float dealt = MinionApi.LAST_HIT_DAMAGE;
		b.hurtServer(level, level.damageSources().mobAttack(r), dealt);
		float minionLoss = 200 - a.getHealth(), fullArmorLoss = 200 - b.getHealth();
		double expectedHalf = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(a, dealt, level.damageSources().mobAttack(r), 10, 0);
		check(hit && minionLoss > fullArmorLoss + 0.5 && Math.abs(minionLoss - expectedHalf) < 0.05,
			f("minion hit treats armor 20 as 10: lost %.2f vs %.2f at full armor (expected %.2f)", minionLoss, fullArmorLoss, expectedHalf));
		check(a.getAttributeValue(Attributes.ARMOR) == 20 && a.getAttribute(Attributes.ARMOR).getModifiers().isEmpty(), "the armor modifier is removed after the hit");
		a.discard();
		b.discard();
		// boss hits are capped at 60% of the minion's max HP; other mobs are not
		var wither = EntityTypes.WITHER.create(level, EntitySpawnReason.COMMAND);
		wither.snapTo(owner.getX() + 4, Y, owner.getZ(), 0, 0);
		wither.setNoAi(true);
		level.addFreshEntity(wither);
		float max = r.getMaxHealth();
		r.hurtServer(level, level.damageSources().mobAttack(wither), 100F);
		float lost = max - r.getHealth();
		check(r.isAlive() && lost > 0 && lost <= max * 0.6 + 0.01, f("one boss hit takes at most 60%% of a minion's HP (lost %.1f of %.0f)", lost, max));
		wither.discard();
		Mob husk = dummy(6, 0);
		MinionEntity r2 = summon(SummonerEntities.ROOTLING);
		r2.hurtServer(level, level.damageSources().mobAttack(husk), 100F);
		check(!r2.isAlive(), "a non-boss hit is not capped");
		husk.discard();
	}

	private static void minionKinds() {
		reset();
		owner.getAttribute(SummonerAttributes.slots()).setBaseValue(3);
		MinionEntity k = summon(SummonerEntities.PAPER_KITE);
		Mob husk = spawn(EntityTypes.HUSK, 6.5, 0.5, true);
		k.setPos(owner.getX(), owner.getY() + 3, owner.getZ());
		Vec3 v0 = husk.getDeltaMovement();
		float hp = husk.getHealth();
		boolean hit = k.hitTarget(level, owner, husk, GearStats.KITE_DAMAGE);
		check(hit && Math.abs(MinionApi.LAST_HIT_DAMAGE - GearStats.KITE_DAMAGE * GearStats.ATTUNE_DAMAGE[2]) < 0.01, f("Paper Kite wind bolt: 4 x Calling 2 (starter staff, owner at Calling 0) = %.2f", MinionApi.LAST_HIT_DAMAGE));
		husk.hurtServer(level, level.damageSources().generic(), 0F);
		owner.setLastHurtMob(husk);
		husk.setHealth(husk.getMaxHealth());
		tick(45, husk);
		check(husk.getHealth() < husk.getMaxHealth(), f("Paper Kite shoots its owner's target from range (husk hp %.1f)", husk.getHealth()));
		check(husk.getDeltaMovement().horizontalDistance() > v0.horizontalDistance() || husk.getX() > 6.6, "Paper Kite bolts knock the target back");
		husk.discard();
		MinionEntity b = summon(SummonerEntities.BRASSBACK);
		check(b.getMaxHealth() == GearStats.BRASSBACK_HP && b.getAttributeValue(Attributes.ARMOR) == GearStats.BRASSBACK_ARMOR, "Brassback: 40 HP, armor 10");
		Mob zombie = spawn(EntityTypes.HUSK, b.getX() + 3, b.getZ(), false);
		var wither = EntityTypes.WITHER.create(level, EntitySpawnReason.COMMAND);
		wither.snapTo(b.getX() - 4, Y, b.getZ(), 0, 0);
		wither.setNoAi(true);
		level.addFreshEntity(wither);
		int n = ((Brassback) b).taunt(level, owner);
		check(zombie.getTarget() == b && wither.getTarget() != b, f("Brassback taunts non-boss mobs within 8 blocks (taunted %d); bosses ignore it", n));
		wither.discard();
		zombie.discard();
		b.hurtServer(level, level.damageSources().generic(), 10F);
		float h = b.getHealth();
		b.setPos(b.getX(), b.getY(), b.getZ());
		level.setBlock(b.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
		b.tickCount = 19;
		tick(2);
		level.setBlock(b.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
		check(b.getHealth() > h || !b.isInWater(), f("Brassback heals in water (%.0f -> %.0f, inWater=%s)", h, b.getHealth(), b.isInWater()));
	}

	private static void griefing() {
		reset();
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		BlockPos p = r.blockPosition();
		level.setBlock(p, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), 3);
		r.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		owner.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 2.5);
		tick(5);
		var st = level.getBlockState(p);
		check(st.is(Blocks.STONE_PRESSURE_PLATE) && !st.getValue(PressurePlateBlock.POWERED), "minions don't press pressure plates");
		level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
		check(r.isIgnoringBlockTriggers() && !r.canBeLeashed() && !r.isPickable() && !r.isPushable(), "minions ignore block triggers and can't be leashed, picked, pushed or ridden");
		check(!r.causeFallDamage(20, 1F, level.damageSources().fall()) && r.canBreatheUnderwater(), "minions take no fall damage and don't drown");
	}

	private static void calling() {
		reset();
		var pieces = new net.minecraft.world.item.Item[]{SummonerItems.HERDCALLER_CAP, SummonerItems.HERDCALLER_VEST, SummonerItems.HERDCALLER_LEGGINGS, SummonerItems.HERDCALLER_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		Double[] dmg = GearStats.ATTUNE_DAMAGE;
		Integer[] timer = GearStats.ATTUNE_FERVOR_TICKS;
		Integer[] reserve = GearStats.ATTUNE_RESERVE;
		Mob husk = spawn(EntityTypes.HUSK, 3.5, 0.5, true);
		boolean ok = true;
		StringBuilder sb = new StringBuilder();
		for (int c = 0; c <= 4; c++) {
			if (c > 0) equip(slots[c - 1], new ItemStack(pieces[c - 1]));
			MinionManager.dismissAll(owner.getUUID());
			MinionEntity r = summon(SummonerEntities.ROOTLING);
			r.hitTarget(level, owner, husk, 10.0);
			double legs = c >= 3 ? 1.15 : 1.0;
			boolean row = Math.abs(MinionApi.LAST_HIT_DAMAGE - 10.0 * dmg[c] * legs) < 0.01 && r.fervorTicks == timer[c] && MinionApi.reservedGlimmer(owner) == reserve[c];
			sb.append(f(" C%d: %.2f/%ds/%d", c, MinionApi.LAST_HIT_DAMAGE, r.fervorTicks / 20, MinionApi.reservedGlimmer(owner)));
			ok &= row;
			husk.setHealth(husk.getMaxHealth());
		}
		check(ok, "Summoner Calling 0..4 sets minion damage, battle timer and Glimmer reserve per GearStats:" + sb);
		unequipAll();
		MinionManager.dismissAll(owner.getUUID());
		MinionEntity k = summon(SummonerEntities.PAPER_KITE);
		check(k.fervorTicks == GearStats.ATTUNE_FERVOR_TICKS[2] && MinionApi.reservedGlimmer(owner) == GearStats.ATTUNE_RESERVE[2], "Kitestring Staff counts as Calling 2 for its kites (timer and reserve of Calling 2)");
		husk.discard();
	}

	private static void herding() {
		reset();
		ItemStack crook = new ItemStack(SummonerItems.HERDING_CROOK);
		equip(EquipmentSlot.MAINHAND, crook);
		check(Math.abs(owner.getAttributeValue(Attributes.ATTACK_DAMAGE) - GearStats.CROOK_DAMAGE) < 0.01 && Math.abs(owner.getAttributeValue(Attributes.ATTACK_SPEED) - 1.6) < 0.01
			&& Math.abs(owner.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - 4.5) < 0.01 && crook.getMaxDamage() == 1561,
			f("Herding Crook: " + (int) GearStats.CROOK_DAMAGE + " damage, 1.6 speed, reach 3+1.5, 1561 durability (%.1f / %.1f / %.1f)", owner.getAttributeValue(Attributes.ATTACK_DAMAGE),
				owner.getAttributeValue(Attributes.ATTACK_SPEED), owner.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)));
		owner.getAttribute(SummonerAttributes.slots()).setBaseValue(2);
		MinionEntity r = summon(SummonerEntities.ROOTLING);
		Mob a = spawn(EntityTypes.HUSK, 3.5, 0.5, true);
		Mob b = spawn(EntityTypes.HUSK, -3.5, 0.5, true);
		owner.setLastHurtMob(a);
		tick(12, a, b);
		LivingEntity before = r.getTarget();
		SummonerItems.HERDING_CROOK.hurtEnemy(crook, b, owner);
		check(b.hasEffect(SummonerEffects.HERDED) && dev.elsewhere.minion.Herding.isHerdedBy(owner, b), "a Crook hit marks the target Herded (5 s)");
		tick(12, a, b);
		check(before == a && r.getTarget() == b, "minions switch to the Herded target");
		r.hitTarget(level, owner, b, GearStats.ROOTLING_DAMAGE);
		check(Math.abs(MinionApi.LAST_HIT_DAMAGE - (GearStats.ROOTLING_DAMAGE * GearStats.ATTUNE_DAMAGE[0] + GearStats.HERDED_BONUS)) < 0.01, f("minion hits on a Herded target deal +1 flat (4 x Calling 0 + 1 = %.2f)", MinionApi.LAST_HIT_DAMAGE));
		SummonerItems.HERDING_CROOK.hurtEnemy(crook, a, owner);
		check(!dev.elsewhere.minion.Herding.isHerdedBy(owner, b) && dev.elsewhere.minion.Herding.isHerdedBy(owner, a), "one Herded target per player");
		a.discard();
		b.discard();
		equip(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		// 0.4.0: the Crook takes only Knockback, Unbreaking and Mending (table, anvil, loot, /enchant)
		var ench = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		StringBuilder bad = new StringBuilder();
		for (var k : List.of(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, net.minecraft.world.item.enchantment.Enchantments.MENDING)) {
			var h = ench.getOrThrow(k);
			// ACCEPTABLE = what Fabric's anvil, enchant_randomly and /enchant hooks ask (vanilla's Enchantment.canEnchant callers)
			if (!crook.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE)) bad.append(" refuses ").append(k.identifier().getPath());
		}
		for (var k : List.of(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, net.minecraft.world.item.enchantment.Enchantments.SMITE, net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS,
			net.minecraft.world.item.enchantment.Enchantments.LOOTING, net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE)) {
			var h = ench.getOrThrow(k);
			if (crook.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE) || crook.canBeEnchantedWith(h, net.fabricmc.fabric.api.item.v1.EnchantingContext.PRIMARY))
				bad.append(" takes ").append(k.identifier().getPath());
		}
		// enchanting table: every option it can roll for the Crook at level 30
		var table = net.minecraft.world.item.enchantment.EnchantmentHelper.getAvailableEnchantmentResults(30, crook,
			ench.get(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE).map(t -> t.stream()).orElse(java.util.stream.Stream.empty()));
		boolean tableKb = false;
		for (var ei : table) {
			if (!dev.elsewhere.summoner.HerdingCrookItem.allows(ei.enchantment())) bad.append(" table offers ").append(ei.enchantment().getRegisteredName());
			if (ei.enchantment().is(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK)) tableKb = true;
		}
		if (!tableKb) bad.append(" table lacks knockback");
		var sword = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
		boolean swordOk = sword.canBeEnchantedWith(ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), net.fabricmc.fabric.api.item.v1.EnchantingContext.ACCEPTABLE);
		check(bad.isEmpty() && swordOk, "Herding Crook takes Knockback, Unbreaking, Mending only (no Sharpness/Smite/Bane/Looting/Fire Aspect/Sweeping); swords unchanged" + bad);
	}
}
