package dev.elsewhere.core.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** new-rift-anchor: your attuned Wayfinder lands next to your anchor in that dimension. One per player per dimension. */
public class RiftAnchorBlock extends Block {
	public RiftAnchorBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity placer, final ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof ServerPlayer sp) {
			Anchors.placeAnchor(sp, pos);
		}
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
				pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
		}
	}
}
