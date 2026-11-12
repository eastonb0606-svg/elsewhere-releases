package dev.elsewhere.gear3.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.s3.whips.Gear3Whips;
import dev.elsewhere.gear3.s3.whips.WhipTags;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.GaleRibbonItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Whip mechanics hooks (s-whips, gear3.s3.whips). All of them run on an event (a hit, a damage calculation, a tag); none
 * ticks. Logic lives in {@link Gear3Whips} / {@link WhipTags}; every hook passes through when the kill switch is off.
 */
public final class WhipMixins {
	private WhipMixins() {}

	/** Summoner's Gale Ribbon: one whip tag per target across every whip, and the gear3 marks drive the minions too. */
	@Mixin(GaleRibbonItem.class)
	public abstract static class Ribbon {
		/** Newest wins: a Gale Ribbon hit removes the owner's gear3 mark and any other whip tag on that target. */
		@Inject(method = "tag", at = @At("HEAD"))
		private static void gear3$newestWins(ServerPlayer owner, LivingEntity target, CallbackInfo ci) {
			if (Gear3Whips.ENABLED) WhipTags.onRibbonTag(owner, target);
		}

		/**
		 * Whip tag bonus time (Marshal's Command): the Ribbon's tag is stamped that much later, so it lasts and idles longer.
		 * A set-bonus API, so it stays on with the whip_mechanics kill switch off (then every whip is a Gale Ribbon again and
		 * still gets the Command's +3 s). 0 when no set bonus is registered or worn.
		 */
		@ModifyExpressionValue(method = "tag", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getGameTime()J"))
		private static long gear3$bonusTime(long now, @Local(argsOnly = true) ServerPlayer owner) {
			return now + WhipTags.bonusTicks(owner);
		}

		/** A gear3 mark counts as the owner's whip tag (ranged minions +4 range against it). */
		@Inject(method = "isTagged", at = @At("HEAD"), cancellable = true)
		private static void gear3$marked(ServerPlayer owner, Entity target, CallbackInfoReturnable<Boolean> cir) {
			if (Gear3Whips.ENABLED && WhipTags.isTaggedBy(owner, target)) cir.setReturnValue(true);
		}

		/** Minions switch to the gear3-marked target (MinionTargeting.pick asks GaleRibbonItem.target). */
		@Inject(method = "target", at = @At("HEAD"), cancellable = true)
		private static void gear3$markedTarget(ServerPlayer owner, CallbackInfoReturnable<LivingEntity> cir) {
			if (!Gear3Whips.ENABLED) return;
			LivingEntity t = WhipTags.target(owner);
			if (t != null) cir.setReturnValue(t);
		}
	}

	/** Minion damage on a marked target: the gear3 mark's own bonus instead of the Gale Ribbon's +30%. */
	@Mixin(MinionApi.class)
	public abstract static class Minions {
		@WrapOperation(method = "minionDamage", at = @At(value = "INVOKE",
			target = "Ldev/elsewhere/summoner/GaleRibbonItem;isTagged(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;)Z"))
		private static boolean gear3$ribbonOnly(ServerPlayer owner, Entity target, Operation<Boolean> original) {
			return original.call(owner, target) && !(Gear3Whips.ENABLED && WhipTags.isTaggedBy(owner, target));
		}

		@ModifyReturnValue(method = "minionDamage", at = @At("RETURN"))
		private static double gear3$markBonus(double d, ServerPlayer owner, MinionEntity minion, LivingEntity target, double base) {
			return Gear3Whips.minionDamage(owner, target, d);
		}
	}

	/** Damage in: whip melee x D(Summoner); minion hits ignore the Venom mark's armor points. */
	@Mixin(LivingEntity.class)
	public abstract static class Living {
		@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
		private float gear3$whipCalling(float amount, @Local(argsOnly = true) DamageSource source) {
			return Gear3Whips.whipMelee((LivingEntity) (Object) this, source, amount);
		}

		@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"), index = 3)
		private float gear3$markArmor(float armor, @Local(argsOnly = true) DamageSource source) {
			return Gear3Whips.armorAgainst((LivingEntity) (Object) this, source, armor);
		}
	}

	/** Herd focus never applies to whips: lifted for the one whip attack, put back after it (even if it throws). */
	@Mixin(Player.class)
	public abstract static class Attack {
		@WrapMethod(method = "attack")
		private void gear3$whipAttack(Entity target, Operation<Void> original) {
			Player self = (Player) (Object) this;
			AttributeModifier lifted = Gear3Whips.liftHerdFocus(self);
			try {
				original.call(target);
			} finally {
				Gear3Whips.restoreHerdFocus(self, lifted);
			}
		}
	}
}
