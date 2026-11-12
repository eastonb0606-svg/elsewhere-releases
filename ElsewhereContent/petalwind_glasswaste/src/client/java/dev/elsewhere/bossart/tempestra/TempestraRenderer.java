package dev.elsewhere.bossart.tempestra;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.tempestra.Tempestra;
import dev.elsewhere.bossart.petalwind.BossArtPetalwindClient;
import dev.elsewhere.bossart.petalwind.GlowLayer;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renders Tempestra with the custom storm model, procedural ring spin, keyframe attacks and glowing eyes/rods. */
public class TempestraRenderer extends MobRenderer<Tempestra, TempestraRenderState, TempestraModel> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/tempestra.png");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/tempestra_emissive.png");
	public static final float SCALE = 2.0F;
	private final Int2FloatOpenHashMap open = new Int2FloatOpenHashMap();

	public TempestraRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new TempestraModel(ctx.bakeLayer(BossArtPetalwindClient.TEMPESTRA)), 0.0F);
		this.addLayer(new GlowLayer<>(this, GLOW));
	}

	@Override
	public TempestraRenderState createRenderState() {
		return new TempestraRenderState();
	}

	@Override
	public Identifier getTextureLocation(TempestraRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(TempestraRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // she unravels in the air instead of toppling
	}

	@Override
	public void extractRenderState(Tempestra entity, TempestraRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float target = entity.shielded() ? 0.0F : 1.0F;
		float cur = open.getOrDefault(entity.getId(), target);
		cur += (target - cur) * 0.08F;
		if (open.size() > 32) open.clear();
		open.put(entity.getId(), cur);
		state.ringsOpen = cur;
		state.phase = Math.max(1, entity.phase());
		state.volley.copyFrom(entity.volleyAnimation);
		state.downburst.copyFrom(entity.downburstAnimation);
		state.phaseRoar.copyFrom(entity.phaseAnimation);
		state.bolt.copyFrom(entity.boltAnimation);
		state.strike.copyFrom(entity.strikeAnimation);
	}
}
