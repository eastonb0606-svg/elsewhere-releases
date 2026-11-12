package dev.elsewhere.boss.hrolf;

import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class HrolfEntities {
	public static final EntityType<HrolfBoss> HROLF = living("hrolf",
		EntityType.Builder.of(HrolfBoss::new, MobCategory.MONSTER).sized(0.9F, 2.9F).eyeHeight(2.5F).ridingOffset(-0.9F).clientTrackingRange(16), HrolfBoss.createAttributes());
	public static final EntityType<GrimhornEntity> GRIMHORN = living("grimhorn",
		EntityType.Builder.of(GrimhornEntity::new, MobCategory.MONSTER).sized(2.7F, 3.9F).clientTrackingRange(16), GrimhornEntity.createAttributes());

	private HrolfEntities() {}

	public static void init() {}
}
