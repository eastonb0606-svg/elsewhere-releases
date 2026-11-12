package dev.elsewhere.petalwind;

import dev.elsewhere.petalwind_glasswaste.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/** Gale Heart (Tempestra's drop): use on a block to raise a temporary updraft column there for 30 s. 30 s cooldown. */
public class GaleHeartItem extends Item {
	public static final int DURATION_TICKS = dev.elsewhere.petalwind_glasswaste.GearStats.GALE_HEART_DURATION;
	public static final int COOLDOWN_TICKS = dev.elsewhere.petalwind_glasswaste.GearStats.GALE_HEART_COOLDOWN;

	public GaleHeartItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		BlockState old = level.getBlockState(pos);
		if (old.hasBlockEntity() || old.getDestroySpeed(level, pos) < 0) return InteractionResult.FAIL;
		// ARSENAL §1: 10 Glimmer instead of the old 30 s cooldown; the 2 s use delay is the item cooldown below.
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && !dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.trySpend(sp,
			dev.elsewhere.petalwind_glasswaste.GearStats.GALE_HEART_GLIMMER)) {
			dev.elsewhere.petalwind_glasswaste.ArsenalHooks.notEnoughGlimmer(sp);
			return InteractionResult.FAIL;
		}
		level.setBlockAndUpdate(pos, PetalwindBlocks.UPDRAFT_VENT.defaultBlockState());
		Tasks.later(level.getServer(), DURATION_TICKS, () -> {
			if (level.getBlockState(pos).is(PetalwindBlocks.UPDRAFT_VENT)) level.setBlockAndUpdate(pos, old);
		});
		level.playSound(null, pos, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.2F, 0.7F);
		if (player != null) player.getCooldowns().addCooldown(context.getItemInHand(), COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
