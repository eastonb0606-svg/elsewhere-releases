package dev.elsewhere.bossart.percolax;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Percolax, the Hotbox Colossus: a glass water-pipe colossus. Stone legs and fists, a glowing water chamber in a glass shell,
 * a smoke-filled glass neck with a mouthpiece rim and lime eyes, a side downstem with a bowl and glowing ember, glass arms.
 * Boxes follow the hero boxes of percolax() in Ideas/art/src/bosses_art.py at 6 px per art unit.
 */
public class PercolaxArtModel extends EntityModel<PercolaxArtState> {
	private final ModelPart neck;
	private final KeyframeAnimation idle, walk, bigRip, exhale, stomp, flick, cough, close, roar, death;

	public PercolaxArtModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		ModelPart body = root.getChild("whole").getChild("body");
		this.neck = body.getChild("neck");
		this.idle = PercolaxArtAnimations.IDLE.bake(root);
		this.walk = PercolaxArtAnimations.WALK.bake(root);
		this.bigRip = PercolaxArtAnimations.BIG_RIP.bake(root);
		this.exhale = PercolaxArtAnimations.EXHALE.bake(root);
		this.stomp = PercolaxArtAnimations.STOMP.bake(root);
		this.flick = PercolaxArtAnimations.FLICK.bake(root);
		this.cough = PercolaxArtAnimations.COUGH.bake(root);
		this.close = PercolaxArtAnimations.CLOSE.bake(root);
		this.roar = PercolaxArtAnimations.ROAR.bake(root);
		this.death = PercolaxArtAnimations.DEATH.bake(root);
	}

	@Override
	public void setupAnim(PercolaxArtState state) {
		super.setupAnim(state);
		this.neck.yRot = Mth.clamp(state.yRot, -25F, 25F) * Mth.DEG_TO_RAD;
		this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.5F, 2.0F);
		this.idle.apply(state.idle, state.ageInTicks);
		this.bigRip.apply(state.bigRip, state.ageInTicks);
		this.exhale.apply(state.exhale, state.ageInTicks);
		this.stomp.apply(state.stomp, state.ageInTicks);
		this.flick.apply(state.flick, state.ageInTicks);
		this.cough.apply(state.cough, state.ageInTicks);
		this.close.apply(state.close, state.ageInTicks);
		this.roar.apply(state.roar, state.ageInTicks);
		if (state.deathTime > 0) this.death.apply((long) (state.deathTime * 50F), 1F);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition whole = root.addOrReplaceChild("whole", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition right_leg = whole.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(108, 103).addBox(-5.5F, 0F, -5.5F, 11F, 19F, 11F), PartPose.offsetAndRotation(-11F, -19F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition left_leg = whole.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(108, 103).addBox(-5.5F, 0F, -5.5F, 11F, 19F, 11F), PartPose.offsetAndRotation(11F, -19F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition body = whole.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -19F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition chamber = body.addOrReplaceChild("chamber", CubeListBuilder.create().texOffs(0, 103).addBox(-15F, -8F, -12F, 30F, 16F, 24F), PartPose.offsetAndRotation(0F, -7F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition shell = body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 58).addBox(-16F, -18F, -13F, 32F, 19F, 26F), PartPose.offsetAndRotation(0F, 0F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(84, 0).addBox(-10F, -35F, -9F, 20F, 36F, 18F).texOffs(0, 0).addBox(-11F, -36F, -10F, 22F, 38F, 20F), PartPose.offsetAndRotation(0F, -18F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition mouthpiece = neck.addOrReplaceChild("mouthpiece", CubeListBuilder.create().texOffs(152, 103).addBox(-14F, -5F, -12F, 28F, 5F, 24F), PartPose.offsetAndRotation(0F, -36F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition eyes = neck.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(136, 143).addBox(-7.3F, -1.5F, 0F, 5F, 3F, 1F).texOffs(136, 143).addBox(2.3F, -1.5F, 0F, 5F, 3F, 1F), PartPose.offsetAndRotation(0F, -24F, -10.6F, 0.0F, 0.0F, 0.0F));
		PartDefinition downstem = body.addOrReplaceChild("downstem", CubeListBuilder.create().texOffs(52, 143).addBox(-2F, -18F, -2F, 4F, 18F, 4F), PartPose.offsetAndRotation(15.5F, -7.5F, -9.5F, 0.0F, 0.0F, 0.87266F));
		PartDefinition bowl = downstem.addOrReplaceChild("bowl", CubeListBuilder.create().texOffs(68, 143).addBox(-5F, -6F, -5F, 10F, 6F, 10F).texOffs(108, 143).addBox(-3.5F, -7F, -3.5F, 7F, 2F, 7F), PartPose.offsetAndRotation(0F, -18F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition right_arm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(116, 58).addBox(-5F, -2F, -5F, 10F, 32F, 10F), PartPose.offsetAndRotation(-20.5F, -14.5F, 0F, -0.61087F, 0.0F, 0.31416F));
		PartDefinition right_fist = right_arm.addOrReplaceChild("right_fist", CubeListBuilder.create().texOffs(0, 143).addBox(-6.5F, -3F, -6.5F, 13F, 11F, 13F), PartPose.offsetAndRotation(0F, 30F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition left_arm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(116, 58).addBox(-5F, -2F, -5F, 10F, 32F, 10F), PartPose.offsetAndRotation(20.5F, -14.5F, 0F, -0.2618F, 0.0F, -0.31416F));
		PartDefinition left_fist = left_arm.addOrReplaceChild("left_fist", CubeListBuilder.create().texOffs(0, 143).addBox(-6.5F, -3F, -6.5F, 13F, 11F, 13F), PartPose.offsetAndRotation(0F, 30F, 0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
	}
}
