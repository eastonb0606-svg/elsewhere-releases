package dev.elsewhere.ironpeak;

import dev.elsewhere.verdigris_ironpeak.GearStats;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Kingsplitter: a sneak-attack (hit while crouching) slams the ground and launches enemies within 3 blocks upward. */
public class KingsplitterItem extends Item {
	public KingsplitterItem(Properties properties) {
		super(properties);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		// BALANCE.md: disables shields for 5 s.
		if (target instanceof Player victim && victim.isBlocking() && !victim.getUseItem().isEmpty()) {
			victim.getCooldowns().addCooldown(victim.getUseItem(), 100);
			victim.stopUsingItem();
		}
		if (!(attacker instanceof Player player) || !player.isShiftKeyDown() || !(attacker.level() instanceof ServerLevel level)) return;
		if (player.getCooldowns().isOnCooldown(stack)) return;
		player.getCooldowns().addCooldown(stack, GearStats.KINGSPLITTER_SLAM_COOLDOWN);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3), e -> e != player && e.isAlive())) {
			e.setDeltaMovement(e.getDeltaMovement().add(0, GearStats.KINGSPLITTER_LAUNCH, 0));
			e.needsSync = true;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), target.getX(), target.getY(), target.getZ(), 40, 1.5, 0.1, 1.5, 0.2);
		level.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
	}
}
