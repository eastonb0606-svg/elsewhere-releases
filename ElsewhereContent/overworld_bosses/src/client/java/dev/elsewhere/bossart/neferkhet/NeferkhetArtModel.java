package dev.elsewhere.bossart.neferkhet;

import dev.elsewhere.bossart.overworld_a.OwaBossModel;
import dev.elsewhere.bossart.overworld_a.OwaRenderState;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;

/** Neferkhet, the Sand-Bound King: a linen-wrapped mummy king in a nemes headdress with the sun staff (see NeferkhetArtMesh). */
public class NeferkhetArtModel extends OwaBossModel<NeferkhetArtModel.State> {
	private final ModelPart head;

	public NeferkhetArtModel(ModelPart root) {
		super(root, NeferkhetArtAnimations.IDLE, NeferkhetArtAnimations.WALK, NeferkhetArtAnimations.ROAR, NeferkhetArtAnimations.DEATH, actions());
		this.head = this.rootPart.getChild("head");
	}

	private static AnimationDefinition[] actions() {
		AnimationDefinition[] a = new AnimationDefinition[8];
		a[NeferkhetEntity.ACT_LANCE] = NeferkhetArtAnimations.LANCE;
		a[NeferkhetEntity.ACT_SINK] = NeferkhetArtAnimations.SINK;
		a[NeferkhetEntity.ACT_CURSE] = NeferkhetArtAnimations.CURSE;
		a[NeferkhetEntity.ACT_SWING] = NeferkhetArtAnimations.SWING;
		a[NeferkhetEntity.ACT_ECLIPSE] = NeferkhetArtAnimations.ROAR;
		a[NeferkhetEntity.ACT_SARCO] = NeferkhetArtAnimations.SARCO;
		return a;
	}

	@Override
	protected float walkSpeed() {
		return 1.2F;
	}

	@Override
	protected void afterAnim(State s) {
		this.head.yRot += Math.max(-45.0F, Math.min(45.0F, s.yRot)) * ((float) Math.PI / 180.0F);
		this.head.xRot += s.xRot * ((float) Math.PI / 180.0F) * 0.6F;
	}

	public static class State extends OwaRenderState {
		public boolean eclipse;
	}
}
