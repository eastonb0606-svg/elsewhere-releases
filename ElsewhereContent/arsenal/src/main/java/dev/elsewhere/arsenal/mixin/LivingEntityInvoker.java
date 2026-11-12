package dev.elsewhere.arsenal.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Self-test only: FakePlayers never tick, so armor attribute modifiers need a manual equipment update. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("detectEquipmentUpdates")
	void elsewhere$detectEquipmentUpdates();
}
