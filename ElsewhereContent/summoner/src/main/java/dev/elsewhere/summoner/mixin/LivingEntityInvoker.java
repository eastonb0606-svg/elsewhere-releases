package dev.elsewhere.summoner.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dev self-test helper: applies equipment attribute changes on a fake player that never ticks. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("detectEquipmentUpdates")
	void elsewhere_summoner$detectEquipmentUpdates();
}
