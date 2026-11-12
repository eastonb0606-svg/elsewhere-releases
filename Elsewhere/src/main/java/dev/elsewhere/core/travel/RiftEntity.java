package dev.elsewhere.core.travel;

import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A rift: opens for {@code riftOpenSeconds} (3 s), then pulls its owner through and stays open
 * {@code riftLingerSeconds} (10 s) so anyone stepping into it follows to the same place. Not saved with the world.
 */
public class RiftEntity extends Entity {
	public static final int CLOSE_TICKS = 15;
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
	/** 0 opening, 1 open, 2 closing. */
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);

	private @Nullable ResourceKey<Level> destinationLevel;
	private @Nullable Vec3 destination;
	private @Nullable UUID owner;
	private @Nullable ServerPlayer ownerRef;
	private boolean forced;
	private boolean ownerSent;
	private int age;
	private int closeAt = -1;
	private final Set<UUID> travelled = new HashSet<>();
	/** Client-side animation clock (ticks since this rift was first seen). */
	public int clientAge;

	public RiftEntity(final EntityType<? extends RiftEntity> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void configure(final ResourceKey<Level> destinationLevel, final Vec3 destination, final @Nullable ServerPlayer owner, final int color,
		final boolean forced) {
		this.destinationLevel = destinationLevel;
		this.destination = destination;
		this.owner = owner == null ? null : owner.getUUID();
		this.ownerRef = owner;
		this.forced = forced;
		this.entityData.set(DATA_COLOR, color);
	}

	public int color() {
		return this.entityData.get(DATA_COLOR);
	}

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	public boolean hasTravelled(final UUID id) {
		return this.travelled.contains(id);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		builder.define(DATA_COLOR, 0xB89CFF);
		builder.define(DATA_STATE, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.clientAge++;
			this.clientParticles();
			return;
		}
		if (!(this.level() instanceof ServerLevel server) || this.destinationLevel == null || this.destination == null) {
			this.discard();
			return;
		}
		ElsewhereConfig.Wayfinders cfg = ElsewhereConfig.get().wayfinders;
		int openTicks = (int)Math.round(cfg.riftOpenSeconds * 20.0);
		int lingerTicks = (int)Math.round(cfg.riftLingerSeconds * 20.0);
		this.age++;
		if (this.age == 1) {
			server.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RIFT_OPEN.value(), SoundSource.PLAYERS, 1.2F, 1.0F);
		}
		if (this.age % 30 == 0 && this.closeAt < 0) {
			server.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RIFT_HUM.value(), SoundSource.AMBIENT, 0.7F, 0.9F);
		}
		if (this.age == openTicks) {
			this.entityData.set(DATA_STATE, 1);
			ServerPlayer ownerPlayer = this.owner == null ? null : server.getServer().getPlayerList().getPlayer(this.owner);
			if (ownerPlayer == null) {
				ownerPlayer = this.ownerRef;
			}
			if (ownerPlayer != null && !ownerPlayer.isRemoved() && ownerPlayer.level() == server
				&& (this.forced || ownerPlayer.distanceToSqr(this) < 16.0)) {
				this.send(ownerPlayer);
				this.ownerSent = true;
			}
		}
		if (this.age >= openTicks && this.closeAt < 0) {
			for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(0.2), pl -> !this.travelled.contains(pl.getUUID()))) {
				if (!p.isSpectator() && p.isAlive()) {
					this.send(p);
				}
			}
			if (this.age >= openTicks + lingerTicks || (!this.ownerSent && this.age >= openTicks + 20 && this.owner != null && this.travelled.isEmpty() && this.forced)) {
				this.closeAt = this.age + CLOSE_TICKS;
				this.entityData.set(DATA_STATE, 2);
			}
		}
		if (this.closeAt >= 0 && this.age >= this.closeAt) {
			this.discard();
		}
	}

	/** A player stepping into the open rift (also used by the self-test, whose fake players are not in the world). */
	public boolean stepIn(final ServerPlayer player) {
		int openTicks = (int)Math.round(ElsewhereConfig.get().wayfinders.riftOpenSeconds * 20.0);
		if (this.age < openTicks || this.closeAt >= 0 || this.travelled.contains(player.getUUID())) {
			return false;
		}
		this.send(player);
		return true;
	}

	public int age() {
		return this.age;
	}

	private void send(final ServerPlayer player) {
		ServerLevel target = player.level().getServer().getLevel(this.destinationLevel);
		if (target == null) {
			return;
		}
		this.travelled.add(player.getUUID());
		Vec3 spot = this.destination;
		if (!player.getUUID().equals(this.owner) && this.travelled.size() > 1) {
			// followers land beside the owner, still on safe ground
			double a = this.travelled.size() * 2.1;
			Vec3 offset = new Vec3(Math.cos(a) * 1.5, 0.0, Math.sin(a) * 1.5);
			spot = SafeLanding.near(target, this.destination.add(offset), 6, ElsewhereDimensions.platformOf(this.destinationLevel));
		}
		RiftTravel.teleport(player, target, spot, player.getYRot());
	}

	private void clientParticles() {
		int c = this.color();
		int state = this.state();
		float open = state == 2 ? 0.4F : Mth.clamp(this.clientAge / 40.0F, 0.15F, 1.0F);
		int count = state == 1 ? 3 : 2;
		for (int i = 0; i < count; i++) {
			double ang = this.random.nextDouble() * Math.PI * 2.0;
			double rx = 0.75 * open;
			double ry = 1.25 * open;
			double yaw = Math.toRadians(-this.getYRot());
			double lx = Math.cos(ang) * rx;
			double ly = Math.sin(ang) * ry;
			double x = this.getX() + lx * Math.cos(yaw);
			double z = this.getZ() + lx * Math.sin(yaw);
			double y = this.getY() + 1.3 + ly;
			this.level().addParticle(ModParticles.spark(c), x, y, z, -lx * 0.05, -ly * 0.05, 0.0);
		}
		if (this.random.nextInt(4) == 0) {
			this.level().addParticle(ModParticles.spark(0xFFFFFF), this.getX(), this.getY() + 1.3, this.getZ(),
				(this.random.nextDouble() - 0.5) * 0.2, (this.random.nextDouble() - 0.5) * 0.2, (this.random.nextDouble() - 0.5) * 0.2);
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
	}
}
