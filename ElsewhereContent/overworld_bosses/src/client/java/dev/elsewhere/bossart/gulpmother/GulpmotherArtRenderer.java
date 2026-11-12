package dev.elsewhere.bossart.gulpmother;

import dev.elsewhere.bossart.overworld_a.OwaBossRenderer;
import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;

/**
 * Old Gulpmother's renderer. In her pool the model is lifted so she sits with her belly just under the surface and
 * the head, eyes and back clearly above the water (the hitbox is unchanged).
 */
public class GulpmotherArtRenderer extends OwaBossRenderer<GulpmotherEntity, GulpmotherArtModel.State, GulpmotherArtModel> {
	public static final float SCALE = 2.2F;

	public GulpmotherArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GulpmotherArtModel(ctx.bakeLayer(GulpmotherArt.LAYER)), new GulpmotherArtModel(ctx.bakeLayer(GulpmotherArt.LAYER)), 3.0F, SCALE,
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/gulpmother.png"),
			Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/gulpmother_glow.png"));
	}

	@Override
	public GulpmotherArtModel.State createRenderState() {
		return new GulpmotherArtModel.State();
	}

	@Override
	public void extractRenderState(GulpmotherEntity e, GulpmotherArtModel.State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.tongue = e.getTongue();
		s.lift = 0.0F;
		BlockPos base = e.blockPosition();
		int depth = 0;
		while (depth < 6 && e.level().getFluidState(base.above(depth)).is(FluidTags.WATER)) depth++;
		if (depth > 0) {
			double surface = base.getY() + depth - 0.1;
			s.lift = (float) Math.max(0.0, surface - e.getY() - 0.8);
		}
	}

	@Override
	protected float lift(GulpmotherArtModel.State s) {
		return s.lift;
	}
}
