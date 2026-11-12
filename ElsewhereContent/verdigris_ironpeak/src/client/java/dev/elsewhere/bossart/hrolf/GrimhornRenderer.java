package dev.elsewhere.bossart.hrolf;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.hrolf.GrimhornEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class GrimhornRenderer extends MobRenderer<GrimhornEntity, GrimhornRenderState, GrimhornModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/grimhorn"), "main");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/grimhorn.png");
	private static final Identifier EMISSIVE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/boss/grimhorn_emissive.png");
	/** Saddle top lands where Hrolf sits (about 3.5 blocks up). */
	public static final float SCALE = 2.7F;

	public GrimhornRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GrimhornModel(ctx.bakeLayer(LAYER)), 1.5F);
		this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> EMISSIVE, (s, age) -> 1.0F, new GrimhornModel(ctx.bakeLayer(LAYER)), RenderTypes::eyes, false));
	}

	@Override
	public GrimhornRenderState createRenderState() {
		return new GrimhornRenderState();
	}

	@Override
	public void extractRenderState(GrimhornEntity goat, GrimhornRenderState s, float partial) {
		super.extractRenderState(goat, s, partial);
		s.goatState = goat.state();
	}

	@Override
	public Identifier getTextureLocation(GrimhornRenderState s) {
		return TEXTURE;
	}

	@Override
	protected void scale(GrimhornRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
