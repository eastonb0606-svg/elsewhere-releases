package dev.elsewhere.bossart.lithara;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.lithara.Lithara;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;

/** Custom renderer for Lithara: cube model x2.9, robe/stone/amethyst skin, emissive eyes, hand crystals and spires. */
public class LitharaRenderer extends MobRenderer<Lithara, LitharaRenderState, LitharaModel> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/lithara.png");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/lithara_glow.png");
	public static final float SCALE = 2.9F;

	private static final class Track {
		final AnimationState idle = new AnimationState(), sing = new AnimationState(), conduct = new AnimationState(), shattered = new AnimationState();
	}

	private final Map<Lithara, Track> tracks = new WeakHashMap<>();

	public LitharaRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new LitharaModel(ctx.bakeLayer(LitharaModel.LAYER)), 1.0F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> GLOW, (s, age) -> s.glow,
			new LitharaModel(ctx.bakeLayer(LitharaModel.LAYER)), RenderTypes::entityTranslucentEmissive, false));
	}

	@Override
	public Identifier getTextureLocation(LitharaRenderState state) {
		return TEXTURE;
	}

	@Override
	public LitharaRenderState createRenderState() {
		return new LitharaRenderState();
	}

	@Override
	public void extractRenderState(Lithara e, LitharaRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		Track t = tracks.computeIfAbsent(e, k -> new Track());
		int a = e.anim();
		int tick = e.tickCount;
		t.idle.startIfStopped(0);
		t.sing.animateWhen(a == Lithara.ANIM_SING, tick);
		t.conduct.animateWhen(a == Lithara.ANIM_CONDUCT, tick);
		t.shattered.animateWhen(a == Lithara.ANIM_SHATTERED, tick);
		s.idleState.copyFrom(t.idle);
		s.singState.copyFrom(t.sing);
		s.conductState.copyFrom(t.conduct);
		s.shatteredState.copyFrom(t.shattered);
		s.psalmState.copyFrom(e.psalmAnim);
		s.rainState.copyFrom(e.rainAnim);
		s.shardsState.copyFrom(e.shardsAnim);
		s.vexState.copyFrom(e.vexAnim);
		s.roarState.copyFrom(e.roarAnim);
		s.armored = e.armored();
		s.anim = a;
		float age = s.ageInTicks;
		float glow = 0.82F + 0.18F * Mth.sin(age * 0.12F);
		if (a == Lithara.ANIM_SING) glow = 0.7F + 0.3F * Mth.cos((age % 16F) / 16F * Mth.TWO_PI);
		if (!s.armored) glow = 0.45F + 0.25F * Math.abs(Mth.sin(age * 0.7F));
		if (s.deathTime > 0) glow *= Math.max(0F, 1F - s.deathTime / 20F);
		s.glow = Mth.clamp(glow, 0F, 1F);
	}

	@Override
	protected void scale(LitharaRenderState state, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0F; // she sinks and crumbles instead of tipping over
	}
}
