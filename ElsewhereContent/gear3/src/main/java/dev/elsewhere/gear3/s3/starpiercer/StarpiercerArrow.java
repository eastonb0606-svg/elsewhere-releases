package dev.elsewhere.gear3.s3.starpiercer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Starpiercer's arrow ({@code elsewhere:starpiercer_arrow}): what a plain arrow becomes when this bow looses it. It flies
 * with no gravity until it is {@link Starpiercer#STRAIGHT_BLOCKS} from where it was loosed (the server measures its own flight,
 * the client follows the synced no-gravity flag), then falls like any arrow. Picks up as a plain arrow. Client: a short star
 * trail while it flies (particles only on the client, culled by the particle engine's own distance limit).
 */
public class StarpiercerArrow extends AbstractArrow {
	/** Where it was loosed (server); null once it has its gravity back. */
	private @Nullable Vec3 launch;

	public StarpiercerArrow(EntityType<? extends StarpiercerArrow> type, Level level) {
		super(type, level);
	}

	public StarpiercerArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(Starpiercer.ARROW, owner, level, pickup, weapon);
	}

	/** Called once when it is loosed (server): weightless from here for 32 blocks. */
	void launch() {
		launch = position();
		setNoGravity(true);
	}

	public @Nullable Vec3 launchPoint() {
		return launch;
	}

	/** Blocks flown from the launch point (0 when it already has its gravity back). */
	public double flown() {
		return launch == null ? 0 : position().distanceTo(launch);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			if (launch != null && (isInGround() || position().distanceToSqr(launch) >= Starpiercer.STRAIGHT_BLOCKS * Starpiercer.STRAIGHT_BLOCKS)) {
				launch = null;
				setNoGravity(false);
			}
		} else if (!isInGround()) {
			trail();
		}
	}

	/** Client: star glints behind the arrow, denser while it flies straight. */
	private void trail() {
		Vec3 v = getDeltaMovement();
		boolean straight = isNoGravity();
		int n = straight ? 2 : (tickCount % 2 == 0 ? 1 : 0);
		for (int i = 0; i < n; i++) {
			double t = i / 2.0;
			level().addParticle(ParticleTypes.END_ROD, getX() - v.x * t, getY() - v.y * t, getZ() - v.z * t, 0, 0, 0);
		}
		if (straight && tickCount % 2 == 0) level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 0, 0, 0);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.ARROW);
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return Starpiercer.HIT != null ? Starpiercer.HIT.value() : super.getDefaultHitGroundSoundEvent();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("starpiercer_launch", Vec3.CODEC, launch);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		launch = input.read("starpiercer_launch", Vec3.CODEC).orElse(null);
		if (launch == null) setNoGravity(false);
	}
}
