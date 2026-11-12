package dev.elsewhere.rivals.mirewick.mixin;

import dev.elsewhere.rivals.mirewick.item.MirewickItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debt-Collector's Leggings (Cobweb-Walker): cobweb and mud never slow the wearer. Runs on both sides, so the client agrees. */
@Mixin(Entity.class)
public abstract class EntityStuckMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void elsewhereMirewick$cobwebWalker(BlockState state, Vec3 speed, CallbackInfo ci) {
		if (!((Object) this instanceof LivingEntity le)) return;
		if (MirewickItems.DEBT_COLLECTORS_LEGGINGS == null || !le.getItemBySlot(EquipmentSlot.LEGS).is(MirewickItems.DEBT_COLLECTORS_LEGGINGS)) return;
		if (state.is(Blocks.COBWEB) || state.is(Blocks.MUD)) ci.cancel();
	}
}
