package dev.elsewhere.bossart.ossyra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.boss.ossyra.Ossyra;
import dev.elsewhere.bossart.nether.GlowLayer;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.BossRenderer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Ossyra art renderer: the queen-shulker model at 4x (6 blocks), emissive pearl/crown/interior, hangs upside down on the ceiling. */
public class OssyraArtRenderer extends BossRenderer<Ossyra, OssyraArtRenderState, OssyraArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Reg.id("boss/ossyra"), "main");
	private static final Identifier TEXTURE = Reg.id("textures/entity/boss/ossyra.png");
	private final Map<Ossyra, float[]> open = new WeakHashMap<>();

	public OssyraArtRenderer(EntityRendererProvider.Context context) {
		super(context, new OssyraArtModel(context.bakeLayer(LAYER)), 2.5F, 4.0F, true);
		this.addLayer(new GlowLayer<>(this, Reg.id("textures/entity/boss/ossyra_glow.png")));
	}

	@Override
	public OssyraArtRenderState createRenderState() {
		return new OssyraArtRenderState();
	}

	@Override
	public void extractRenderState(Ossyra entity, OssyraArtRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		dev.elsewhere.bossart.nether.HurtAnim.extract(entity, state, partialTicks);
		float[] o = open.computeIfAbsent(entity, e -> new float[] {0.0F, -1.0F});
		float target = entity.hasFlag(Ossyra.FLAG_OPEN) || entity.isDeadOrDying() ? 1.0F : 0.0F;
		float now = entity.tickCount + partialTicks;
		float dt = o[1] < 0 ? 100.0F : Math.max(0.0F, now - o[1]);
		o[1] = now;
		o[0] += Mth.clamp(target - o[0], -0.12F * dt, 0.12F * dt);
		state.openAmount = o[0];
		state.onCeiling = entity.hasFlag(Ossyra.FLAG_ON_CEILING);
	}

	@Override
	protected void setupRotations(OssyraArtRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		if (state.onCeiling) {
			poseStack.translate(0.0F, state.boundingBoxHeight, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, 180.0F);
		}
		super.setupRotations(state, poseStack, bodyRot, scale);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // she cracks open instead of tipping over (see OssyraAnimations.DEATH)
	}

	@Override
	public Identifier getTextureLocation(OssyraArtRenderState state) {
		return TEXTURE;
	}
}
