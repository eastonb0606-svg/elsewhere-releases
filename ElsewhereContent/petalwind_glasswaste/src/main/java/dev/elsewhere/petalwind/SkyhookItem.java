package dev.elsewhere.petalwind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Skyhook: a grappling rod. Use it while looking at a block within {@link #RANGE} blocks: the hook bites and the line
 * reels you toward it. (The doc's bobber is simplified to an instant hook with a visible line of particles.)
 */
public class SkyhookItem extends Item {
	public static final double RANGE = dev.elsewhere.petalwind_glasswaste.GearStats.SKYHOOK_RANGE;
	public static final int COOLDOWN_TICKS = dev.elsewhere.petalwind_glasswaste.GearStats.SKYHOOK_COOLDOWN;

	public SkyhookItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.FAIL;
		if (level instanceof ServerLevel server) {
			Vec3 target = hit.getLocation();
			Vec3 pull = target.subtract(player.position());
			double dist = pull.length();
			Vec3 v = pull.normalize().scale(Math.min(2.2, 0.6 + dist * 0.09)).add(0, 0.35, 0);
			player.setDeltaMovement(v);
			player.needsSync = true;
			player.resetFallDistance();
			for (int i = 0; i < 24; i++) {
				Vec3 p = eye.lerp(target, i / 24.0);
				server.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			server.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
			stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
