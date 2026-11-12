package dev.elsewhere.rivals.hrafnholt.unit;

import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Hrafnholt Wolf-Handler: fights beside 2 kingdom wolves (20 HP, bite 4) that leave with him: they are discarded when he dies or
 * is discarded, they stay within 12 blocks, and they only ever attack what the handler targets (never passive animals, pets or
 * villagers). Handler plus wolves count as 2 soldiers against the caps: the wolves only spawn when the caps have room for one more.
 */
public class WolfHandler extends KingdomSoldier {
	public static final String TAG = "elsewhere_hrafnholt_hound";
	public static final double LEASH = 12.0;
	public static final int WOLVES = 2;
	private final List<UUID> wolves = new ArrayList<>();
	private boolean spawned;
	/** Test counter: wolf ticks spent circling to a flank. */
	public int flankSteps;

	public WolfHandler(EntityType<? extends KingdomSoldier> type, Level level) {
		super(type, level);
	}

	public List<UUID> wolfIds() {
		return wolves;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!spawned && tickCount > 2 && isActive()) {
			if (KingdomPerf.room(level, position()) >= 1 || KingdomPerf.activeCount(level) <= 1) spawnWolves(level);
			spawned = true;
		}
		if (spawned && tickCount % 5 == 0) tendWolves(level);
	}

	void spawnWolves(ServerLevel level) {
		for (int i = 0; i < WOLVES; i++) {
			Wolf w = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (w == null) continue;
			w.snapTo(getX() + (i == 0 ? -1.2 : 1.2), getY(), getZ() + 0.6, getYRot(), 0F);
			w.setCustomName(Component.translatable("entity.elsewhere.hrafnholt_hound"));
			w.addTag(TAG);
			w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
			w.setHealth(20.0F);
			w.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);
			w.setPersistenceRequired();
			level.addFreshEntity(w);
			wolves.add(w.getUUID());
		}
	}

	void tendWolves(ServerLevel level) {
		LivingEntity target = getTarget();
		for (int i = wolves.size() - 1; i >= 0; i--) {
			Entity e = level.getEntity(wolves.get(i));
			if (!(e instanceof Wolf w) || !w.isAlive()) {
				if (e == null && level.isLoaded(blockPosition())) wolves.remove(i);
				continue;
			}
			if (target != null && target.isAlive()) {
				// wolf_flank (FIELD-TACTICS 4): the handler holds the front; each wolf circles to its own side of the enemy, then bites
				if (w.distanceToSqr(target) > 5.5 * 5.5 && w.distanceToSqr(target) < 24 * 24 && distanceToSqr(target) < 20 * 20) {
					Vec3 f = target.position().subtract(position()).multiply(1, 0, 1);
					if (f.lengthSqr() > 1) {
						f = f.normalize();
						Vec3 spot = target.position().add(new Vec3(-f.z, 0, f.x).scale((i % 2 == 0 ? 1 : -1) * 4.5)).add(f.scale(1.5));
						if (w.getTarget() != null) w.setTarget(null);
						w.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);
						flankSteps++;
						continue;
					}
				}
				if (w.getTarget() != target) w.setTarget(target);
				if (w.distanceToSqr(this) > LEASH * LEASH && w.distanceToSqr(target) > LEASH * LEASH) w.getNavigation().moveTo(this, 1.3);
			} else {
				if (w.getTarget() != null) w.setTarget(null);
				w.setPersistentAngerTarget(null);
				if (w.distanceToSqr(this) > 6 * 6) w.getNavigation().moveTo(this, 1.2);
			}
			if (w.distanceToSqr(this) > (LEASH * 2) * (LEASH * 2)) w.getNavigation().moveTo(this, 1.5);
		}
	}

	void releaseWolves() {
		if (!(level() instanceof ServerLevel level)) return;
		for (UUID id : wolves) {
			Entity e = level.getEntity(id);
			if (e != null) e.discard();
		}
		wolves.clear();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		releaseWolves();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (reason.shouldDestroy()) releaseWolves();
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putBoolean("HrafnholtWolvesSpawned", spawned);
		for (int i = 0; i < wolves.size(); i++) out.putString("HrafnholtWolf" + i, wolves.get(i).toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		spawned = in.getBooleanOr("HrafnholtWolvesSpawned", false);
		wolves.clear();
		for (int i = 0; i < WOLVES; i++) in.getString("HrafnholtWolf" + i).ifPresent(s -> wolves.add(UUID.fromString(s)));
	}
}
