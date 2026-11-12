package dev.elsewhere.gear3.mixin.setbonus;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.gear3.setbonus.a.HirdPair;
import dev.elsewhere.summoner.SummonerPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** gear3 set and pair bonus hooks (SetBonuses). Every handler is one early-out check unless someone wears a set. */
public final class SetBonusMixins {
	private SetBonusMixins() {}

	/** Server damage entry: set-bonus damage multipliers (dealt and taken), before armor. */
	@Mixin(LivingEntity.class)
	public abstract static class Hurt {
		@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
		private float gear3sb$damage(float amount, @Local(argsOnly = true) DamageSource source) {
			return SetBonuses.modifyDamage((LivingEntity) (Object) this, source, amount);
		}
	}

	/** Starting or stopping item use (a raised shield, a drawn bow) is a stance change. */
	@Mixin(LivingEntity.class)
	public abstract static class Use {
		@Inject(method = "startUsingItem", at = @At("RETURN"))
		private void gear3sb$startUse(CallbackInfo ci) {
			if ((Object) this instanceof ServerPlayer p) SetBonuses.stanceChanged(p);
		}

		@Inject(method = "stopUsingItem", at = @At("RETURN"))
		private void gear3sb$stopUse(CallbackInfo ci) {
			if ((Object) this instanceof ServerPlayer p) SetBonuses.stanceChanged(p);
		}
	}

	/** Sneaking on or off (the server sets it from the player's input packet) is a stance change; only a real change counts. */
	@Mixin(Entity.class)
	public abstract static class Sneak {
		@WrapMethod(method = "setShiftKeyDown")
		private void gear3sb$sneak(boolean down, Operation<Void> original) {
			boolean before = ((Entity) (Object) this).isShiftKeyDown();
			original.call(down);
			if (before != down && (Object) this instanceof ServerPlayer p) SetBonuses.stanceChanged(p);
		}
	}

	/** Glimmer cost of spells and Glimmer items (minion summons pay through trySpendScaled and never come here). */
	@Mixin(Glimmer.class)
	public abstract static class Cost {
		@ModifyReturnValue(method = "cost(Lnet/minecraft/world/entity/player/Player;IZ)F", at = @At("RETURN"))
		private static float gear3sb$cost(float original, Player p, int raw, boolean starter) {
			return SetBonuses.spellCost(p, original);
		}

		@ModifyReturnValue(method = "regenPerSecond", at = @At("RETURN"))
		private static float gear3sb$regen(float original, ServerPlayer p) {
			return SetBonuses.regen(p, original);
		}
	}

	/** Herd focus (own damage per extra minion): the Hird pair halves it (x H). */
	@Mixin(SummonerPerks.class)
	public abstract static class Herd {
		@ModifyReturnValue(method = "herdFocus", at = @At("RETURN"))
		private static double gear3sb$herdFocus(double original, Player p) {
			return HirdPair.herdFocus(p, original);
		}
	}
}
