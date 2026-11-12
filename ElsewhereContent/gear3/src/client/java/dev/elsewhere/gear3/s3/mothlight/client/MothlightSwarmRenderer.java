package dev.elsewhere.gear3.s3.mothlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.mothlight.MothlightSwarm;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Mothlight Swarm renderer: three small pale moths ({@link MothlightSwarmModel}), full-bright, no shadow. */
public class MothlightSwarmRenderer extends MobRenderer<MothlightSwarm, MothlightSwarmRenderer.State, MothlightSwarmModel> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/mothlight_swarm.png");
	/** Model scale: a moth's wingspan is 18 px, so about half a block at this scale. */
	static final float SCALE = 0.45F;

	/** Render state: living moths, ticks since the last flurry / fallen moth, a per-swarm phase seed. */
	public static class State extends LivingEntityRenderState {
		public int moths = 3;
		public float sinceFlurry = 1000F, sinceFall = 1000F;
		public int seed;
	}

	public MothlightSwarmRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new MothlightSwarmModel(ctx.bakeLayer(MothlightSwarmModel.LAYER)), 0.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MothlightSwarm e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.moths = e.moths();
		s.sinceFlurry = e.tickCount - e.flurryTick + pt;
		s.sinceFall = e.tickCount - e.fallTick + pt;
		s.seed = e.getId();
	}

	@Override
	public Identifier getTextureLocation(State s) {
		return TEXTURE;
	}

	@Override
	protected void scale(State s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	/** Mothlight: the moths glow faintly, drawn full-bright. */
	@Override
	protected int getBlockLightLevel(MothlightSwarm e, BlockPos pos) {
		return 15;
	}
}
