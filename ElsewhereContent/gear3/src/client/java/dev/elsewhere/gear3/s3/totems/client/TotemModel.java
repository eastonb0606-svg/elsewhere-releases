package dev.elsewhere.gear3.s3.totems.client;

import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The three totem rigs (vanilla-style cuboids on 64x64 textures; the UV boxes match tools/totems_art.py):
 * <ul>
 * <li>Bramble Totem: a bark post on a root flare, a fern crown and two thorn-vine arms that whip out on each lash.</li>
 * <li>Clockwork Sentry: a copper tripod, a turret head with a crossbow barrel and limbs that turns to its target and
 *     kicks back on each bolt, a brass winding key turning in its back, a lamp eye on top.</li>
 * <li>Sunglass Obelisk: a sandstone plinth and shaft with gold bands, a gilded pyramidion and a floating, turning
 *     sunglass lens that idles slowly when dormant.</li>
 * </ul>
 */
public class TotemModel extends EntityModel<TotemRenderer.State> {
	public enum Kind { BRAMBLE, SENTRY, OBELISK }

	public static final ModelLayerLocation BRAMBLE = new ModelLayerLocation(Gear3Content.id("bramble_totem"), "main");
	public static final ModelLayerLocation SENTRY = new ModelLayerLocation(Gear3Content.id("clockwork_sentry"), "main");
	public static final ModelLayerLocation OBELISK = new ModelLayerLocation(Gear3Content.id("azmar_sunglass_obelisk"), "main");

	static final float ARM_REST = 0.45F, LEG_SPLAY = 0.38F, LENS_Y = -4F;

	private final Kind kind;
	private ModelPart crown, leftArm, rightArm, fernA, fernB; // bramble
	private ModelPart head, barrel, key; // sentry
	private ModelPart lens; // obelisk

	public TotemModel(ModelPart root, Kind kind) {
		super(root);
		this.kind = kind;
		switch (kind) {
			case BRAMBLE -> {
				ModelPart post = root.getChild("post");
				crown = post.getChild("crown");
				leftArm = crown.getChild("left_arm");
				rightArm = crown.getChild("right_arm");
				fernA = crown.getChild("fern_a");
				fernB = crown.getChild("fern_b");
			}
			case SENTRY -> {
				ModelPart body = root.getChild("body");
				head = root.getChild("head");
				barrel = head.getChild("barrel");
				key = body.getChild("key");
			}
			case OBELISK -> lens = root.getChild("lens");
		}
	}

