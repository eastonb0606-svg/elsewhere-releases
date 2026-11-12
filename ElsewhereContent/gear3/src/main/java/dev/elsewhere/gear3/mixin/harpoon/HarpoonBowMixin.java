package dev.elsewhere.gear3.mixin.harpoon;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.elsewhere.gear3.s3.harpoon.HarpoonBolts;
import java.util.function.Predicate;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Harpoon Bolts are crossbow ammo (GEAR.md): bows skip arrows tagged #elsewhere:crossbow_only_ammo. Crossbows and dispensers are untouched. */
@Mixin(BowItem.class)
public abstract class HarpoonBowMixin {
	@ModifyReturnValue(method = "getAllSupportedProjectiles", at = @At("RETURN"))
	private Predicate<ItemStack> gear3$noCrossbowOnlyAmmo(Predicate<ItemStack> original) {
		return HarpoonBolts.bowAmmo(original);
	}
}
