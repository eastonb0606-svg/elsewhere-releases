package dev.elsewhere.stub.nether_end_bosses;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;

/** Base renderer for bosses: fills {@link BossRenderState}, applies a uniform model scale, optional full-bright. */
public abstract class BossRenderer<T extends ElsewhereBoss, S extends BossRenderState, M extends EntityModel<? super S>> extends MobRenderer<T, S, M> {
	private final float modelScale;
	private final boolean fullBright;

	protected BossRenderer(EntityRendererProvider.Context context, M model, float shadow, float modelScale, boolean fullBright) {
		super(context, model, shadow);
		this.modelScale = modelScale;
		this.fullBright = fullBright;
	}

	@Override
	public void extractRenderState(T entity, S state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.phase = entity.getPhase();
		state.transition = entity.inTransition();
		state.dormant = entity.isDormant();
		state.attack = entity.currentAttackId();
		state.attackAge = entity.attackAge() + partialTicks;
		state.partialTick = partialTicks;
		state.transitionAnim.copyFrom(entity.transitionAnim);
		state.attackAnim.copyFrom(entity.attackAnim);
		state.flags = flagsOf(entity);
	}

	protected int flagsOf(T entity) {
		int f = 0;
		for (int bit = 0; bit < 24; bit++) if (entity.hasFlag(1 << bit)) f |= 1 << bit;
		return f;
	}

	@Override
	protected void scale(S state, PoseStack poseStack) {
		poseStack.scale(modelScale, modelScale, modelScale);
	}

	@Override
	protected int getBlockLightLevel(T entity, BlockPos pos) {
		return fullBright ? 15 : super.getBlockLightLevel(entity, pos);
	}
}
