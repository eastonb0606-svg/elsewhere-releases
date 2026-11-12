package dev.elsewhere.palehollow.item;

import dev.elsewhere.palehollow.GearStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Selenne's Moon-Eye: 3 Glimmer, the mob you are looking at (32 blocks) glows for 10 s. Caster trinket (off hand or
 * hotbar, tag {@code #elsewhere:trinket} from the arsenal): your spells deal +20% to mobs you marked. Spells only.
 */
public class MoonEyeItem extends Item {
	public MoonEyeItem(final Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(final net.minecraft.world.item.ItemStack stack, final TooltipContext context,
		final net.minecraft.world.item.component.TooltipDisplay display, final java.util.function.Consumer<net.minecraft.network.chat.Component> builder,
		final net.minecraft.world.item.TooltipFlag flag) {
		builder.accept(net.minecraft.network.chat.Component.translatable("item.elsewhere.moon_eye.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
		builder.accept(net.minecraft.network.chat.Component.translatable("item.elsewhere.moon_eye.trinket").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(GearStats.MOON_EYE_RANGE));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1.0),
			e -> e instanceof LivingEntity && e != player && !e.isSpectator(), GearStats.MOON_EYE_RANGE * GearStats.MOON_EYE_RANGE);
		if (hit == null) {
			return InteractionResult.FAIL;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && !dev.elsewhere.stub.core.GlimmerStub.trySpend(sp, GearStats.MOON_EYE_GLIMMER)) {
			return InteractionResult.FAIL;
		}
		Entity target = hit.getEntity();
		((LivingEntity)target).addEffect(new MobEffectInstance(MobEffects.GLOWING, GearStats.MOON_EYE_GLOW_TICKS, 0), player);
		// caster trinket (ARSENAL.md §7): the arsenal gives your spells +20% on this mob while the Moon-Eye is an active trinket
		dev.elsewhere.palehollow.ArsenalLinks.markForSpells((LivingEntity)target, player);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
		return InteractionResult.SUCCESS;
	}
}
