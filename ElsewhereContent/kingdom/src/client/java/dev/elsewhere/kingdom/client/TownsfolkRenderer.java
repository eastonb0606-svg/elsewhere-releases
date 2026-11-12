package dev.elsewhere.kingdom.client;

import dev.elsewhere.kingdom.armies.townsfolk.Townsfolk;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * {@code elsewhere:townsfolk}: the vanilla player model (wide arms) with the resident's kingdom/role/variant skin
 * ({@code textures/entity/townsfolk/<kingdom>/<role>_<n>.png}, TOWNSFOLK-IDS.md) and its held tool. 26.3 draws every
 * {@link AvatarRenderState} with the vanilla avatar renderer, so the skin goes onto {@code state.skin} ({@link NpcSkin}).
 * The overlay layers are dropped beyond 24 blocks (the crowd is cheap to draw); no nameplates.
 */
public class TownsfolkRenderer extends HumanoidMobRenderer<Townsfolk, AvatarRenderState, PlayerModel> {
	private static final Map<String, Identifier> PATHS = new HashMap<>();
	private static final Map<Integer, Identifier> BY_ID = new HashMap<>();

	public TownsfolkRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new dev.elsewhere.kingdom.client.idle.PosedAvatarState(); // 3.0 P9: carries the routine-pose track
	}

	static Identifier texture(String look) {
		if (PATHS.size() > 256) PATHS.clear();
		return PATHS.computeIfAbsent(look, Townsfolk::skinPath);
	}

	@Override
	public void extractRenderState(Townsfolk e, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(e, state, partialTicks);
		Identifier tex = texture(e.look());
		state.id = e.getId();
		NpcSkin.apply(state, tex); // the vanilla AvatarRenderer draws state.skin
		if (BY_ID.size() > 1024) BY_ID.clear();
		BY_ID.put(e.getId(), tex);
		boolean near = state.distanceToCameraSq < 24 * 24;
		state.showHat = state.showJacket = state.showLeftPants = state.showRightPants = state.showLeftSleeve = state.showRightSleeve = near;
		if (state instanceof dev.elsewhere.kingdom.client.idle.PosedAvatarState p) { // 3.0 P9: the routine pose (sit, work, carry, pray...) in its kingdom's style
			String look = e.look();
			int slash = look.indexOf('/');
			dev.elsewhere.kingdom.client.idle.IdleAnim.extract(e, p, p, e.idlePose(), dev.elsewhere.kingdom.client.idle.IdleAnim.styleOfFaction(slash > 0 ? look.substring(0, slash) : look), this.itemModelResolver);
		}
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		Identifier t = BY_ID.get(state.id);
		return t != null ? t : Townsfolk.skinPath(Townsfolk.DEFAULT_LOOK);
	}

	@Override
	protected boolean shouldShowName(Townsfolk e, double distSqr) {
		return e.hasCustomName() && super.shouldShowName(e, distSqr);
	}
}
