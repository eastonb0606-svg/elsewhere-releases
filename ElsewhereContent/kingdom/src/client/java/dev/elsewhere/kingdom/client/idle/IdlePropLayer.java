package dev.elsewhere.kingdom.client.idle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 3.0 polish P9: props for the idle/work poses, drawn from vanilla item models resolved at extraction (no new textures): a
 * broom (stripped spruce pole + hay head) for sweeping, and a carried load between the hands or balanced on the head (barrel,
 * timber, a pottery jar, a strongbox; a porter's own load). Only for {@link IdleAnim.Holder} states with a prop this frame,
 * which {@link IdleAnim} sets within 24 blocks only. Added to the vanilla avatar renderers through {@code AvatarLayers}.
 */
public class IdlePropLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public IdlePropLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (!(state instanceof IdleAnim.Holder h) || state.isInvisible) return;
		IdleAnim.Track t = h.idleTrack();
		if (t == null || !t.on || !t.propOn) return;
		PlayerModel m = getParentModel();
		int outline = state.outlineColor;
		switch (t.prop) {
			case IdleAnim.P_BROOM -> {
				ps.pushPose();
				m.translateToHand(state, t.mainArm, ps);
				// the handle runs through the fist and on down the line of the arm to the ground
				ps.translate(0F, 10F / 16F + 0.45F, 0F);
				ps.pushPose();
				ps.scale(0.07F, 1.4F, 0.07F);
				t.propA.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, outline);
				ps.popPose();
				ps.translate(0F, 0.68F, 0F);
				ps.scale(0.3F, 0.2F, 0.12F);
				t.propB.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, outline);
				ps.popPose();
			}
			case IdleAnim.P_LOAD_HANDS -> {
				ps.pushPose();
				m.body.translateAndRotate(ps);
				ps.translate(0F, 8F / 16F, -7F / 16F);
				ps.rotateDegrees(Axis.ZP, 180F); // model space is upside down: stand the load upright
				ps.scale(0.42F, 0.42F, 0.42F);
				t.propA.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, outline);
				ps.popPose();
			}
			case IdleAnim.P_LOAD_HEAD -> {
				ps.pushPose();
				m.body.translateAndRotate(ps);
				ps.translate(0F, -11.5F / 16F, 0F);
				ps.rotateDegrees(Axis.ZP, 180F);
				ps.scale(0.42F, 0.42F, 0.42F);
				t.propA.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, outline);
				ps.popPose();
			}
			default -> {
			}
		}
	}
}
