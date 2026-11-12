package dev.elsewhere.rivals.hrafnholt.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Puts a boss skin on an {@link AvatarRenderState} (26.3 draws every such state with the vanilla avatar renderer, which samples {@code state.skin}).
 * Calls the skin-fix agent's helper {@code dev.elsewhere.kingdom.client.NpcSkin.apply} when the war lane's jar carries it; until then it sets the same
 * {@link PlayerSkin} itself, so the bosses render correctly with either kingdom jar.
 */
final class SkinBridge {
	private SkinBridge() {}

	private static final MethodHandle APPLY = find();

	private static MethodHandle find() {
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("dev.elsewhere.kingdom.client.NpcSkin"), "apply", MethodType.methodType(void.class, AvatarRenderState.class, Identifier.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	static void apply(AvatarRenderState state, Identifier texture) {
		if (texture == null) return;
		try {
			if (APPLY != null) {
				APPLY.invoke(state, texture);
				return;
			}
		} catch (Throwable ignored) {
		}
		state.skin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(texture, texture), null, null, PlayerModelType.WIDE);
	}
}
