package dev.elsewhere.bossart.hrolf;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.hrolf.HrolfBoss;
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
public class HrolfRenderer extends MobRenderer<HrolfBoss, HrolfRenderState, HrolfModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/hrolf"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/hrolf.png");
	private static final Identifier EMISSIVE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/hrolf_emissive.png");
	/** 32 model pixels = 2.9 blocks (the hitbox height). */
	public static final float SCALE = 1.45F;

	public HrolfRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HrolfModel(ctx.bakeLayer(LAYER)), 0.7F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> EMISSIVE, (s, age) -> 1.0F, new HrolfModel(ctx.bakeLayer(LAYER)), RenderTypes::eyes, false));
	}

	@Override
	public HrolfRenderState createRenderState() {
		return new HrolfRenderState();
	}

	@Override
	public void extractRenderState(HrolfBoss boss, HrolfRenderState s, float partial) {
		super.extractRenderState(boss, s, partial);
		float age = boss.tickCount + partial;
		s.attack = boss.currentAttack();
		s.riding = boss.isPassenger();
		long phaseMs = boss.phaseAnim.isStarted() ? boss.phaseAnim.getTimeInMillis(age) : Long.MAX_VALUE;
		s.transition = phaseMs < 2000;
		s.swing.copyFrom(boss.attackAnims[HrolfBoss.A_SWING]);
		s.overhead.copyFrom(boss.attackAnims[HrolfBoss.A_OVERHEAD]);
		s.kick.copyFrom(boss.attackAnims[HrolfBoss.A_KICK]);
		s.warcry.copyFrom(boss.attackAnims[HrolfBoss.A_WARCRY]);
		s.rally.copyFrom(boss.phaseAnim);
		AnimationState horn = boss.attackAnims[HrolfBoss.A_HORN];
		if (horn.isStarted()) {
			long ms = horn.getTimeInMillis(age);
			// The dismount horn is started with the phase change; the boss is frozen for the 2 s transition first.
			if (Math.abs(ms - phaseMs) < 150) ms -= 2000;
			s.hornMillis = ms;
		} else {
			s.hornMillis = -1;
		}
	}

	@Override
	public Identifier getTextureLocation(HrolfRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(HrolfRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}
}
