package dev.elsewhere.gear3.s3.miresnare;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A placed Mire Snare's memory: who placed it (for kill credit). Server data only; never ticks, never synced to clients. */
public class MireSnareBlockEntity extends BlockEntity {
	private UUID owner;

	public MireSnareBlockEntity(BlockPos pos, BlockState state) {
		super(MireSnare.BLOCK_ENTITY, pos, state);
	}

	/** The placer's UUID, or null (placed by a command or a non-player). */
	public UUID owner() {
		return owner;
	}

	public void setOwner(UUID id) {
		owner = id;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.storeNullable("owner", UUIDUtil.CODEC, owner);
	}
}
