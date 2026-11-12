package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.Dmg;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Burning Glass: scope it while standing under open sky and a beam of focused sunlight burns what you look at,
 * ramping from 2 to 6 fire damage per second the longer you hold it on target.
 */
public class BurningGlassItem extends SpyglassItem {
	public BurningGlassItem(Properties properties) {
		super(properties);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!(level instanceof ServerLevel server)) return;
		int held = getUseDuration(stack, user) - ticksRemaining;
		if (!server.canSeeSky(user.blockPosition().above()) || !Heat.isSunlit(server)) {
			if (held % 20 == 0) server.sendParticles(ParticleTypes.SMOKE, user.getX(), user.getEyeY(), user.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			return;
		}
		LivingEntity target = Dmg.lookTarget(user, GearStats.BURNING_GLASS_RANGE);
		Vec3 eye = user.getEyePosition();
		Vec3 end = target != null ? target.getBoundingBox().getCenter() : eye.add(user.getLookAngle().scale(8));
		if (held % 4 == 0) beam(server, hand(user), end); // P13: every 4th tick, was 3rd (end rods linger 3 s)
		if (target != null && held % 20 == 0) {
			float dps = Math.min(GearStats.BURNING_GLASS_MAX_DPS, GearStats.BURNING_GLASS_MIN_DPS + held / 20.0F);
			target.hurtServer(server, Dmg.fire(server, user), dps);
			target.igniteForSeconds(2.0F);
			stack.hurtAndBreak(1, user, user.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
	}

	private static Vec3 hand(LivingEntity e) {
		Vec3 look = e.getLookAngle().normalize();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
		return e.getEyePosition().add(look.scale(0.7)).add(right.scale(0.45)).add(0, -0.4, 0);
	}

	public static void beam(ServerLevel level, Vec3 from, Vec3 to) {
		int n = (int) Math.max(4, from.distanceTo(to) * 2.5); // w4-vfx: 2.5 per block (0.4 spacing) so the beam is one line, not puffs
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.lerp(to, i / (double) n);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}
}
