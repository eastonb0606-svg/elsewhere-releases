package dev.elsewhere.overworld_bosses.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Renders an entity as a (scaled) block, like a falling block: the Strawgrim head, the glacier boulder. */
public class BlockEntityLikeRenderer<T extends Entity> extends EntityRenderer<T, FallingBlockRenderState> {
	private final Function<T, BlockState> block;
	private final float scale;

	public BlockEntityLikeRenderer(EntityRendererProvider.Context context, Function<T, BlockState> block, float scale) {
		super(context);
		this.block = block;
		this.scale = scale;
		this.shadowRadius = 0.4F * scale;
	}

	@Override
	public void submit(FallingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.scale(this.scale, this.scale, this.scale);
		poseStack.translate(-0.5, 0.0, -0.5);
		collector.submitMovingBlock(poseStack, state.movingBlockRenderState, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public FallingBlockRenderState createRenderState() {
		return new FallingBlockRenderState();
	}

	@Override
	public void extractRenderState(T entity, FallingBlockRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
		state.movingBlockRenderState.randomSeedPos = pos;
		state.movingBlockRenderState.blockPos = pos;
		state.movingBlockRenderState.blockState = this.block.apply(entity);
		if (entity.level() instanceof ClientLevel clientLevel) {
			state.movingBlockRenderState.biome = clientLevel.getBiome(pos);
			state.movingBlockRenderState.cardinalLighting = clientLevel.cardinalLighting();
			state.movingBlockRenderState.lightEngine = clientLevel.getLightEngine();
		}
	}
}
