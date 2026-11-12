package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.GearStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Ninefold Rod: right-click fires 3 small fireballs in a fan. Spends 6 Glimmer (a spell), 1 s use delay. */
public class NinefoldRodItem extends Item {
	public NinefoldRodItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			if (!dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(player, GearStats.NINEFOLD_ROD_GLIMMER)) return InteractionResult.FAIL;
			Vec3 look = player.getLookAngle();
			Vec3 eye = player.getEyePosition();
			for (int i = 0; i < GearStats.NINEFOLD_ROD_FIREBALLS; i++) {
				double spread = (i - (GearStats.NINEFOLD_ROD_FIREBALLS - 1) / 2.0) * 0.18;
				Vec3 dir = look.yRot((float) spread).normalize();
				SmallFireball ball = new SmallFireball(server, player, dir);
				ball.setPos(eye.add(dir.scale(0.8)));
				server.addFreshEntity(ball);
			}
			server.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.1F);
			stack.hurtAndBreak(1, player, hand);
		}
		player.getCooldowns().addCooldown(stack, (int) (GearStats.NINEFOLD_ROD_USE_DELAY_S * 20));
		return InteractionResult.SUCCESS;
	}
}
