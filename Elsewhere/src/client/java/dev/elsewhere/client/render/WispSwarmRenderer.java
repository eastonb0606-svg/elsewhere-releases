package dev.elsewhere.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.core.s3.groups.Swarm3;
import dev.elsewhere.palehollow.entity.EyeblossomWisp;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

/**
 * Swarm-as-one wisps (ARMIES.md 6.1 rule 3, s-spawngroups, 3.0.0): one {@code elsewhere:eyeblossom_wisp} entity draws its
 * 3-6 wisps (the 2.1 model, glow layer included), one per member still alive ({@link Swarm3#alive}, from synced health).
 * The swarm's wider hitbox (SCALE) is cancelled for the wisps themselves. LOD: at most 3 wisps beyond 32 blocks.
 * A plain wisp (kill switch off, an old save, a Mother Creak add) draws exactly as in 2.1.
 */
public class WispSwarmRenderer extends Renderers.Wisp {
	static final class State extends States.Wisp {
		int drawn = 1;
		int seed;
	}

	public WispSwarmRenderer(final EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public States.Wisp createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final EyeblossomWisp e, final States.Wisp s, final float pt) {
		super.extractRenderState(e, s, pt);
		if (s instanceof State w) {
			int alive = Swarm3.alive(e, 0.5);
			w.drawn = s.distanceToCameraSq > 32 * 32 ? Math.min(alive, 3) : alive;
			w.seed = e.getId();
		}
	}

	@Override
	protected void scale(final States.Wisp s, final PoseStack pose) {
		super.scale(s, pose);
		if (s.scale > 0.01F && s.scale != 1F) pose.scale(1F / s.scale, 1F / s.scale, 1F / s.scale);
	}

	@Override
	public void submit(final States.Wisp s, final PoseStack pose, final SubmitNodeCollector c, final CameraRenderState cam) {
		int n = s instanceof State w ? w.drawn : 1;
		if (n <= 1) {
			super.submit(s, pose, c, cam);
			return;
		}
		int seed = ((State) s).seed;
		Component name = s.nameTag;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			double a = i * 2.39996 + seed * 0.7 + s.ageInTicks * 0.05 * (i % 2 == 0 ? 1 : -1);
			double r = 0.9 * Math.sqrt((i + 0.5) / n);
			pose.translate(Math.cos(a) * r, Math.sin(s.ageInTicks * 0.12 + i * 1.9) * 0.3, Math.sin(a) * r);
			super.submit(s, pose, c, cam);
			pose.popPose();
			s.nameTag = null;
		}
		s.nameTag = name;
	}
}
