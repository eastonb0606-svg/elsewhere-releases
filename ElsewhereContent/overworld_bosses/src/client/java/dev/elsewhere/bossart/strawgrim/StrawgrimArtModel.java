package dev.elsewhere.bossart.strawgrim;

import dev.elsewhere.bossart.overworld_a.OwaBossModel;
import dev.elsewhere.bossart.overworld_a.OwaRenderState;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;

/** Strawgrim, the Hollow Shepherd: a scarecrow on one post with a pumpkin head and a long scythe (see StrawgrimArtMesh). */
public class StrawgrimArtModel extends OwaBossModel<StrawgrimArtModel.State> {
	private final ModelPart head;

	public StrawgrimArtModel(ModelPart root) {
		super(root, StrawgrimArtAnimations.IDLE, StrawgrimArtAnimations.WALK, StrawgrimArtAnimations.ROAR, StrawgrimArtAnimations.DEATH, actions());
		this.head = this.rootPart.getChild("body").getChild("head");
	}

	private static AnimationDefinition[] actions() {
		AnimationDefinition[] a = new AnimationDefinition[8];
		a[StrawgrimEntity.ACT_REAP] = StrawgrimArtAnimations.REAP;
		a[StrawgrimEntity.ACT_FURROW] = StrawgrimArtAnimations.FURROW;
		a[StrawgrimEntity.ACT_CROWS] = StrawgrimArtAnimations.CROWS;
		a[StrawgrimEntity.ACT_VOLLEY] = StrawgrimArtAnimations.VOLLEY;
		a[StrawgrimEntity.ACT_HOP] = StrawgrimArtAnimations.HOP;
		a[StrawgrimEntity.ACT_IGNITE] = StrawgrimArtAnimations.ROAR;
		return a;
	}

	@Override
	protected void afterAnim(State s) {
		this.head.yRot += Math.max(-40.0F, Math.min(40.0F, s.yRot)) * ((float) Math.PI / 180.0F);
		this.head.xRot += s.xRot * ((float) Math.PI / 180.0F) * 0.5F;
		// phase 3: the pumpkin head has been torn off and flies on its own (StrawgrimHead entity)
		this.head.visible = !s.headless;
	}

	public static class State extends OwaRenderState {
		public boolean headless;
		public boolean burning;
	}
}
