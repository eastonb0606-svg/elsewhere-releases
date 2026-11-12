package dev.elsewhere.kingdom.client.heraldry;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.kingdom.client.KingdomSoldierRenderer;
import dev.elsewhere.kingdom.heraldry.Heraldry;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * 3.0 polish P10: officers' and captains' capes. Added to vanilla's avatar renderers through {@code AvatarLayers}; draws
 * only for a soldier state whose {@code capeTexture} was set by {@link SoldierHeraldry#extract} (kingdom heraldry, rank,
 * distance LOD and kill switch already decided there), so real players and every other avatar are skipped at once.
 * One model submit per caped soldier: the 8-panel waving cape near, the one-panel cape far.
 */
public final class SoldierCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final SoldierCapeModel wave, flat;

	public SoldierCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.wave = new SoldierCapeModel(models.bakeLayer(SoldierCapeModel.WAVE), Heraldry.CAPE_SEGMENTS);
		this.flat = new SoldierCapeModel(models.bakeLayer(SoldierCapeModel.FLAT), 1);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		// the final state decides: no cape without the tabard (soldier-detail or army LOD) or once the army LOD drops capes far away
		if (state.isInvisible || !state.showCape || !(state instanceof KingdomSoldierRenderer.SoldierRenderState s) || s.capeTexture == null || !s.showTabard) return;
		collector.submitModel(s.capeWave ? this.wave : this.flat, state, poseStack, SoldierHeraldry.capeType(s.capeTexture), light,
			LivingEntityRenderer.getOverlayCoords(state, 0.0F), s.heraldryTint, null, state.outlineColor);
	}
}
