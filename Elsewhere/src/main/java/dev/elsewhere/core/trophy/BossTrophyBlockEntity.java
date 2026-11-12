package dev.elsewhere.core.trophy;

import dev.elsewhere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds which boss a trophy statue shows (entity id path, e.g. "mother_creak"). Synced to clients for the renderer. */
public class BossTrophyBlockEntity extends BlockEntity {
	private String boss = "";

	public BossTrophyBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlocks.BOSS_TROPHY_ENTITY, pos, state);
	}

	public String boss() {
		return this.boss;
	}

	public void setBoss(final String boss) {
		this.boss = boss == null ? "" : boss;
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
		}
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.boss = input.getStringOr("boss", "");
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putString("boss", this.boss);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}
}
