package dev.elsewhere.bossart.grumblegut;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.grumblegut.Grumblegut;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/**
 * Renders Grumblegut with its own moss boar-beast model. Attacks come from the boss's synced ANIM value, the phase-transition roar
 * from the health fraction crossing the boss's thresholds (60% / 30%). The red eyes glow except while it naps or dies.
 */
public class GrumblegutArtRenderer extends MobRenderer<Grumblegut, GrumblegutArtState, GrumblegutArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "grumblegut_art"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/grumblegut.png");
	private static final RenderType GLOW = RenderTypes.eyes(Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/grumblegut_glow.png"));
	private static final float SCALE = 1.15F;
	private static final float[] THRESHOLDS = {0.60F, 0.30F};

	private static final Map<Grumblegut, Track> TRACK = new WeakHashMap<>();

	private static final class Track {
		final AnimationState idle = new AnimationState(), paw = new AnimationState(), charge = new AnimationState(), munch = new AnimationState(),
			sniff = new AnimationState(), chew = new AnimationState(), slam = new AnimationState(), yawn = new AnimationState(),
			nap = new AnimationState(), puff = new AnimationState(), roar = new AnimationState();
		int anim = -1, phase = -1;

		void stopAttacks() {
			paw.stop(); charge.stop(); munch.stop(); sniff.stop(); chew.stop(); slam.stop(); yawn.stop(); nap.stop(); puff.stop();
		}
	}

	public GrumblegutArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GrumblegutArtModel(ctx.bakeLayer(LAYER)), 1.5F);
		this.addLayer(new Glow(this));
	}

	@Override
	public Identifier getTextureLocation(GrumblegutArtState state) {
		return TEXTURE;
	}

	@Override
	public GrumblegutArtState createRenderState() {
		return new GrumblegutArtState();
	}

	@Override
	public void extractRenderState(Grumblegut e, GrumblegutArtState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		Track t = TRACK.computeIfAbsent(e, k -> new Track());
		int tick = e.tickCount;
		t.idle.startIfStopped(tick);
		int a = e.anim();
		if (a != t.anim) {
			switch (a) {
				case Grumblegut.ANIM_PAW -> { t.stopAttacks(); t.paw.start(tick); }
				case Grumblegut.ANIM_CHARGE -> { t.stopAttacks(); t.charge.start(tick); }
				case Grumblegut.ANIM_MUNCH -> { t.stopAttacks(); t.munch.start(tick); }
				case Grumblegut.ANIM_SNIFF -> { t.stopAttacks(); t.sniff.start(tick); }
				case Grumblegut.ANIM_CHEW -> { t.stopAttacks(); t.chew.start(tick); }
				case Grumblegut.ANIM_SLAM -> { t.stopAttacks(); t.slam.start(tick); }
				case Grumblegut.ANIM_YAWN -> { t.stopAttacks(); t.yawn.start(tick); }
				case Grumblegut.ANIM_NAP -> { t.stopAttacks(); t.nap.start(tick); }
				case Grumblegut.ANIM_PUFF -> { t.stopAttacks(); t.puff.start(tick); }
				default -> { t.paw.stop(); t.charge.stop(); t.sniff.stop(); t.chew.stop(); t.nap.stop(); } // one-shots finish at rest
			}
			t.anim = a;
		}
		float frac = e.getMaxHealth() > 0 ? e.getHealth() / e.getMaxHealth() : 1F;
		int phase = 0;
		for (float th : THRESHOLDS) if (frac <= th) phase++;
		if (t.phase >= 0 && phase > t.phase && e.isAlive()) { t.stopAttacks(); t.roar.start(tick); }
		t.phase = phase;
		state.idle.copyFrom(t.idle);
		state.paw.copyFrom(t.paw);
		state.charge.copyFrom(t.charge);
		state.munch.copyFrom(t.munch);
		state.sniff.copyFrom(t.sniff);
		state.chew.copyFrom(t.chew);
		state.slam.copyFrom(t.slam);
		state.yawn.copyFrom(t.yawn);
		state.nap.copyFrom(t.nap);
		state.puff.copyFrom(t.puff);
		state.roar.copyFrom(t.roar);
		state.napping = a == Grumblegut.ANIM_NAP;
	}

	@Override
	protected void scale(GrumblegutArtState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	/** Glowing red eyes (closed while napping or dying). */
	static final class Glow extends RenderLayer<GrumblegutArtState, GrumblegutArtModel> {
		Glow(RenderLayerParent<GrumblegutArtState, GrumblegutArtModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, GrumblegutArtState state, float yRot, float xRot) {
			if (state.napping || state.deathTime > 0) return;
			collector.order(1).submitModel(getParentModel(), state, pose, GLOW, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
		}
	}
}
