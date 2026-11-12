package dev.elsewhere.boss.galvanox;

import static dev.elsewhere.verdigris_ironpeak.Reg.entity;
import static dev.elsewhere.verdigris_ironpeak.Reg.living;

import dev.elsewhere.verdigris_ironpeak.BossShot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;

public final class GalvanoxEntities {
	public static final EntityType<GalvanoxBoss> GALVANOX = living("galvanox",
		EntityType.Builder.of(GalvanoxBoss::new, MobCategory.MONSTER).sized(3.0F, 7.0F).eyeHeight(6.0F).clientTrackingRange(16).fireImmune(),
		GalvanoxBoss.createAttributes());
	public static final EntityType<BossShot> WAX_SHELL = entity("wax_shell",
		EntityType.Builder.<BossShot>of((t, l) -> new BossShot(t, l, Items.HONEYCOMB, true, true, ParticleTypes.WAX_ON), MobCategory.MISC)
			.sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(5));
	public static final EntityType<BossShot> ARC_BOLT = entity("arc_bolt",
		EntityType.Builder.<BossShot>of((t, l) -> new BossShot(t, l, Items.PRISMARINE_CRYSTALS, false, false, ParticleTypes.ELECTRIC_SPARK), MobCategory.MISC)
			.sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(5));

	private GalvanoxEntities() {}

	public static void init() {}
}
