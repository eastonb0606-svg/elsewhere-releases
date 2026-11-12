package dev.elsewhere.kingdom.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EntityTypes;

/**
 * Shared mechanism for feature layers on kingdom NPCs. In 26.3 {@code EntityRenderDispatcher} draws every
 * {@code AvatarRenderState} with vanilla's player {@code AvatarRenderer}, so layers added to our own renderers never run.
 * Register a factory here instead: it is added to the vanilla avatar renderers (wide and slim) when they are built.
 * <p>
 * <b>The layer must draw only for its own state type</b> ({@code if (!(state instanceof MyState)) return;}), because the same
 * renderer also draws real players. Call {@link #add} from your {@code ClientModInitializer} (any time before the first world load).
 * Example for the court: {@code AvatarLayers.add((parent, models) -> new RulerCapeLayer(parent, models));}
 */
public final class AvatarLayers {
	/** Builds a layer for one avatar renderer. */
	@FunctionalInterface
	public interface Factory {
		RenderLayer<AvatarRenderState, PlayerModel> create(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models);
	}

	private static final List<Factory> FACTORIES = new ArrayList<>();
	private static boolean hooked;

	private AvatarLayers() {}

	public static synchronized void add(Factory f) {
		FACTORIES.add(f);
		hook();
	}

	@SuppressWarnings("unchecked")
	private static void hook() {
		if (hooked) return;
		hooked = true;
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (type != EntityTypes.PLAYER || !(renderer instanceof AvatarRenderer<?> ar)) return;
			RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (RenderLayerParent<?, ?>) ar;
			List<Factory> list;
			synchronized (AvatarLayers.class) {
				list = new ArrayList<>(FACTORIES);
			}
			for (Factory f : list) helper.register(f.create(parent, ctx.getModelSet()));
		});
	}
}
