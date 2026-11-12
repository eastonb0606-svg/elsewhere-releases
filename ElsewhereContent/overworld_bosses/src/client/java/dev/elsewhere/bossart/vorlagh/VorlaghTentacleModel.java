package dev.elsewhere.bossart.vorlagh;

import dev.elsewhere.boss.vorlagh.VorlaghTentacle;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * One of Vorlagh's five tentacles: seven tapering segments (banded every third, pale sucker rows on the inner face,
 * the last two glow cyan on the emissive layer while it winds up a grab), rendered x2 (8 blocks). Keyframe
 * animations per tentacle state; a severed tentacle is a twitching three-segment stump with a raw cut.
 */
public class VorlaghTentacleModel extends EntityModel<VorlaghTentacleRenderState> {
	private final ModelPart[] seg = new ModelPart[7];
	private final KeyframeAnimation idle, raise, slam, recover, glow, grab, release, stump;

	public VorlaghTentacleModel(ModelPart root) {
		super(root);
		ModelPart p = root.getChild("root");
		for (int i = 0; i < 7; i++) {
			p = p.getChild("seg" + i);
			this.seg[i] = p;
		}
		this.idle = VorlaghAnimations.T_IDLE.bake(root);
		this.raise = VorlaghAnimations.T_RAISE.bake(root);
		this.slam = VorlaghAnimations.T_SLAM.bake(root);
		this.recover = VorlaghAnimations.T_RECOVER.bake(root);
		this.glow = VorlaghAnimations.T_GLOW.bake(root);
		this.grab = VorlaghAnimations.T_GRAB.bake(root);
		this.release = VorlaghAnimations.T_RELEASE.bake(root);
		this.stump = VorlaghAnimations.T_STUMP.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition p = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
		// {u, v, width, height}
		float[][] s = {{0, 0, 11, 10}, {44, 0, 10, 10}, {84, 0, 9, 9}, {0, 22, 8, 9}, {32, 22, 6, 9}, {56, 22, 5, 9}, {76, 22, 3, 8}};
		float y = 0;
		for (int i = 0; i < s.length; i++) {
			float w = s[i][2], h = s[i][3];
			p = p.addOrReplaceChild("seg" + i, CubeListBuilder.create().texOffs((int) s[i][0], (int) s[i][1]).addBox(-w / 2, -h, -w / 2, w, h, w),
				PartPose.offset(0, y, 0));
			y = -h;
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(VorlaghTentacleRenderState s) {
		super.setupAnim(s);
		for (int i = 3; i < 7; i++) this.seg[i].visible = !s.severed;
		if (s.severed) {
			this.stump.apply((long) ((s.ageInTicks + s.index * 7) * 50.0F), 1.0F);
			return;
		}
		boolean idleish = s.tentacleState == VorlaghTentacle.STATE_IDLE;
		this.idle.apply((long) ((s.ageInTicks + s.index * 13) * 50.0F), idleish ? 1.0F : 0.35F);
		switch (s.tentacleState) {
			case VorlaghTentacle.STATE_RAISE -> this.raise.apply(s.stateMillis, 1.0F);
			case VorlaghTentacle.STATE_SLAM -> this.slam.apply(s.stateMillis, 1.0F);
			case VorlaghTentacle.STATE_GLOW -> this.glow.apply(s.stateMillis, 1.0F);
			case VorlaghTentacle.STATE_GRAB -> this.grab.apply(s.stateMillis, 1.0F);
			default -> {
				if (s.stateMillis < 800L && s.prevState == VorlaghTentacle.STATE_SLAM) this.recover.apply(s.stateMillis, 1.0F);
				else if (s.stateMillis < 800L && (s.prevState == VorlaghTentacle.STATE_GRAB || s.prevState == VorlaghTentacle.STATE_GLOW))
					this.release.apply(s.stateMillis, 1.0F);
			}
		}
	}
}
