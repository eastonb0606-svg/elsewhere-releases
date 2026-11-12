package dev.elsewhere.gear3.s3.echoarrow;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Echo Arrow in flight (see {@link EchoArrows} for the design). Base damage, pickup, enchantments, Piercing, Flame and the
 * arsenal Marksman/pierce hooks are the vanilla AbstractArrow's. Added: silent flight (silent entity + dampened vibrations, the
 * loosing vibration dropped too) and one echo pulse at the first impact. Client visuals come from one entity event.
 */
public class EchoArrowEntity extends AbstractArrow {
	private static final String PULSED_KEY = "EchoPulsed";
	/** True once the pulse has fired (an arrow knocked loose and landing again stays quiet); saved with the arrow. */
	private boolean pulsed;

	/** Type factory (client spawn, loading from disk). */
	public EchoArrowEntity(EntityType<? extends EchoArrowEntity> type, Level level) {
		super(type, level);
		quiet();
	}

	/** Shot by a mob or player (bow / crossbow via {@link EchoArrowItem#createArrow}). */
	public EchoArrowEntity(Level level, LivingEntity owner, ItemStack pickup, ItemStack firedFromWeapon) {
		super(EchoArrows.TYPE, owner, level, pickup, firedFromWeapon);
		quiet();
	}

	/** Dispensed ({@link EchoArrowItem#asProjectile}). */
	public EchoArrowEntity(Level level, double x, double y, double z, ItemStack pickup, ItemStack firedFromWeapon) {
		super(EchoArrows.TYPE, x, y, z, level, pickup, firedFromWeapon);
		quiet();
	}

	/** Silent flight: no hit/landing sound and no hit "ding" for the shooter (Entity.playSound and AbstractArrow honour isSilent). */
	private void quiet() {
		if (EchoArrows.ENABLED) this.setSilent(true);
	}

	public boolean hasPulsed() {
		return pulsed;
	}

	/** Silent flight: sculk sensors, shriekers and Wardens ignore vibrations whose source dampens them (its landing). */
	@Override
	public boolean dampensVibrations() {
		return EchoArrows.ENABLED || super.dampensVibrations();
	}

	/** Silent flight: the loosing vibration (Projectile.tick sends PROJECTILE_SHOOT once, credited to the shooter) is not sent. */
	@Override
	public void gameEvent(Holder<GameEvent> event, Entity sourceEntity) {
		if (EchoArrows.ENABLED && GameEvent.PROJECTILE_SHOOT.equals(event)) return;
		super.gameEvent(event, sourceEntity);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		// pulse first: a non-piercing arrow is discarded inside super.onHitEntity, and a removed entity can no longer send its event
		echo(hit.getLocation());
		super.onHitEntity(hit);
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		echo(hit.getLocation());
	}

	/** The pulse, once per arrow, server side; then one entity event so tracking clients draw it. */
	private void echo(Vec3 at) {
		if (pulsed || !EchoArrows.ENABLED || !(this.level() instanceof ServerLevel level)) return;
		pulsed = true;
		EchoArrows.pulse(level, at, this.getEffectSource());
		level.broadcastEntityEvent(this, EchoArrows.EVENT_PULSE);
	}

	@Override
	public void tick() {
		super.tick();
		// faint sculk trail in flight (client only, every third tick; nothing on the server)
		if (EchoArrows.ENABLED && this.level().isClientSide() && !this.isInGround() && this.tickCount % 3 == 0)
			this.level().addParticle(ParticleTypes.SCULK_CHARGE_POP, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EchoArrows.EVENT_PULSE) {
			if (this.level().isClientSide()) echoVisuals();
			return;
		}
		super.handleEntityEvent(id);
	}

	/**
	 * Client: a flat ring of sculk pops at the impact, one vibration line from the impact to each mob the pulse reached (at most
	 * {@link EchoArrows#MAX_LINES}), and a soft sculk click for the shooter only. Runs once per pulse on clients tracking the arrow.
	 */
	private void echoVisuals() {
		Level l = this.level();
		Vec3 at = this.position();
		// teal sculk ring (two rings, 1.5 and 3 blocks) so the pulse reads at 10+ blocks; dust lives scale x longer
		net.minecraft.core.particles.DustParticleOptions teal = new net.minecraft.core.particles.DustParticleOptions(0x2FD9C4, 1.4F);
		for (int ring = 1; ring <= 2; ring++) {
			double rad = ring * 1.5;
			int n = ring * 20;
			for (int i = 0; i < n; i++) {
				double a = i * (Math.PI * 2.0 / n);
				double cx = Math.cos(a), sz = Math.sin(a);
				l.addParticle(teal, at.x + cx * rad, at.y + 0.15, at.z + sz * rad, 0, 0, 0);
				if (i % 2 == 0) l.addParticle(ParticleTypes.SCULK_CHARGE_POP, at.x + cx * rad, at.y + 0.2, at.z + sz * rad, cx * 0.05, 0.02, sz * 0.05);
			}
		}
		double r = EchoArrows.RADIUS * 2 + 2;
		int lines = 0;
		for (Mob m : l.getEntitiesOfClass(Mob.class, AABB.ofSize(at, r, r, r), e -> EchoArrows.reaches(e, at))) {
			if (lines++ >= EchoArrows.MAX_LINES) break;
			Vec3 to = new Vec3(m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ());
			Vec3 d = to.subtract(at);
			double len = d.length();
			if (len > 0.5) for (double t = 0.3; t < len; t += 0.3) { // a continuous teal vibration line, impact to mob
				Vec3 pt = at.add(d.scale(t / len));
				l.addParticle(teal, pt.x, pt.y, pt.z, 0, 0, 0);
			}
			l.addParticle(new VibrationParticleOption(new EntityPositionSource(m, m.getBbHeight() * 0.5F), 20), at.x, at.y, at.z, 0.0, 0.0, 0.0);
		}
		if (this.getOwner() instanceof Player p && p.isLocalPlayer())
			l.playLocalSound(at.x, at.y, at.z, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.7F, 1.5F, false);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		// called from AbstractArrow's field initialiser (before this class's fields exist): static lookup only
		return EchoArrows.stack();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(PULSED_KEY, pulsed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		pulsed = input.getBooleanOr(PULSED_KEY, false);
	}
}
