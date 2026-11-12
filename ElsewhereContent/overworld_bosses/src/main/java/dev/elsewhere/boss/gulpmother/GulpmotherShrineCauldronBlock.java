package dev.elsewhere.boss.gulpmother;

import dev.elsewhere.overworld_bosses.Summons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code elsewhere:gulpmother_shrine_cauldron}: the Croaking Shrine cauldron. Drop (or use) 3 slimeballs into it at
 * night and Old Gulpmother surfaces from the pool. Unbreakable so the shrine always works.
 */
public class GulpmotherShrineCauldronBlock extends Block {
	public static final IntegerProperty SLIME = IntegerProperty.create("slime", 0, 3);
	private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

	public GulpmotherShrineCauldronBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(SLIME, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SLIME);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.SLIME_BALL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level instanceof ServerLevel sl) {
			if (state.getValue(SLIME) < 3) {
				stack.consume(1, player);
				addSlime(sl, pos, state.getValue(SLIME) + 1, player);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && state.getValue(SLIME) == 3) addSlime(sl, pos, 3, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (level instanceof ServerLevel sl && entity instanceof ItemEntity item && item.getItem().is(Items.SLIME_BALL) && state.getValue(SLIME) < 3) {
			ItemStack st = item.getItem();
			int have = state.getValue(SLIME);
			int take = Math.min(3 - have, st.getCount());
			st.shrink(take);
			if (st.isEmpty()) item.discard();
			addSlime(sl, pos, have + take, null);
		}
	}

	private void addSlime(ServerLevel level, BlockPos pos, int count, Player player) {
		level.setBlock(pos, defaultBlockState().setValue(SLIME, count), 3);
		level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0F, 0.6F + count * 0.1F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.25, 0.1, 0.25, 0.05);
		if (count < 3) return;
		if (!level.isDarkOutside()) {
			level.playSound(null, pos, SoundEvents.FROG_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.5F);
			if (player != null) player.sendOverlayMessage(Component.translatable("message.elsewhere.gulpmother.not_night"));
			return;
		}
		level.setBlock(pos, defaultBlockState(), 3);
		Summons.summonGulpmother(level, pos);
	}
}
