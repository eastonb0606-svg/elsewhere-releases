package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A child hitbox of a multipart boss, like {@code EnderDragonPart} but for any {@link ElsewhereBoss}.
 * Parts are not added to the level; {@link Parts} plus two small mixins make them hittable and pickable.
 */
public class BossPart extends Entity {
	public final ElsewhereBoss parent;
	public final String name;
	public final int index;
	private EntityDimensions size;

	public BossPart(ElsewhereBoss parent, String name, int index, float w, float h) {
		super(parent.getType(), parent.level());
		this.size = EntityDimensions.scalable(w, h);
		this.refreshDimensions();
		this.parent = parent;
		this.name = name;
		this.index = index;
	}

	public void resize(float w, float h) {
		this.size = EntityDimensions.scalable(w, h);
		this.refreshDimensions();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return parent.getPickResult();
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !this.isInvulnerableToBase(source) && parent.hurtPart(level, this, source, damage);
	}

	@Override
	public boolean is(Entity other) {
		return this == other || parent == other;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return size;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
