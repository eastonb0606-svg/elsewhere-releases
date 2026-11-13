package dev.elsewhere.gear3.s3.reliquary;

import dev.elsewhere.minion.MinionEntity;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The Echo Knight (Echo Reliquary): the echo of a Mute Order knight. A ground minion on the summoner framework
 * ({@link MinionEntity}: owner, targeting, fervor timer, no griefing, not saved). 2 slots (gear3 {@code MinionSlots}),
 * 60 HP, armor 16, 10 damage every 1.2 s; its blows ignore 40% of armor on top of the minion rule.
 * Silent: no sounds ({@code setSilent}), no step sounds or step vibrations ({@code MovementEmission.NONE}), every
 * vibration it is the source of is dampened (sculk sensors, shriekers and Wardens ignore it), and the damage
 * vibration of its blows and of hits it takes is never emitted (mixin {@code EchoSilenceMixin} -> {@link #vibrates}),
 * so the Hush's noise bus never hears its fights either.
 */
public class EchoKnight extends MinionEntity {
	/** Transient armor modifier on the target for the duration of one blow. */
	public static final Identifier PIERCE_ID = EchoReliquary.id("echo_knight_armor_pierce");
	/** Self-test: damage vibrations this class kept from being emitted. */
	public static int silencedHits;

	public EchoKnight(EntityType<? extends EchoKnight> type, Level level) {
		super(type, level);
		this.setSilent(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, EchoReliquary.HP)
			.add(Attributes.ARMOR, EchoReliquary.ARMOR)
			.add(Attributes.MOVEMENT_SPEED, EchoReliquary.SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, EchoReliquary.KNOCKBACK_RESISTANCE)
			.add(Attributes.ATTACK_DAMAGE, EchoReliquary.DAMAGE);
	}

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	/** Server: tickCount of the last blow; the striking stance (Mob aggressive flag) drops 40 ticks after it. */
	private int lastStrikeTick = -1000;

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && isAggressive() && tickCount - lastStrikeTick > 40) setAggressive(false);
	}

	@Override
	public double baseDamage() {
		return EchoReliquary.DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return EchoReliquary.ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return EchoReliquary.REACH;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		this.swingForAttack(InteractionHand.MAIN_HAND);
		this.setAggressive(true); // synced mob flag: the client holds the blade raised ("striking" stance) between blows
		this.lastStrikeTick = this.tickCount;
		if (hitTarget(level, owner, target, baseDamage())) {
			// a soundless blow: a small sculk puff where it lands is the only tell
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 5, 0.25, 0.3, 0.25, 0.01);
		}
	}

	/** Every blow (also the summoner's own paths that call hitTarget) ignores 40% of the target's armor. */
	@Override
	public boolean hitTarget(ServerLevel level, ServerPlayer owner, LivingEntity target, double base) {
		return withArmorPierce(target, () -> super.hitTarget(level, owner, target, base));
	}

	/**
	 * Runs {@code body} with the target's armor at 60% (a transient ADD_MULTIPLIED_TOTAL -0.40 modifier, removed right
	 * after). Inside {@code MinionEntity.hitTarget} the minion rule's own x0.5 multiplies with it: 20 armor counts as 6.
	 */
	public static <T> T withArmorPierce(LivingEntity target, Supplier<T> body) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		if (armor != null) armor.addOrUpdateTransientModifier(new AttributeModifier(PIERCE_ID, -EchoReliquary.ARMOR_IGNORE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		try {
			return body.get();
		} finally {
			if (armor != null) armor.removeModifier(PIERCE_ID);
		}
	}

	// ---------------------------------------------------------------- silent

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.NONE;
	}

	@Override
	public boolean dampensVibrations() {
		return true;
	}

	/** Self-test view of the three silence rules on the entity itself. */
	public boolean silentByDesign() {
		return isSilent() && dampensVibrations() && getMovementEmission() == Entity.MovementEmission.NONE;
	}

	/**
	 * EchoSilenceMixin (LivingEntity.actuallyHurt): false = don't emit the ENTITY_DAMAGE vibration. Blows of an Echo
	 * Knight and hits on one make no vibration; every other hit is unchanged.
	 */
	public static boolean vibrates(LivingEntity victim, DamageSource source) {
		if (victim instanceof EchoKnight || source.getDirectEntity() instanceof EchoKnight) {
			silencedHits++;
			return false;
		}
		return true;
	}
}
