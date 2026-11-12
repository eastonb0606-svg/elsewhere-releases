package dev.elsewhere.palehollow.item;

import dev.elsewhere.palehollow.GearStats;
import dev.elsewhere.palehollow.StillnessMob;
import dev.elsewhere.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Freezes every Stillness mob within 24 blocks for 8 s, even when you look away (bosses: 2 s). */
public class UnblinkingEyeItem extends Item {
	public UnblinkingEyeItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level instanceof ServerLevel server) {
			int frozen = 0;
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GearStats.UNBLINKING_EYE_RADIUS),
				e -> (e instanceof StillnessMob || e instanceof Creaking) && !(e instanceof dev.elsewhere.core.boss.ElsewhereBoss))) {
				if (e instanceof StillnessMob s) {
					s.stillnessFreeze(GearStats.UNBLINKING_EYE_FREEZE_TICKS);
				} else {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GearStats.UNBLINKING_EYE_FREEZE_TICKS, 9, false, true));
				}
				server.sendParticles(ModParticles.MOON_MOTE, e.getX(), e.getEyeY(), e.getZ(), 12, 0.3, 0.3, 0.3, 0.01);
				frozen++;
			}
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.6F);
			ItemStack stack = player.getItemInHand(hand);
			stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		return InteractionResult.SUCCESS;
	}
}
