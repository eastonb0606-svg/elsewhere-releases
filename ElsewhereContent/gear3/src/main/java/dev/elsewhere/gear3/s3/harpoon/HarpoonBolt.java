package dev.elsewhere.gear3.s3.harpoon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The flying Harpoon Bolt ({@code elsewhere:tidebrass_harpoon_bolt} entity): a crossbow arrow with its own look
 * (HarpoonBoltRenderer), a pull on a successful hit, x1.5 damage against a target in water, and a trident's water inertia.
 * Picked up as a Harpoon Bolt like any arrow. Rules and numbers: {@link HarpoonBolts}.
 */
public class HarpoonBolt extends AbstractArrow {
	/** The base damage as last set (AbstractArrow has no getter): the water bonus scales it for one hit and puts it back. */
	private double base = 2.0;

	public HarpoonBolt(EntityType<? extends HarpoonBolt> type, Level level) {
		super(type, level);
	}

	public HarpoonBolt(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(HarpoonBolts.TYPE, owner, level, pickup, weapon);
	}

	public HarpoonBolt(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
		super(HarpoonBolts.TYPE, x, y, z, level, pickup, weapon);
	}

	/** Runs inside the super constructor: static data only. */
	@Override
	protected ItemStack getDefaultPickupItem() {
		Item bolt = HarpoonBolts.item();
		return new ItemStack(bolt != null ? bolt : Items.ARROW);
	}

	@Override
	public void setBaseDamage(double damage) {
		base = damage;
		super.setBaseDamage(damage);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		base = in.getDoubleOr("damage", 2.0); // AbstractArrow reads the same key straight into its field
	}

	/** A harpoon keeps its speed underwater (a trident's 0.99; a vanilla arrow's is 0.6). */
	@Override
	protected float getWaterInertia() {
		return HarpoonBolts.ON ? HarpoonBolts.WATER_INERTIA : super.getWaterInertia();
	}

	/** x1.5 damage against a target in water: the base damage is scaled for this one hit (enchantments and crits apply on top, as for any arrow). */
	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (HarpoonBolts.ON && !level().isClientSide() && hit.getEntity().isInWater()) {
			double b = base;
			super.setBaseDamage(b * HarpoonBolts.WATER_MULT);
			try {
				super.onHitEntity(hit);
			} finally {
				super.setBaseDamage(b);
			}
			return;
		}
		super.onHitEntity(hit);
	}

	/** Called by AbstractArrow only after the hit actually hurt a living target (a shield block or an immune target gets no pull). */
	@Override
	protected void doPostHurtEffects(LivingEntity target) {
		super.doPostHurtEffects(target);
		if (HarpoonBolts.ON && level() instanceof ServerLevel sl) HarpoonBolts.onHit(sl, this, target);
	}

	// ---------------------------------------------------------------- self-test access (HarpoonSelfTest)

	public void hitForTest(EntityHitResult hit) {
		onHitEntity(hit);
	}

	public float waterInertiaForTest() {
		return getWaterInertia();
	}

	public double baseDamageForTest() {
		return base;
	}
}
