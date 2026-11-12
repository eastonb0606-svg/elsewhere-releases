package dev.elsewhere.glasswaste;

import dev.elsewhere.petalwind_glasswaste.Dmg;
import dev.elsewhere.petalwind_glasswaste.GearStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Heliograph Lens (Heliodor's drop): focus a beam (no open sky needed) that ignites and marks a target. 6 Glimmer, 1 s use delay (ARSENAL §1). */
public class HeliographLensItem extends Item {
	public HeliographLensItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel server) {
			LivingEntity target = Dmg.lookTarget(player, 48.0);
			if (target == null) return InteractionResult.FAIL;
			// ARSENAL §1: 6 Glimmer and a 1 s use delay (item cooldown below); counts as a spell (spell_power by id).
			if (player instanceof net.minecraft.server.level.ServerPlayer sp
				&& !dev.elsewhere.stub.petalwind_glasswaste.GlimmerStub.trySpend(sp, GearStats.HELIOGRAPH_LENS_GLIMMER)) {
				dev.elsewhere.petalwind_glasswaste.ArsenalHooks.notEnoughGlimmer(sp);
				return InteractionResult.FAIL;
			}
			BurningGlassItem.beam(server, player.getEyePosition().add(player.getLookAngle().scale(0.6)), target.getBoundingBox().getCenter());
			target.hurtServer(server, Dmg.fire(server, player), GearStats.HELIOGRAPH_LENS_DAMAGE
				* dev.elsewhere.petalwind_glasswaste.ArsenalHooks.spellPower(player));
			target.igniteForSeconds(4.0F);
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
			server.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.8F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), GearStats.HELIOGRAPH_LENS_COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}
}
