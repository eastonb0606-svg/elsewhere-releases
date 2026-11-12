package dev.elsewhere.gear3.setbonus.b;

import dev.elsewhere.gear3.setbonus.SetBonuses;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/**
 * The (B) half of the gear3 set and pair bonuses (GEAR.md A5), on the shared SetBonus API (s-setbonus-a):
 * Command (Marshal's Panoply), the Outrider pair, Steady Aim (Ridgewatch), the Starsight pair, Zenith (Sunscribe) and
 * Wolf-Fury (Ulfhednar). One init call from {@code Gear3Content.init()}.
 *
 * <p>Lag rule: nothing here ticks or scans. Every effect runs on one game event (a hit, an arrow being loosed, a spell, a
 * Glimmer cost or regen step that arsenal already computes, a stance change) or on one scheduled expiry timer
 * ({@code Gear3Scheduler}), and each first checks the wearer's cached bonus mask ({@code SetBonuses.active/power}).
 *
 * <p>PvP: like the Callings (GEAR.md A1), none of these bonuses adds damage against players, and hits on players, armor
 * stands, pets, minions and villagers ({@link #target}) give no stacks, marks or Glimmer.
 *
 * <p>Kill switches (config/elsewhere-overhaul3.json, default on): {@code setbonus_command}, {@code setbonus_outrider},
 * {@code setbonus_steady_aim}, {@code setbonus_starsight}, {@code setbonus_zenith}, {@code setbonus_wolf_fury}; master
 * {@code set_bonuses}.
 */
public final class SetBonusesB {
	private SetBonusesB() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		SetBonuses.init(); // idempotent
		MarshalCommand.init();
		OutriderPair.init();
		RidgewatchSteadyAim.init();
		StarsightPair.init();
		SunscribeZenith.init();
		UlfhednarWolfFury.init();
		// arrows loosed by a set wearer: Outrider (sprinting) and Steady Aim (still or sneaking full draw) fix their bonus on the
		// arrow as a tag, read when it hits (the same pattern as arsenal's Marksman tag)
		ServerEntityEvents.ENTITY_LOAD.register(SetBonusesB::onEntityLoad);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> lastLoosed = null); // never keep a closed world's entity alive
	}

	static void onEntityLoad(Entity e, ServerLevel level) {
		if (!(e instanceof AbstractArrow arrow) || e instanceof ThrownTrident || arrow.tickCount != 0) return;
		if (!SetBonuses.anyone() || !(arrow.getOwner() instanceof ServerPlayer p)) return;
		onLoose(p, arrow);
	}

	/** The last arrow a set wearer loosed (self-test reads it). */
	public static AbstractArrow lastLoosed;

	/** An arrow a player just loosed (also called by the self-test). */
	public static void onLoose(ServerPlayer p, AbstractArrow arrow) {
		if (SetBonuses.mask(p) == 0L) return;
		lastLoosed = arrow;
		OutriderPair.onLoose(p, arrow);
		RidgewatchSteadyAim.onLoose(p, arrow);
	}

	/** A hit on this entity may feed a bonus (stacks, marks, Glimmer): a living non-player that is not an armor stand, a pet, a minion or a villager. */
	public static boolean target(ServerPlayer owner, LivingEntity victim) {
		if (victim == null || victim == owner || victim instanceof Player || victim instanceof ArmorStand) return false;
		if (victim instanceof OwnableEntity o && o.getOwner() != null) return false; // pets and minions
		return !(victim instanceof AbstractVillager); // a killing blow still counts (arsenal's Spells.friendly treats the dead as friendly)
	}

	/** The multiplier stored in an arrow tag {@code prefix + value}, or 0 when the arrow has none. */
	static float tagValue(Entity arrow, String prefix) {
		for (String t : arrow.entityTags()) {
			if (!t.startsWith(prefix)) continue;
			try {
				return Float.parseFloat(t.substring(prefix.length()));
			} catch (NumberFormatException ex) {
				return 0F;
			}
		}
		return 0F;
	}
}
