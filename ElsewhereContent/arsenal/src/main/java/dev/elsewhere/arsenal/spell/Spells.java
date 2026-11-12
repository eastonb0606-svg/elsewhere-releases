package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.ArsenalEffects;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.trinket.Trinkets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleBiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** Spell damage (ARSENAL §3 / §3b): the {@code elsewhere:spell} damage type and every multiplier in one place. */
public final class Spells {
	private Spells() {}

	public static final ResourceKey<DamageType> SPELL = ResourceKey.create(Registries.DAMAGE_TYPE, ArsenalContent.id("spell"));

	/** Called after every successful cast (owner, item). Other modules hook in here, e.g. the Hush adds Resonance. */
	public static final List<BiConsumer<ServerPlayer, Item>> ON_CAST = new ArrayList<>();
	/** Last computed damage, for the self-test. */
	public static float lastDamage;

	// ---------------------------------------------------------------- 3.0 hooks for other modules (gear3 items; kill switch spell_hooks)

	/** Extra spell-damage multipliers (owner, target; target may be null). Register once at init; read only when a spell hits. */
	public static final List<ToDoubleBiFunction<ServerPlayer, LivingEntity>> DAMAGE_MULT = new ArrayList<>();
	/** Crowd-control duration multipliers (owner, target), e.g. the Binding Sigil's +30% on non-bosses. Read by {@link #ccTicks}. */
	public static final List<ToDoubleBiFunction<ServerPlayer, LivingEntity>> CC_MULT = new ArrayList<>();
	private static float powerScale = 1F;

	/** Runs {@code r} with every spell damage it computes (including bolts it launches) scaled by {@code scale}; e.g. a 40% recast. */
	public static void withPowerScale(float scale, Runnable r) {
		float old = powerScale;
		powerScale = old * scale;
		try {
			r.run();
		} finally {
			powerScale = old;
		}
	}

	/** The scale set by {@link #withPowerScale} right now (1 outside it). */
	public static float powerScale() {
		return dev.elsewhere.arsenal.Overhaul3.SPELL_HOOKS ? powerScale : 1F;
	}

	/** A crowd-control duration in ticks after the {@link #CC_MULT} hooks: call it wherever your spell applies Slowness, Weakness, Dazed... */
	public static int ccTicks(ServerPlayer owner, LivingEntity target, int ticks) {
		if (!dev.elsewhere.arsenal.Overhaul3.SPELL_HOOKS) return ticks;
		double m = 1;
		for (ToDoubleBiFunction<ServerPlayer, LivingEntity> f : CC_MULT) m *= f.applyAsDouble(owner, target);
		return (int) Math.round(ticks * m);
	}

	public static DamageSource source(ServerLevel level, Entity direct, Entity owner) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SPELL), direct, owner);
	}

	/**
	 * Final spell damage before armor: base × spell_power × caster Calling × summoner-piece penalty × Dazed × Test Meter
	 * half bonus × Moon-Eye × Moonlit.
	 */
	public static float damage(ServerPlayer owner, LivingEntity target, float base, boolean starter, boolean moonlit) {
		double d = base * ArsenalAttributes.value(owner, ArsenalAttributes.SPELL_POWER);
		d *= Calling.spellMult(owner, starter);
		// summoner pieces' own -20% is a spell_power attribute modifier on those pieces (summoner module)
		if (ArsenalEffects.DAZED != null && owner.hasEffect(ArsenalEffects.DAZED)) d *= ArsenalStats.DAZED_SPELL_DAMAGE;
		d *= testMeterHalf(owner);
		d *= moonEyeBonus(owner, target);
		if (moonlit) d *= ArsenalStats.MOONLIT_BONUS;
		if (dev.elsewhere.arsenal.Overhaul3.SPELL_HOOKS) {
			d *= powerScale();
			for (ToDoubleBiFunction<ServerPlayer, LivingEntity> f : DAMAGE_MULT) d *= f.applyAsDouble(owner, target);
		}
		return lastDamage = (float) d;
	}

	public static boolean hurt(ServerPlayer owner, Entity direct, LivingEntity target, float damage) {
		DamageSource src = source((ServerLevel) owner.level(), direct, owner);
		// 3.0 testmeter_compat: a bolt is a Projectile, which Test Meter scales again on top of the half bonus already in damage()
		return target.hurtServer((ServerLevel) owner.level(), src, dev.elsewhere.arsenal.compat.TestMeterCompat.preCompensate(src, damage));
	}

	public static void afterCast(ServerPlayer p, Item item) {
		for (BiConsumer<ServerPlayer, Item> c : ON_CAST) c.accept(p, item);
	}

	/** Never hit by spell area effects: the caster, other players' pets, villagers, minions (and players unless PvP is a direct hit). */
	public static boolean friendly(ServerPlayer owner, Entity e) {
		if (e == owner) return true;
		if (e instanceof Player) return true;
		if (e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() != null) return true;
		if (e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) return true;
		return !(e instanceof LivingEntity) || !e.isAlive();
	}

	// ---------------------------------------------------------------- Test Meter (soft)

	private static Method tmData, tmMult;
	private static boolean tmLooked;

	/** Arrows, spells and minions get half the Test Meter melee bonus: 1 + (m − 1) / 2 (1.0 without Test Meter). */
	public static double testMeterHalf(Player owner) {
		if (!(owner instanceof ServerPlayer sp) || !FabricLoader.getInstance().isModLoaded("testmeter")) return 1.0;
		try {
			if (!tmLooked) {
				tmLooked = true;
				Class<?> tm = Class.forName("dev.testmeter.TestMeter");
				tmData = tm.getMethod("data", ServerPlayer.class);
				Class<?> dataClass = tmData.getReturnType();
				tmMult = Class.forName("dev.testmeter.Physiology").getMethod("damageMultiplier", dataClass);
			}
			if (tmData == null || tmMult == null) return 1.0;
			double m = ((Number) tmMult.invoke(null, tmData.invoke(null, sp))).doubleValue();
			return 1.0 + (m - 1.0) / 2.0;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			tmData = null;
			return 1.0;
		}
	}

	// ---------------------------------------------------------------- Moon-Eye (core item, caster trinket)

	/** +20% spell damage to a mob glowing from the owner's Moon-Eye while it is an active trinket. */
	public static double moonEyeBonus(Player owner, LivingEntity target) {
		Item eye = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", "moon_eye"));
		if (eye == null || eye == net.minecraft.world.item.Items.AIR || !Trinkets.isActive(owner, eye)) return 1.0;
		if (!target.hasEffect(MobEffects.GLOWING)) return 1.0;
		return markedBy(target, owner) ? ArsenalStats.MOON_EYE_BONUS : 1.0;
	}

	/** Moon-Eye marks: the core calls {@link #mark} when its Moon-Eye glows a mob. */
	private static final java.util.Map<java.util.UUID, java.util.UUID> MARKS = new java.util.WeakHashMap<>();

	public static void mark(LivingEntity target, Player by) {
		MARKS.put(target.getUUID(), by.getUUID());
	}

	private static boolean markedBy(LivingEntity target, Player owner) {
		java.util.UUID u = MARKS.get(target.getUUID());
		return u == null || u.equals(owner.getUUID());
	}
}
