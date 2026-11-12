package dev.elsewhere.boss.thurgrim;

import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ThurgrimEntities {
	public static final EntityType<ThurgrimBoss> THURGRIM = living("thurgrim",
		EntityType.Builder.of(ThurgrimBoss::new, MobCategory.MONSTER).sized(1.6F, 5.8F).eyeHeight(5.0F).clientTrackingRange(16), ThurgrimBoss.createAttributes());

	private ThurgrimEntities() {}

	public static void init() {}
}
