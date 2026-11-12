package dev.elsewhere.glasswaste;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Heliodor's rotatable mirror stand ({@code elsewhere:sun_mirror}). Right-click turns it 45 degrees. {@link #AIM} 0..7 is
 * the horizontal direction its reflected sunbeam travels: 0 = south (+Z), 2 = west (-X), 4 = north, 6 = east, odd = diagonals.
 */
public class SunMirrorBlock extends Block {
	public static final IntegerProperty AIM = IntegerProperty.create("aim", 0, 7);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
	/** Server-side listeners told whenever a mirror is turned (the Heliodor arena checks its summon pattern). */
	public static final List<BiConsumer<ServerLevel, BlockPos>> TURN_LISTENERS = new CopyOnWriteArrayList<>();

	public SunMirrorBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AIM, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AIM);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		int aim = Math.floorMod(Math.round(context.getRotation() / 45.0F), 8);
		return this.defaultBlockState().setValue(AIM, aim);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int next = (state.getValue(AIM) + (player.isShiftKeyDown() ? 7 : 1)) % 8;
		level.setBlock(pos, state.setValue(AIM, next), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1.0F, 0.7F + next * 0.08F);
		if (level instanceof ServerLevel server) for (BiConsumer<ServerLevel, BlockPos> l : TURN_LISTENERS) l.accept(server, pos);
		return InteractionResult.SUCCESS;
	}

	/** Unit vector of the reflected beam for an aim index. */
	public static Vec3 direction(int aim) {
		double a = Math.toRadians(aim * 45.0);
		return new Vec3(-Math.sin(a), 0, Math.cos(a));
	}

	/** Aim index whose direction is closest to the given horizontal vector. */
	public static int aimToward(Vec3 v) {
		double yaw = Math.toDegrees(Math.atan2(-v.x, v.z));
		return Math.floorMod(Math.round((float) (yaw / 45.0)), 8);
	}
}
