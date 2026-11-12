package dev.elsewhere.greenhaze_hush;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.greenhaze.GreenhazeMobs;
import dev.elsewhere.greenhaze_hush.s3.groups.Swarm3;
import dev.elsewhere.hush.HushMobs;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

/**
 * Swarm-as-one renderers (ARMIES.md 6.1 rule 3, s-spawngroups, 3.0.0): one entity, many members drawn. The members still
 * alive come from the entity's synced health ({@link Swarm3#alive}); the swarm's wider hitbox (SCALE) is cancelled for the
 * members themselves, which keep their 2.1 size. Distance LOD: at most 6 members beyond 24 blocks, 3 beyond 48. A plain mob
 * (kill switch off, an old save, a boss add) draws exactly as in 2.1.
 */
public final class Swarm3Renderers {
	private Swarm3Renderers() {}

	/** Members drawn after distance LOD. */
	static int lod(final int alive, final double distSq) {
		return distSq > 48 * 48 ? Math.min(alive, 3) : distSq > 24 * 24 ? Math.min(alive, 6) : alive;
	}

	/** Moves the pose to member {@code i}'s spot in a loose, drifting cloud of {@code n} around the entity. */
	static void place(final PoseStack pose, final int i, final int n, final int seed, final float age, final float radius) {
		if (n <= 1) return;
		double a = i * 2.39996 + seed * 0.7 + age * 0.045 * (i % 2 == 0 ? 1 : -1);
		double r = radius * Math.sqrt((i + 0.5) / n);
		double y = Math.sin(age * 0.13 + i * 1.7) * radius * 0.35;
		pose.translate(Math.cos(a) * r, y, Math.sin(a) * r);
	}

	// ------------------------------------------------------------------ shrieker swarm: 8-12 bats

	static final class BatSwarmState extends BatRenderState {
		int drawn = 1;
		int seed;
	}

	/** {@code elsewhere:shrieker_swarm}: the 2.1 bat look (Simple, scale 1.5), drawn once per living bat of the swarm. */
	static final class ShriekerSwarmRenderer extends GreenhazeHushClient.Simple<HushMobs.ShriekerSwarm, BatRenderState, BatModel> {
		ShriekerSwarmRenderer(final EntityRendererProvider.Context c) {
			super(c, new BatModel(c.bakeLayer(ModelLayers.BAT)), 0.2F, "shrieker_swarm", BatSwarmState::new, 1.5F);
		}

		@Override
		public void extractRenderState(final HushMobs.ShriekerSwarm e, final BatRenderState s, final float pt) {
			super.extractRenderState(e, s, pt);
			if (s instanceof BatSwarmState b) {
				b.drawn = lod(Swarm3.alive(e, 1.0 / 3.0), s.distanceToCameraSq);
				b.seed = e.getId();
			}
		}

		@Override
		protected void scale(final BatRenderState s, final PoseStack pose) {
			super.scale(s, pose);
			if (s.scale > 0.01F && s.scale != 1F) pose.scale(1F / s.scale, 1F / s.scale, 1F / s.scale);
		}

		@Override
		public void submit(final BatRenderState s, final PoseStack pose, final SubmitNodeCollector c, final CameraRenderState cam) {
			int n = s instanceof BatSwarmState b ? b.drawn : 1;
			int seed = s instanceof BatSwarmState b ? b.seed : 0;
			drawMany(s, n, seed, 1.4F, pose, () -> super.submit(s, pose, c, cam));
		}
	}

	// ------------------------------------------------------------------ hotbox smog: a bank of 3 puffs

	static final class SmogBankState extends GreenhazeHushClient.SmogState {
		int drawn = 1;
		int seed;
	}

	/** {@code elsewhere:hotbox_smog}: the 2.1 smog puff (SmogRenderer, slime size), drawn once per puff left in the bank. */
	static final class SmogBankRenderer extends GreenhazeHushClient.SmogRenderer {
		SmogBankRenderer(final EntityRendererProvider.Context c) {
			super(c);
		}

		@Override
		public GreenhazeHushClient.SmogState createRenderState() {
			return new SmogBankState();
		}

		@Override
		public void extractRenderState(final GreenhazeMobs.HotboxSmog e, final GreenhazeHushClient.SmogState s, final float pt) {
			super.extractRenderState(e, s, pt);
			if (s instanceof SmogBankState b) {
				b.drawn = lod(Swarm3.alive(e, 1.0), s.distanceToCameraSq);
				b.seed = e.getId();
			}
		}

		@Override
		protected void scale(final GreenhazeHushClient.SmogState s, final PoseStack pose) {
			super.scale(s, pose);
			if (s.scale > 0.01F && s.scale != 1F) pose.scale(1F / s.scale, 1F / s.scale, 1F / s.scale);
		}

		@Override
		public void submit(final GreenhazeHushClient.SmogState s, final PoseStack pose, final SubmitNodeCollector c, final CameraRenderState cam) {
			int n = s instanceof SmogBankState b ? b.drawn : 1;
			int seed = s instanceof SmogBankState b ? b.seed : 0;
			drawMany(s, n, seed, 1.0F * Math.max(1, s.size), pose, () -> super.submit(s, pose, c, cam));
		}
	}

	/** Submits the 2.1 model once per member; the name tag (if any) only on the first. */
	static void drawMany(final LivingEntityRenderState s, final int n, final int seed, final float radius, final PoseStack pose, final Runnable one) {
		if (n <= 1) {
			one.run();
			return;
		}
		Component name = s.nameTag;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			place(pose, i, n, seed, s.ageInTicks, radius);
			one.run();
			pose.popPose();
			s.nameTag = null;
		}
		s.nameTag = name;
	}
}
