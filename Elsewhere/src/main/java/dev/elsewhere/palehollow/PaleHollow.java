package dev.elsewhere.palehollow;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.travel.ElsewhereDimensions;
import dev.elsewhere.palehollow.item.HeartwoodGreatbowItem;
import dev.elsewhere.palehollow.world.WorldTrunkFeature;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Blocks;

/** Pale Hollow content entrypoint (dimension info, trips, worldgen feature types, item hooks). */
public final class PaleHollow {
	private static final java.util.Map<java.util.UUID, Long> ROOTED = new java.util.HashMap<>();

	private PaleHollow() {
	}

	public static void init() {
		ElsewhereDimensions.register(new ElsewhereDimensions.Info(PaleHollowDimension.LEVEL, "pale_hollow", PaleHollowDimension.COLOR,
			PaleHollowDimension.MAX_LANDING_Y, Blocks.PALE_OAK_PLANKS, true));
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Elsewhere.id("world_trunk"), WorldTrunkFeature.CODEC);
		dev.elsewhere.palehollow.world.ResinKiln.init();
		dev.elsewhere.palehollow.world.PHSites.init();
		dev.elsewhere.palehollow.fit3.CoreFit.init();
		dev.elsewhere.palehollow.LanternEvent.init();
		PaleHollowTrips.init();
		ArsenalLinks.init();

		// Heartwood Greatbow arrows root what they hit (4 s per target; bosses: Slowness I 1 s, once per 10 s, rule G5).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(HeartwoodGreatbowItem.ROOT_TAG)) {
				long now = entity.level().getGameTime();
				boolean boss = entity instanceof dev.elsewhere.core.boss.ElsewhereBoss;
				Long last = ROOTED.get(entity.getUUID());
				if (last == null || now - last >= (boss ? GearStats.BOSS_CC_COOLDOWN_TICKS : GearStats.GREATBOW_ROOT_COOLDOWN_TICKS)) {
					ROOTED.put(entity.getUUID(), now);
					entity.addEffect(boss ? new MobEffectInstance(MobEffects.SLOWNESS, GearStats.BOSS_CC_TICKS, 0)
						: new MobEffectInstance(MobEffects.SLOWNESS, GearStats.GREATBOW_ROOT_TICKS, GearStats.GREATBOW_ROOT_AMPLIFIER));
				}
				if (ROOTED.size() > 512) {
					ROOTED.clear();
				}
			}
		});
	}
}
