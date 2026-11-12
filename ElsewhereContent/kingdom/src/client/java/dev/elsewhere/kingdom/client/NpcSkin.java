package dev.elsewhere.kingdom.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Skin fix for every humanoid NPC that uses {@link AvatarRenderState} (kingdom soldiers, court rulers, serfs, duelists).
 * <p>
 * Root cause (26.3): {@code EntityRenderDispatcher.getRenderer(EntityRenderState)} sends every {@code AvatarRenderState}
 * to the vanilla player {@code AvatarRenderer}, whose {@code getTextureLocation} returns
 * {@code state.skin.body().texturePath()}. A mod renderer's own {@code getTextureLocation} and layers are never called for
 * such a state, and {@code state.skin} defaults to {@code DefaultPlayerSkin.getDefaultSkin()} (Steve). So the NPC's skin must
 * be put on {@code state.skin} in {@code extractRenderState}: call {@link #apply} after {@code super.extractRenderState}.
 */
public final class NpcSkin {
	private static final Map<Identifier, PlayerSkin> CACHE = new HashMap<>();

	private NpcSkin() {}

	/** @param texture full texture path, e.g. {@code elsewhere:textures/entity/kingdom/officer.png} */
	public static PlayerSkin of(Identifier texture) {
		if (CACHE.size() > 512) CACHE.clear();
		return CACHE.computeIfAbsent(texture, t -> PlayerSkin.insecure(new ClientAsset.ResourceTexture(t, t), null, null, PlayerModelType.WIDE));
	}

	/** Makes the vanilla avatar renderer draw {@code texture} (full path) on this state; null leaves the state alone. */
	public static void apply(AvatarRenderState state, Identifier texture) {
		if (texture != null) state.skin = of(texture);
	}
}
