package dev.elsewhere.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.boss.mother_creak.RootSpike;
import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.client.model.CreakingHuskModel;
import dev.elsewhere.client.model.EyeblossomWispModel;
import dev.elsewhere.client.model.MotherCreakModel;
import dev.elsewhere.client.model.RiftModel;
import dev.elsewhere.client.model.RootSpikeModel;
import dev.elsewhere.client.model.SelenneModel;
import dev.elsewhere.core.travel.RiftEntity;
import dev.elsewhere.palehollow.entity.CreakingHusk;
import dev.elsewhere.palehollow.entity.EyeblossomWisp;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** All Elsewhere entity renderers and their model layers. */
public final class Renderers {
	public static final ModelLayerLocation HUSK = layer("creaking_husk");
	public static final ModelLayerLocation WISP = layer("eyeblossom_wisp");
	public static final ModelLayerLocation MOTHER = layer("mother_creak");
	public static final ModelLayerLocation SELENNE = layer("selenne");
	public static final ModelLayerLocation SPIKE = layer("root_spike");
	public static final ModelLayerLocation RIFT = layer("rift");

	private Renderers() {
	}

	private static ModelLayerLocation layer(final String name) {
		return new ModelLayerLocation(Elsewhere.id(name), "main");
	}

	static Identifier tex(final String name) {
		return Elsewhere.id("textures/entity/" + name + ".png");
	}

	// ------------------------------------------------------------------ Creaking Husk
	public static class Husk extends MobRenderer<CreakingHusk, States.Husk, CreakingHuskModel> {
		public Husk(final EntityRendererProvider.Context ctx) {
			super(ctx, new CreakingHuskModel(ctx.bakeLayer(HUSK)), 0.6F);
			this.addLayer(new GlowLayer<>(this, tex("creaking_husk_glow")));
		}

		@Override
		public States.Husk createRenderState() {
			return new States.Husk();
		}

		@Override
		public void extractRenderState(final CreakingHusk e, final States.Husk s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.frozen = e.isFrozen();
		}

		@Override
		public Identifier getTextureLocation(final States.Husk s) {
			return tex("creaking_husk");
		}
	}

	// ------------------------------------------------------------------ Eyeblossom Wisp
	public static class Wisp extends MobRenderer<EyeblossomWisp, States.Wisp, EyeblossomWispModel> {
		public Wisp(final EntityRendererProvider.Context ctx) {
			super(ctx, new EyeblossomWispModel(ctx.bakeLayer(WISP)), 0.25F);
			this.addLayer(new GlowLayer<>(this, tex("eyeblossom_wisp_glow")));
		}

		@Override
		public States.Wisp createRenderState() {
			return new States.Wisp();
		}

		@Override
		public void extractRenderState(final EyeblossomWisp e, final States.Wisp s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.shriek = e.shriekTicks();
		}

		@Override
		public Identifier getTextureLocation(final States.Wisp s) {
			return tex("eyeblossom_wisp");
		}
	}

	// ------------------------------------------------------------------ Mother Creak
	public static class Mother extends MobRenderer<MotherCreak, States.Mother, MotherCreakModel> {
		public static final float SCALE = 2.8F;

		public Mother(final EntityRendererProvider.Context ctx) {
			super(ctx, new MotherCreakModel(ctx.bakeLayer(MOTHER)), 1.6F);
			this.addLayer(new GlowLayer<>(this, tex("mother_creak_glow")));
		}

		@Override
		public States.Mother createRenderState() {
			return new States.Mother();
		}

		@Override
		public void extractRenderState(final MotherCreak e, final States.Mother s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.awake = e.isAwake();
			s.watched = e.isWatched();
			s.stun = e.stunTicks();
			s.claw = e.clawTicks() > 0 ? e.clawTicks() - pt : 0;
			s.slam = e.slamTicks() > 0 ? e.slamTicks() - pt : 0;
			s.hearts = e.heartsLeft();
			s.phase = e.phase();
		}

