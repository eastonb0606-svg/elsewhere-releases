package dev.elsewhere.stub.greenhaze_hush;

import dev.elsewhere.greenhaze_hush.Reg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * STUB blocks the core will own: {@code elsewhere:arena_seal} (translucent force field; solid for mobs, passable for
 * players so they can drop out) and {@code elsewhere:return_rift} (one-way rift to the Overworld).
 */
public final class StubBlocks {
	private StubBlocks() {}

	public static Block ARENA_SEAL, RETURN_RIFT;

	public static void init() {
		ARENA_SEAL = Reg.block("arena_seal", ArenaSealBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
			.strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.GLASS).lightLevel(s -> 6)
			.isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
			.isViewBlocking((s, l, p, a) -> false).pushReaction(PushReaction.IMMOVEABLE), false);
		RETURN_RIFT = Reg.block("return_rift", ReturnRiftBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.strength(-1.0F, 3600000.0F).noLootTable().noCollision().noOcclusion().lightLevel(s -> 15).pushReaction(PushReaction.IMMOVEABLE), false);
	}

	public static class ArenaSealBlock extends Block {
		public ArenaSealBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			if (ctx instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player) return Shapes.empty();
			return Shapes.block();
		}

		@Override
		protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return Shapes.empty();
		}

		@Override
		protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
			return 1.0F;
		}

		@Override
		protected boolean propagatesSkylightDown(BlockState state) {
			return true;
		}

		@Override
		protected boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction direction) {
			return neighbour.is(this) || super.skipRendering(state, neighbour, direction);
		}
	}

	/** Walk into it to be sent to your Overworld respawn point. */
	public static class ReturnRiftBlock extends Block {
		public ReturnRiftBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return Block.column(12.0, 0.0, 16.0);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
			if (entity instanceof ServerPlayer p && !p.isOnPortalCooldown()) {
				p.setPortalCooldown(100);
				ReturnRift.sendHome(p);
			}
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 2,
				pos.getZ() + random.nextDouble(), 0, 0.05, 0);
		}
	}
}
