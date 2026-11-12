package dev.elsewhere.gear2.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Self-test helper: FakePlayers never tick, so armor attribute modifiers need a manual equipment update. */
@Mixin(LivingEntity.class)
public interface Gear2EquipmentInvoker {
	@Invoker("detectEquipmentUpdates")
	void gear2$detectEquipmentUpdates();
}
