package dev.elsewhere.kingdom_court.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.client.KingdomSoldierRenderer;
import dev.elsewhere.kingdom.client.TabardLayer;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The six rulers: the soldier renderer plus a cape in the ruler's colours. The cape is vanilla's cape model (one box, no
 * animation cost beyond the player cape's) drawn with K-A's white 64x32 tabard texture tinted by the ruler's colour, and
 * only within 48 blocks.
 */
public class CourtRulerRenderer extends KingdomSoldierRenderer {
	public CourtRulerRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	/**
	 * 26.3 draws every avatar render state with vanilla's player renderer, so the cape is registered with the kingdom module's shared mechanism
	 * ({@code dev.elsewhere.kingdom.client.AvatarLayers.add}, kingdom 0.4.2+), which adds it to the avatar renderers. Looked up by name so this module still
	 * loads against an older kingdom jar (the cape is then simply not drawn). The layer draws for {@link RulerState} only.
	 */
	@SuppressWarnings("unchecked")
	public static void registerCapeLayer() {
		try {
			Class<?> layers = Class.forName("dev.elsewhere.kingdom.client.AvatarLayers");
			Class<?> factory = Class.forName("dev.elsewhere.kingdom.client.AvatarLayers$Factory");
			Object f = java.lang.reflect.Proxy.newProxyInstance(factory.getClassLoader(), new Class<?>[] {factory}, (proxy, method, args) -> {
				if (!method.getName().equals("create")) return method.getDefaultValue();
				var parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) args[0];
				var models = (net.minecraft.client.model.geom.EntityModelSet) args[1];
				return new RulerCapeLayer(parent, new PlayerCapeModel(models.bakeLayer(ModelLayers.PLAYER_CAPE)));
			});
			layers.getMethod("add", factory).invoke(null, f);
		} catch (ReflectiveOperationException | LinkageError e) {
			dev.elsewhere.kingdom_court.CourtContent.LOG.info("ruler cape layer not registered (kingdom jar without AvatarLayers): {}", e.toString());
		}
	}

	/** Cape colour (0xRRGGBB) per ruler. */
	public static int capeColor(Ruler r) {
		return switch (r) {
			case KING -> 0x24408E;
			case MARSHAL -> 0xA32A2A;
			case CASTELLAN -> 0x56677F;
			case MASTER_AT_ARMS -> 0xB9822A;
			case HUNTSMAN -> 0x2F5D2A;
			case CHANCELLOR -> 0x4B2A6B;
		};
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new RulerState();
	}

	@Override
	public void extractRenderState(KingdomSoldier e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		if (state instanceof RulerState s) s.cape = e instanceof CourtRuler r ? 0xFF000000 | capeColor(r.ruler) : 0;
	}

	public static class RulerState extends SoldierRenderState {
		public int cape;
	}

	static final class RulerCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final HumanoidModel<AvatarRenderState> model;

		RulerCapeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, HumanoidModel<AvatarRenderState> model) {
			super(parent);
			this.model = model;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			if (state.isInvisible || !(state instanceof RulerState s) || s.cape == 0 || state.distanceToCameraSq > 48.0 * 48.0) return;
			poseStack.pushPose();
			if (!state.chestEquipment.isEmpty()) poseStack.translate(0.0F, -0.053125F, 0.06875F);
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TabardLayer.TEXTURE), light, OverlayTexture.NO_OVERLAY, s.cape, null, state.outlineColor);
			poseStack.popPose();
		}
	}
}
