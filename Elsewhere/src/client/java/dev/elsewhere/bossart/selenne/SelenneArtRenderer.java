package dev.elsewhere.bossart.selenne;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.client.render.GlowLayer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/**
 * Selenne renderer: art model + iris/bloom glow layer. Animation states start on the client from her synced data
 * (open/closed, Moonbeam charge, phase, torn lids, Full Moon), so the boss class needs no changes.
 */
public class SelenneArtRenderer extends MobRenderer<Selenne, SelenneArtState, SelenneArtModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Elsewhere.id("boss/selenne"), "main");
	public static final float SCALE = 3.0F;
	private static final Identifier TEXTURE = Elsewhere.id("textures/entity/boss/selenne.png");
	private static final Identifier GLOW = Elsewhere.id("textures/entity/boss/selenne_glow.png");

	private final Map<Selenne, Anims> anims = new WeakHashMap<>();

	public SelenneArtRenderer(final EntityRendererProvider.Context ctx) {
		super(ctx, new SelenneArtModel(ctx.bakeLayer(LAYER)), 1.2F);
		this.addLayer(new GlowLayer<>(this, GLOW));
	}

	@Override
	public SelenneArtState createRenderState() {
		return new SelenneArtState();
	}

	@Override
	public void extractRenderState(final Selenne e, final SelenneArtState s, final float pt) {
		super.extractRenderState(e, s, pt);
		Anims a = this.anims.computeIfAbsent(e, k -> new Anims());
		int tc = e.tickCount;
		boolean open = e.isOpen();
		boolean torn = e.lidsTorn();
		int beam = e.beamCharge();
		int phase = e.phase();
		if (a.fresh) {
			a.fresh = false;
			if (open) {
				a.open.start(tc - 40);
			}
		} else {
			if (open != a.isOpen) {
				if (open) {
					a.close.stop();
					a.open.start(tc);
				} else {
					a.open.stop();
					a.close.start(tc);
				}
			}
			if (beam > a.beamTicks) {
				a.beam.start(tc - (Selenne.BEAM_WINDUP - beam));
			}
			if (phase > a.phase && phase == Selenne.PHASE_PETALS) {
				a.petals.start(tc);
			}
			if (torn && !a.torn) {
				a.tear.start(tc);
			}
		}
		a.isOpen = open;
		a.beamTicks = beam;
		a.phase = phase;
		a.torn = torn;
		s.awake = e.isAwake();
		s.fullMoon = e.fullMoon() > 0;
		s.lidsVisible = !torn || (a.tear.isStarted() && a.tear.getTimeInMillis(s.ageInTicks) < 1400L);
		s.open.copyFrom(a.open);
		s.close.copyFrom(a.close);
		s.beam.copyFrom(a.beam);
		s.petals.copyFrom(a.petals);
		s.tear.copyFrom(a.tear);
	}

	@Override
	protected void scale(final SelenneArtState s, final PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(final SelenneArtState s) {
		return TEXTURE;
	}

	private static final class Anims {
		boolean fresh = true;
		boolean isOpen;
		boolean torn;
		int beamTicks;
		int phase;
		final AnimationState open = new AnimationState();
		final AnimationState close = new AnimationState();
		final AnimationState beam = new AnimationState();
		final AnimationState petals = new AnimationState();
		final AnimationState tear = new AnimationState();
	}
}
