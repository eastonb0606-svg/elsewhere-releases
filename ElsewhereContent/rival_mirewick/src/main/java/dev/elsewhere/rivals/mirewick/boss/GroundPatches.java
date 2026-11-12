package dev.elsewhere.rivals.mirewick.boss;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Timed damaging or slowing areas (X5 GroundPatch): particles only, they never ignite, flood or edit a block. Affects players and
 * soldiers of other factions; never Mirewick's own. Used by Lantern Fire, Mud Surge, the Almoner's oil and the Mire-Trapper's mud.
 */
public final class GroundPatches {
	private GroundPatches() {}

	public enum Kind {
		/** Marrow's oil fire: raw 6 per second inside. */
		FIRE,
		/** Sorrel's mud and the Trapper's mud: Slowness II. */
		MUD,
		/** The Almoner's oil: Slowness I; ignites nothing. */
		OIL
	}

	public static final class Patch {
		public final ServerLevel level;
		public final Vec3 center;
		public final double radius;
		public final long expiry;
		public final Kind kind;
		public final UUID owner;
		public int hits;

		Patch(ServerLevel level, Vec3 center, double radius, long expiry, Kind kind, UUID owner) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.expiry = expiry;
			this.kind = kind;
			this.owner = owner;
		}

		public boolean contains(Vec3 p) {
			double dx = p.x - center.x, dz = p.z - center.z;
			return Math.abs(dy(p)) < 2.0 && dx * dx + dz * dz <= radius * radius;
		}

		private double dy(Vec3 p) {
			return p.y - center.y;
		}
	}

	private static final List<Patch> PATCHES = new ArrayList<>();

	public static Patch add(ServerLevel level, Vec3 center, double radius, int ticks, Kind kind, UUID owner) {
		Patch p = new Patch(level, center, radius, level.getGameTime() + ticks, kind, owner);
		PATCHES.add(p);
		return p;
	}

	public static List<Patch> active() {
		return PATCHES;
	}

	public static int count() {
		return PATCHES.size();
	}

	/** True when {@code pos} is inside a patch of {@code kind} that {@code except} did not lay (allies keep out of each other's fire). */
	public static boolean inForeign(ServerLevel level, Vec3 pos, Kind kind, UUID except) {
		for (Patch p : PATCHES) if (p.level == level && p.kind == kind && !p.owner.equals(except) && p.contains(pos)) return true;
		return false;
	}

	public static void clear() {
		PATCHES.clear();
	}

	static boolean victim(LivingEntity e) {
		if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
		return e instanceof KingdomSoldier s && !s.factionId().equals(MirewickContent.FACTION) && e instanceof KingdomSoldier;
	}

	public static void tick(MinecraftServer server) {
		if (PATCHES.isEmpty()) return;
		for (Iterator<Patch> it = PATCHES.iterator(); it.hasNext(); ) {
			Patch p = it.next();
			long now = p.level.getGameTime();
			if (now >= p.expiry || p.level.isClientSide()) {
				it.remove();
				continue;
			}
			if (now % 4 == 0) {
				ParticleOptions part = switch (p.kind) {
					case FIRE -> ParticleTypes.FLAME;
					case MUD -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
					case OIL -> ParticleTypes.SQUID_INK;
				};
				p.level.sendParticles(part, p.center.x, p.center.y + 0.15, p.center.z, 6 + (int) (p.radius * 3), p.radius * 0.55, 0.05, p.radius * 0.55, 0.01);
			}
			if (now % 20 != 0) continue;
			AABB box = new AABB(p.center.x - p.radius, p.center.y - 1.0, p.center.z - p.radius, p.center.x + p.radius, p.center.y + 2.5, p.center.z + p.radius);
			for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box, GroundPatches::victim)) {
				if (!p.contains(e.position())) continue;
				p.hits++;
				switch (p.kind) {
					case FIRE -> e.hurtServer(p.level, p.level.damageSources().magic(), 6.0F);
					case MUD -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, true, true));
					case OIL -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, true, true));
				}
			}
		}
	}

	/** The floor cell under {@code around}: first non-air cell below, so patches sit on the ground. */
	public static Vec3 onGround(ServerLevel level, Vec3 around) {
		BlockPos p = BlockPos.containing(around);
		for (int i = 0; i < 6 && level.getBlockState(p).isAir(); i++) p = p.below();
		return new Vec3(around.x, p.getY() + 1.0, around.z);
	}
}
