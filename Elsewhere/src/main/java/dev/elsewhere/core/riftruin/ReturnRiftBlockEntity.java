package dev.elsewhere.core.riftruin;

import dev.elsewhere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data; it exists so the client can render the tall beam above a Return Rift. */
public class ReturnRiftBlockEntity extends BlockEntity {
	public ReturnRiftBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlocks.RETURN_RIFT_ENTITY, pos, state);
	}
}
