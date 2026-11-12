package dev.elsewhere.kingdom_villages.client;

import dev.elsewhere.kingdom_villages.Serf;
import dev.elsewhere.kingdom_villages.VillagesContent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Serf: the vanilla player model with the brown-tunic serf skin. */
public class SerfRenderer extends HumanoidMobRenderer<Serf, AvatarRenderState, PlayerModel> {
	public static final Identifier TEXTURE = VillagesContent.id("textures/entity/village/serf.png");

	/** Serf-tier variant by entity id (serf, thrall, porter, peat_cutter); the vanilla render state has no room for it. */
	private static final java.util.Map<Integer, String> VARIANTS = new java.util.HashMap<>();

	public SerfRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(Serf e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		if (VARIANTS.size() > 1024) VARIANTS.clear();
		state.id = e.getId();
		dev.elsewhere.kingdom.client.NpcSkin.apply(state, textureFor(e.variant)); // vanilla AvatarRenderer draws state.skin
		VARIANTS.put(e.getId(), e.variant);
		state.showHat = state.showJacket = state.showLeftPants = state.showRightPants = state.showLeftSleeve = state.showRightSleeve = true;
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return textureFor(VARIANTS.getOrDefault(state.id, "serf"));
	}

	private static Identifier textureFor(String variant) {
		return switch (variant) {
			case "thrall", "porter", "peat_cutter" -> VillagesContent.id("textures/entity/village/serf_" + variant + ".png");
			default -> TEXTURE;
		};
	}
}
