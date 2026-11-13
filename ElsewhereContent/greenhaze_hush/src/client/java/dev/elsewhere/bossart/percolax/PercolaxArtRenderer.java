package dev.elsewhere.bossart.percolax;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.percolax.Percolax;
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
 * Renders Percolax with its own glass-colossus model. Attacks come from the boss's synced ANIM value, the phase-transition
 * roar from the health fraction crossing the boss's thresholds (65% / 30%), strain colour from STRAIN, smoke-hiding from HIDDEN.
 */
public class PercolaxArtRenderer extends MobRenderer<Percolax, PercolaxArtState, PercolaxArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "percolax_art"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/percolax.png");
	private static final RenderType GLOW = RenderTypes.eyes(Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/percolax_glow.png"));
	private static final RenderType SMOKE = RenderTypes.eyes(Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/percolax_smoke.png"));
	private static final float SCALE = 1.54F;
	private static final float[] THRESHOLDS = {0.65F, 0.30F};
	/** Strain smoke tints: Sativa gold, Indica purple, Hybrid green; default pale haze green. */
	private static final int[] STRAIN_TINT = {0xFFFFC840, 0xFFB060FF, 0xFF70FF60};
	private static final int HAZE_TINT = 0xFFB8E0A8;

	private static final Map<Percolax, Track> TRACK = new WeakHashMap<>();

	private static final class Track {
		final AnimationState idle = new AnimationState(), bigRip = new AnimationState(), exhale = new AnimationState(),
			stomp = new AnimationState(), flick = new AnimationState(), cough = new AnimationState(), close = new AnimationState(),
			roar = new AnimationState();
		int anim = -1, phase = -1;

		void stopAttacks() {
			bigRip.stop(); exhale.stop(); stomp.stop(); flick.stop(); cough.stop(); close.stop();
		}
	}

	public PercolaxArtRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PercolaxArtModel(ctx.bakeLayer(LAYER)), 1.6F);
		this.addLayer(new Glow(this));
	}

	@Override
	public Identifier getTextureLocation(PercolaxArtState state) {
		return TEXTURE;
	}

	@Override
	public PercolaxArtState createRenderState() {
		return new PercolaxArtState();
	}

	@Override
	public void extractRenderState(Percolax e, PercolaxArtState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		Track t = TRACK.computeIfAbsent(e, k -> new Track());
		int tick = e.tickCount;
		t.idle.startIfStopped(tick);
		int a = e.anim();
		if (a != t.anim) {
			switch (a) {
				case Percolax.ANIM_RIP -> { t.stopAttacks(); t.bigRip.start(tick); }
				case Percolax.ANIM_EXHALE -> { t.stopAttacks(); t.exhale.start(tick); }
				case Percolax.ANIM_STOMP -> { t.stopAttacks(); t.stomp.start(tick); }
				case Percolax.ANIM_FLICK -> { t.stopAttacks(); t.flick.start(tick); }
				case Percolax.ANIM_COUGH -> { t.stopAttacks(); t.cough.start(tick); }
				case Percolax.ANIM_CLOSE -> { t.stopAttacks(); t.close.start(tick); }
				default -> { t.cough.stop(); t.close.stop(); t.bigRip.stop(); } // one-shots finish on their own, back at rest
			}
			t.anim = a;
		}
		float frac = e.getMaxHealth() > 0 ? e.getHealth() / e.getMaxHealth() : 1F;
		int phase = 0;
		for (float th : THRESHOLDS) if (frac <= th) phase++;
		if (t.phase >= 0 && phase > t.phase && e.isAlive()) { t.stopAttacks(); t.roar.start(tick); }
		t.phase = phase;
		state.idle.copyFrom(t.idle);
		state.bigRip.copyFrom(t.bigRip);
		state.exhale.copyFrom(t.exhale);
		state.stomp.copyFrom(t.stomp);
		state.flick.copyFrom(t.flick);
		state.cough.copyFrom(t.cough);
		state.close.copyFrom(t.close);
		state.roar.copyFrom(t.roar);
		state.strain = e.strain();
		state.strainShown = phase >= 2;
		state.hidden = e.hidden();
		state.ember = e.emberGlow();
	}

	@Override
	protected void scale(PercolaxArtState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0F; // the death animation slumps it instead of tipping over
	}

	@Override
	protected int getModelTint(PercolaxArtState state) {
		return state.hidden ? 0x1EFFFFFF : 0xFFFFFFFF; // fades into its smoke while hiding
	}

	/** Emissive water, eyes and ember, plus the smoke column tinted by strain. */
	static final class Glow extends RenderLayer<PercolaxArtState, PercolaxArtModel> {
		Glow(RenderLayerParent<PercolaxArtState, PercolaxArtModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, PercolaxArtState state, float yRot, float xRot) {
			int glow = state.ember || !state.hidden ? 0xFFFFFFFF : 0x28606060;
			collector.order(1).submitModel(getParentModel(), state, pose, GLOW, light, OverlayTexture.NO_OVERLAY, glow, null, state.outlineColor);
			int tint = state.strainShown ? STRAIN_TINT[Math.floorMod(state.strain, 3)] : HAZE_TINT;
			if (state.deathTime > 0) tint = 0xFF404040;
			collector.order(1).submitModel(getParentModel(), state, pose, SMOKE, light, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor);
		}
	}
}
