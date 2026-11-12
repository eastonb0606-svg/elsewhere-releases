package dev.elsewhere.stub.nether_end_bosses.mixin;

import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.Parts;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boss parts show up in entity box queries (melee picking, projectile hits), like dragon parts. */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"))
	private void elsewhere$addBossParts(Entity except, AABB bb, Predicate<? super Entity> selector, CallbackInfoReturnable<List<Entity>> cir) {
		for (BossPart part : Parts.all((Level) (Object) this)) {
			if (part != except && part.parent != except && !part.parent.isRemoved() && selector.test(part) && bb.intersects(part.getBoundingBox())) {
				cir.getReturnValue().add(part);
			}
		}
	}
}
