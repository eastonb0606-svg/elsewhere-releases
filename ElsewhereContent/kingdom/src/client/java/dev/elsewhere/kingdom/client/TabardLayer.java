package dev.elsewhere.kingdom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.kingdom.KingdomContent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** A tabard drawn over the armor (slightly inflated body box), tinted with the faction colour. One white texture. */
public class TabardLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final Identifier TEXTURE = KingdomContent.id("textures/entity/kingdom/tabard.png");
	private final HumanoidModel<AvatarRenderState> model;

	public TabardLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.model = new HumanoidModel<>(models.bakeLayer(KingdomClient.TABARD));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		// the vanilla avatar renderer also draws real players: only our soldiers get a tabard
		if (!(state instanceof KingdomSoldierRenderer.SoldierRenderState s) || !s.showTabard) return;
		// 3.0 P10: under a kingdom emblem the tabard drops its generic tower motif; the emblem is a full-colour overlay on the same model
		coloredCutoutModelCopyLayerRender(model, s.emblem != null ? dev.elsewhere.kingdom.heraldry.Heraldry.PLAIN_TABARD : TEXTURE, poseStack, collector, light, state, s.tabardColor, 2);
		if (s.emblem != null) coloredCutoutModelCopyLayerRender(model, s.emblem, poseStack, collector, light, state, s.heraldryTint, 3);
	}
}
