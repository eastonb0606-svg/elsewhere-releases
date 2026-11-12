package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Ulfhednar set "Wolf-Fury" (Hrafnholt Berserker; GEAR.md A5): Ulfhednar Mask + Ulfhednar Pelt + Ulfhednar Breeches +
 * Wolfskin Boots (the Pelt and Boots are Hrafnholt drops). Each melee hit gives a Fury stack (max 5) for 4 s, refreshed by
 * every hit: +4% attack speed per stack. At 5 stacks each hit heals 10% of the melee damage dealt (5% against bosses).
 * The Mead-Horn of the Hall grants 3 stacks ({@link #grant}), with or without the set.
 *
 * <p>Events only: a landed hit (SetBonuses.AFTER_HIT) adds a stack and queues ONE expiry timer for that hit
 * ({@code Gear3Scheduler}, never re-queued); the timer clears the stacks only if no later stack came since. Attack speed is
 * one transient modifier (multiply total), so it syncs to the client like any attribute. Kill switch
 * {@code setbonus_wolf_fury}.
 */
public final class UlfhednarWolfFury {
	private UlfhednarWolfFury() {}

	public static final int MAX_STACKS = 5, STACK_TICKS = 80, HORN_STACKS = 3;
	public static final float SPEED_PER_STACK = 0.04F, HEAL = 0.10F, HEAL_BOSS = 0.05F;
	public static final Identifier SPEED_ID = Gear3Content.id("setbonus/wolf_fury.attack_speed");
	public static SetBonus SET;

	private static final class Fury {
		int stacks;
		long seq;
		long last;
		float perStack;
	}

	private static final Map<UUID, Fury> FURY = new HashMap<>();

	static void init() {
		SET = SetBonuses.register(SetBonus.of("wolf_fury")
			.piece(EquipmentSlot.HEAD, "hrafnholt_ulfhednar_mask")
			.piece(EquipmentSlot.CHEST, "hrafnholt_ulfhednar_pelt")
			.piece(EquipmentSlot.LEGS, "hrafnholt_ulfhednar_breeches")
			.piece(EquipmentSlot.FEET, "hrafnholt_wolfskin_boots")
			.details(3)
			.onChange((p, on) -> {
				if (!on) clear(p);
			}));
		SetBonuses.AFTER_HIT.add(UlfhednarWolfFury::afterHit);
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> FURY.remove(h.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> FURY.remove(oldP.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> FURY.clear());
	}

	/** Attack-speed multiplier at {@code stacks} stacks and bonus strength {@code power}: 1 + 0.04 x power x stacks. */
	public static float speedMult(int stacks, float power) {
		return 1F + SPEED_PER_STACK * Math.max(0F, power) * Math.max(0, Math.min(MAX_STACKS, stacks));
	}

	/** HP healed by a hit at full Fury: 10% of the damage dealt (5% against a boss), x power. */
	public static float healFor(float damageDealt, boolean boss, float power) {
		return Math.max(0F, damageDealt) * (boss ? HEAL_BOSS : HEAL) * Math.max(0F, power);
	}

	public static int stacks(ServerPlayer p) {
		Fury f = FURY.get(p.getUUID());
		if (f == null) return 0;
		return p.level().getGameTime() - f.last > STACK_TICKS ? 0 : f.stacks;
	}

	/** SetBonuses.AFTER_HIT: a wearer's melee hit landed on a mob. */
	public static void afterHit(LivingEntity victim, DamageSource src, float taken) {
		if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
		float pw = SetBonuses.power(p, SET);
		if (pw <= 0F || !SetBonusesB.target(p, victim)) return;
		int n = add(p, 1, pw);
		if (n >= MAX_STACKS && p.isAlive() && p.getHealth() < p.getMaxHealth()) {
			float heal = healFor(taken, BossCheck.isBoss(victim), pw);
			if (heal > 0F) p.heal(heal);
		}
	}

	/** The Mead-Horn of the Hall: +3 Fury stacks (for 4 s, like hit stacks); the set's strength if worn, else the base 4% each. */
	public static int grant(ServerPlayer p, int stacks) {
		if (!SET.enabled()) return 0;
		return add(p, stacks, Math.max(1F, SetBonuses.power(p, SET)));
	}

	/** Adds stacks (max 5), refreshes the 4 s window, updates the attack-speed modifier and queues this stack's expiry. */
	static int add(ServerPlayer p, int k, float power) {
		long now = p.level().getGameTime();
		Fury f = FURY.computeIfAbsent(p.getUUID(), u -> new Fury());
		if (now - f.last > STACK_TICKS) {
			f.stacks = 0;
			f.perStack = 0F;
		}
		f.stacks = Math.min(MAX_STACKS, f.stacks + k);
		f.last = now;
		f.perStack = Math.max(f.perStack, power); // a horn stack never weakens the set's own stacks
		final long seq = ++f.seq;
		setSpeed(p, speedMult(f.stacks, f.perStack) - 1F);
		Gear3Scheduler.after(p.level().getServer(), STACK_TICKS, () -> expire(p, f, seq));
		return f.stacks;
	}

	/** The expiry timer of one stack: clears Fury unless a later stack came (or this player's Fury was already reset). */
	static void expire(ServerPlayer p, Fury f, long seq) {
		if (FURY.get(p.getUUID()) != f || f.seq != seq) return;
		clear(p);
	}

	static void clear(ServerPlayer p) {
		FURY.remove(p.getUUID());
		setSpeed(p, 0F);
	}

	private static void setSpeed(ServerPlayer p, float amount) {
		AttributeInstance inst = p.getAttribute(Attributes.ATTACK_SPEED);
		if (inst == null) return;
		if (amount <= 0F) {
			inst.removeModifier(SPEED_ID);
			return;
		}
		inst.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	/** Self-test: the current attack-speed modifier amount (0 when none). */
	public static double speedModifier(ServerPlayer p) {
		AttributeInstance inst = p.getAttribute(Attributes.ATTACK_SPEED);
		AttributeModifier m = inst == null ? null : inst.getModifier(SPEED_ID);
		return m == null ? 0.0 : m.amount();
	}
}
