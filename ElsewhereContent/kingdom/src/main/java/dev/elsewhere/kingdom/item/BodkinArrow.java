package dev.elsewhere.kingdom.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla arrow (same entity type, renders and saves as one; its pickup item stays a Bodkin Bolt) whose hit treats the
 * target's armor as 25% lower: the damage is scaled by {@link #pierceFactor} for the hit.
 */
public class BodkinArrow extends Arrow {
	public static final float ARMOR_IGNORED = 0.25F;
	private double base = 2.0;

	@Override
	public void setBaseDamage(double d) {
		this.base = d;
		super.setBaseDamage(d);
	}

	public BodkinArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(level, owner, pickup, weapon);
	}

	public BodkinArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
		super(level, x, y, z, pickup, weapon);
	}

	/**
	 * Damage multiplier so that {@code damage} against {@code armor}/{@code toughness} lands as if the armor were 25% lower
	 * (vanilla armor formula: reduction = clamp(armor - dmg / (2 + toughness / 4), armor / 5, 20) / 25).
	 */
	public static float pierceFactor(float damage, float armor, float toughness) {
		if (armor <= 0F || damage <= 0F) return 1F;
		float full = 1F - reduction(damage, armor, toughness);
		float pierced = 1F - reduction(damage, armor * (1F - ARMOR_IGNORED), toughness);
		return full <= 0.01F ? 1F : pierced / full;
	}

	private static float reduction(float dmg, float armor, float toughness) {
		float f = 2.0F + toughness / 4.0F;
		return Math.clamp(armor - dmg / f, armor * 0.2F, 20.0F) / 25.0F;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (hit.getEntity() instanceof LivingEntity le && le.getArmorValue() > 0) {
			double b = base;
			float est = (float) (getDeltaMovement().length() * b);
			float k = pierceFactor(est, le.getArmorValue(), (float) le.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
			super.setBaseDamage(b * k);
			super.onHitEntity(hit);
			super.setBaseDamage(b);
			return;
		}
		super.onHitEntity(hit);
	}
}
