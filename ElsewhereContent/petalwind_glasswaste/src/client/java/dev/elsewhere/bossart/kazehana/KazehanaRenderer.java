package dev.elsewhere.bossart.kazehana;

import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.boss.kazehana.PetalMirage;
import dev.elsewhere.bossart.petalwind.BossArtPetalwindClient;
import dev.elsewhere.bossart.petalwind.GlowLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Monster;

/** Renders Kazehana and (translucent, petal-tinted) his Petal Mirages with the custom model, animations and glow. */
public class KazehanaRenderer<T extends Monster> extends MobRenderer<T, KazehanaRenderState, KazehanaModel> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/kazehana.png");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/kazehana_emissive.png");
	public static final float SCALE = 1.6F;
	private final boolean mirage;

	public KazehanaRenderer(EntityRendererProvider.Context ctx, boolean mirage) {
		super(ctx, new KazehanaModel(ctx.bakeLayer(BossArtPetalwindClient.KAZEHANA), mirage), mirage ? 0.0F : 0.8F);
		this.mirage = mirage;
		this.addLayer(new GlowLayer<>(this, GLOW));
	}

	@Override
	public KazehanaRenderState createRenderState() {
		return new KazehanaRenderState();
	}

	@Override
	public Identifier getTextureLocation(KazehanaRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(KazehanaRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // the death animation kneels and falls forward instead of the vanilla topple
	}

	@Override
	protected int getModelTint(KazehanaRenderState state) {
		return state.mirage ? 0xB0FFD8EC : -1;
	}

	@Override
	public void extractRenderState(T entity, KazehanaRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.mirage = this.mirage;
		if (entity instanceof Kazehana k) {
			state.duelState = k.state();
			state.dormant = k.isDormant();
			state.bow.copyFrom(k.bowAnimation);
			state.iaido.copyFrom(k.iaidoAnimation);
			state.dash.copyFrom(k.dashAnimation);
			state.combo.copyFrom(k.comboAnimation);
			state.parry.copyFrom(k.parryAnimation);
			state.stun.copyFrom(k.stunAnimation);
			state.phase.copyFrom(k.phaseAnimation);
			state.gale.copyFrom(k.galeAnimation);
		} else if (entity instanceof PetalMirage m) {
			state.duelState = m.state();
			state.dormant = false;
			state.iaido.copyFrom(m.iaidoAnimation);
			state.dash.copyFrom(m.dashAnimation);
		}
	}
}
