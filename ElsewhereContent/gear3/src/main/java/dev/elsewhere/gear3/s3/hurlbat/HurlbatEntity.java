package dev.elsewhere.gear3.s3.hurlbat;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A thrown Hurlbat. Flies like an arrow (arrow gravity, sticks in blocks with the shake), strikes once for 7, then either
 * breaks (50%: item-crack particles, break sound, gone) or glances off the target like a trident and drops to the ground,
 * where its thrower picks it back up (anyone may take an ownerless one; a creative throw is never picked up as an item).
 * A strike on a raised shield disables the shield for 2 s. Despawns like an arrow (1 minute in the ground).
 * Only its own movement ticks (AbstractArrow); there is no scan of any kind.
 */
public class HurlbatEntity extends AbstractArrow {
	/** Self-test only: forces the break roll (true = breaks, false = survives); null = the real 50% roll. */
	static @Nullable Boolean forceBreak;

	private boolean dealtDamage;

	public HurlbatEntity(EntityType<? extends HurlbatEntity> type, Level level) {
		super(type, level);
	}

	public HurlbatEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(Hurlbats.ENTITY, owner, level, stack, null);
	}

	@Override
	public void tick() {
		if (this.inGroundTime > 4) this.dealtDamage = true;
		super.tick();
	}

	/** One strike per throw: after it has hit something (or lain in the ground) it no longer hits entities. */
	@Override
	protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
		return this.dealtDamage ? null : super.findHitEntity(from, to);
	}

	@Override
	protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
		EntityHitResult e = this.findHitEntity(from, to);
		return e != null ? List.of(e) : List.of();
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity target = hit.getEntity();
		Entity owner = this.getOwner();
		DamageSource source = Hurlbats.damageSource(this, owner == null ? this : owner);
		float dmg = Hurlbats.DAMAGE;
		ServerLevel sl = this.level() instanceof ServerLevel s ? s : null;
		if (sl != null) dmg = EnchantmentHelper.modifyDamage(sl, this.getWeaponItem(), target, source, dmg);
		this.dealtDamage = true;
		// the shield test must run before the hit: a disabled shield is no longer raised afterwards
		ItemStack shield = sl != null && target instanceof LivingEntity le ? Hurlbats.blockingShield(le, source, dmg) : null;
		boolean hurt = target.hurtOrSimulate(source, dmg);
		if (hurt) {
			if (sl != null) EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(sl, target, source, this.getWeaponItem(), weapon -> this.kill(sl));
			if (target instanceof LivingEntity le) {
				this.doKnockback(le, source);
				this.doPostHurtEffects(le);
			}
		}
		if (shield != null && target instanceof LivingEntity le) Hurlbats.disableShield(sl, le, shield);
		if (this.isRemoved() || !target.projectileReceivesSideEffectsOnHit(hurt)) return;
		this.playSound(Hurlbats.HIT, 1.0F, 1.0F);
		if (sl != null && breaks(sl)) {
			shatter(sl);
			return;
		}
		this.deflect(ProjectileDeflection.REVERSE, target, this.owner, false, new Vec3(0.02, 0.2, 0.02));
	}

	private boolean breaks(ServerLevel sl) {
		Boolean forced = forceBreak;
		return forced != null ? forced : Hurlbats.rollBreak(sl.getRandom());
	}

	/** Breaks on the target: a puff of hurlbat shards and the break sound, then gone (nothing to pick up). */
	void shatter(ServerLevel sl) {
		sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Hurlbats.item()), this.getX(), this.getY(), this.getZ(), 10, 0.12, 0.12, 0.12, 0.06);
		sl.playSound(null, this.getX(), this.getY(), this.getZ(), Hurlbats.BREAK, this.getSoundSource(), 0.8F, 0.9F + sl.getRandom().nextFloat() * 0.2F);
		this.discard();
	}

	@Override
	public ItemStack getWeaponItem() {
		return this.getPickupItemStackOrigin();
	}

	/** Like the trident: only the thrower (or anyone, if it has no thrower) picks it up. */
	@Override
	public void playerTouch(Player player) {
		if (this.ownedBy(player) || this.getOwner() == null) super.playerTouch(player);
	}

	/** Called from the field initialisers during super construction: static state only. */
	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Hurlbats.item());
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return Hurlbats.HIT_GROUND;
	}

	public boolean hasDealtDamage() {
		return this.dealtDamage;
	}

	/** True while it lies in a block (the renderer buries the blade instead of spinning). */
	public boolean isStuck() {
		return this.isInGround();
	}

	/** Self-test only: a direct strike on {@code target} (the same path a flight collision takes). */
	void hitForTest(Entity target) {
		this.onHitEntity(new EntityHitResult(target));
	}

	/** Self-test only: as if it had landed and stopped shaking, so it can be picked up now. */
	void landForTest() {
		this.setInGround(true);
		this.shakeTime = 0;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.dealtDamage = input.getBooleanOr("DealtDamage", false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("DealtDamage", this.dealtDamage);
	}
}
