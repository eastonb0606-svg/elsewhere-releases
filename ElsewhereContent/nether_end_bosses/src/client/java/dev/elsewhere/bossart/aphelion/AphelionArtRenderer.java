package dev.elsewhere.bossart.aphelion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.boss.aphelion.Aphelion;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Aphelion art renderer: the dragon-skull head (the hitbox) plus 21 body pieces laid along the head's recent flight path,
 * alternating plain obsidian and violet rift segments (as in the concept art) and ending in a finned tail. Segments taper,
 * roll in a slow travelling wave, glow on their fins and seams, and wither from the tail forward when she dies.
 */
public class AphelionArtRenderer extends BossRenderer<Aphelion, AphelionArtModel.State, AphelionArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/aphelion"), "main");
	public static final ModelLayerLocation SEGMENT_LAYER = new ModelLayerLocation(Reg.id("boss/aphelion"), "segment");
	public static final ModelLayerLocation TAIL_LAYER = new ModelLayerLocation(Reg.id("boss/aphelion"), "tail");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/aphelion_head.png");
	private static final RenderType SEG = RenderTypes.entityCutoutCull(Reg.id("textures/entity/boss/aphelion_segment.png"));
	private static final RenderType SEG_GLOW = RenderTypes.eyes(Reg.id("textures/entity/boss/aphelion_segment_glow.png"));
	private static final RenderType RIFT = RenderTypes.entityCutoutCull(Reg.id("textures/entity/boss/aphelion_segment_rift.png"));
	private static final RenderType RIFT_GLOW = RenderTypes.eyes(Reg.id("textures/entity/boss/aphelion_segment_rift_glow.png"));
	private static final double SPACING = 2.4;
	private static final int PIECES = Aphelion.SEGMENTS - 1;
	private final ModelPart segment;
	private final ModelPart tail;

	public AphelionArtRenderer(EntityRendererProvider.Context context) {
		super(context, new AphelionArtModel(context.bakeLayer(LAYER)), 0.0F, 4.0F, true);
		this.segment = context.bakeLayer(SEGMENT_LAYER);
		this.tail = context.bakeLayer(TAIL_LAYER);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/aphelion_head_glow.png")));
	}

	@Override
	public AphelionArtModel.State createRenderState() {
		return new AphelionArtModel.State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Aphelion entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(60);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public void extractRenderState(Aphelion entity, AphelionArtModel.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
		state.coiling = entity.hasFlag(Aphelion.FLAG_COILING);
		state.diving = entity.hasFlag(Aphelion.FLAG_DIVING);
		Vec3 head = entity.getPosition(partialTicks);
		Vec3 prev = head;
		for (int i = 0; i < PIECES; i++) {
			Vec3 p = entity.alongTrail(head, SPACING * (i + 1.4));
			Vec3 d = prev.subtract(p);
			float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) (-(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
			int k = i * 6;
			state.seg[k] = (float) (p.x - head.x);
			state.seg[k + 1] = (float) (p.y - head.y);
			state.seg[k + 2] = (float) (p.z - head.z);
			state.seg[k + 3] = yaw;
			state.seg[k + 4] = pitch;
			state.seg[k + 5] = 1.0F - 0.5F * i / PIECES;
			prev = p;
		}
	}

	@Override
	public void submit(AphelionArtModel.State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		float age = state.ageInTicks;
		float dying = state.deathTime > 0 ? Math.min(1.0F, state.deathTime / 20.0F) : 0.0F;
		for (int i = 0; i < PIECES; i++) {
			int k = i * 6;
			// on death the body withers from the tail forward
			float wither = Mth.clamp(dying * 2.0F - (float) (PIECES - 1 - i) / PIECES, 0.0F, 1.0F);
			float s = 4.0F * state.seg[k + 5] * (1.0F - 0.85F * wither);
			if (s <= 0.05F) continue;
			boolean last = i == PIECES - 1;
			boolean rift = !last && i % 2 == 1;
			// the coil makes the body ripple harder
			float wave = Mth.sin(age * (state.coiling ? 0.35F : 0.18F) - i * 0.55F);
			poseStack.pushPose();
			poseStack.translate(state.seg[k], state.seg[k + 1] + 1.4F - wither * 1.5F, state.seg[k + 2]);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.seg[k + 3]);
			poseStack.rotateDegrees(Axis.XP, state.seg[k + 4]);
			poseStack.rotateDegrees(Axis.ZP, wave * (state.coiling ? 14.0F : 7.0F));
			float breathe = 1.0F + 0.04F * Mth.sin(age * 0.2F - i * 0.8F);
			poseStack.scale(-s * breathe, -s * breathe, s);
			poseStack.translate(0.0F, 0.0F, 0.0F);
			ModelPart part = last ? tail : segment;
			collector.submitModelPart(part, poseStack, rift ? RIFT : SEG, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
			collector.submitModelPart(part, poseStack, rift ? RIFT_GLOW : SEG_GLOW, 0xF000F0, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}

	@Override
	public Identifier getTextureLocation(AphelionArtModel.State state) {
		return TEXTURE;
	}
}
