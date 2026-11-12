package dev.elsewhere.ironpeak;

import dev.elsewhere.verdigris_ironpeak.GearStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Avalanche Horn: on a snowy slope, sends a 7-wide wave of falling snow and powder snow downhill, shoving mobs with it. */
public final class AvalancheHorn {
	private AvalancheHorn() {}

	public static boolean blow(ServerLevel level, ServerPlayer player, ItemStack stack) {
		BlockPos feet = player.blockPosition();
		BlockState under = level.getBlockState(feet.below());
		boolean snowy = under.is(BlockTags.SNOW) || level.getBlockState(feet).is(Blocks.SNOW) || under.is(Blocks.PACKED_ICE) || under.is(Blocks.POWDER_SNOW);
		Vec3 downhill = downhill(level, feet);
		if (!snowy || downhill == null) {
			player.sendOverlayMessage(Component.literal("The horn echoes, but there is no snowy slope here."));
			return false;
		}
		Vec3 side = new Vec3(-downhill.z, 0, downhill.x);
		int half = GearStats.AVALANCHE_WIDTH / 2;
		for (int i = -half; i <= half; i++) {
			for (int row = 0; row < 2; row++) {
				Vec3 at = player.position().add(downhill.scale(2 + row * 1.5)).add(side.scale(i));
				BlockPos p = BlockPos.containing(at.x, player.getY() + 1.5 + row, at.z);
				if (!level.getBlockState(p).isAir()) continue;
				BlockState state = (i + row) % 3 == 0 ? Blocks.POWDER_SNOW.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
				FallingBlockEntity falling = FallingBlockEntity.fall(level, p, state);
				falling.setDeltaMovement(downhill.scale(0.9).add(0, 0.1, 0));
				falling.dropItem = false;
				falling.needsSync = true;
			}
		}
		AABB lane = player.getBoundingBox().expandTowards(downhill.scale(14)).inflate(half, 2, half);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, lane, e -> e != player && !(e instanceof Player) && e.isAlive())) {
			e.setDeltaMovement(e.getDeltaMovement().add(downhill.scale(1.4)).add(0, 0.3, 0));
			e.needsSync = true;
			e.hurtServer(level, level.damageSources().fallingBlock(player), 4.0F);
		}
		return true;
	}

	/** The steepest downhill direction within 6 blocks, or null on flat ground. */
	private static Vec3 downhill(ServerLevel level, BlockPos feet) {
		int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
		Vec3 best = null;
		int drop = 1;
		for (int a = 0; a < 8; a++) {
			double ang = Math.PI * 2 * a / 8;
			int x = feet.getX() + (int) Math.round(Math.cos(ang) * 6), z = feet.getZ() + (int) Math.round(Math.sin(ang) * 6);
			int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (here - h > drop) {
				drop = here - h;
				best = new Vec3(Math.cos(ang), 0, Math.sin(ang));
			}
		}
		return best;
	}
}