		@Override
		protected void scale(final States.Mother s, final PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}

		@Override
		public Identifier getTextureLocation(final States.Mother s) {
			return tex("mother_creak");
		}
	}

	// ------------------------------------------------------------------ Selenne
	public static class SelenneR extends MobRenderer<Selenne, States.Selenne, SelenneModel> {
		public static final float SCALE = 3.0F;

		public SelenneR(final EntityRendererProvider.Context ctx) {
			super(ctx, new SelenneModel(ctx.bakeLayer(SELENNE)), 1.2F);
			this.addLayer(new GlowLayer<>(this, tex("selenne_glow")));
		}

		@Override
		public States.Selenne createRenderState() {
			return new States.Selenne();
		}

		@Override
		public void extractRenderState(final Selenne e, final States.Selenne s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.awake = e.isAwake();
			// lids ease toward the synced open/closed state
			float target = e.isOpen() ? 1.0F : 0.0F;
			float cur = e.lidOpenness;
			e.lidOpenness = cur + (target - cur) * 0.12F;
			s.open = Mth.clamp(e.lidOpenness, 0.0F, 1.0F);
			s.torn = e.lidsTorn();
			s.beam = e.beamCharge();
			s.fullMoon = e.fullMoon();
		}

		@Override
		protected void scale(final States.Selenne s, final PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}

		@Override
		protected float getFlipDegrees() {
			return 0.0F;
		}

		@Override
		public Identifier getTextureLocation(final States.Selenne s) {
			return tex("selenne");
		}
	}

	// ------------------------------------------------------------------ Root spike
	public static class Spike extends EntityRenderer<RootSpike, States.Spike> {
		private final RootSpikeModel model;

		public Spike(final EntityRendererProvider.Context ctx) {
			super(ctx);
			this.model = new RootSpikeModel(ctx.bakeLayer(SPIKE));
		}

		@Override
		public States.Spike createRenderState() {
			return new States.Spike();
		}

		@Override
		public void extractRenderState(final RootSpike e, final States.Spike s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.height = e.height(pt);
			s.yRot = e.getYRot();
		}

		@Override
		public void submit(final States.Spike s, final PoseStack pose, final SubmitNodeCollector out, final CameraRenderState camera) {
			pose.pushPose();
			pose.rotateDegrees(Axis.YP, 180.0F - s.yRot);
			pose.scale(-1.0F, -1.0F, 1.0F);
			pose.translate(0.0F, -1.501F, 0.0F);
			out.submitModel(this.model, s, pose, tex("root_spike"), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
			pose.popPose();
			super.submit(s, pose, out, camera);
		}
	}

	// ------------------------------------------------------------------ Rift
	public static class Rift extends EntityRenderer<RiftEntity, States.Rift> {
		private final RiftModel model;

		public Rift(final EntityRendererProvider.Context ctx) {
			super(ctx);
			this.model = new RiftModel(ctx.bakeLayer(RIFT));
		}

		@Override
		public States.Rift createRenderState() {
			return new States.Rift();
		}

		@Override
		public void extractRenderState(final RiftEntity e, final States.Rift s, final float pt) {
			super.extractRenderState(e, s, pt);
			s.color = e.color();
			s.age = e.clientAge + pt;
			s.state = e.state();
			s.yRot = e.getYRot();
		}

		@Override
		public void submit(final States.Rift s, final PoseStack pose, final SubmitNodeCollector out, final CameraRenderState camera) {
			pose.pushPose();
			pose.translate(0.0F, 1.3F, 0.0F);
			pose.rotateDegrees(Axis.YP, -s.yRot);
			float sc = 1.0F / 16.0F;
			pose.scale(sc, -sc, sc);
			out.submitModel(this.model, s, pose, RenderTypes.entityTranslucentEmissive(tex("rift")), 15728880, OverlayTexture.NO_OVERLAY,
				0xFF000000 | s.color, null, s.outlineColor);
			pose.popPose();
			super.submit(s, pose, out, camera);
		}
	}
}
