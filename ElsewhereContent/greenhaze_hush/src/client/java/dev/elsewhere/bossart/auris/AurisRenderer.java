package dev.elsewhere.bossart.auris;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.auris.Auris;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;

/** Custom renderer for Auris: cube model x2, sculk-teal skin and an emissive layer for the membranes and chest core. */
public class AurisRenderer extends MobRenderer<Auris, AurisRenderState, AurisModel> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/auris.png");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/auris_glow.png");
	public static final float SCALE = 2.0F;

	/** Client-side animation clocks per entity, started when the synced ANIM value changes. */
	private static final class Track {
		int last = -1;
		final AnimationState idle = new AnimationState(), listen = new AnimationState(), charge = new AnimationState(),
			smash = new AnimationState(), deaf = new AnimationState();
	}

	private final Map<Auris, Track> tracks = new WeakHashMap<>();

	public AurisRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new AurisModel(ctx.bakeLayer(AurisModel.LAYER)), 1.3F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, (s, age) -> s.glow,
			new AurisModel(ctx.bakeLayer(AurisModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public Identifier getTextureLocation(AurisRenderState state) {
		return TEXTURE;
	}

	@Override
	public AurisRenderState createRenderState() {
		return new AurisRenderState();
	}

	@Override
	public void extractRenderState(Auris e, AurisRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		Track t = tracks.computeIfAbsent(e, k -> new Track());
		int a = e.anim();
		int tick = e.tickCount;
		if (a != t.last) {
			if (a == Auris.ANIM_CHARGE) t.charge.start(tick);
			if (a == Auris.ANIM_SMASH) t.smash.start(tick);
			t.last = a;
		}
		t.idle.startIfStopped(0);
		t.listen.animateWhen(a == Auris.ANIM_LISTEN, tick);
		t.deaf.animateWhen(e.deafened(), tick);
		if (t.charge.isStarted() && t.charge.getTimeInMillis(tick) > 2300) t.charge.stop();
		if (t.smash.isStarted() && t.smash.getTimeInMillis(tick) > 1300) t.smash.stop();
		s.idleState.copyFrom(t.idle);
		s.listenState.copyFrom(t.listen);
		s.chargeState.copyFrom(t.charge);
		s.smashState.copyFrom(t.smash);
		s.deafState.copyFrom(t.deaf);
		s.roarState.copyFrom(e.roarAnim);
		s.deaf = e.deafened();
		s.anim = a;
		float age = s.ageInTicks;
		float glow = 0.8F + 0.2F * Mth.sin(age * 0.15F);
		if (a == Auris.ANIM_CHARGE) glow = 1F;
		if (s.deaf) glow = 0.35F + 0.3F * Math.abs(Mth.sin(age * 0.9F));
		if (s.deathTime > 0) glow *= Math.max(0F, 1F - s.deathTime / 20F);
		s.glow = Mth.clamp(glow, 0F, 1F);
	}

	@Override
	protected void scale(AurisRenderState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0F; // the death animation folds it to its knees instead of tipping over
	}
}
