package dev.elsewhere.stub.arsenal;

import dev.elsewhere.arsenal.ArsenalStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * STUB: boss detection for dodge and kill-heal. Bosses = entity type tags {@code #c:bosses} or {@code #elsewhere:bosses}.
 * "Inside a sealed boss arena" is approximated as "a boss within 64 blocks"; at merge use the core's arena seal.
 */
public final class BossCheck {
	private BossCheck() {}

	public static final TagKey<EntityType<?>> C_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
	public static final TagKey<EntityType<?>> ELSEWHERE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("elsewhere", "bosses"));

	public static boolean isBoss(Entity e) {
		return e.is(C_BOSSES) || e.is(ELSEWHERE_BOSSES);
	}

	public static boolean inBossFight(Player p) {
		return !p.level().getEntities(p, p.getBoundingBox().inflate(ArsenalStats.BOSS_NEARBY_RADIUS), BossCheck::isBoss).isEmpty();
	}
}
