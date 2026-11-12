package dev.elsewhere.rivals.azmar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Falconer's Gauntlet: right-click sends a falcon at the living target in view (range 32): Glowing 8 s and 3 damage, 20 s cooldown. */
public class GauntletItem extends Item {
	public static final double RANGE = 32.0;
	public static final int COOLDOWN = 400, GLOW_TICKS = 160;
	public static final float DAMAGE = 3.0F;

	public GauntletItem(Item.Properties p) {
		super(p);
	}

	/** The nearest living entity along the player's view, within {@link #RANGE}, or null. */
	public static LivingEntity target(Player player) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(RANGE))).inflate(1.5))) {
			if (e == player || !e.isAlive()) continue;
			Vec3 c = e.getBoundingBox().getCenter();
			double t = c.subtract(eye).dot(look);
			if (t < 0 || t > bestD) continue;
			if (e.getBoundingBox().inflate(0.4).clip(eye, eye.add(look.scale(RANGE))).isEmpty()) continue;
			bestD = t; best = e;
		}
		return best;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		ItemStack stack = player.getItemInHand(hand);
		LivingEntity t = target(player);
		if (t == null) {
			player.sendOverlayMessage(Component.translatable("message.elsewhere.azmar_gauntlet_nothing"));
			return InteractionResult.FAIL;
		}
		dive(sl, player, t);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** The falcon dive: gold streak, Glowing 8 s, 3 damage. */
	public static void dive(ServerLevel sl, LivingEntity owner, LivingEntity t) {
		Vec3 from = owner.getEyePosition().add(0, 1.5, 0), to = t.getBoundingBox().getCenter();
		AzmarFx.falconStreak(sl, from, to);
		sl.playSound(null, owner.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.PLAYERS, 1.0F, 1.6F);
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0));
		t.hurtServer(sl, sl.damageSources().indirectMagic(owner, owner), DAMAGE);
	}
}
