package dev.elsewhere.gear3.s3.waryak.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.waryak.WarYakEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** War-Yak renderer: {@link WarYakModel}, textures/entity/war_yak.png, a little larger than a cow. */
public class WarYakRenderer extends MobRenderer<WarYakEntity, WarYakRenderer.State, WarYakModel> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/war_yak.png");
	static final float SCALE = 1.15F;

	/** Render state: the charge blend (0 = walking, 1 = full charge), eased over a few frames. */
	public static class State extends LivingEntityRenderState {
		public float charge;
	}

	public WarYakRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new WarYakModel(ctx.bakeLayer(WarYakModel.LAYER)), 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarYakEntity e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.charge = net.minecraft.util.Mth.lerp(pt, e.chargeAnimO, e.chargeAnim);
	}

	@Override
	public Identifier getTextureLocation(State s) {
		return TEXTURE;
	}

	@Override
	protected void scale(State s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
