package dev.elsewhere.gear3.s3.pennant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.s3.pennant.HerdcallerStandard;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Renders the planted standard: {@link HerdcallerStandardModel}, its flag turned towards whoever planted it. */
public class HerdcallerStandardRenderer extends EntityRenderer<HerdcallerStandard, HerdcallerStandardRenderer.State> {
	public static final Identifier TEXTURE = Gear3Content.id("textures/entity/herdcaller_standard.png");
	private final HerdcallerStandardModel model;

	/** Render state: facing, unfurl (0-1), and a per-standard wave phase so two standards never flap in step. */
	public static class State extends EntityRenderState {
		public float yRot;
		public float unfurl = 1F;
		public float phase;
	}

	public HerdcallerStandardRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new HerdcallerStandardModel(ctx.bakeLayer(HerdcallerStandardModel.LAYER));
		this.shadowRadius = 0.2F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HerdcallerStandard e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.yRot = e.getYRot();
		s.unfurl = e.unfurl(pt);
		s.phase = (e.getId() * 37) % 100;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, -s.yRot);
		pose.scale(-1F, -1F, 1F);
		pose.translate(0F, -1.501F, 0F);
		out.submitModel(model, s, pose, TEXTURE, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
		pose.popPose();
		super.submit(s, pose, out, camera);
	}
}
