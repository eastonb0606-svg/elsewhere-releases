package dev.elsewhere.kingdom.client.idle;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 polish P9: an avatar render state that carries an idle track, for NPC renderers that otherwise use a plain
 * {@link AvatarRenderState} (serfs; townsfolk can use it too). Return it from {@code createRenderState()} and call
 * {@link IdleAnim#extract} at the end of {@code extractRenderState}.
 */
public class PosedAvatarState extends AvatarRenderState implements IdleAnim.Holder {
	public IdleAnim.@Nullable Track idle;

	@Override
	public IdleAnim.@Nullable Track idleTrack() {
		return idle;
	}

	@Override
	public void idleTrack(IdleAnim.@Nullable Track t) {
		this.idle = t;
	}
}
