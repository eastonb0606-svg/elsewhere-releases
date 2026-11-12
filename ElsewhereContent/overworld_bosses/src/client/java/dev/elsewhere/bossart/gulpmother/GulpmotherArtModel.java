package dev.elsewhere.bossart.gulpmother;

import dev.elsewhere.bossart.overworld_a.OwaBossModel;
import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;

/** Old Gulpmother: a giant mangrove toad built from the concept-art boxes (see GulpmotherArtMesh). */
public class GulpmotherArtModel extends OwaBossModel<GulpmotherArtModel.State> {
	/** Model pixels per block at the renderer's scale. */
	static final float PX_PER_BLOCK = 16.0F / GulpmotherArtRenderer.SCALE;

	private final ModelPart head, tongue, tongueTip;

	public GulpmotherArtModel(ModelPart root) {
		super(root, GulpmotherArtAnimations.IDLE, GulpmotherArtAnimations.WALK, GulpmotherArtAnimations.ROAR, GulpmotherArtAnimations.DEATH, actions());
		ModelPart body = this.rootPart.getChild("body");
		this.head = body.getChild("head");
		this.tongue = body.getChild("tongue");
		this.tongueTip = body.getChild("tongue_tip");
	}

	private static AnimationDefinition[] actions() {
		AnimationDefinition[] a = new AnimationDefinition[8];
		a[GulpmotherEntity.ACT_TONGUE] = GulpmotherArtAnimations.TONGUE;
		a[GulpmotherEntity.ACT_FLOP] = GulpmotherArtAnimations.FLOP;
		a[GulpmotherEntity.ACT_SWALLOW] = GulpmotherArtAnimations.SWALLOW;
		a[GulpmotherEntity.ACT_SPIT] = GulpmotherArtAnimations.SPIT;
		a[GulpmotherEntity.ACT_EAT] = GulpmotherArtAnimations.EAT;
		a[GulpmotherEntity.ACT_EMERGE] = GulpmotherArtAnimations.EMERGE;
		return a;
	}

	@Override
	protected float walkSpeed() {
		return 1.5F;
	}

	@Override
	protected void afterAnim(State s) {
		this.head.yRot += Math.max(-25.0F, Math.min(25.0F, s.yRot)) * ((float) Math.PI / 180.0F) * 0.5F;
		// the synced tongue length (blocks) stretches the tongue out of the mouth toward the target
		if (s.tongue > 0.05F) {
			float px = s.tongue * PX_PER_BLOCK;
			this.tongue.zScale = Math.max(1.0F, (px + 6.0F) / 8.0F);
			this.tongueTip.z -= px;
			this.head.xRot = Math.min(this.head.xRot, -0.7F);
		}
	}

	public static class State extends dev.elsewhere.bossart.overworld_a.OwaRenderState {
		public float tongue;
		public float lift;
	}
}
