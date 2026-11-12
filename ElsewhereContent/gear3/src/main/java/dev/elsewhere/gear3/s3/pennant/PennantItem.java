package dev.elsewhere.gear3.s3.pennant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Herdcaller's Pennant: use it on the ground to plant your standard there (in the air while standing: at your feet). The pennant
 * is kept and goes on a 60 s cooldown; see {@link Pennant} for the aura. A spot without a solid top or without 3 blocks of room
 * plants nothing (and says so), so a standard never floats or sticks out of a ceiling.
 */
public class PennantItem extends Item {
	public PennantItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player p = ctx.getPlayer();
		if (p == null) return InteractionResult.PASS;
		Level level = ctx.getLevel();
		BlockPos base = Pennant.target(level, ctx.getClickedPos(), ctx.getClickedFace());
		if (!p.mayUseItemAt(base, ctx.getClickedFace(), ctx.getItemInHand())) return InteractionResult.FAIL;
		return plant(level, p, ctx.getItemInHand(), base);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.onGround()) return InteractionResult.PASS;
		ItemStack st = player.getItemInHand(hand);
		BlockPos base = player.blockPosition();
		if (!player.mayUseItemAt(base, Direction.UP, st)) return InteractionResult.FAIL;
		return plant(level, player, st, base);
	}

	/** Plants at {@code base} (the pole's lowest block) for {@code p}; the cooldown starts when it stands. Self-test entry too. */
	public InteractionResult plant(Level level, Player p, ItemStack st, BlockPos base) {
		if (!Pennant.ENABLED) {
			if (p instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.elsewhere.herdcaller_pennant.disabled"));
			return InteractionResult.FAIL;
		}
		if (p.getCooldowns().isOnCooldown(st)) return InteractionResult.FAIL;
		if (!Pennant.canPlantAt(level, base)) {
			if (p instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.elsewhere.herdcaller_pennant.no_room"));
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel sl && p instanceof ServerPlayer sp) {
			if (Pennant.plant(sp, sl, base, sp.getYRot()) == null) return InteractionResult.FAIL;
			sp.getCooldowns().addCooldown(st, Pennant.COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}
}
