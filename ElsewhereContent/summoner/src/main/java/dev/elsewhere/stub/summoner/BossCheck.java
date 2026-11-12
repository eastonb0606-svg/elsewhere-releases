package dev.elsewhere.stub.summoner;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * STUB of the shared boss framework's "is this a boss" check (bosses ignore taunts and roots). Uses the entity type
 * tags {@code elsewhere:bosses} and {@code c:bosses} plus the two vanilla bosses. At merge, point it at the core.
 */
public final class BossCheck {
	private BossCheck() {}

	public static final TagKey<EntityType<?>> ELSEWHERE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("elsewhere", "bosses"));
	public static final TagKey<EntityType<?>> C_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));

	public static boolean isBoss(Entity e) {
		return e instanceof WitherBoss || e instanceof EnderDragon || e.getType().builtInRegistryHolder().is(ELSEWHERE_BOSSES) || e.getType().builtInRegistryHolder().is(C_BOSSES);
	}
}
