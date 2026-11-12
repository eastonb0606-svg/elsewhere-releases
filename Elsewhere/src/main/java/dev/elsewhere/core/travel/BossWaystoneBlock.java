package dev.elsewhere.core.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** new-boss-waystones: appears at a boss arena after the boss falls. Use it to remember it; sneak-use a Wayfinder to warp back. */
public class BossWaystoneBlock extends Block {
	public BossWaystoneBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			Anchors.attuneWaystone(sp, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
			(random.nextDouble() - 0.5) * 1.5, -0.4, (random.nextDouble() - 0.5) * 1.5);
	}
}