	public static LayerDefinition bramble() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("roots", CubeListBuilder.create().texOffs(0, 40).addBox(-4F, -2F, -4F, 8F, 2F, 8F), PartPose.offset(0F, 24F, 0F));
		PartDefinition post = root.addOrReplaceChild("post", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -16F, -3F, 6F, 16F, 6F), PartPose.offset(0F, 24F, 0F));
		PartDefinition crown = post.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(24, 0).addBox(-4F, -3F, -4F, 8F, 3F, 8F), PartPose.offset(0F, -16F, 0F));
		crown.addOrReplaceChild("fern_a", CubeListBuilder.create().texOffs(0, 24).addBox(-5F, -8F, 0F, 10F, 8F, 0F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, Mth.PI / 4F, 0F));
		crown.addOrReplaceChild("fern_b", CubeListBuilder.create().texOffs(20, 24).addBox(-5F, -8F, 0F, 10F, 8F, 0F), PartPose.offsetAndRotation(0F, -3F, 0F, 0F, -Mth.PI / 4F, 0F));
		crown.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 24).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(4F, -1F, 0F, 0F, 0F, -ARM_REST));
		crown.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 24).addBox(-1F, 0F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(-4F, -1F, 0F, 0F, 0F, ARM_REST));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition sentry() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(24, 10).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.offset(0F, 12F, 0F));
		body.addOrReplaceChild("key", CubeListBuilder.create().texOffs(48, 0).addBox(-2F, -2F, 0F, 4F, 4F, 1F), PartPose.offset(0F, 0F, 3.5F));
		for (int i = 0; i < 3; i++) {
			float yaw = i * Mth.TWO_PI / 3F;
			root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(i * 8, 10).addBox(-1F, 0F, -1F, 2F, 10F, 2F),
				PartPose.offsetAndRotation(Mth.sin(yaw) * 1.5F, 14.5F, Mth.cos(yaw) * 1.5F, LEG_SPLAY, yaw, 0F));
		}
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -5F, -3.5F, 7F, 5F, 7F), PartPose.offset(0F, 9F, 0F));
		PartDefinition barrel = head.addOrReplaceChild("barrel", CubeListBuilder.create().texOffs(28, 24).addBox(-1F, -1F, -6F, 2F, 2F, 6F), PartPose.offset(0F, -2.5F, -3.5F));
		barrel.addOrReplaceChild("limbs", CubeListBuilder.create().texOffs(28, 32).addBox(-6F, -0.5F, -0.5F, 12F, 1F, 1F), PartPose.offset(0F, 0F, -4.5F));
		head.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(44, 24).addBox(-1F, -2F, -1F, 2F, 2F, 2F), PartPose.offset(0F, -5F, 0.5F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition obelisk() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("plinth", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -3F, -5F, 10F, 3F, 10F), PartPose.offset(0F, 24F, 0F));
		PartDefinition shaft = root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 13).addBox(-3F, -18F, -3F, 6F, 18F, 6F), PartPose.offset(0F, 21F, 0F));
		shaft.addOrReplaceChild("band_low", CubeListBuilder.create().texOffs(0, 40).addBox(-3.5F, -1F, -3.5F, 7F, 1F, 7F), PartPose.offset(0F, -5F, 0F));
		shaft.addOrReplaceChild("band_high", CubeListBuilder.create().texOffs(28, 40).addBox(-3.5F, -1F, -3.5F, 7F, 1F, 7F), PartPose.offset(0F, -15F, 0F));
		shaft.addOrReplaceChild("pyramidion", CubeListBuilder.create().texOffs(24, 13).addBox(-2F, -3F, -2F, 4F, 3F, 4F), PartPose.offset(0F, -18F, 0F));
		root.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(48, 0).addBox(-2F, -2F, -0.5F, 4F, 4F, 1F), PartPose.offset(0F, LENS_Y, 0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(TotemRenderer.State s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		switch (kind) {
			case BRAMBLE -> {
				// a lash: both arms whip out and back over 8 ticks after each pulse
				float lash = s.pulseAge < 8F ? Mth.sin(s.pulseAge / 8F * Mth.PI) : 0F;
				float sway = Mth.sin(age * 0.06F) * 0.06F;
				leftArm.zRot = -ARM_REST - lash * 1.1F + sway;
				rightArm.zRot = ARM_REST + lash * 1.1F + sway;
				leftArm.xRot = -lash * 0.5F;
				rightArm.xRot = -lash * 0.5F;
				fernA.zRot = Mth.sin(age * 0.05F) * 0.08F;
				fernB.zRot = Mth.cos(age * 0.045F) * 0.08F;
				crown.yRot = Mth.sin(age * 0.02F) * 0.05F;
			}
			case SENTRY -> {
				if (s.hasAim) {
					head.yRot = s.aimYaw;
					head.xRot = s.aimPitch;
				} else {
					head.yRot = Mth.sin(age * 0.03F) * 0.9F; // idle sweep
					head.xRot = 0.05F;
				}
				float kick = s.pulseAge < 3F ? 1F - s.pulseAge / 3F : 0F;
				barrel.z = -3.5F + kick * 1.5F;
				key.zRot = age * 0.08F;
			}
			case OBELISK -> {
				float spin = s.lit ? 0.06F : 0.012F;
				lens.yRot = age * spin;
				lens.y = LENS_Y + Mth.sin(age * 0.08F) * 0.6F;
			}
		}
	}
}
