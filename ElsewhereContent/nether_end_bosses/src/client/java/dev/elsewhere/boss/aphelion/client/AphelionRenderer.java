package dev.elsewhere.boss.aphelion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.boss.aphelion.Aphelion;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders Aphelion's head (the hitbox) and lays the 21 body segments along the head's recent path. */
public class AphelionRenderer extends BossRenderer<Aphelion, AphelionRenderer.State, AphelionRenderer.Model> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("aphelion"), "main");
	public static final ModelLayerLocation SEGMENT_LAYER = new ModelLayerLocation(Reg.id("aphelion"), "segment");
	private static final Identifier TEXTURE = Reg.id("textures/entity/aphelion/aphelion.png");
	private static final Identifier SEGMENT_TEXTURE = Reg.id("textures/entity/aphelion/aphelionsegment.png");
	private static final double SPACING = 2.4;
	private final ModelPart segment;

	public static class State extends BossRenderState {
		public final float[] seg = new float[(Aphelion.SEGMENTS - 1) * 6];
	}

	public AphelionRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.0F, 4.0F, true);
		this.segment = context.bakeLayer(SEGMENT_LAYER);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Aphelion entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(60);
	}

	@Override
	public void extractRenderState(Aphelion entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Vec3 head = entity.getPosition(partialTicks);
		Vec3 prev = head;
		for (int i = 0; i < Aphelion.SEGMENTS - 1; i++) {
			Vec3 p = entity.alongTrail(head, SPACING * (i + 1.4));
			Vec3 d = prev.subtract(p);
			float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) (-(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
			float scale = 1.0F - 0.55F * i / (Aphelion.SEGMENTS - 1);
			int k = i * 6;
			state.seg[k] = (float) (p.x - head.x);
			state.seg[k + 1] = (float) (p.y - head.y);
			state.seg[k + 2] = (float) (p.z - head.z);
			state.seg[k + 3] = yaw;
			state.seg[k + 4] = pitch;
			state.seg[k + 5] = scale;
			prev = p;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		for (int i = 0; i < Aphelion.SEGMENTS - 1; i++) {
			int k = i * 6;
			float s = 4.0F * state.seg[k + 5];
			poseStack.pushPose();
			poseStack.translate(state.seg[k], state.seg[k + 1] + 1.2F, state.seg[k + 2]);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.seg[k + 3]);
			poseStack.rotateDegrees(Axis.XP, state.seg[k + 4]);
			poseStack.scale(-s, -s, s);
			poseStack.translate(0.0F, -18.0F / 16.0F, 0.0F);
			collector.submitModelPart(segment, poseStack, RenderTypes.entityCutoutCull(SEGMENT_TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	/** Jaw unhinges for bites and dives; the head tilts with its flight pitch. */
	public static class Model extends EntityModel<State> {
		private final ModelPart skull;
		private final ModelPart jaw;

		public Model(ModelPart root) {
			super(root);
			this.skull = root.getChild("skull");
			this.jaw = skull.getChild("jaw");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			skull.xRot = state.xRot * Mth.DEG_TO_RAD;
			boolean biting = "bite".equals(state.attack) || "devour_dive".equals(state.attack) || "crystal_feast".equals(state.attack);
			jaw.xRot = biting ? 0.6F + 0.2F * Mth.sin(state.ageInTicks * 0.6F) : 0.08F * Mth.sin(state.ageInTicks * 0.1F);
		}
	}
}
