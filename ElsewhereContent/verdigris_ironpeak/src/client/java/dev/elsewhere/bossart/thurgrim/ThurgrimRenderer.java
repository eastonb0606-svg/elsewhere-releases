package dev.elsewhere.bossart.thurgrim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.thurgrim.ThurgrimBoss;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class ThurgrimRenderer extends MobRenderer<ThurgrimBoss, ThurgrimRenderState, ThurgrimModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/thurgrim"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/thurgrim.png");
	private static final Identifier EMISSIVE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/thurgrim_emissive.png");
	/** 32 model pixels tall body = 5.8 blocks (the hitbox height). */
	public static final float SCALE = 2.9F;

	public ThurgrimRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new ThurgrimModel(ctx.bakeLayer(LAYER)), 1.4F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> EMISSIVE, (s, age) -> 1.0F, new ThurgrimModel(ctx.bakeLayer(LAYER)), RenderTypes::eyes, false));
	}

	@Override
	public ThurgrimRenderState createRenderState() {
		return new ThurgrimRenderState();
	}

	@Override
	public void extractRenderState(ThurgrimBoss boss, ThurgrimRenderState s, float partial) {
		super.extractRenderState(boss, s, partial);
		s.attack = boss.currentAttack();
		s.transition = boss.phaseAnim.isStarted() && boss.phaseAnim.getTimeInMillis(boss.tickCount + partial) < 2000;
		copy(boss.attackAnims[ThurgrimBoss.A_SPLIT], s.split);
		copy(boss.attackAnims[ThurgrimBoss.A_ROAR], s.roar);
		copy(boss.attackAnims[ThurgrimBoss.A_COMBO], s.combo);
		copy(boss.attackAnims[ThurgrimBoss.A_BOOMERANG], s.boomerang);
		s.command.copyFrom(boss.phaseAnim);
	}

	private static void copy(AnimationState from, AnimationState to) {
		to.copyFrom(from);
	}

	@Override
	public Identifier getTextureLocation(ThurgrimRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(ThurgrimRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // the death animation kneels instead of the vanilla topple
	}
}
