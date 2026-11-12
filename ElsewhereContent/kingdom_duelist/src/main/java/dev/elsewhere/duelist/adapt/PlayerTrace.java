package dev.elsewhere.duelist.adapt;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;

/**
 * What the readers see of each player: shield, bow, pearls, heals, hits, high ground. Fed by a few server events and a
 * once-per-tick sample of players that a reader is watching (only those, so it costs nothing when no reader runs).
 */
public final class PlayerTrace {
	/** Per-player observations. Times are game ticks. */
	public static final class Trace {
		public long lastShieldUp = -100000, lastBowShot = -100000, lastGroundJump = -100000;
		public int pearlsThrown, healUses, potionsUsed;
		public long lastPearlLanded = -100000;
		public double lastPearlLandX, lastPearlLandY, lastPearlLandZ;
		public long lastArrowTick = -100000;
		public double lastArrowDist;
		public final ArrayDeque<Long> hits = new ArrayDeque<>();
		public final boolean[] shield = new boolean[200];
		public int shieldIdx;
		public long highGroundSince = -1;
		public boolean wasOnGround = true, wasSprinting, wasUsing;
		public double healStartDist;
		public long healStartTick = -100000;
		public long jumpTakeoffTick = -100000;
		public double jumpTakeoffDist = 1e9;
		public long watchedUntil;

		public double shieldFraction() {
			int n = 0;
			for (boolean b : shield) if (b) n++;
			return n / (double) shield.length;
		}
	}

	private PlayerTrace() {}

	private static final Map<UUID, Trace> TRACES = new HashMap<>();
	private static boolean inited;

	public static Trace of(Player p) {
		return TRACES.computeIfAbsent(p.getUUID(), k -> new Trace());
	}

	public static void forgetAll() {
		TRACES.clear();
	}

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof AbstractArrow a && a.getOwner() instanceof Player p) {
				Trace t = of(p);
				t.lastBowShot = level.getGameTime();
				t.lastArrowTick = level.getGameTime();
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof ThrownEnderpearl pearl && pearl.getOwner() instanceof Player p) {
				Trace t = of(p);
				t.pearlsThrown++;
				t.lastPearlLanded = level.getGameTime();
				t.lastPearlLandX = pearl.getX();
				t.lastPearlLandY = pearl.getY();
				t.lastPearlLandZ = pearl.getZ();
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof Player p && victim.level() instanceof ServerLevel l) {
				Trace t = of(p);
				TacticReader.onPlayerHit(victim, p, l.getGameTime());
				t.hits.addLast(l.getGameTime());
				while (t.hits.size() > 16) t.hits.pollFirst();
			}
		});
	}

	/** Marks a player as watched for the next 400 ticks (readers call this each tick). */
	public static void watch(Player p, long now) {
		of(p).watchedUntil = now + 400;
	}

	/** One sample of {@code p} (shield, heal use, sprint-jump take-off). Readers call it each tick for the player they watch. */
	public static void sample(Player p, long now) {
		Trace t = of(p);
		boolean up = p.isBlocking();
		t.shield[t.shieldIdx] = up;
		t.shieldIdx = (t.shieldIdx + 1) % t.shield.length;
		if (up) t.lastShieldUp = now;
		boolean using = p.isUsingItem();
		if (using && !t.wasUsing) {
			var it = p.getUseItem();
			if (it.is(Items.POTION) || it.is(Items.GOLDEN_APPLE) || it.is(Items.ENCHANTED_GOLDEN_APPLE) || it.has(net.minecraft.core.component.DataComponents.FOOD)) {
				t.healUses++;
				t.healStartTick = now;
				if (it.is(Items.POTION)) t.potionsUsed++;
			}
		}
		t.wasUsing = using;
		boolean ground = p.onGround();
		if (t.wasOnGround && !ground && p.getDeltaMovement().y > 0.2 && p.isSprinting()) t.jumpTakeoffTick = now;
		t.wasOnGround = ground;
		t.wasSprinting = p.isSprinting();
	}

	/** True if the player has a bow or crossbow in either hand. */
	public static boolean holdsRanged(LivingEntity p) {
		return p.getMainHandItem().is(Items.BOW) || p.getMainHandItem().is(Items.CROSSBOW) || p.getOffhandItem().is(Items.BOW) || p.getOffhandItem().is(Items.CROSSBOW);
	}

	public static boolean elytra(LivingEntity p) {
		return p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
	}
}
