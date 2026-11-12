package dev.elsewhere.stub.verdigris_ironpeak;

import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * STUB of the core's accidental-trip service: familiarity damping, hourly budgets, 2 h cooldown, 60 min grace,
 * pity timer, 3-second cancellable warning, safe arrival with a Frayed Wayfinder. Persisted in {@link StubPlayerData}.
 */
public final class Trips {
	public static double scale = 1.0;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private record Pending(TripTrigger trigger, int ticksLeft, float healthAtStart) {}

	private Trips() {}

	public static double playtimeHours(ServerPlayer p) {
		return p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000.0;
	}

	static String regionKey(ServerPlayer p) {
		return "fam:" + p.level().dimension().identifier() + ":" + Math.floorDiv(p.getBlockX(), TripMath.REGION) + ":" + Math.floorDiv(p.getBlockZ(), TripMath.REGION);
	}

	public static boolean nearHome(ServerPlayer p) {
		var respawn = p.getRespawnConfig();
		ServerLevel level = p.level();
		BlockPos home;
		if (respawn != null && respawn.respawnData().dimension() == level.dimension()) {
			home = respawn.respawnData().pos();
		} else if (level.dimension() == Level.OVERWORLD) {
			home = level.getRespawnData().pos();
		} else {
			return false;
		}
		return home.distSqr(p.blockPosition()) <= TripMath.HOME_RADIUS * TripMath.HOME_RADIUS;
	}

	/** Each minute of playtime adds to the familiarity of the region the player is in. Call every 20 ticks. */
	public static void tickFamiliarity(ServerPlayer p) {
		CompoundTag t = StubPlayerData.section(p, "trips");
		String key = regionKey(p);
		t.putDouble(key, t.getDoubleOr(key, 0) + 1.0 / 3600.0);
	}

	/** Fire a trigger event: rolls the damped chance and, on success, starts the 3 s warning. Returns the final chance. */
	public static double fire(ServerPlayer p, TripTrigger trigger) {
		CompoundTag t = StubPlayerData.section(p, "trips");
		double play = playtimeHours(p);
		double last = t.contains("last_trip") ? t.getDoubleOr("last_trip", 0) : Double.NEGATIVE_INFINITY;
		int hour = (int) play;
		String budgetKey = "budget:" + trigger.id() + ":" + hour;
		int events = t.getIntOr(budgetKey, 0);
		if (trigger.kind() == TripTrigger.Kind.ACTIVITY) t.putInt(budgetKey, events + 1);
		boolean blocked = p.isPassenger() || PENDING.containsKey(p.getUUID()) || inCombat(p) || inBossFight(p) || p.level().dimension() == trigger.target();
		double c = TripMath.chance(trigger.kind(), trigger.id(), trigger.baseChance(p), scale, play, play - last, t.getDoubleOr(regionKey(p), 0), nearHome(p), events, blocked);
		if (c > 0 && p.getRandom().nextDouble() < c) {
			PENDING.put(p.getUUID(), new Pending(trigger, 60, p.getHealth()));
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
			p.level().playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 0.6F);
		}
		return c;
	}

	static boolean inCombat(ServerPlayer p) {
		return p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 200;
	}

	static boolean inBossFight(ServerPlayer p) {
		return !p.level().getEntitiesOfClass(StubBoss.class, p.getBoundingBox().inflate(48), b -> b.isInside(p)).isEmpty();
	}

	/** Runs the warnings: particles each tick, cancelled by damage; teleports when done. */
	public static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) tickFamiliarity(p);
		PENDING.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) return true;
			Pending pend = e.getValue();
			if (p.getHealth() < pend.healthAtStart() - 0.01F || inCombat(p)) {
				p.removeEffect(MobEffects.DARKNESS);
				p.sendSystemMessage(Component.literal("The pull fades."));
				return true;
			}
			if (pend.ticksLeft() % 2 == 0) p.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.6, 0.9, 0.6, 0.05); // P13: was 8 every tick
			if (pend.ticksLeft() % 20 == 0) p.level().playSound(null, p.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.4F, 1.6F);
			if (pend.ticksLeft() > 0) {
				e.setValue(new Pending(pend.trigger(), pend.ticksLeft() - 1, pend.healthAtStart()));
				return false;
			}
			arrive(server, p, pend.trigger());
			return true;
		});
	}

	/** Safe arrival in the target dimension, a Frayed Wayfinder and a message. */
	public static boolean arrive(MinecraftServer server, ServerPlayer p, TripTrigger trigger) {
		ServerLevel target = server.getLevel(trigger.target());
		if (target == null) return false;
		BlockPos spot = safeSpot(target, new BlockPos(p.getRandom().nextInt(600) - 300, 0, p.getRandom().nextInt(600) - 300));
		CompoundTag t = StubPlayerData.section(p, "trips");
		t.putDouble("last_trip", playtimeHours(p));
		t.putLong("home_x", p.blockPosition().asLong());
		t.putString("home_dim", p.level().dimension().identifier().toString());
		p.teleport(new TeleportTransition(target, Vec3.atBottomCenterOf(spot), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
		p.getInventory().placeItemBackInInventory(new ItemStack(ReturnRiftStub.FRAYED_WAYFINDER), net.minecraft.util.Prediction.SERVER_ONLY);
		p.sendSystemMessage(Component.translatable("message.elsewhere.drifted", Component.translatable("dimension." + trigger.target().identifier().toLanguageKey())));
		VerdigrisIronpeakContent.LOG.info("{} drifted into {} ({})", p.getName().getString(), trigger.target().identifier(), trigger.id());
		return true;
	}

	/** Solid ground, no fluid underfoot, two blocks of air. Searches outward from the given column. */
	public static BlockPos safeSpot(ServerLevel level, BlockPos near) {
		for (int r = 0; r < 64; r += 4) {
			for (int a = 0; a < 8; a++) {
				int x = near.getX() + (int) (Math.cos(a * Math.PI / 4) * r), z = near.getZ() + (int) (Math.sin(a * Math.PI / 4) * r);
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos feet = new BlockPos(x, y, z);
				var below = level.getBlockState(feet.below());
				if (below.isSolid() && level.getFluidState(feet.below()).isEmpty() && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) return feet;
			}
		}
		BlockPos fallback = new BlockPos(near.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()), near.getZ());
		level.setBlockAndUpdate(fallback.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		return fallback;
	}
}
