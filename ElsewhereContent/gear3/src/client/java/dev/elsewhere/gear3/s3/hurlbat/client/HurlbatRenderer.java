package dev.elsewhere.gear3.s3.hurlbat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.gear3.s3.hurlbat.HurlbatEntity;
import dev.elsewhere.gear3.s3.hurlbat.Hurlbats;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/**
 * Draws a thrown Hurlbat with its own item model (the 16x16 hurlbat sprite, extruded like any held item), so the flying axe is
 * exactly the one in the hand. In flight it tumbles end over end in the plane of its flight, about its head (8 ticks a turn);
 * stuck in a block it stops with the blade buried in the face it hit and the haft standing out behind, and quivers with the
 * arrow shake. Client only; vanilla frustum culling on a slightly grown box (the spinning haft reaches past the hitbox).
 */
public class HurlbatRenderer extends EntityRenderer<HurlbatEntity, HurlbatRenderer.State> {
	/** Degrees per tick of the in-flight tumble (forward over the top). */
	static final float SPIN = 45.0F;
	static final float SCALE = 0.8F;
	/** Model-space pivots from the sprite (pixel (px, py) -> (px/16 - 0.5, 0.5 - py/16)): head centre (9.5, 5), blade edge (13, 5). */
	static final float HEAD_X = 0.094F, HEAD_Y = 0.1875F, EDGE_X = 0.3125F, EDGE_Y = 0.1875F;
	/** How far the stuck blade sinks past the hit point, in blocks. */
	static final float SINK = 0.25F;
	/** Stuck pose: the sprite's haft-to-head axis points 61.9 degrees above the sprite's +X; the pivot is the top of the head. */
	static final float AXIS_DEG = 61.9F, TIP_X = 0.094F, TIP_Y = 0.3125F;

	public static void register() {
		if (Hurlbats.ENTITY != null) EntityRendererRegistry.register(Hurlbats.ENTITY, HurlbatRenderer::new);
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float xRot, yRot, shake;
		public boolean stuck;
	}

	private final ItemModelResolver itemModelResolver;

	public HurlbatRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.itemModelResolver = ctx.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HurlbatEntity e, State s, float partialTicks) {
		super.extractRenderState(e, s, partialTicks);
		s.xRot = e.getXRot(partialTicks);
		s.yRot = e.getYRot(partialTicks);
		s.shake = e.shakeTime - partialTicks;
		s.stuck = e.isStuck();
		this.itemModelResolver.updateForNonLiving(s.item, e.getPickupItemStackOrigin(), ItemDisplayContext.NONE, e);
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!s.item.isEmpty()) {
			pose.pushPose();
			// the arrow frame: +X along the flight, +Y up, the sprite in the X/Y plane
			pose.rotateDegrees(Axis.YP, s.yRot - 90.0F);
			pose.rotateDegrees(Axis.ZP, s.xRot);
			if (s.shake > 0.0F) pose.rotateDegrees(Axis.ZP, -Mth.sin(s.shake * 3.0F) * s.shake);
			if (s.stuck) {
				// the haft runs along the flight line (standing out of the face), the head buried; a second sprite rolled 90 degrees about
				// the flight line crosses the first, so it reads as a solid blade from every side instead of one flat sticker
				for (int i = 0; i < 2; i++) {
					pose.pushPose();
					pose.translate(SINK, 0.0F, 0.0F);
					pose.scale(SCALE, SCALE, SCALE);
					if (i == 1) pose.rotateDegrees(Axis.XP, 90.0F);
					pose.rotateDegrees(Axis.ZP, -AXIS_DEG);
					pose.translate(-TIP_X, -TIP_Y, 0.0F);
					s.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
					pose.popPose();
				}
			} else {
				pose.scale(SCALE, SCALE, SCALE);
				pose.rotateDegrees(Axis.ZP, -s.ageInTicks * SPIN);
				pose.translate(-HEAD_X, -HEAD_Y, 0.0F);
				s.item.submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			}
			pose.popPose();
		}
		super.submit(s, pose, collector, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(HurlbatEntity e, float partialTicks) {
		return super.getBoundingBoxForCulling(e, partialTicks).inflate(0.5);
	}
}
