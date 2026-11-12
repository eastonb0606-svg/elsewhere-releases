package dev.elsewhere.verdigris_ironpeak;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block at the heart of a boss arena (organ console, lighthouse lantern, war drum, challenge horn).
 * Using it hands off to the boss's summon logic, registered by the boss package under the block's id.
 */
public class SummonFocusBlock extends Block {
	public interface Handler {
		InteractionResult use(ServerLevel level, BlockPos pos, ServerPlayer player);
	}

	public static final Map<String, Handler> HANDLERS = new HashMap<>();
	private final String key;
	private final VoxelShape shape;

	public SummonFocusBlock(String key, VoxelShape shape, BlockBehaviour.Properties properties) {
		super(properties);
		this.key = key;
		this.shape = shape;
	}

	public String key() {
		return key;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		Handler handler = HANDLERS.get(key);
		return handler == null ? InteractionResult.PASS : handler.use(server, pos, sp);
	}
}
