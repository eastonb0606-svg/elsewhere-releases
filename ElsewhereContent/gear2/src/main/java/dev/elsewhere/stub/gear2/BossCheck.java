package dev.elsewhere.stub.gear2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** STUB (same as arsenal's): bosses are entity types in {@code #c:bosses} or {@code #elsewhere:bosses}. */
public final class BossCheck {
	private BossCheck() {}

	public static final TagKey<EntityType<?>> C_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
	public static final TagKey<EntityType<?>> ELSEWHERE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("elsewhere", "bosses"));

	public static boolean isBoss(Entity e) {
		return e.is(C_BOSSES) || e.is(ELSEWHERE_BOSSES);
	}
}
