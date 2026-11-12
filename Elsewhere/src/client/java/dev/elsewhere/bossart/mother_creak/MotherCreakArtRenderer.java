package dev.elsewhere.bossart.mother_creak;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.client.render.GlowLayer;
import dev.elsewhere.palehollow.GearStats;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/**
 * Mother Creak renderer: art model + resin/eye glow layer. Animation states are started on the client from her
 * synced data (awake, watched, claw/slam/stun timers, phase), so the boss class needs no changes.
 */
public class MotherCreakArtRenderer extends MobRenderer<MotherCreak, MotherCreakArtState, MotherCreakArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Elsewhere.id("boss/mother_creak"), "main");
	public static final float SCALE = 3.6F;
	private static final Identifier TEXTURE = Elsewhere.id("textures/entity/boss/mother_creak.png");
	private static final Identifier GLOW = Elsewhere.id("textures/entity/boss/mother_creak_glow.png");
	private static final int CLAW_TOTAL = MotherCreak.CLAW_WINDUP + 8;
	private static final int SLAM_TOTAL = 30;

	private final Map<MotherCreak, Anims> anims = new WeakHashMap<>();

	public MotherCreakArtRenderer(final EntityRendererProvider.Context ctx) {
		super(ctx, new MotherCreakArtModel(ctx.bakeLayer(LAYER)), 1.6F);
		this.addLayer(new GlowLayer<>(this, GLOW));
	}

	@Override
	public MotherCreakArtState createRenderState() {
		return new MotherCreakArtState();
	}

	@Override
	public void extractRenderState(final MotherCreak e, final MotherCreakArtState s, final float pt) {
		super.extractRenderState(e, s, pt);
		Anims a = this.anims.computeIfAbsent(e, k -> new Anims());
		int tc = e.tickCount;
		boolean awake = e.isAwake();
		int claw = e.clawTicks();
		int slam = e.slamTicks();
		int stun = e.stunTicks();
		if (a.fresh) {
			a.fresh = false;
		} else {
			if (awake && !a.awake) {
				a.roar.start(tc);
			}
			if (claw > a.claw) {
				a.clawAnim.start(tc - (CLAW_TOTAL - claw));
			}
			if (slam > a.slam) {
				a.burst.start(tc - (SLAM_TOTAL - slam));
			}
			if (stun > a.stun + 1) {
				a.clawAnim.stop();
				a.burst.stop();
				a.roar.stop();
				a.stunAnim.start(tc - (GearStats.MOTHER_CREAK_STUN_TICKS - stun));
			}
			if (stun == 0 && a.stun > 0 && e.isAlive()) {
				a.stunAnim.stop();
				a.roar.start(tc);
			}
		}
		if (stun > 0 && !a.stunAnim.isStarted()) {
			a.stunAnim.start(tc - (GearStats.MOTHER_CREAK_STUN_TICKS - stun));
		}
		boolean watched = e.isWatched();
		if (awake && watched && stun == 0 && claw == 0 && slam == 0 && tc >= a.nextTwitch) {
			a.twitch.start(tc);
			a.nextTwitch = tc + 45 + e.getRandom().nextInt(70);
		}
		a.awake = awake;
		a.claw = claw;
		a.slam = slam;
		a.stun = stun;
		s.awake = awake;
		s.watched = watched;
		s.fury = e.phase() == MotherCreak.PHASE_FURY;
		s.twitch.copyFrom(a.twitch);
		s.claw.copyFrom(a.clawAnim);
		s.burst.copyFrom(a.burst);
		s.stun.copyFrom(a.stunAnim);
		s.roar.copyFrom(a.roar);
	}

	@Override
	protected void scale(final MotherCreakArtState s, final PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(final MotherCreakArtState s) {
		return TEXTURE;
	}

	private static final class Anims {
		boolean fresh = true;
		boolean awake;
		int claw;
		int slam;
		int stun;
		int nextTwitch;
		final AnimationState twitch = new AnimationState();
		final AnimationState clawAnim = new AnimationState();
		final AnimationState burst = new AnimationState();
		final AnimationState stunAnim = new AnimationState();
		final AnimationState roar = new AnimationState();
	}
}
