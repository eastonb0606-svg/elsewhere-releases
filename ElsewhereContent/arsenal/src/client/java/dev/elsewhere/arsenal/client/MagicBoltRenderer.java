package dev.elsewhere.arsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.spell.MagicBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** A glowing camera-facing amethyst bolt (texture tinted by the bolt's colour). */
public class MagicBoltRenderer extends EntityRenderer<MagicBolt, MagicBoltRenderer.State> {
	private static final Identifier TEX = ArsenalContent.id("textures/entity/spell/amethyst_bolt.png");
	private static final RenderType TYPE = RenderTypes.entityTranslucentEmissive(TEX);

	public static class State extends EntityRenderState {
		int color = 0xFFA77BF3;
	}

	public MagicBoltRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	protected int getBlockLightLevel(MagicBolt entity, BlockPos pos) {
		return 15;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MagicBolt e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.color = 0xFF000000 | (e.color() & 0xFFFFFF);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, 0.15F, 0);
		pose.scale(0.6F, 0.6F, 0.6F);
		pose.rotate(camera.orientation);
		out.submitCustomGeometry(pose, TYPE, (p, buf) -> quad(state, p, buf));
		pose.popPose();
		super.submit(state, pose, out, camera);
	}

	private static void quad(State s, PoseStack.Pose p, VertexConsumer b) {
		v(b, p, s, -0.5F, -0.5F, 0, 1);
		v(b, p, s, 0.5F, -0.5F, 1, 1);
		v(b, p, s, 0.5F, 0.5F, 1, 0);
		v(b, p, s, -0.5F, 0.5F, 0, 0);
	}

	private static void v(VertexConsumer b, PoseStack.Pose p, State s, float x, float y, int u, int v) {
		b.addVertex(p, x, y, 0).setColor(s.color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 1, 0);
	}
}
