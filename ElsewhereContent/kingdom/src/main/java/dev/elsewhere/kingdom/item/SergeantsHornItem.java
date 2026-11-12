package dev.elsewhere.kingdom.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Sergeant's Horn: a goat-horn-like call. The user, other players and the user's tamed animals within 12 blocks get
 * Strength I for 8 s. 90 s cooldown. Not consumed.
 */
public class SergeantsHornItem extends Item {
	public SergeantsHornItem(Properties p) {
		super(p);
	}

	/** Applies the horn's effect around {@code user}; returns how many entities were buffed. */
	public static int sound(Player user) {
		Level level = user.level();
		double r2 = KingdomItems.HORN_RADIUS * KingdomItems.HORN_RADIUS;
		List<LivingEntity> near = new java.util.ArrayList<>();
		near.add(user);
		for (Player p : level.players()) if (p != user && !p.isSpectator() && p.isAlive() && p.distanceToSqr(user) <= r2) near.add(p);
		near.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(user.blockPosition()).inflate(KingdomItems.HORN_RADIUS),
			e -> e.isAlive() && !(e instanceof Player) && e.distanceToSqr(user) <= r2 && e instanceof OwnableEntity o
				&& o.getOwnerReference() != null && user.getUUID().equals(o.getOwnerReference().getUUID())));
		for (LivingEntity e : near) e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, KingdomItems.HORN_STRENGTH_TICKS, 0), user);
		return near.size();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack self = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			sound(player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.PLAYERS, 8.0F, 1.1F);
		}
		player.getCooldowns().addCooldown(self, KingdomItems.HORN_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, java.util.function.Consumer<Component> out, TooltipFlag flag) {
		out.accept(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
	}
}
