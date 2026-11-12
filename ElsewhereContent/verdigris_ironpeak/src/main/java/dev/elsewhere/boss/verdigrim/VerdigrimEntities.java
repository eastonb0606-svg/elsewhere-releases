package dev.elsewhere.boss.verdigrim;

import static dev.elsewhere.verdigris_ironpeak.Reg.entity;
import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import dev.elsewhere.verdigris_ironpeak.BossShot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;

public final class VerdigrimEntities {
	public static final EntityType<VerdigrimBoss> VERDIGRIM = living("verdigrim",
		EntityType.Builder.of(VerdigrimBoss::new, MobCategory.MONSTER).sized(1.8F, 6.2F).eyeHeight(5.4F).clientTrackingRange(16).fireImmune(),
		VerdigrimBoss.createAttributes());
	public static final EntityType<BossShot> HYMN_TRIDENT = entity("hymn_trident",
		EntityType.Builder.<BossShot>of((t, l) -> new BossShot(t, l, Items.TRIDENT, false, false, ParticleTypes.BUBBLE), MobCategory.MISC)
			.sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(10));

	private VerdigrimEntities() {}

	public static void init() {}
}
